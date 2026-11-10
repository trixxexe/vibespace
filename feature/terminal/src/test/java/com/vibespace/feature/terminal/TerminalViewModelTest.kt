package com.vibespace.feature.terminal

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.terminal.service.DefaultTerminalService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalViewModelTest {

    private val terminalService = DefaultTerminalService(dispatchers = TestDispatcherProvider())

    @Test
    fun initial_session_created_and_commands_can_be_sent() = runTest {
        val viewModel = TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        )

        assertNotNull(viewModel.uiState.value.activeSession)

        viewModel.onCommandInputChange("echo MobileIDE")
        viewModel.submitCommand()

        assertEquals("", viewModel.uiState.value.commandInput)
        val active = viewModel.uiState.value.activeSession
        assertTrue(active!!.lines.any { it.text.contains("MobileIDE") })
    }

    @Test
    fun selectTab_switches_active_tab() = runTest {
        val viewModel = TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.selectTab(BottomPanelTab.OUTPUT)
        assertEquals(BottomPanelTab.OUTPUT, viewModel.uiState.value.activeTab)
    }
}
