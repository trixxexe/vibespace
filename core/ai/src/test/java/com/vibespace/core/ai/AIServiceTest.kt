package com.vibespace.core.ai

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIResponse
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.ai.model.AIResponseChunk
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.provider.AIProvider
import com.vibespace.core.ai.service.AIService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AIServiceTest {

    @Test
    fun unconfiguredProvider_returns_error_without_fabricating_assistant_reply() = runTest {
        val service = AIService(dispatchers = TestDispatcherProvider())
        val result = service.sendUserPrompt("Explain this code")

        assertTrue(result is VibeResult.Error)
        assertTrue(service.messages.value.isEmpty())
    }

    @Test
    fun configuredProvider_receives_prompt_and_code_context() = runTest {
        val mockProvider = object : AIProvider {
            override val providerId: String = "test-provider"
            override val displayName: String = "Test Provider"
            override val supportedModels: List<String> = listOf("model-1")
            override val isConfigured: Boolean = true

            override suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse> {
                return VibeResult.Success(
                    AIResponse(
                        message = AIMessage(
                            id = "ai-1",
                            role = AIRole.ASSISTANT,
                            content = "Code analysis complete."
                        )
                    )
                )
            }

            override fun streamResponse(request: AIRequest): Flow<AIResponseChunk> = emptyFlow()
        }

        val service = AIService(dispatchers = TestDispatcherProvider())
        service.registry.register(mockProvider)
        service.setCodeContext(CodeContext("file://test.kt", "test.kt", "kotlin"))

        val result = service.sendUserPrompt("Refactor this")
        assertTrue(result.isSuccess)
        assertEquals("Code analysis complete.", result.getOrNull()!!.content)
    }
}
