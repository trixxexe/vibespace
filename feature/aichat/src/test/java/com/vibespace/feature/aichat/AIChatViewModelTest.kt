package com.vibespace.feature.aichat

import com.vibespace.core.ai.service.AIService
import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIChatViewModelTest {

    private val aiService = AIService(dispatchers = TestDispatcherProvider())

    @Test
    fun sendPrompt_submits_to_ai_service_and_clears_input() = runTest {
        val viewModel = AIChatViewModel(
            aiService = aiService,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.onPromptChange("How to optimize Android Compose?")
        assertEquals("How to optimize Android Compose?", viewModel.uiState.value.inputPrompt)

        viewModel.sendPrompt()
        assertEquals("", viewModel.uiState.value.inputPrompt)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("No AI provider configured"))
    }

    @Test
    fun clearHistory_resets_messages() = runTest {
        val viewModel = AIChatViewModel(
            aiService = aiService,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.sendPrompt("Hello")
        viewModel.clearHistory()
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun configureApiKey_updates_configuration_state() = runTest {
        val viewModel = AIChatViewModel(
            aiService = aiService,
            dispatchers = TestDispatcherProvider()
        )

        assertFalse(viewModel.uiState.value.isConfigured)
        viewModel.configureApiKey("AIzaSyTest123")
        assertTrue(viewModel.uiState.value.isConfigured)
        assertEquals("gemini", viewModel.uiState.value.activeProvider)
    }

    @Test
    fun unconfigured_prompt_shows_setup_error_without_assistant_placeholder() = runTest {
        val service = AIService(dispatchers = TestDispatcherProvider())
        val viewModel = AIChatViewModel(aiService = service, dispatchers = TestDispatcherProvider())

        viewModel.sendPrompt("Explain this file")

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("No AI provider configured"))
    }
}
