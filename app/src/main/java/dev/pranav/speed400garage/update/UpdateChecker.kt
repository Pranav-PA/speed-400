package dev.pranav.speed400garage.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks GitHub whether a newer build exists.
 *
 * A note on §12 ("nothing phones home"): this is the one outbound call the app makes
 * without being asked a question. It sends an HTTP GET and an auth token, and nothing
 * else — no odometer, no spend, no identifiers, no telemetry of any kind. It can be
 * switched off in Settings, in which case the app makes no network calls at all until
 * the Phase 4 assistant exists.
 *
 * The repository is PRIVATE, so every request needs a token and the plain
 * `releases/latest/download/...` URLs do not work. The token is a fine-grained,
 * read-only, single-repository PAT that the owner pastes into Settings once; it is
 * stored in encrypted preferences and is never compiled into the APK.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val settings: UpdateSettings,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(currentVersionCode: Int): UpdateState = withContext(Dispatchers.IO) {
        val token = settings.token()
        if (token.isNullOrBlank()) {
            return@withContext UpdateState.Failed(
                "Add a GitHub token in Settings to check for updates.",
                needsToken = true,
            )
        }

        try {
            val release = fetchLatestRelease(token)
                ?: return@withContext UpdateState.Failed("No published release yet.")

            val manifestAsset = release.assets.firstOrNull { it.name == MANIFEST_ASSET }
                ?: return@withContext UpdateState.Failed(
                    "Release ${release.tagName} has no $MANIFEST_ASSET — it was not built by CI."
                )

            val manifest = json.decodeFromString<UpdateManifest>(
                String(downloadAssetBytes(manifestAsset.url, token), Charsets.UTF_8)
            )

            if (manifest.versionCode <= currentVersionCode) return@withContext UpdateState.UpToDate

            val apkAsset = release.assets.firstOrNull { it.name == manifest.apk }
                ?: return@withContext UpdateState.Failed(
                    "Release ${release.tagName} names ${manifest.apk} but does not contain it."
                )

            UpdateState.Available(
                manifest = manifest,
                asset = ReleaseAsset(apkAsset.url, apkAsset.browserDownloadUrl, apkAsset.size),
            )
        } catch (e: UnauthorizedException) {
            UpdateState.Failed(
                "GitHub rejected the token. Check it hasn't expired and still grants " +
                    "read access to Contents on this repository.",
                needsToken = true,
            )
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Update check failed.")
        }
    }

    private fun fetchLatestRelease(token: String): GitHubRelease? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return try {
            when (connection.responseCode) {
                200 -> json.decodeFromString<GitHubRelease>(connection.inputStream.bufferedReader().use { it.readText() })
                // 404 on a private repo means "no release yet" OR "token can't see it".
                // The token was accepted at all, so treat it as no release.
                404 -> null
                401, 403 -> throw UnauthorizedException()
                else -> throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
            }
        } catch (e: FileNotFoundException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads a release asset from a private repo.
     *
     * GitHub answers the asset API URL with a redirect to object storage that rejects
     * requests carrying an `Authorization` header. So redirects are followed by hand
     * and the header is dropped the moment the host changes — following automatically
     * produces a confusing 400 from S3 instead.
     */
    internal fun downloadAssetBytes(apiUrl: String, token: String): ByteArray =
        openAssetStream(apiUrl, token).use { it.readBytes() }

    internal fun openAssetStream(apiUrl: String, token: String): java.io.InputStream {
        var url = URL(apiUrl)
        var useAuth = true
        repeat(MAX_REDIRECTS) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
                if (useAuth) setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            when (val code = connection.responseCode) {
                200 -> return connection.inputStream
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect with no Location header")
                    connection.disconnect()
                    val next = URL(url, location)
                    useAuth = next.host.equals(url.host, ignoreCase = true) && useAuth
                    url = next
                }
                401, 403 -> { connection.disconnect(); throw UnauthorizedException() }
                else -> { connection.disconnect(); throw IllegalStateException("HTTP $code fetching asset") }
            }
        }
        throw IllegalStateException("Too many redirects fetching asset")
    }

    class UnauthorizedException : Exception("GitHub rejected the credentials")

    companion object {
        const val OWNER = "Pranav-PA"
        const val REPO = "speed-400"
        const val MANIFEST_ASSET = "update.json"
        const val MAX_REDIRECTS = 5
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"
    }
}
