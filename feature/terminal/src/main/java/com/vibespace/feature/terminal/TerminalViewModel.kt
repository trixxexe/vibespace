package com.vibespace.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.language.model.DiagnosticPosition
import com.vibespace.core.language.model.DiagnosticRange
import com.vibespace.core.language.model.DiagnosticSeverity
import com.vibespace.core.runner.alpine.AlpineInstallationState
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.manager.RunnerManager
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.model.TerminalLine
import com.vibespace.core.terminal.service.DefaultTerminalService
import com.vibespace.core.terminal.service.TerminalService
import com.vibespace.core.workspace.manager.WorkspaceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    private var editorDiagnostics: List<Diagnostic> = emptyList()
    private var runtimeDiagnostics: List<Diagnostic> = emptyList()

    init {
        observeSessions()
        observeRunnerManager()
        observeAlpineState()
        observeWorkspaceProject()
        ensureInitialSession()
    }

    private fun getActiveWorkingDirectory(): String {
        return workspaceManager?.currentProject?.value?.rootPath
            ?: workspaceManager?.currentProject?.value?.rootUri?.takeIf { it.startsWith("/") }
            ?: "~"
    }

    private fun observeWorkspaceProject() {
        val manager = workspaceManager ?: return
        viewModelScope.launch(dispatchers.default) {
            manager.currentProject.collect { project ->
                val projectPath = project?.rootPath
                if (!projectPath.isNullOrBlank() && File(projectPath).exists()) {
                    val active = _uiState.value.activeSession
                    if (active != null && (active.workingDirectory == "~" || !File(active.workingDirectory).exists())) {
                        terminalService.setWorkingDirectory(active.sessionId, projectPath)
                    }
                }
            }
        }
    }

    private fun observeAlpineState() {
        val manager = runnerManager ?: return
        val alpineRunner = manager.getRunnerForMode(RunnerMode.ALPINE) as? AlpineLinuxRunner ?: return
        viewModelScope.launch(dispatchers.default) {
            alpineRunner.installationState.collect { state ->
                val lspReady = state is AlpineInstallationState.Installed &&
                    alpineRunner.isLanguageServerInstalled("python") &&
                    alpineRunner.isLanguageServerInstalled("typescript")
                _uiState.update {
                    it.copy(
                        isAlpineInstalled = state is AlpineInstallationState.Installed,
                        isLanguageServersReady = lspReady,
                        alpineInstallationState = state,
                        languageServerMessage = if (lspReady && it.languageServerMessage == null) {
                            "Python and TypeScript LSP servers are ready."
                        } else {
                            it.languageServerMessage
                        }
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
                terminalService.createSession("Terminal 1", getActiveWorkingDirectory())
            }
        }
    }

    fun selectTab(tab: BottomPanelTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateDiagnostics(diagnosticsList: List<Diagnostic>) {
        editorDiagnostics = diagnosticsList
        refreshCombinedDiagnostics()
    }

    private fun refreshCombinedDiagnostics() {
        val combined = (editorDiagnostics + runtimeDiagnostics).distinctBy {
            "${it.range.start.line}:${it.range.start.column}:${it.message}"
        }
        _uiState.update {
            it.copy(
                diagnostics = combined,
                problemsCount = combined.size
            )
        }
    }

    fun switchRunnerMode(mode: RunnerMode) {
        runnerManager?.setRunnerMode(mode)
        _uiState.update { it.copy(activeRunnerMode = mode) }
        val session = _uiState.value.activeSession ?: return
        if (mode != RunnerMode.ALPINE) {
            interactiveCollectors.remove(session.sessionId)?.cancel()
            viewModelScope.launch(dispatchers.io) {
                runnerManager?.closeInteractiveTerminal(session.sessionId)
            }
        }
    }

    fun interruptTerminalCommand() {
        val sessionId = _uiState.value.activeSession?.sessionId ?: return
        viewModelScope.launch(dispatchers.io) {
            val activeExecs = runnerManager?.activeExecutions?.value.orEmpty()
            val runningExec = activeExecs.lastOrNull { it.state == com.vibespace.core.runner.model.ExecutionState.RUNNING }
            if (runningExec != null) {
                runnerManager?.stopExecution(runningExec.executionId)
                return@launch
            }
            when (val result = runnerManager?.interruptTerminalCommand(sessionId)) {
                is VibeResult.Error -> {
                    if (!result.message.contains("No interactive terminal")) {
                        terminalService.appendLine(sessionId, result.message, StreamType.STDERR)
                    }
                }
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
                val lspReady = alpineRunner.isLanguageServerInstalled("python") &&
                    alpineRunner.isLanguageServerInstalled("typescript")
                _uiState.update {
                    it.copy(
                        isAlpineInstalled = true,
                        isLanguageServersReady = lspReady
                    )
                }
            }
        }
    }

    fun installLanguageServers() {
        val manager = runnerManager ?: return
        val session = _uiState.value.activeSession ?: return
        val alpineRunner = manager.getRunnerForMode(RunnerMode.ALPINE) as? AlpineLinuxRunner
        if (alpineRunner != null &&
            alpineRunner.isLanguageServerInstalled("python") &&
            alpineRunner.isLanguageServerInstalled("typescript")
        ) {
            viewModelScope.launch(dispatchers.default) {
                terminalService.appendLine(session.sessionId, "Python (pylsp) and TypeScript language servers are installed and ready in Alpine.", StreamType.SYSTEM)
                _uiState.update {
                    it.copy(
                        isInstallingLanguageServers = false,
                        isLanguageServersReady = true,
                        languageServerMessage = "Python and TypeScript LSP servers are ready."
                    )
                }
            }
            return
        }
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
                        _uiState.update {
                            it.copy(
                                isInstallingLanguageServers = false,
                                isLanguageServersReady = true,
                                languageServerMessage = "Python and TypeScript LSP servers are ready."
                            )
                        }
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

    fun navigateHistoryUp() {
        val history = _uiState.value.commandHistory
        if (history.isEmpty()) return
        val currentIdx = _uiState.value.historyIndex
        val nextIdx = if (currentIdx <= 0) 0 else currentIdx - 1
        val cmd = history.getOrNull(nextIdx) ?: return
        _uiState.update { it.copy(commandInput = cmd, historyIndex = nextIdx) }
    }

    fun navigateHistoryDown() {
        val history = _uiState.value.commandHistory
        if (history.isEmpty()) return
        val currentIdx = _uiState.value.historyIndex
        if (currentIdx == -1) return
        val nextIdx = currentIdx + 1
        if (nextIdx >= history.size) {
            _uiState.update { it.copy(commandInput = "", historyIndex = -1) }
        } else {
            _uiState.update { it.copy(commandInput = history[nextIdx], historyIndex = nextIdx) }
        }
    }

    fun onConsoleInputChange(text: String) {
        _uiState.update { it.copy(consoleInput = text) }
    }

    fun submitConsoleInput() {
        val raw = _uiState.value.consoleInput.trim()
        if (raw.isEmpty()) return
        _uiState.update { it.copy(consoleInput = "") }
        val session = _uiState.value.activeSession ?: return

        viewModelScope.launch(dispatchers.default) {
            appendConsoleLine("> $raw", StreamType.COMMAND)
            val evalCommand = when {
                raw.startsWith("python") || raw.startsWith("node") || raw.startsWith("sh ") -> raw
                else -> "python3 -c ${shellQuote("print($raw)")}"
            }
            if (runnerManager != null) {
                val targetRunner = if (_uiState.value.isAlpineInstalled) RunnerMode.ALPINE.id else _uiState.value.activeRunnerMode.id
                when (val res = runnerManager.runCommand(evalCommand, session.workingDirectory, preferredRunnerId = targetRunner, sessionId = session.sessionId)) {
                    is VibeResult.Success -> {
                        if (res.data.outputs.isEmpty()) {
                            appendConsoleLine("(completed)", StreamType.SYSTEM)
                        } else {
                            for (out in res.data.outputs) {
                                appendConsoleLine(out.line, if (out.isError) StreamType.STDERR else StreamType.STDOUT)
                            }
                        }
                    }
                    is VibeResult.Error -> appendConsoleLine(res.message, StreamType.STDERR)
                    else -> Unit
                }
            } else {
                val beforeCount = _uiState.value.activeSession?.lines?.size ?: 0
                terminalService.sendInput(session.sessionId, raw)
                val afterLines = _uiState.value.activeSession?.lines.orEmpty().drop(beforeCount + 1)
                afterLines.forEach { appendConsoleLine(it.text, it.streamType) }
            }
        }
    }

    private fun appendOutputExecution(command: String, outputs: List<TerminalLine>) {
        val header = TerminalLine(
            id = IdGenerator.shortId("out"),
            text = "Executing: $command",
            streamType = StreamType.COMMAND
        )
        val cleanOutputs = outputs.filterNot { line ->
            line.text.startsWith("[Process finished with exit code 0]") ||
                line.text.startsWith("[Process exited with code 0]") ||
                line.text.startsWith("[Alpine Linux PRoot Container]") ||
                line.text.startsWith("[VibeSpace Instant Runner]")
        }
        _uiState.update { state ->
            val updatedLines = (state.outputLines + header + cleanOutputs).takeLast(1000)
            val updatedLogs = updatedLines.map { it.text }
            state.copy(
                outputLines = updatedLines,
                outputLogs = updatedLogs
            )
        }
    }

    private fun appendConsoleLine(text: String, type: StreamType) {
        val line = TerminalLine(id = IdGenerator.shortId("con"), text = text, streamType = type)
        _uiState.update { state ->
            state.copy(consoleLines = (state.consoleLines + line).takeLast(500))
        }
    }

    private fun parseRuntimeDiagnosticsFromOutput(lines: List<String>) {
        val parsed = mutableListOf<Diagnostic>()
        val pyLineRegex = Regex("""File "([^"]+)", line (\d+)""")
        val nodeLineRegex = Regex("""at (?:.+ \()?([^():]+):(\d+):(\d+)\)?""")

        for (i in lines.indices) {
            val line = lines[i]
            val pyMatch = pyLineRegex.find(line)
            if (pyMatch != null) {
                val lineNum = pyMatch.groupValues[2].toIntOrNull() ?: 1
                val errMsg = lines.drop(i + 1).lastOrNull {
                    it.contains("Error:") || it.contains("Exception:")
                } ?: lines.getOrNull(i + 1)?.trim() ?: "Runtime error"
                parsed.add(
                    Diagnostic(
                        id = IdGenerator.shortId("rtdiag"),
                        message = errMsg,
                        severity = DiagnosticSeverity.ERROR,
                        range = DiagnosticRange(
                            start = DiagnosticPosition(lineNum, 1),
                            end = DiagnosticPosition(lineNum, 80)
                        ),
                        source = "runtime"
                    )
                )
            }
            val nodeMatch = nodeLineRegex.find(line)
            if (nodeMatch != null) {
                val lineNum = nodeMatch.groupValues[2].toIntOrNull() ?: 1
                val colNum = nodeMatch.groupValues[3].toIntOrNull() ?: 1
                val errMsg = lines.take(i).lastOrNull {
                    it.contains("Error:") || it.startsWith("Uncaught ")
                } ?: "Runtime error"
                parsed.add(
                    Diagnostic(
                        id = IdGenerator.shortId("rtdiag"),
                        message = errMsg,
                        severity = DiagnosticSeverity.ERROR,
                        range = DiagnosticRange(
                            start = DiagnosticPosition(lineNum, colNum),
                            end = DiagnosticPosition(lineNum, colNum + 20)
                        ),
                        source = "runtime"
                    )
                )
            }
        }
        runtimeDiagnostics = parsed
        refreshCombinedDiagnostics()
    }

    fun executeCommand(command: String, preferredRunnerId: String? = null) {
        val session = terminalService.activeSession.value ?: _uiState.value.activeSession ?: return
        val enteredCommand = command.trim()
        val cleanCommand = if (enteredCommand.startsWith("$ ")) enteredCommand.removePrefix("$ ").trim() else enteredCommand
        if (cleanCommand.isEmpty()) return

        _uiState.update { state ->
            val updatedHistory = (state.commandHistory + cleanCommand).takeLast(200)
            state.copy(commandHistory = updatedHistory, historyIndex = updatedHistory.size)
        }

        viewModelScope.launch(dispatchers.default) {
            val activeSession = terminalService.activeSession.value ?: session
            val cmdBinary = cleanCommand.split("\\s+".toRegex()).firstOrNull()?.lowercase()?.trim('"', '\'').orEmpty()
            val safeBuiltins = DefaultTerminalService.SUPPORTED_SHELL_COMMANDS

            val effectiveRunnerId = preferredRunnerId ?: _uiState.value.activeRunnerMode.id
            val useInternalShell = (effectiveRunnerId == RunnerMode.EMBEDDED.id || runnerManager == null) &&
                cmdBinary in safeBuiltins &&
                !cleanCommand.startsWith("python", ignoreCase = true) &&
                !cleanCommand.startsWith("node", ignoreCase = true)

            if (useInternalShell) {
                val beforeSize = (terminalService.activeSession.value ?: activeSession).lines.size
                terminalService.sendInput(activeSession.sessionId, cleanCommand)
                val currentLines = (terminalService.activeSession.value ?: activeSession).lines
                val added = if (cleanCommand == "clear" || cleanCommand == "reset") {
                    emptyList()
                } else {
                    currentLines.drop((beforeSize + 1).coerceAtMost(currentLines.size))
                }
                if (cleanCommand != "clear" && cleanCommand != "reset") {
                    appendOutputExecution(cleanCommand, added)
                }
            } else if (runnerManager != null) {
                // Keep host session workingDirectory in sync if user runs `cd <dir>` in Alpine/Runner mode
                if (cmdBinary == "cd") {
                    val targetArg = cleanCommand.removePrefix("cd").trim().removeSurrounding("\"").removeSurrounding("'")
                    if (targetArg.isNotEmpty() && !targetArg.startsWith("/workspace")) {
                        terminalService.setWorkingDirectory(activeSession.sessionId, targetArg)
                    }
                }
                terminalService.appendLine(activeSession.sessionId, "$ $cleanCommand", StreamType.COMMAND)
                when (val result = runnerManager.runCommand(
                    cleanCommand,
                    activeSession.workingDirectory,
                    preferredRunnerId = preferredRunnerId,
                    sessionId = activeSession.sessionId
                )) {
                    is VibeResult.Success -> {
                        val producedLines = mutableListOf<TerminalLine>()
                        for (out in result.data.outputs) {
                            val type = if (out.isError) StreamType.STDERR else StreamType.STDOUT
                            terminalService.appendLine(activeSession.sessionId, out.line, type)
                            producedLines.add(TerminalLine(IdGenerator.shortId("out"), out.line, type))
                        }
                        appendOutputExecution(cleanCommand, producedLines)
                        parseRuntimeDiagnosticsFromOutput(result.data.outputs.map { it.line })
                    }
                    is VibeResult.Error -> {
                        terminalService.appendLine(activeSession.sessionId, result.message, StreamType.STDERR)
                        appendOutputExecution(
                            cleanCommand,
                            listOf(TerminalLine(IdGenerator.shortId("err"), result.message, StreamType.STDERR))
                        )
                    }
                    else -> Unit
                }
            } else {
                val beforeSize = (terminalService.activeSession.value ?: activeSession).lines.size
                terminalService.sendInput(activeSession.sessionId, cleanCommand)
                val currentLines = (terminalService.activeSession.value ?: activeSession).lines
                val added = currentLines.drop((beforeSize + 1).coerceAtMost(currentLines.size))
                appendOutputExecution(cleanCommand, added)
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
            fileName.endsWith(".sh", ignoreCase = true) || normalizedLanguage == "shell" -> "sh"
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
            when (_uiState.value.activeTab) {
                BottomPanelTab.OUTPUT -> _uiState.update { it.copy(outputLines = emptyList(), outputLogs = emptyList()) }
                BottomPanelTab.CONSOLE -> _uiState.update { it.copy(consoleLines = emptyList()) }
                else -> terminalService.clearSession(session.sessionId)
            }
        }
    }

    fun clearOutput() {
        _uiState.update { it.copy(outputLines = emptyList(), outputLogs = emptyList()) }
    }

    fun clearConsole() {
        _uiState.update { it.copy(consoleLines = emptyList()) }
    }

    fun createNewSession(title: String? = null, workingDirectory: String = getActiveWorkingDirectory()) {
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
                terminalService.createSession("Terminal 1", getActiveWorkingDirectory())
            }
        }
    }

    fun launchTerminalAgent(agentName: String = "AI Agent") {
        viewModelScope.launch(dispatchers.default) {
            val title = "Agent: $agentName"
            val result = terminalService.createSession(title, getActiveWorkingDirectory())
            if (result is VibeResult.Success) {
                val sessionId = result.data.sessionId
                terminalService.appendLine(sessionId, "=== Agent Terminal ===", StreamType.SYSTEM)
                terminalService.appendLine(sessionId, "VibeSpace does not bundle CLI coding agents. Configure a supported runner or use the AI Assistant panel.", StreamType.SYSTEM)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        interactiveCollectors.values.forEach { it.cancel() }
        interactiveCollectors.clear()
    }
}
