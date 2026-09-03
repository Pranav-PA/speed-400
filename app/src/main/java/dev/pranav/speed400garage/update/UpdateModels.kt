package dev.pranav.speed400garage.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The release manifest CI publishes alongside the APK.
 *
 * Deriving the version from the git tag alone would work, but this file also carries
 * the SHA-256 the app checks before handing anything to the package installer. An APK
 * is the most dangerous thing this app will ever download; a truncated or swapped file
 * should fail loudly rather than reach the installer.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** Release asset filename of the APK this manifest describes. */
    val apk: String,
    /** Lowercase hex SHA-256 of that APK. */
    val sha256: String,
    val notes: String = "",
    @SerialName("released_at") val releasedAt: String? = null,
)

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    /** API URL — the one that works for a PRIVATE repo, given a token. */
    val url: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/** What the UI renders. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest, val asset: ReleaseAsset) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val fraction: Float) : UpdateState
    data class ReadyToInstall(val manifest: UpdateManifest, val path: String) : UpdateState
    /** [needsToken] distinguishes "you haven't set this up yet" from a genuine failure. */
    data class Failed(val message: String, val needsToken: Boolean = false) : UpdateState
}

data class ReleaseAsset(val apiUrl: String, val browserUrl: String, val sizeBytes: Long)
