package com.vibespace.feature.terminal

import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.runner.alpine.AlpineInstallationState
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.terminal.model.TerminalSession

enum class BottomPanelTab {
    TERMINAL,
    OUTPUT,
    PROBLEMS,
    CONSOLE
}

data class TerminalUiState(
    val activeTab: BottomPanelTab = BottomPanelTab.TERMINAL,
    val sessions: List<TerminalSession> = emptyList(),
    val activeSession: TerminalSession? = null,
    val outputLogs: List<String> = emptyList(),
    val problemsCount: Int = 0,
    val commandInput: String = "",
    val isPanelExpanded: Boolean = true,
    val activeRunnerMode: RunnerMode = RunnerMode.EMBEDDED,
    val availableRunnerModes: List<RunnerMode> = RunnerMode.entries,
    val isAlpineInstalled: Boolean = false,
    val alpineInstallationState: AlpineInstallationState = AlpineInstallationState.NotInstalled,
    val isInstallingLanguageServers: Boolean = false,
    val languageServerMessage: String? = null,
    val diagnostics: List<Diagnostic> = emptyList()
)
