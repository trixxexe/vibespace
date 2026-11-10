package com.vibespace.feature.aichat

import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.CodeContext

data class AIChatUiState(
    val messages: List<AIMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val attachedCodeContext: CodeContext? = null,
    val inputPrompt: String = "",
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
    val activeProvider: String = "gemini",
    val activeModel: String = "gemini-2.0-flash"
)
