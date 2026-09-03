package dev.pranav.speed400garage.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks GitHub whether a newer build exists.
 *
 * The repository is public, so this needs no credentials: GitHub serves
 * `releases/latest/download/<asset>` as a stable redirect to whatever the newest
 * release holds. No API calls, no rate limits worth worrying about, nothing to
 * configure on the tablet.
 *
 * A token is still accepted, and is only needed if the repository is ever made
 * private again — private release assets are reachable only through the API, which
 * is why [fetchViaApi] exists at all.
 *
 * On §12 ("nothing phones home"): this is the one outbound call the app makes without
 * being asked a question. It sends an HTTP GET and nothing else — no odometer, no
 * spend, no identifiers, no telemetry. Switch auto-check off in Settings and the app
 * makes no network calls at all.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val settings: UpdateSettings,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(currentVersionCode: Int): UpdateState = withContext(Dispatchers.IO) {
        try {
            val token = settings.token()
            val manifestBytes = if (token.isNullOrBlank()) {
                fetchPublic(assetUrl(MANIFEST_ASSET)).use { it.readBytes() }
            } else {
                fetchViaApi(MANIFEST_ASSET, token).use { it.readBytes() }
            }

            val manifest = json.decodeFromString<UpdateManifest>(String(manifestBytes, Charsets.UTF_8))
            if (manifest.versionCode <= currentVersionCode) return@withContext UpdateState.UpToDate

            UpdateState.Available(manifest, ReleaseAsset(assetUrl(manifest.apk), UNKNOWN_SIZE))
        } catch (e: NoReleaseException) {
            UpdateState.Failed("No published release yet.")
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Update check failed.")
        }
    }

    /**
     * Opens a release asset for reading.
     *
     * Redirects are followed by hand because GitHub bounces these to object storage,
     * which rejects any request still carrying an `Authorization` header — following
     * automatically produces a confusing 400 instead of the file.
     */
    fun open(url: String): InputStream {
        val token = settings.token()
        return if (token.isNullOrBlank()) fetchPublic(url) else fetchViaApiUrl(url, token)
    }

    private fun fetchPublic(url: String): InputStream = follow(URL(url), token = null)

    /** Private-repo path: resolve the asset through the releases API, then download it. */
    private fun fetchViaApi(assetName: String, token: String): InputStream {
        val release = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).run {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            try {
                when (responseCode) {
                    200 -> inputStream.bufferedReader().use { it.readText() }
                    404 -> throw NoReleaseException()
                    401, 403 -> throw IllegalStateException("GitHub rejected the token.")
                    else -> throw IllegalStateException("GitHub returned HTTP $responseCode")
                }
            } finally { disconnect() }
        }
        val assetUrl = Regex(""""url":\s*"([^"]+)"[^}]*?"name":\s*"${Regex.escape(assetName)}"""")
            .find(release)?.groupValues?.get(1)
            ?: Regex(""""name":\s*"${Regex.escape(assetName)}"[^}]*?"url":\s*"([^"]+)"""")
                .find(release)?.groupValues?.get(1)
            ?: throw IllegalStateException("Latest release has no $assetName — it was not built by CI.")
        return fetchViaApiUrl(assetUrl, token)
    }

    private fun fetchViaApiUrl(url: String, token: String): InputStream = follow(URL(url), token)

    private fun follow(start: URL, token: String?): InputStream {
        var url = start
        var auth = token
        repeat(MAX_REDIRECTS) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
                auth?.let { setRequestProperty("Authorization", "Bearer $it") }
                connectTimeout = TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            when (val code = connection.responseCode) {
                200 -> return connection.inputStream
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect with no Location header")
                    connection.disconnect()
                    val next = URL(url, location)
                    // Drop credentials the moment we leave GitHub's host.
                    if (!next.host.equals(url.host, ignoreCase = true)) auth = null
                    url = next
                }
                404 -> { connection.disconnect(); throw NoReleaseException() }
                401, 403 -> { connection.disconnect(); throw IllegalStateException("GitHub refused the request (HTTP $code).") }
                else -> { connection.disconnect(); throw IllegalStateException("HTTP $code fetching ${url.path.substringAfterLast('/')}") }
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    class NoReleaseException : Exception("No published release")

    companion object {
        const val OWNER = "Pranav-PA"
        const val REPO = "speed-400"
        const val MANIFEST_ASSET = "update.json"
        const val UNKNOWN_SIZE = -1L
        private const val MAX_REDIRECTS = 5
        private const val TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000

        const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

        /** The stable "whatever is newest" URL. Public repos only. */
        fun assetUrl(name: String) = "https://github.com/$OWNER/$REPO/releases/latest/download/$name"
    }
}
