package dev.pranav.speed400garage.update

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the GitHub token lives.
 *
 * Encrypted preferences, per §14 — and, more to the point, the token is never
 * committed and never compiled into the APK. The owner pastes it in once on the
 * tablet. If the encrypted store cannot be opened on a given device, this falls back
 * to a plain store rather than crashing the app: the token is read-only and
 * single-repository, so a readable-but-degraded store is a better failure than an
 * app that will not start.
 */
@Singleton
class UpdateSettings @Inject constructor(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token.trim())
        }.apply()
    }

    fun hasToken(): Boolean = token() != null

    /**
     * Whether to check on launch. On by default — an update mechanism nobody
     * remembers to trigger is one that never gets used.
     */
    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    var lastCheckedAt: Long
        get() = prefs.getLong(KEY_LAST_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECKED, value).apply()

    private companion object {
        const val ENCRYPTED_FILE = "garage_secure_prefs"
        const val FALLBACK_FILE = "garage_prefs"
        const val KEY_TOKEN = "github.token"
        const val KEY_AUTO_CHECK = "updates.auto_check"
        const val KEY_LAST_CHECKED = "updates.last_checked"
    }
}
