package dev.pranav.speed400garage.ui.log

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.backup.BackupManager
import dev.pranav.speed400garage.data.backup.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupManager,
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun suggestedFilename(): String = BackupManager.suggestedFilename()

    fun export(target: Uri) = viewModelScope.launch {
        _status.value = "Exporting…"
        _status.value = runCatching { backups.export(target) }
            .fold(
                onSuccess = { "Exported. Open it and check it is not empty — an untested backup is a belief." },
                onFailure = { "Export failed: ${it.message}" },
            )
    }

    fun restore(source: Uri) = viewModelScope.launch {
        _status.value = "Restoring…"
        _status.value = when (val result = runCatching { backups.restore(source) }.getOrElse { RestoreResult.Failed(it.message ?: "Restore failed") }) {
            is RestoreResult.Restored -> "Restored. Close and reopen the app to load it."
            is RestoreResult.Failed -> result.message
        }
    }
}
