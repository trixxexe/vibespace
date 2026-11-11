package com.vibespace.feature.terminal

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.runner.manager.DefaultRunnerManager
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.service.DefaultTerminalService
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalViewModelTest {

    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun clearViewModels() {
        viewModelStore.clear()
    }

    private fun track(viewModel: TerminalViewModel): TerminalViewModel = viewModel.also {
        viewModelStore.put("terminal-test-${nextViewModelKey++}", it)
    }

    private val terminalService = DefaultTerminalService(dispatchers = TestDispatcherProvider())

    @Test
    fun initial_session_created_and_commands_can_be_sent() = runTest {
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        ))

        assertNotNull(viewModel.uiState.value.activeSession)

        viewModel.onCommandInputChange("echo MobileIDE")
        viewModel.submitCommand()

        assertEquals("", viewModel.uiState.value.commandInput)
        val active = viewModel.uiState.value.activeSession
        assertTrue(active!!.lines.any { it.text.contains("MobileIDE") })
    }

    @Test
    fun selectTab_switches_active_tab() = runTest {
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        ))

        viewModel.selectTab(BottomPanelTab.OUTPUT)
        assertEquals(BottomPanelTab.OUTPUT, viewModel.uiState.value.activeTab)
    }

    @Test
    fun switchRunnerMode_updates_active_mode_in_manager_and_state() = runTest {
        val runnerManager = DefaultRunnerManager(dispatchers = TestDispatcherProvider())
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            runnerManager = runnerManager,
            dispatchers = TestDispatcherProvider()
        ))

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
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            runnerManager = runnerManager,
            dispatchers = TestDispatcherProvider()
        ))

        viewModel.runActiveDocument("script.py", "python")
        val active = viewModel.uiState.value.activeSession
        assertNotNull(active)
        assertTrue(active!!.lines.any { it.text.contains("python") && it.text.contains("script.py") })
    }

    @Test
    fun runActiveDocument_uses_workspace_relative_path_and_real_alpine_runner() = runTest {
        val workspace = com.vibespace.core.workspace.manager.DefaultWorkspaceManager(dispatchers = TestDispatcherProvider())
        val project = com.vibespace.core.workspace.model.Project("run-project", "Run Project", "vibe://projects/run-project")
        workspace.openProject(project)
        val directory = workspace.createDirectory(project.rootUri, "src").getOrNull()!!
        val file = workspace.createFile(directory.uri, "main.py", "print('real runtime')").getOrNull()!!
        val runnerManager = DefaultRunnerManager(workspaceManager = workspace, dispatchers = TestDispatcherProvider())
        val viewModel = track(TerminalViewModel(
            terminalService = DefaultTerminalService(dispatchers = TestDispatcherProvider()),
            runnerManager = runnerManager,
            workspaceManager = workspace,
            dispatchers = TestDispatcherProvider()
        ))

        viewModel.runActiveDocument("main.py", "python", file.uri)

        val lines = viewModel.uiState.value.activeSession!!.lines
        assertTrue(lines.any { it.text.contains("/workspace/src/main.py") })
        assertTrue(lines.none { it.text == "main.py" && it.streamType == StreamType.STDOUT })
        assertTrue(lines.any { it.streamType == StreamType.STDERR && it.text.contains("not installed", ignoreCase = true) })
    }

    @Test
    fun multiple_sessions_can_be_created_switched_and_closed() = runTest {
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        ))

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
    fun launchTerminalAgent_creates_session_with_capability_disclosure() = runTest {
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        ))

        viewModel.launchTerminalAgent("Code Agent")
        val active = viewModel.uiState.value.activeSession
        assertNotNull(active)
        assertTrue(active!!.title.contains("Code Agent"))
        assertTrue(active.lines.any { it.text.contains("does not bundle CLI coding agents") })
    }

    @Test
    fun updateDiagnostics_updates_problems_count() {
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        ))

        val diag = com.vibespace.core.language.model.Diagnostic(
            id = "d1",
            message = "Syntax error",
            severity = com.vibespace.core.language.model.DiagnosticSeverity.ERROR,
            range = com.vibespace.core.language.model.DiagnosticRange(
                start = com.vibespace.core.language.model.DiagnosticPosition(1, 1),
                end = com.vibespace.core.language.model.DiagnosticPosition(1, 5)
            )
        )

        viewModel.updateDiagnostics(listOf(diag))
        assertEquals(1, viewModel.uiState.value.problemsCount)
        assertEquals(1, viewModel.uiState.value.diagnostics.size)
    }

    @Test
    fun command_with_leading_prompt_is_sanitized_and_not_duplicated() = runTest {
        val viewModel = track(TerminalViewModel(
            terminalService = terminalService,
            dispatchers = TestDispatcherProvider()
        ))

        viewModel.executeCommand("$ echo sanitized")
        val active = viewModel.uiState.value.activeSession
        assertNotNull(active)
        assertTrue(active!!.lines.any { it.streamType == StreamType.COMMAND && it.text == "$ echo sanitized" })
        assertTrue(active.lines.any { it.streamType == StreamType.STDOUT && it.text == "sanitized" })
    }
}
