package dev.pranav.speed400garage.ai

import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.update.UpdateSettings
import javax.inject.Inject
import javax.inject.Singleton

/** One turn of the conversation, as the screen shows it. */
data class Exchange(
    val question: String,
    val answer: String,
    val provenance: Provenance,
    val pageRef: Int? = null,
    val toolUsed: String? = null,
    /** True when the answer never left the device beyond the question text (§10.6). */
    val computedOnDevice: Boolean = true,
    val blocked: Boolean = false,
    val downgradedNumbers: List<String> = emptyList(),
)

/**
 * Plan on the server, compute and compose on the device (§10.6).
 *
 * The sequence is deliberately one round trip: ask the model which tool, run it
 * locally, render from a template. The model never sees a result, so it cannot garble
 * a total, cannot invent a figure, and cannot carry the owner's spend history into
 * anybody's training set.
 */
@Singleton
class Assistant @Inject constructor(
    private val client: GeminiClient,
    private val executor: ToolExecutor,
    private val settings: UpdateSettings,
) {

    suspend fun ask(question: String): Exchange {
        val key = settings.geminiKey()
            ?: return offline(question, "Add a Gemini API key in Settings and I can route questions.")
        val model = settings.geminiModel
            ?: return offline(question, "Pick a Gemini model in Settings first — I don't assume one, because Google retires them.")

        val call = try {
            client.route(key, model, question)
        } catch (e: GeminiClient.GeminiException) {
            return offline(question, e.message ?: "Couldn't reach Gemini.")
        } catch (e: Exception) {
            return offline(question, "Couldn't reach Gemini — you're probably offline. Quick Specs works without a network.")
        } ?: return offline(question, "I don't have a tool that answers that. I only know this bike's records and its handbook.")

        val answer = executor.execute(call)

        // §10.4 — deterministic post-check, never a prompt instruction.
        val safetyCritical = answer.isSafetyCritical || SafetyTopics.isSafetyCritical(question)
        return when (val verdict = GroundingCheck.check(answer.text, answer.sources, safetyCritical, answer.provenance)) {
            is GroundingCheck.Verdict.Ok -> Exchange(
                question = question,
                answer = answer.text,
                provenance = answer.provenance,
                pageRef = answer.pageRef,
                toolUsed = call.name,
                computedOnDevice = call.name in Tools.RECORD_TOOLS || call.name in Tools.KNOWLEDGE_TOOLS,
            )

            is GroundingCheck.Verdict.Blocked -> Exchange(
                question = question,
                answer = SafetyTopics.REFUSAL,
                provenance = Provenance.GENERAL,
                toolUsed = call.name,
                blocked = true,
            )

            is GroundingCheck.Verdict.Downgraded -> Exchange(
                question = question,
                answer = answer.text,
                // Any figure the app cannot account for drops the whole answer to ⚪.
                provenance = Provenance.GENERAL,
                pageRef = answer.pageRef,
                toolUsed = call.name,
                downgradedNumbers = verdict.numbers,
            )
        }
    }

    /**
     * The offline path. Safety-critical questions still get a real answer from the
     * curated fact table, because that is exactly when you need one and exactly when
     * there is no signal — on a road trip (§10.3).
     */
    private suspend fun offline(question: String, reason: String): Exchange {
        if (SafetyTopics.isSafetyCritical(question)) {
            val answer = executor.execute(ToolCall(Tools.SPEC_LOOKUP, mapOf("query" to question)))
            if (answer.pageRef != null) {
                return Exchange(
                    question = question,
                    answer = answer.text,
                    provenance = answer.provenance,
                    pageRef = answer.pageRef,
                    toolUsed = "${Tools.SPEC_LOOKUP} (offline)",
                )
            }
        }
        return Exchange(question, reason, Provenance.GENERAL, computedOnDevice = true)
    }
}
