package com.vibespace.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.runner.alpine.AlpineInstallationState
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.manager.RunnerManager
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.service.TerminalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TerminalViewModel(
    private val terminalService: TerminalService,
    private val runnerManager: RunnerManager? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        observeSessions()
        observeRunnerManager()
        observeAlpineState()
        ensureInitialSession()
    }

    private fun observeAlpineState() {
        val manager = runnerManager ?: return
        val alpineRunner = manager.getRunnerForMode(RunnerMode.ALPINE) as? AlpineLinuxRunner ?: return
        viewModelScope.launch(dispatchers.default) {
            alpineRunner.installationState.collect { state ->
                _uiState.update {
                    it.copy(
                        isAlpineInstalled = state is AlpineInstallationState.Installed,
                        alpineInstallationState = state
                    )
                }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch(dispatchers.default) {
            terminalService.sessions.collect { sessionsList ->
                _uiState.update { it.copy(sessions = sessionsList) }
            }
        }
        viewModelScope.launch(dispatchers.default) {
            terminalService.activeSession.collect { active ->
                _uiState.update { it.copy(activeSession = active) }
            }
        }
    }

    private fun observeRunnerManager() {
        val manager = runnerManager ?: return
        viewModelScope.launch(dispatchers.default) {
            manager.activeRunnerMode.collect { mode ->
                _uiState.update { it.copy(activeRunnerMode = mode) }
            }
        }
    }

    private fun ensureInitialSession() {
        viewModelScope.launch(dispatchers.default) {
            if (terminalService.sessions.value.isEmpty()) {
                terminalService.createSession("Terminal 1", "~")
            }
        }
    }

    fun selectTab(tab: BottomPanelTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateDiagnostics(diagnosticsList: List<Diagnostic>) {
        _uiState.update {
            it.copy(
                diagnostics = diagnosticsList,
                problemsCount = diagnosticsList.size
            )
        }
    }

    fun switchRunnerMode(mode: RunnerMode) {
        runnerManager?.setRunnerMode(mode)
        _uiState.update { it.copy(activeRunnerMode = mode) }
    }

    fun installAlpineSubsystem() {
        val manager = runnerManager ?: return
        val alpineRunner = manager.getRunnerForMode(RunnerMode.ALPINE) as? AlpineLinuxRunner ?: return
        viewModelScope.launch(dispatchers.default) {
            val result = alpineRunner.installSubsystem()
            if (result.isSuccess) {
                _uiState.update { it.copy(isAlpineInstalled = true) }
            }
        }
    }

    fun onCommandInputChange(text: String) {
        _uiState.update { it.copy(commandInput = text) }
    }

    fun executeCommand(command: String) {
        val session = _uiState.value.activeSession ?: return
        val enteredCommand = command.trim()
        val cleanCommand = if (enteredCommand.startsWith("$ ")) enteredCommand.removePrefix("$ ").trim() else enteredCommand
        if (cleanCommand.isEmpty()) return

        viewModelScope.launch(dispatchers.default) {
            terminalService.appendLine(session.sessionId, "$ $cleanCommand", StreamType.COMMAND)

            if (runnerManager != null) {
                when (val result = runnerManager.runCommand(cleanCommand, session.workingDirectory)) {
                    is VibeResult.Success -> {
                        for (out in result.data.outputs) {
                            val type = if (out.isError) StreamType.STDERR else StreamType.STDOUT
                            terminalService.appendLine(session.sessionId, out.line, type)
                        }
                    }
                    is VibeResult.Error -> {
                        terminalService.appendLine(session.sessionId, result.message, StreamType.STDERR)
                    }
                    else -> Unit
                }
            } else {
                terminalService.sendInput(session.sessionId, cleanCommand)
            }
        }
    }

    fun submitCommand() {
        val input = _uiState.value.commandInput.trim()
        if (input.isEmpty()) return
        _uiState.update { it.copy(commandInput = "") }
        executeCommand(input)
    }

    fun runActiveDocument(fileName: String, languageId: String) {
        if (_uiState.value.activeRunnerMode == RunnerMode.TERMUX) {
            val session = _uiState.value.activeSession ?: return
            viewModelScope.launch(dispatchers.default) {
                terminalService.appendLine(session.sessionId, "Run is unavailable through Termux because this project is not shared with Termux.", StreamType.STDERR)
                terminalService.appendLine(session.sessionId, "Open the project from Termux or switch to the embedded evaluator for supported Python/JavaScript snippets.", StreamType.SYSTEM)
            }
            return
        }

        val normalizedLanguage = languageId.lowercase()
        val escapedFile = "\"" + fileName.replace("\"", "\\\"").replace("$", "\\$") + "\""
        val command = when {
            fileName.endsWith(".py", ignoreCase = true) || normalizedLanguage == "python" -> "python $escapedFile"
            fileName.endsWith(".js", ignoreCase = true) || normalizedLanguage == "javascript" -> "node $escapedFile"
            else -> {
                val session = _uiState.value.activeSession ?: return
                viewModelScope.launch(dispatchers.default) {
                    terminalService.appendLine(session.sessionId, "The embedded evaluator cannot run '${fileName.substringAfterLast('.')}' files.", StreamType.STDERR)
                    terminalService.appendLine(session.sessionId, "Supported Run targets are basic Python and JavaScript files. No command was executed.", StreamType.SYSTEM)
                }
                return
            }
        }
        executeCommand(command)
    }

    fun clearActiveSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(dispatchers.default) {
            terminalService.clearSession(session.sessionId)
        }
    }

    fun createNewSession(title: String? = null, workingDirectory: String = "~") {
        viewModelScope.launch(dispatchers.default) {
            val count = _uiState.value.sessions.size + 1
            val sessionTitle = title ?: "Terminal $count"
            terminalService.createSession(sessionTitle, workingDirectory)
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch(dispatchers.default) {
            terminalService.setActiveSession(sessionId)
        }
    }

    fun closeSession(sessionId: String) {
        viewModelScope.launch(dispatchers.default) {
            terminalService.closeSession(sessionId)
            if (terminalService.sessions.value.isEmpty()) {
                terminalService.createSession("Terminal 1", "~")
            }
        }
    }

    fun launchTerminalAgent(agentName: String = "AI Agent") {
        viewModelScope.launch(dispatchers.default) {
            val title = "Agent: $agentName"
            val result = terminalService.createSession(title, "~")
            if (result is VibeResult.Success) {
                val sessionId = result.data.sessionId
                terminalService.appendLine(sessionId, "=== Agent Terminal ===", StreamType.SYSTEM)
                terminalService.appendLine(sessionId, "VibeSpace does not bundle CLI coding agents. Configure a supported runner or use the AI Assistant panel.", StreamType.SYSTEM)
                terminalService.appendLine(sessionId, "$ ", StreamType.SYSTEM)
            }
        }
    }
}
