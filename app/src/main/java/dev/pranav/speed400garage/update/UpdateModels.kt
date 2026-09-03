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

/** Where to fetch the APK from, and how big it is if GitHub told us up front. */
data class ReleaseAsset(val url: String, val sizeBytes: Long)

/** What the UI renders. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest, val asset: ReleaseAsset) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val fraction: Float) : UpdateState
    data class ReadyToInstall(val manifest: UpdateManifest, val path: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}
