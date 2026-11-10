package com.vibespace.core.ai.provider

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIResponse
import com.vibespace.core.ai.model.AIResponseChunk
import kotlinx.coroutines.flow.Flow

/**
 * AI Provider contract allowing multiple LLM backends (Anthropic, Gemini, OpenAI, Ollama).
 * Section 12: "Prepare an AIProvider interface supporting multiple providers. Do not create fake AI responses or hardcode API keys."
 */
interface AIProvider {
    val providerId: String
    val displayName: String
    val supportedModels: List<String>
    val isConfigured: Boolean

    suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse>
    fun streamResponse(request: AIRequest): Flow<AIResponseChunk>
}

class AIProviderRegistry {
    private val providers = mutableMapOf<String, AIProvider>()
    private var activeProviderId: String? = null

    fun register(provider: AIProvider) {
        providers[provider.providerId] = provider
        if (activeProviderId == null && provider.isConfigured) {
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
}
