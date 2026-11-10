package com.vibespace.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.manager.RunnerManager
import com.vibespace.core.runner.model.RunnerMode
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
        ensureInitialSession()
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

    fun submitCommand() {
        val input = _uiState.value.commandInput.trim()
        val session = _uiState.value.activeSession ?: return
        if (input.isEmpty()) return

        _uiState.update { it.copy(commandInput = "") }
        viewModelScope.launch(dispatchers.default) {
            // Echo input to terminal session
            terminalService.sendInput(session.sessionId, input)

            // Execute via active Tri-Mode Runner and pipe outputs back to session
            runnerManager?.let { manager ->
                val result = manager.runCommand(input, session.workingDirectory)
                result.onSuccess { exec ->
                    exec.outputs.forEach { out ->
                        terminalService.sendInput(session.sessionId, out.line)
                    }
                }
            }
        }
    }

    fun clearActiveSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch(dispatchers.default) {
            terminalService.clearSession(session.sessionId)
        }
    }
}
