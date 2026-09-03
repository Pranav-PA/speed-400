package dev.pranav.speed400garage.ui.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.ai.HandbookImporter
import dev.pranav.speed400garage.ai.ImportResult
import dev.pranav.speed400garage.data.db.dao.HandbookDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HandbookState(
    val chunkCount: Int = 0,
    val importing: Boolean = false,
    val pagesDone: Int = 0,
    val totalPages: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class HandbookViewModel @Inject constructor(
    private val importer: HandbookImporter,
    private val dao: HandbookDao,
) : ViewModel() {

    private val _state = MutableStateFlow(HandbookState())
    val state: StateFlow<HandbookState> = _state.asStateFlow()

    init { refresh() }

    private fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(chunkCount = dao.count())
    }

    fun import(source: Uri) = viewModelScope.launch {
        _state.value = _state.value.copy(importing = true, message = null, pagesDone = 0, totalPages = 0)
        val result = importer.import(source) { done, total ->
            _state.value = _state.value.copy(pagesDone = done, totalPages = total)
        }
        _state.value = when (result) {
            is ImportResult.Imported -> _state.value.copy(
                importing = false,
                chunkCount = dao.count(),
                message = "Indexed ${result.chunks} passages from ${result.pages} pages.",
            )
            is ImportResult.Failed -> _state.value.copy(importing = false, message = result.message)
        }
    }

    fun clear() = viewModelScope.launch {
        dao.clear()
        _state.value = _state.value.copy(chunkCount = 0, message = "Handbook removed.")
    }
}
