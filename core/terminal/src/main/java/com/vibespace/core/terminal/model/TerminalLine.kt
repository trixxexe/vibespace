package com.vibespace.core.terminal.model

enum class StreamType {
    COMMAND,
    STDOUT,
    STDERR,
    SYSTEM
}

data class TerminalLine(
    val id: String,
    val text: String,
    val streamType: StreamType,
    val timestamp: Long = System.currentTimeMillis()
)
