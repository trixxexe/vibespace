package com.vibespace.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.service.AIService
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.editor.engine.EditorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AIChatViewModel(
    private val aiService: AIService,
    private val editorEngine: EditorEngine? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        observeEditorContext()
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

    fun onPromptChange(text: String) {
        _uiState.update { it.copy(inputPrompt = text) }
    }

    fun sendPrompt(promptText: String? = null) {
        val prompt = promptText ?: _uiState.value.inputPrompt
        if (prompt.isBlank()) return

        _uiState.update { it.copy(inputPrompt = "", isGenerating = true, errorMessage = null) }
        viewModelScope.launch(dispatchers.default) {
            val result = aiService.sendUserPrompt(prompt)
            result.onError { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
            _uiState.update { it.copy(isGenerating = false) }
        }
    }

    fun clearHistory() {
        aiService.clearHistory()
    }
}
