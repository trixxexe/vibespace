package com.vibespace.core.ai.model

enum class AIRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class AIMessage(
    val id: String,
    val role: AIRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedCodeContext: CodeContext? = null,
    val proposedChanges: List<CodeChange> = emptyList()
)

data class AIRequest(
    val messages: List<AIMessage>,
    val model: String? = null,
    val systemPrompt: String? = null,
    val codeContext: CodeContext? = null,
    val temperature: Float = 0.7f
)

data class AIResponse(
    val message: AIMessage,
    val totalTokens: Int? = null,
    val finishReason: String? = null
)

data class AIResponseChunk(
    val delta: String,
    val isComplete: Boolean = false
)
