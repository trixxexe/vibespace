package com.vibespace.feature.terminal

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.runner.manager.DefaultRunnerManager
import com.vibespace.core.runner.model.RunnerMode
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

    @Test
    fun switchRunnerMode_updates_active_mode_in_manager_and_state() = runTest {
        val runnerManager = DefaultRunnerManager(dispatchers = TestDispatcherProvider())
        val viewModel = TerminalViewModel(
            terminalService = terminalService,
            runnerManager = runnerManager,
            dispatchers = TestDispatcherProvider()
        )

        assertEquals(RunnerMode.EMBEDDED, viewModel.uiState.value.activeRunnerMode)

        viewModel.switchRunnerMode(RunnerMode.ALPINE)
        assertEquals(RunnerMode.ALPINE, viewModel.uiState.value.activeRunnerMode)
        assertEquals(RunnerMode.ALPINE, runnerManager.activeRunnerMode.value)

        viewModel.switchRunnerMode(RunnerMode.TERMUX)
        assertEquals(RunnerMode.TERMUX, viewModel.uiState.value.activeRunnerMode)
        assertEquals(RunnerMode.TERMUX, runnerManager.activeRunnerMode.value)
    }

    @Test
    fun runActiveDocument_dispatches_command_to_runner() = runTest {
        val runnerManager = DefaultRunnerManager(dispatchers = TestDispatcherProvider())
        val viewModel = TerminalViewModel(
            terminalService = terminalService,
            runnerManager = runnerManager,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.runActiveDocument("script.py", "python")
        val active = viewModel.uiState.value.activeSession
        assertNotNull(active)
        assertTrue(active!!.lines.any { it.text.contains("$ python script.py") })
    }

    @Test
    fun multiple_sessions_can_be_created_switched_and_closed() = runTest {
        val viewModel = TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        )

        val initialCount = viewModel.uiState.value.sessions.size
        viewModel.createNewSession("Terminal 2")
        assertEquals(initialCount + 1, viewModel.uiState.value.sessions.size)

        val secondSession = viewModel.uiState.value.sessions.last()
        assertEquals("Terminal 2", secondSession.title)
        assertEquals(secondSession.sessionId, viewModel.uiState.value.activeSession?.sessionId)

        val firstSession = viewModel.uiState.value.sessions.first()
        viewModel.switchSession(firstSession.sessionId)
        assertEquals(firstSession.sessionId, viewModel.uiState.value.activeSession?.sessionId)

        viewModel.closeSession(secondSession.sessionId)
        assertEquals(initialCount, viewModel.uiState.value.sessions.size)
    }

    @Test
    fun launchTerminalAgent_creates_dedicated_agent_session() = runTest {
        val viewModel = TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.launchTerminalAgent("Code Agent")
        val active = viewModel.uiState.value.activeSession
        assertNotNull(active)
        assertTrue(active!!.title.contains("Code Agent"))
        assertTrue(active.lines.any { it.text.contains("VibeSpace AI Terminal Agent") })
    }
}
