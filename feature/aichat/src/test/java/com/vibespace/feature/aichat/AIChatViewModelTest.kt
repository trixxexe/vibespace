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
        assertTrue(viewModel.uiState.value.messages.isNotEmpty())
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
}
