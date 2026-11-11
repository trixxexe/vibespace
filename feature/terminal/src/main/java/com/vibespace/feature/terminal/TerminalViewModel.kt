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
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.VirtualFile
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.service.TerminalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TerminalViewModel(
    private val terminalService: TerminalService,
    private val runnerManager: RunnerManager? = null,
    private val workspaceManager: WorkspaceManager? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()
    private val interactiveCollectors = ConcurrentHashMap<String, Job>()

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
                if (state is AlpineInstallationState.Installed && _uiState.value.activeRunnerMode == RunnerMode.ALPINE) {
                    _uiState.value.activeSession?.let { startInteractiveTerminal(it.sessionId, it.workingDirectory) }
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
            var lastActiveSessionId: String? = null
            terminalService.activeSession.collect { active ->
                val sessionChanged = active?.sessionId != lastActiveSessionId
                lastActiveSessionId = active?.sessionId
                _uiState.update { it.copy(activeSession = active) }
                if (sessionChanged && active != null && _uiState.value.activeRunnerMode == RunnerMode.ALPINE) {
                    startInteractiveTerminal(active.sessionId, active.workingDirectory)
                }
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
        val session = _uiState.value.activeSession ?: return
        if (mode == RunnerMode.ALPINE) {
            startInteractiveTerminal(session.sessionId, session.workingDirectory)
        } else {
            interactiveCollectors.remove(session.sessionId)?.cancel()
            viewModelScope.launch(dispatchers.io) {
                runnerManager?.closeInteractiveTerminal(session.sessionId)
            }
        }
    }

    private fun startInteractiveTerminal(sessionId: String, workingDirectory: String) {
        val manager = runnerManager ?: return
        if (interactiveCollectors[sessionId]?.isActive == true) return
        val job = viewModelScope.launch(dispatchers.io, start = CoroutineStart.LAZY) {
            when (val started = manager.startInteractiveTerminal(sessionId, workingDirectory)) {
                is VibeResult.Success -> {
                    terminalService.appendLine(
                        sessionId,
                        "Alpine interactive shell connected. Commands run with VibeSpace app permissions; Alpine is not an Android security sandbox.",
                        StreamType.SYSTEM
                    )
                    manager.observeInteractiveOutput(sessionId).collect { output ->
                        val type = if (output.isError) StreamType.STDERR else StreamType.STDOUT
                        terminalService.appendLine(sessionId, output.line, type)
                    }
                }
                is VibeResult.Error -> terminalService.appendLine(sessionId, started.message, StreamType.STDERR)
                else -> Unit
            }
        }
        val previous = interactiveCollectors.putIfAbsent(sessionId, job)
        if (previous == null) {
            job.invokeOnCompletion { interactiveCollectors.remove(sessionId, job) }
            job.start()
        } else job.cancel()
    }

    fun interruptTerminalCommand() {
        val sessionId = _uiState.value.activeSession?.sessionId ?: return
        viewModelScope.launch(dispatchers.io) {
            when (val result = runnerManager?.interruptTerminalCommand(sessionId)) {
                is VibeResult.Error -> terminalService.appendLine(sessionId, result.message, StreamType.STDERR)
                else -> Unit
            }
        }
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

    fun installLanguageServers() {
        val manager = runnerManager ?: return
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isInstallingLanguageServers = true, languageServerMessage = null) }
            val command = "apk add --no-cache py3-lsp-server nodejs npm && npm install --global typescript-language-server typescript"
            when (val result = manager.runCommand(
                command = command,
                workingDirectory = session.workingDirectory,
                preferredRunnerId = RunnerMode.ALPINE.id,
                sessionId = session.sessionId
            )) {
                is VibeResult.Success -> {
                    result.data.outputs.forEach { out ->
                        terminalService.appendLine(session.sessionId, out.line, if (out.isError) StreamType.STDERR else StreamType.STDOUT)
                    }
                    if (result.data.exitCode == 0) {
                        terminalService.appendLine(session.sessionId, "Python and TypeScript language servers installed in Alpine.", StreamType.SYSTEM)
                        _uiState.update { it.copy(isInstallingLanguageServers = false, languageServerMessage = "Python and TypeScript LSP servers are ready.") }
                    } else {
                        val message = "Language server installation exited with code ${result.data.exitCode}. See terminal output for details."
                        terminalService.appendLine(session.sessionId, message, StreamType.STDERR)
                        _uiState.update { it.copy(isInstallingLanguageServers = false, languageServerMessage = message) }
                    }
                }
                is VibeResult.Error -> {
                    terminalService.appendLine(session.sessionId, result.message, StreamType.STDERR)
                    _uiState.update { it.copy(isInstallingLanguageServers = false, languageServerMessage = result.message) }
                }
                else -> _uiState.update { it.copy(isInstallingLanguageServers = false) }
            }
        }
    }

    fun onCommandInputChange(text: String) {
        _uiState.update { it.copy(commandInput = text) }
    }

    fun executeCommand(command: String, preferredRunnerId: String? = null) {
        val session = _uiState.value.activeSession ?: return
        val enteredCommand = command.trim()
        val cleanCommand = if (enteredCommand.startsWith("$ ")) enteredCommand.removePrefix("$ ").trim() else enteredCommand
        if (cleanCommand.isEmpty()) return

        viewModelScope.launch(dispatchers.default) {
            if (_uiState.value.activeRunnerMode == RunnerMode.ALPINE) {
                when (val inputResult = runnerManager?.sendTerminalInput(session.sessionId, cleanCommand)) {
                    is VibeResult.Success -> return@launch
                    is VibeResult.Error -> {
                        if (!inputResult.message.contains("No interactive terminal is attached")) {
                            terminalService.appendLine(session.sessionId, inputResult.message, StreamType.STDERR)
                            return@launch
                        }
                    }
                    else -> Unit
                }
            }

            terminalService.appendLine(session.sessionId, "$ $cleanCommand", StreamType.COMMAND)

            if (runnerManager != null) {
                when (val result = runnerManager.runCommand(
                    cleanCommand,
                    session.workingDirectory,
                    preferredRunnerId = preferredRunnerId,
                    sessionId = session.sessionId
                )) {
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

    fun runActiveDocument(fileName: String, languageId: String, documentUri: String? = null) {
        if (_uiState.value.activeRunnerMode == RunnerMode.TERMUX) {
            val session = _uiState.value.activeSession ?: return
            viewModelScope.launch(dispatchers.default) {
                terminalService.appendLine(session.sessionId, "Run is unavailable through Termux because this project is not shared with Termux.", StreamType.STDERR)
                terminalService.appendLine(session.sessionId, "Open the project from Termux or switch to the embedded evaluator for supported Python/JavaScript snippets.", StreamType.SYSTEM)
            }
            return
        }

        val normalizedLanguage = languageId.lowercase()
        val commandPrefix = when {
            fileName.endsWith(".py", ignoreCase = true) || normalizedLanguage == "python" -> "python3"
            fileName.endsWith(".js", ignoreCase = true) || fileName.endsWith(".mjs", ignoreCase = true) ||
                fileName.endsWith(".cjs", ignoreCase = true) || normalizedLanguage == "javascript" -> "node"
            else -> {
                val session = _uiState.value.activeSession ?: return
                viewModelScope.launch(dispatchers.default) {
                    terminalService.appendLine(session.sessionId, "The embedded evaluator cannot run '${fileName.substringAfterLast('.')}' files.", StreamType.STDERR)
                    terminalService.appendLine(session.sessionId, "Supported Run targets are basic Python and JavaScript files. No command was executed.", StreamType.SYSTEM)
                }
                return
            }
        }
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(dispatchers.io) {
            val relativePath = if (documentUri == null) fileName else resolveWorkspaceRelativePath(documentUri)
            if (relativePath == null || relativePath.isBlank()) {
                terminalService.appendLine(session.sessionId, "Cannot run $fileName: it is not inside the active workspace.", StreamType.STDERR)
                return@launch
            }
            val command = "$commandPrefix ${shellQuote("/workspace/$relativePath")}"
            executeCommand(command, preferredRunnerId = RunnerMode.ALPINE.id)
        }
    }

    private suspend fun resolveWorkspaceRelativePath(documentUri: String): String? {
        val manager = workspaceManager ?: return null
        val project = manager.currentProject.value ?: return null
        val allFiles = when (val result = manager.getAllProjectFiles(project.rootUri)) {
            is VibeResult.Success -> result.data
            else -> return null
        }
        val byUri = allFiles.associateBy { it.uri }
        val file = byUri[documentUri] ?: return null
        if (!file.isFile) return null
        val parts = mutableListOf(file.name)
        var parentUri = file.parentUri ?: return null
        val seen = mutableSetOf<String>()
        while (parentUri != project.rootUri) {
            if (!seen.add(parentUri)) return null
            val parent = byUri[parentUri]?.takeIf { it.isDirectory } ?: return null
            parts += parent.name
            parentUri = parent.parentUri ?: return null
        }
        return parts.asReversed().joinToString("/")
    }

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

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
            runnerManager?.closeInteractiveTerminal(sessionId)
            interactiveCollectors.remove(sessionId)?.cancel()
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

    override fun onCleared() {
        super.onCleared()
        interactiveCollectors.values.forEach { it.cancel() }
        interactiveCollectors.clear()
    }
}
