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
            register(AnthropicAIProvider())
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
            register(OpenAIAIProvider(
                providerId = "openrouter",
                displayName = "OpenRouter",
                activeModel = "anthropic/claude-3.7-sonnet",
                customEndpoint = "https://openrouter.ai/api/v1/chat/completions",
                supportedModels = listOf(
                    "anthropic/claude-3.7-sonnet",
                    "openai/gpt-4o-mini",
                    "google/gemini-2.5-flash",
                    "deepseek/deepseek-chat"
                )
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
                if (config.model.isNotBlank()) provider.activeModel = config.model
            }
            is AnthropicAIProvider -> {
                provider.apiKey = config.apiKey
                if (config.model.isNotBlank()) provider.activeModel = config.model
                if (config.customEndpoint.isNotBlank()) {
                    provider.customEndpoint = config.customEndpoint
                } else {
                    provider.customEndpoint = "https://api.anthropic.com/v1/messages"
                }
            }
            is OpenAIAIProvider -> {
                provider.apiKey = config.apiKey
                if (config.model.isNotBlank()) provider.activeModel = config.model
                val defaultEndpoint = when (config.providerId) {
                    "deepseek" -> "https://api.deepseek.com/chat/completions"
                    "groq" -> "https://api.groq.com/openai/v1/chat/completions"
                    "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
                    else -> ""
                }
                provider.customEndpoint = config.customEndpoint.ifBlank { defaultEndpoint }
            }
        }
    }
}
