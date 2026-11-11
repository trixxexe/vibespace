package com.vibespace.core.ai

import com.vibespace.core.ai.model.AIConfiguration
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIResponse
import com.vibespace.core.ai.model.AIResponseChunk
import com.vibespace.core.ai.provider.AIProvider
import com.vibespace.core.ai.provider.AIProviderRegistry
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AIProviderRegistryTest {

    private fun createMockProvider(
        id: String,
        name: String = id,
        configured: Boolean = false
    ): AIProvider = object : AIProvider {
        override val providerId: String = id
        override val displayName: String = name
        override val supportedModels: List<String> = listOf("model-1")
        override val isConfigured: Boolean = configured

        override suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse> =
            VibeResult.Error("Not implemented")

        override fun streamResponse(request: AIRequest): Flow<AIResponseChunk> = emptyFlow()
    }

    @Test
    fun init_with_registerDefaults_false_has_empty_providers_and_null_active() {
        val registry = AIProviderRegistry(registerDefaults = false)
        assertTrue(registry.getAllProviders().isEmpty())
        assertNull(registry.getActiveProvider())
    }

    @Test
    fun register_unconfigured_provider_when_active_is_null_sets_activeProvider() {
        val registry = AIProviderRegistry(registerDefaults = false)
        val unconfigured = createMockProvider("unconfigured_1", configured = false)

        registry.register(unconfigured)

        assertEquals(1, registry.getAllProviders().size)
        assertEquals("unconfigured_1", registry.getActiveProvider()?.providerId)
    }

    @Test
    fun register_second_unconfigured_provider_does_not_override_active() {
        val registry = AIProviderRegistry(registerDefaults = false)
        val unconfigured1 = createMockProvider("unconfigured_1", configured = false)
        val unconfigured2 = createMockProvider("unconfigured_2", configured = false)

        registry.register(unconfigured1)
        registry.register(unconfigured2)

        assertEquals("unconfigured_1", registry.getActiveProvider()?.providerId)
    }

    @Test
    fun register_configured_provider_promotes_activeProviderId_over_unconfigured() {
        val registry = AIProviderRegistry(registerDefaults = false)
        val unconfigured = createMockProvider("unconfigured_1", configured = false)
        val configured = createMockProvider("configured_1", configured = true)

        registry.register(unconfigured)
        assertEquals("unconfigured_1", registry.getActiveProvider()?.providerId)

        // Registering configured provider must promote it to activeProviderId
        registry.register(configured)
        assertEquals("configured_1", registry.getActiveProvider()?.providerId)
    }

    @Test
    fun register_second_configured_provider_retains_current_configured_active() {
        val registry = AIProviderRegistry(registerDefaults = false)
        val configured1 = createMockProvider("configured_1", configured = true)
        val configured2 = createMockProvider("configured_2", configured = true)

        registry.register(configured1)
        registry.register(configured2)

        // First configured provider remains active
        assertEquals("configured_1", registry.getActiveProvider()?.providerId)
    }

    @Test
    fun setActiveProvider_and_configure_with_unknown_ids_cause_zero_exceptions() {
        val registry = AIProviderRegistry(registerDefaults = false)

        // Unknown provider
        registry.setActiveProvider("non_existent")
        assertNull(registry.getActiveProvider())

        // Configure unknown provider
        registry.configure(AIConfiguration(providerId = "non_existent", apiKey = "key123"))
        assertNull(registry.getActiveProvider())
    }

    @Test
    fun defaults_registration_handles_configuration_correctly() {
        val registry = AIProviderRegistry(registerDefaults = true)
        assertEquals(2, registry.getAllProviders().size)
        assertEquals("gemini", registry.getActiveProvider()?.providerId)

        // Gemini is not configured initially
        assertFalse(registry.getActiveProvider()!!.isConfigured)

        // Register custom configured provider -> promotes over unconfigured Gemini
        val customConfigured = createMockProvider("custom_llm", configured = true)
        registry.register(customConfigured)
        assertEquals("custom_llm", registry.getActiveProvider()?.providerId)
    }
}
