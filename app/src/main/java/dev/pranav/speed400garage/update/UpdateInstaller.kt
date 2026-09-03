package dev.pranav.speed400garage.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads a release APK and hands it to Android's package installer.
 *
 * Two things make this safe enough to run on a tablet holding a ten-year record:
 *
 *  1. The download is verified against the SHA-256 in the manifest BEFORE the file is
 *     offered to the installer. A mismatch deletes the file and fails loudly.
 *  2. Android refuses to install an APK signed with a different key than the installed
 *     app, so a release built with the wrong key fails at install time rather than
 *     replacing the app. That is a feature — see docs/releasing.md on why the signing
 *     keystore must never be regenerated.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    private val context: Context,
    private val checker: UpdateChecker,
) {

    suspend fun download(
        manifest: UpdateManifest,
        asset: ReleaseAsset,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }
        // One update in flight at a time; a stale part-file is never reused.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, manifest.apk)

        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        checker.open(asset.url).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n == -1) break
                    output.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    read += n
                    if (asset.sizeBytes > 0) onProgress((read.toDouble() / asset.sizeBytes).toFloat())
                }
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(manifest.sha256, ignoreCase = true)) {
            target.delete()
            throw SecurityException(
                "Downloaded APK failed its checksum. Expected ${manifest.sha256.take(12)}…, " +
                    "got ${actual.take(12)}…. The file was discarded."
            )
        }
        target
    }

    /** True once the owner has allowed this app to install packages. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Sends the owner to the one Android settings screen that grants that permission. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val UPDATE_DIR = "updates"
    }
}
