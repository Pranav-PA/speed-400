package dev.pranav.speed400garage.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The router (§10.1) — and the only part of the assistant that crosses the network.
 *
 * What it sends: the question text, and the tool schema (names, descriptions,
 * parameter types). What it does NOT send: any value from the database. That is the
 * §10.6 plan-then-render split, and it is the difference between a personal ten-year
 * record staying private and becoming somebody's training data.
 *
 * Model IDs are never hardcoded. Google retires models on a schedule — the 2.5 series
 * named in the plan is already legacy and 2.0 Flash is shut down — so the app asks the
 * API which models exist and lets the owner pick. A hardcoded ID is a bug with a
 * delayed fuse.
 */
@Singleton
class GeminiClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Lists the models this key can actually use, newest-looking first. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val body = get("$BASE/models?pageSize=200", apiKey)
        json.parseToJsonElement(body).jsonObject["models"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            .map { it.removePrefix("models/") }
            .filter { it.startsWith("gemini") }
            .sortedDescending()
    }

    /**
     * Asks the model which tool answers this question.
     *
     * Returns null when the model declines to call anything, which is a legitimate
     * outcome — "what's the weather" has no tool, and the app says so rather than
     * forcing a bad match.
     */
    suspend fun route(apiKey: String, model: String, question: String): ToolCall? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", question) }) }
                    }
                )
            }
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            Tools.declarations().forEach { add(declarationJson(it)) }
                        }
                    }
                )
            }
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", SYSTEM_INSTRUCTION) }) }
            }
            putJsonObject("generationConfig") { put("temperature", 0) }
        }

        val response = post("$BASE/models/$model:generateContent", apiKey, payload.toString())
        val parts = json.parseToJsonElement(response).jsonObject["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return@withContext null

        val fn = parts.firstNotNullOfOrNull { it.jsonObject["functionCall"]?.jsonObject }
            ?: return@withContext null

        ToolCall(
            name = fn["name"]?.jsonPrimitive?.content ?: return@withContext null,
            args = fn["args"]?.jsonObject.orEmpty()
                .mapValues { (_, v) -> runCatching { v.jsonPrimitive.content }.getOrDefault("") },
        )
    }

    private fun declarationJson(declaration: FunctionDeclaration): JsonObject = buildJsonObject {
        put("name", declaration.name)
        put("description", declaration.description)
        put("parameters", schemaJson(declaration.parameters))
    }

    private fun schemaJson(schema: Schema): JsonObject = buildJsonObject {
        put("type", schema.type)
        schema.description?.let { put("description", it) }
        schema.properties?.takeIf { it.isNotEmpty() }?.let { props ->
            putJsonObject("properties") { props.forEach { (k, v) -> put(k, schemaJson(v)) } }
        }
    }

    private fun get(url: String, apiKey: String): String = request(url, apiKey, null)

    private fun post(url: String, apiKey: String, body: String): String = request(url, apiKey, body)

    private fun request(url: String, apiKey: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            // Header rather than a query parameter, so the key never lands in a log or
            // a proxy's access record.
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = body != null
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toByteArray()) } }
            return when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                400 -> throw GeminiException("Gemini rejected the request. The model ID may be wrong or retired.")
                401, 403 -> throw GeminiException("Gemini rejected the API key. Check it hasn't expired.")
                429 -> throw GeminiException("Rate limited by Gemini. Try again in a moment.")
                else -> {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw GeminiException("Gemini returned HTTP $code. ${detail.take(200)}")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    class GeminiException(message: String) : Exception(message)

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

        /**
         * Deliberately narrow. The model's only job is to pick a tool — it is never
         * asked to state a fact, because it is never given one.
         */
        private const val SYSTEM_INSTRUCTION =
            "You route questions about a Triumph Speed 400 motorcycle to exactly one tool. " +
                "Choose the single tool that best answers the question and call it. " +
                "Never answer in prose and never state a specification, figure or date yourself — " +
                "you do not have access to the data and any number you produce would be invented. " +
                "For questions about specifications, torque, pressures, capacities or intervals, " +
                "use spec_lookup. For questions about what the owner has spent, done, or when, " +
                "use the record tools."
    }
}
