package dev.pranav.speed400garage.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.db.dao.SearchDao
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.data.repo.GarageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val results: List<EventEntity> = emptyList(),
    val searching: Boolean = false,
    val ranFallback: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: GarageRepository,
    private val dao: SearchDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        job?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searching = false, ranFallback = false)
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.value = _state.value.copy(searching = true)
            val bikeId = repo.activeBikeId() ?: return@launch
            val trimmed = query.trim()

            // Prefix-match each word so "brak" finds "brake pads" before you finish
            // typing. If FTS rejects the input — an apostrophe, a stray quote — fall
            // back to a LIKE scan rather than throwing. A search box that crashes on
            // a punctuation mark is worse than a slow one.
            val ftsQuery = trimmed.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { "${it.replace("\"", "")}*" }

            val results = runCatching { dao.search(bikeId, ftsQuery) }
            _state.value = if (results.isSuccess && results.getOrThrow().isNotEmpty()) {
                _state.value.copy(results = results.getOrThrow(), searching = false, ranFallback = false)
            } else {
                val fallback = runCatching { dao.searchLike(bikeId, trimmed) }.getOrDefault(emptyList())
                _state.value.copy(results = fallback, searching = false, ranFallback = results.isFailure)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 180L
    }
}
