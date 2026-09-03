package dev.pranav.speed400garage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.BuildConfig
import dev.pranav.speed400garage.update.UpdateChecker
import dev.pranav.speed400garage.update.UpdateInstaller
import dev.pranav.speed400garage.update.UpdateSettings
import dev.pranav.speed400garage.update.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val settings: UpdateSettings,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _hasToken = MutableStateFlow(settings.hasToken())
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    private val _autoCheck = MutableStateFlow(settings.autoCheck)
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    val currentVersion: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private var job: Job? = null

    /** Called once on launch. Silent unless there is genuinely something to act on. */
    fun checkOnLaunch() {
        if (!settings.autoCheck) return
        check()
    }

    fun check() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = checker.check(BuildConfig.VERSION_CODE)
            settings.lastCheckedAt = System.currentTimeMillis()
        }
    }

    fun download() {
        val available = _state.value as? UpdateState.Available ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(available.manifest, 0f)
            _state.value = try {
                val file = installer.download(available.manifest, available.asset) { fraction ->
                    _state.value = UpdateState.Downloading(available.manifest, fraction)
                }
                UpdateState.ReadyToInstall(available.manifest, file.absolutePath)
            } catch (e: SecurityException) {
                // A checksum mismatch is worth saying out loud rather than retrying.
                UpdateState.Failed(e.message ?: "Downloaded file failed verification.")
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Download failed.")
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun setToken(token: String?) {
        settings.setToken(token)
        _hasToken.value = settings.hasToken()
        _state.value = UpdateState.Idle
    }

    fun setAutoCheck(enabled: Boolean) {
        settings.autoCheck = enabled
        _autoCheck.value = enabled
    }

    fun canInstall(): Boolean = installer.canInstall()
    fun permissionIntent() = installer.permissionIntent()
    fun installIntent(path: String) = installer.installIntent(java.io.File(path))
}
