package dev.pranav.speed400garage.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.ai.Assistant
import dev.pranav.speed400garage.ai.Exchange
import dev.pranav.speed400garage.ai.GeminiClient
import dev.pranav.speed400garage.update.UpdateSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantState(
    val history: List<Exchange> = emptyList(),
    val thinking: Boolean = false,
    val hasKey: Boolean = false,
    val model: String? = null,
    val availableModels: List<String> = emptyList(),
    val modelError: String? = null,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistant: Assistant,
    private val client: GeminiClient,
    private val settings: UpdateSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AssistantState(hasKey = settings.geminiKey() != null, model = settings.geminiModel)
    )
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    fun ask(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(thinking = true)
            val exchange = assistant.ask(question.trim())
            _state.value = _state.value.copy(
                history = _state.value.history + exchange,
                thinking = false,
            )
        }
    }

    fun setKey(key: String?) {
        settings.setGeminiKey(key)
        _state.value = _state.value.copy(hasKey = settings.geminiKey() != null, availableModels = emptyList())
    }

    fun setModel(model: String) {
        settings.geminiModel = model
        _state.value = _state.value.copy(model = model)
    }

    /**
     * Asks Google which models this key can use, rather than trusting a written-down
     * ID. Model IDs go stale — the ones the plan named are already retired.
     */
    fun refreshModels() = viewModelScope.launch {
        val key = settings.geminiKey() ?: return@launch
        _state.value = _state.value.copy(modelError = null)
        runCatching { client.listModels(key) }
            .onSuccess { _state.value = _state.value.copy(availableModels = it, modelError = null) }
            .onFailure { _state.value = _state.value.copy(modelError = it.message ?: "Couldn't list models.") }
    }

    fun clear() { _state.value = _state.value.copy(history = emptyList()) }
}
