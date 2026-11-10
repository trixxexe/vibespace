package com.vibespace.core.terminal.model

data class TerminalSession(
    val sessionId: String,
    val title: String,
    val workingDirectory: String,
    val lines: List<TerminalLine> = emptyList(),
    val isAlive: Boolean = true
)
