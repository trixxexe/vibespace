package com.vibespace.core.terminal.service

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.terminal.model.TerminalLine
import com.vibespace.core.terminal.model.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Terminal service contract managing pseudo-terminals and command shells safely.
 * Section 6.D: "Do not implement unsafe shell execution."
 */
interface TerminalService {
    val sessions: StateFlow<List<TerminalSession>>
    val activeSession: StateFlow<TerminalSession?>

    suspend fun createSession(title: String = "Terminal", workingDirectory: String = "~"): VibeResult<TerminalSession>
    suspend fun closeSession(sessionId: String): VibeResult<Unit>
    suspend fun setActiveSession(sessionId: String): VibeResult<Unit>
    suspend fun sendInput(sessionId: String, input: String): VibeResult<Unit>
    suspend fun appendLine(sessionId: String, text: String, streamType: com.vibespace.core.terminal.model.StreamType = com.vibespace.core.terminal.model.StreamType.STDOUT): VibeResult<Unit>
    suspend fun clearSession(sessionId: String): VibeResult<Unit>
    fun observeLines(sessionId: String): Flow<TerminalLine>
}
