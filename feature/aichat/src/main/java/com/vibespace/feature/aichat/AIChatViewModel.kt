package com.vibespace.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.ai.model.AIConfiguration
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.service.AIService
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.editor.engine.EditorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class AIChatViewModel(
    private val aiService: AIService,
    private val editorEngine: EditorEngine? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val persistConfiguration: suspend (AIConfiguration) -> VibeResult<Unit> = { VibeResult.Success(Unit) }
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        observeEditorContext()
        observeConfiguration()
    }

    private fun observeMessages() {
        viewModelScope.launch(dispatchers.default) {
            aiService.messages.collect { msgList ->
                _uiState.update { it.copy(messages = msgList) }
            }
        }
    }

    private fun observeEditorContext() {
        editorEngine?.let { engine ->
            viewModelScope.launch(dispatchers.default) {
                engine.activeDocument.collect { activeDoc ->
                    if (activeDoc != null) {
                        val ctx = CodeContext(
                            fileUri = activeDoc.uri,
                            fileName = activeDoc.fileName,
                            language = activeDoc.language.displayName,
                            fullContent = activeDoc.content,
                            cursorLine = activeDoc.cursorPosition.line
                        )
                        aiService.setCodeContext(ctx)
                        _uiState.update { it.copy(attachedCodeContext = ctx) }
                    } else {
                        aiService.setCodeContext(null)
                        _uiState.update { it.copy(attachedCodeContext = null) }
                    }
                }
            }
        }
    }

    private fun observeConfiguration() {
        viewModelScope.launch(dispatchers.default) {
            aiService.configuration.collect { config ->
                _uiState.update {
                    it.copy(
                        isConfigured = config.isConfigured,
                        activeProvider = config.providerId,
                        activeModel = config.model
                    )
                }
            }
        }
    }

    fun onPromptChange(text: String) {
        _uiState.update { it.copy(inputPrompt = text) }
    }

    fun sendPrompt(promptText: String? = null) {
        val prompt = promptText ?: _uiState.value.inputPrompt
        if (prompt.isBlank()) return

        _uiState.update { it.copy(inputPrompt = "", isGenerating = true, errorMessage = null) }
        viewModelScope.launch(dispatchers.default) {
            try {
                aiService.streamUserPrompt(prompt).collect { chunk ->
                    if (chunk.isComplete) _uiState.update { it.copy(isGenerating = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _uiState.update { it.copy(errorMessage = failure.message ?: "AI request failed.") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun configureApiKey(apiKey: String, providerId: String = "gemini", model: String = "gemini-2.5-flash", customEndpoint: String = "") {
        val config = AIConfiguration(
                providerId = providerId,
                apiKey = apiKey.trim(),
                model = model.trim(),
                customEndpoint = customEndpoint.trim()
        )
        viewModelScope.launch(dispatchers.io) {
            when (val result = persistConfiguration(config)) {
                is VibeResult.Success -> {
                    aiService.updateConfiguration(config)
                    _uiState.update { it.copy(errorMessage = null) }
                }
                is VibeResult.Error -> _uiState.update { it.copy(errorMessage = "Could not save API key: ${result.message}") }
                else -> _uiState.update { it.copy(errorMessage = "Could not save API key: no result returned") }
            }
        }
    }

    fun clearHistory() {
        aiService.clearHistory()
    }
}
