package com.vibespace.core.ai.service

import com.vibespace.core.ai.model.AIConfiguration
import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.provider.AIProviderRegistry
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * AI Assistant service orchestrating chat conversation history, active code context attachment,
 * and user-supplied API key execution.
 */
class AIService(
    val registry: AIProviderRegistry = AIProviderRegistry(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) {

    private val _configuration = MutableStateFlow(AIConfiguration())
    val configuration: StateFlow<AIConfiguration> = _configuration.asStateFlow()

    private val _messages = MutableStateFlow<List<AIMessage>>(emptyList())
    val messages: StateFlow<List<AIMessage>> = _messages.asStateFlow()

    private val _activeContext = MutableStateFlow<CodeContext?>(null)
    val activeContext: StateFlow<CodeContext?> = _activeContext.asStateFlow()

    fun updateConfiguration(config: AIConfiguration) {
        _configuration.value = config
        registry.configure(config)
    }

    fun setCodeContext(context: CodeContext?) {
        _activeContext.value = context
    }

    suspend fun sendUserPrompt(prompt: String): VibeResult<AIMessage> = withContext(dispatchers.default) {
        if (prompt.isBlank()) {
            return@withContext VibeResult.Error("Prompt cannot be empty.")
        }

        val userMsg = AIMessage(
            id = IdGenerator.shortId("msg"),
            role = AIRole.USER,
            content = prompt,
            attachedCodeContext = _activeContext.value
        )
        _messages.value = _messages.value + userMsg

        val provider = registry.getActiveProvider()
        if (provider == null || !provider.isConfigured) {
            val unconfiguredMsg = AIMessage(
                id = IdGenerator.shortId("msg"),
                role = AIRole.ASSISTANT,
                content = "No AI provider configured. Please configure your API key (Google Gemini or OpenAI) in Settings or in the AI Chat panel to activate intelligent coding assistance."
            )
            _messages.value = _messages.value + unconfiguredMsg
            return@withContext VibeResult.Success(unconfiguredMsg)
        }

        val request = AIRequest(
            messages = _messages.value,
            codeContext = _activeContext.value
        )

        when (val responseResult = provider.generateResponse(request)) {
            is VibeResult.Success -> {
                _messages.value = _messages.value + responseResult.data.message
                VibeResult.Success(responseResult.data.message)
            }
            is VibeResult.Error -> {
                val errorMsg = AIMessage(
                    id = IdGenerator.shortId("msg"),
                    role = AIRole.ASSISTANT,
                    content = "[Error] ${responseResult.message}"
                )
                _messages.value = _messages.value + errorMsg
                VibeResult.Error(responseResult.message, responseResult.throwable)
            }
            is VibeResult.Loading -> VibeResult.Loading
        }
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }
}
