package com.vibespace.core.ai.provider

import com.vibespace.core.ai.model.AIConfiguration
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIResponse
import com.vibespace.core.ai.model.AIResponseChunk
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.Flow

/**
 * AI Provider contract allowing multiple LLM backends (Gemini, OpenAI, Anthropic, Ollama).
 * Strictly requires real provider execution; never creates fake AI responses.
 */
interface AIProvider {
    val providerId: String
    val displayName: String
    val supportedModels: List<String>
    val isConfigured: Boolean

    suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse>
    fun streamResponse(request: AIRequest): Flow<AIResponseChunk>
}

class AIProviderRegistry(
    registerDefaults: Boolean = true
) {
    private val providers = mutableMapOf<String, AIProvider>()
    private var activeProviderId: String? = null

    init {
        if (registerDefaults) {
            register(GeminiAIProvider())
            register(OpenAIAIProvider())
            register(OpenAIAIProvider(
                providerId = "anthropic",
                displayName = "Anthropic (Claude)",
                activeModel = "claude-3-7-sonnet",
                customEndpoint = "https://api.anthropic.com/v1/messages",
                supportedModels = listOf("claude-3-7-sonnet", "claude-3-5-sonnet", "claude-3-5-haiku")
            ))
            register(OpenAIAIProvider(
                providerId = "deepseek",
                displayName = "DeepSeek",
                activeModel = "deepseek-chat",
                customEndpoint = "https://api.deepseek.com/chat/completions",
                supportedModels = listOf("deepseek-chat", "deepseek-reasoner")
            ))
            register(OpenAIAIProvider(
                providerId = "groq",
                displayName = "Groq",
                activeModel = "llama-3.3-70b-versatile",
                customEndpoint = "https://api.groq.com/openai/v1/chat/completions",
                supportedModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
            ))
            activeProviderId = "gemini"
        }
    }

    fun register(provider: AIProvider) {
        providers[provider.providerId] = provider
        val currentActive = activeProviderId?.let { providers[it] }
        if (currentActive == null || (!currentActive.isConfigured && provider.isConfigured)) {
            activeProviderId = provider.providerId
        }
    }

    fun getAllProviders(): List<AIProvider> = providers.values.toList()

    fun getActiveProvider(): AIProvider? = activeProviderId?.let { providers[it] } ?: providers.values.firstOrNull()

    fun setActiveProvider(providerId: String) {
        if (providers.containsKey(providerId)) {
            activeProviderId = providerId
        }
    }

    fun configure(config: AIConfiguration) {
        setActiveProvider(config.providerId)
        val provider = providers[config.providerId]
        when (provider) {
            is GeminiAIProvider -> {
                provider.apiKey = config.apiKey
                provider.activeModel = config.model
            }
            is OpenAIAIProvider -> {
                provider.apiKey = config.apiKey
                provider.activeModel = config.model
                provider.customEndpoint = config.customEndpoint
            }
        }
    }
}
