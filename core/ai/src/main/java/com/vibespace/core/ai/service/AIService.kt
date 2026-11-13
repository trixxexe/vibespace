package com.vibespace.core.ai.service

import com.vibespace.core.ai.model.AIConfiguration
import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.ai.model.AIResponseChunk
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.provider.AIProviderRegistry
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import java.io.IOException

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
    private val sendMutex = Mutex()

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

        sendMutex.withLock {
            val provider = registry.getActiveProvider()
            if (provider == null || !provider.isConfigured) {
                return@withLock VibeResult.Error("No AI provider configured. Add an API key in Settings or the AI Assistant panel.")
            }

            val userMsg = AIMessage(
                id = IdGenerator.shortId("msg"),
                role = AIRole.USER,
                content = prompt.trim(),
                attachedCodeContext = _activeContext.value
            )
            val requestMessages = _messages.value + userMsg
            val request = AIRequest(messages = requestMessages, codeContext = _activeContext.value)

            when (val responseResult = provider.generateResponse(request)) {
                is VibeResult.Success -> {
                    _messages.value = requestMessages + responseResult.data.message
                    VibeResult.Success(responseResult.data.message)
                }
                is VibeResult.Error -> VibeResult.Error(responseResult.message, responseResult.throwable)
                is VibeResult.Loading -> VibeResult.Loading
            }
        }
    }

    /** Streams provider deltas while publishing the growing assistant message to chat state. */
    fun streamUserPrompt(prompt: String): Flow<AIResponseChunk> = flow {
        if (prompt.isBlank()) throw IllegalArgumentException("Prompt cannot be empty.")
        sendMutex.withLock {
            val provider = registry.getActiveProvider()
            if (provider == null || !provider.isConfigured) {
                throw IOException("No AI provider configured. Add an API key in Settings or the AI Assistant panel.")
            }
            val userMsg = AIMessage(
                id = IdGenerator.shortId("msg"),
                role = AIRole.USER,
                content = prompt.trim(),
                attachedCodeContext = _activeContext.value
            )
            val requestMessages = _messages.value + userMsg
            val previousMessages = _messages.value
            val request = AIRequest(messages = requestMessages, codeContext = _activeContext.value)
            val assistant = AIMessage(id = IdGenerator.shortId("msg"), role = AIRole.ASSISTANT, content = "")
            val response = StringBuilder()
            _messages.value = requestMessages
            var streamFailure: Throwable? = null
            try {
                provider.streamResponse(request).collect { chunk ->
                    response.append(chunk.delta)
                    _messages.value = requestMessages + assistant.copy(content = response.toString())
                    emit(chunk)
                }
                if (response.isEmpty()) {
                    _messages.value = requestMessages
                    throw IOException("The AI provider completed without returning any response text.")
                }
            } catch (failure: kotlinx.coroutines.CancellationException) {
                _messages.value = previousMessages
                throw failure
            } catch (failure: Throwable) {
                if (response.isEmpty()) {
                    streamFailure = failure
                } else {
                    _messages.value = previousMessages
                    throw failure
                }
            }

            if (streamFailure != null) {
                when (val fallback = provider.generateResponse(request)) {
                    is VibeResult.Success -> {
                        val content = fallback.data.message.content
                        if (content.isBlank()) {
                            _messages.value = previousMessages
                            throw streamFailure
                        }
                        _messages.value = requestMessages + assistant.copy(content = content)
                        emit(AIResponseChunk(delta = content, isComplete = true))
                    }
                    else -> {
                        _messages.value = previousMessages
                        throw streamFailure
                    }
                }
            }
        }
    }.flowOn(dispatchers.default)

    fun clearHistory() {
        _messages.value = emptyList()
    }
}
