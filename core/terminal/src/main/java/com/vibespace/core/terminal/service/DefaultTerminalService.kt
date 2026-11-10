package com.vibespace.core.terminal.service

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.model.TerminalLine
import com.vibespace.core.terminal.model.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DefaultTerminalService(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : TerminalService {

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    override val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSession = MutableStateFlow<TerminalSession?>(null)
    override val activeSession: StateFlow<TerminalSession?> = _activeSession.asStateFlow()

    private val lineFlows = mutableMapOf<String, MutableSharedFlow<TerminalLine>>()

    override suspend fun createSession(
        title: String,
        workingDirectory: String
    ): VibeResult<TerminalSession> = withContext(dispatchers.default) {
        val sessionId = IdGenerator.shortId("term")
        val welcomeLines = listOf(
            TerminalLine(IdGenerator.shortId(), "VibeSpace Terminal v0.1.0", StreamType.SYSTEM),
            TerminalLine(IdGenerator.shortId(), "Type 'help' for available commands. External execution requires Termux bridge.", StreamType.SYSTEM),
            TerminalLine(IdGenerator.shortId(), "$ ", StreamType.SYSTEM)
        )

        val session = TerminalSession(
            sessionId = sessionId,
            title = title,
            workingDirectory = workingDirectory,
            lines = welcomeLines
        )

        val flow = MutableSharedFlow<TerminalLine>(replay = 50)
        lineFlows[sessionId] = flow

        _sessions.value = _sessions.value + session
        _activeSession.value = session
        VibeResult.Success(session)
    }

    override suspend fun closeSession(sessionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val list = _sessions.value
        val index = list.indexOfFirst { it.sessionId == sessionId }
        if (index == -1) {
            return@withContext VibeResult.Error("Session not found: $sessionId")
        }

        lineFlows.remove(sessionId)
        val updated = list.filterNot { it.sessionId == sessionId }
        _sessions.value = updated

        if (_activeSession.value?.sessionId == sessionId) {
            _activeSession.value = updated.lastOrNull()
        }

        VibeResult.Success(Unit)
    }

    override suspend fun setActiveSession(sessionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val session = _sessions.value.firstOrNull { it.sessionId == sessionId }
            ?: return@withContext VibeResult.Error("Session not found: $sessionId")
        _activeSession.value = session
        VibeResult.Success(Unit)
    }

    override suspend fun sendInput(sessionId: String, input: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val session = _sessions.value.firstOrNull { it.sessionId == sessionId }
            ?: return@withContext VibeResult.Error("Session not found: $sessionId")

        val commandLine = TerminalLine(
            id = IdGenerator.shortId(),
            text = "$ $input",
            streamType = StreamType.COMMAND
        )
        emitLine(sessionId, commandLine)

        // Process built-in safe terminal command
        executeInternalCommand(sessionId, session, input.trim())
        VibeResult.Success(Unit)
    }

    override suspend fun appendLine(
        sessionId: String,
        text: String,
        streamType: StreamType
    ): VibeResult<Unit> = withContext(dispatchers.default) {
        val line = TerminalLine(
            id = IdGenerator.shortId(),
            text = text,
            streamType = streamType
        )
        emitLine(sessionId, line)
        VibeResult.Success(Unit)
    }

    override suspend fun clearSession(sessionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val session = _sessions.value.firstOrNull { it.sessionId == sessionId }
            ?: return@withContext VibeResult.Error("Session not found: $sessionId")

        val cleared = session.copy(lines = emptyList())
        updateSession(cleared)
        VibeResult.Success(Unit)
    }

    override fun observeLines(sessionId: String): Flow<TerminalLine> {
        return lineFlows.getOrPut(sessionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }

    private suspend fun executeInternalCommand(sessionId: String, session: TerminalSession, rawCommand: String) {
        if (rawCommand.isEmpty()) return

        val parts = rawCommand.split("\\s+".toRegex())
        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        when (cmd) {
            "help" -> {
                val helpText = """
                    VibeSpace Built-in Safe Console:
                      help             - Show this help message
                      clear            - Clear the terminal screen
                      echo <text>      - Echo arguments to output
                      pwd              - Print current working directory
                      date             - Print current system date and time
                      version          - Display VibeSpace version info
                      whoami           - Print active developer profile
                """.trimIndent()
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), helpText, StreamType.STDOUT))
            }
            "clear" -> {
                clearSession(sessionId)
                return
            }
            "echo" -> {
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), args.joinToString(" "), StreamType.STDOUT))
            }
            "pwd" -> {
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), session.workingDirectory, StreamType.STDOUT))
            }
            "date" -> {
                val formatted = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).format(Date())
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), formatted, StreamType.STDOUT))
            }
            "version" -> {
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "VibeSpace Mobile IDE v0.1.0-alpha", StreamType.STDOUT))
            }
            "whoami" -> {
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "vibespace-developer", StreamType.STDOUT))
            }
            else -> {
                emitLine(
                    sessionId,
                    TerminalLine(
                        id = IdGenerator.shortId(),
                        text = "vibespace: command not found: $cmd. (External shell commands require Termux RUN_COMMAND bridge)",
                        streamType = StreamType.STDERR
                    )
                )
            }
        }
    }

    private suspend fun emitLine(sessionId: String, line: TerminalLine) {
        val flow = lineFlows.getOrPut(sessionId) { MutableSharedFlow(replay = 50) }
        flow.emit(line)

        val currentSession = _sessions.value.firstOrNull { it.sessionId == sessionId } ?: return
        val updatedSession = currentSession.copy(lines = currentSession.lines + line)
        updateSession(updatedSession)
    }

    private fun updateSession(updated: TerminalSession) {
        _sessions.value = _sessions.value.map {
            if (it.sessionId == updated.sessionId) updated else it
        }
        if (_activeSession.value?.sessionId == updated.sessionId) {
            _activeSession.value = updated
        }
    }
}
