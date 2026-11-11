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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
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

    private val lineFlows = java.util.concurrent.ConcurrentHashMap<String, MutableSharedFlow<TerminalLine>>()

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

        _sessions.update { it + session }
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
        _sessions.update { it.filterNot { session -> session.sessionId == sessionId } }
        val updated = _sessions.value

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

        val enteredInput = input.trim()
        val cleanInput = if (enteredInput.startsWith("$ ")) enteredInput.removePrefix("$ ").trim() else enteredInput
        val commandLine = TerminalLine(
            id = IdGenerator.shortId(),
            text = "$ $cleanInput",
            streamType = StreamType.COMMAND
        )
        emitLine(sessionId, commandLine)

        // Process built-in safe terminal command
        executeInternalCommand(sessionId, session, cleanInput)
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
                      ls [path]        - List files in current or specified directory
                      cat <file>       - Print file contents to terminal
                      touch <file>     - Create an empty file
                      mkdir <dir>      - Create a new directory
                      rm <path>        - Delete a file or empty directory
                      cp <src> <dest>  - Copy a file
                      mv <src> <dest>  - Move or rename a file
                      head [-n N] <f>  - Display beginning of a file
                      tail [-n N] <f>  - Display end of a file
                      wc <file>        - Count lines, words, and characters
                      which <cmd>      - Locate a shell command
                      uname [-a]       - Print system architecture information
                      env              - Print environment variables
                      date             - Print current system date and time
                      version          - Display VibeSpace version info
                      whoami           - Print active developer profile
                      sh / bash        - Start or explain interactive shell
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
            "ls" -> {
                val targetDir = if (args.isNotEmpty()) resolveFile(session.workingDirectory, args[0]) else File(session.workingDirectory)
                if (targetDir.exists() && targetDir.isDirectory) {
                    val files: List<File> = targetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                    val output = if (files.isEmpty()) "(empty directory)" else files.joinToString("  ") {
                        if (it.isDirectory) "${it.name}/" else it.name
                    }
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), output, StreamType.STDOUT))
                } else if (targetDir.exists() && targetDir.isFile) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), targetDir.name, StreamType.STDOUT))
                } else {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "ls: cannot access '${targetDir.path}': No such file or directory", StreamType.STDERR))
                }
            }
            "cat" -> {
                if (args.isEmpty()) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cat: missing file operand", StreamType.STDERR))
                } else {
                    for (arg in args) {
                        val file = resolveFile(session.workingDirectory, arg)
                        if (file.exists() && file.isFile) {
                            try {
                                val content = file.readText()
                                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), content, StreamType.STDOUT))
                            } catch (e: Exception) {
                                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cat: ${file.name}: ${e.message}", StreamType.STDERR))
                            }
                        } else {
                            emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cat: ${arg}: No such file or directory", StreamType.STDERR))
                        }
                    }
                }
            }
            "touch" -> {
                if (args.isEmpty()) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "touch: missing file operand", StreamType.STDERR))
                } else {
                    for (arg in args) {
                        val file = resolveFile(session.workingDirectory, arg)
                        try {
                            file.parentFile?.mkdirs()
                            if (!file.exists()) file.createNewFile()
                            else file.setLastModified(System.currentTimeMillis())
                        } catch (e: Exception) {
                            emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "touch: cannot touch '${arg}': ${e.message}", StreamType.STDERR))
                        }
                    }
                }
            }
            "mkdir" -> {
                if (args.isEmpty()) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "mkdir: missing operand", StreamType.STDERR))
                } else {
                    for (arg in args) {
                        val dir = resolveFile(session.workingDirectory, arg)
                        if (!dir.mkdirs() && !dir.isDirectory) {
                            emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "mkdir: cannot create directory '${arg}'", StreamType.STDERR))
                        }
                    }
                }
            }
            "rm" -> {
                if (args.isEmpty()) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "rm: missing operand", StreamType.STDERR))
                } else {
                    val recursive = args.contains("-r") || args.contains("-rf") || args.contains("-R")
                    val targets = args.filterNot { it.startsWith("-") }
                    for (target in targets) {
                        val file = resolveFile(session.workingDirectory, target)
                        if (file.exists()) {
                            val deleted = if (recursive) file.deleteRecursively() else file.delete()
                            if (!deleted) emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "rm: cannot remove '${target}'", StreamType.STDERR))
                        } else {
                            emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "rm: cannot remove '${target}': No such file or directory", StreamType.STDERR))
                        }
                    }
                }
            }
            "cp" -> {
                if (args.size < 2) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cp: missing destination file operand after '${args.firstOrNull().orEmpty()}'", StreamType.STDERR))
                } else {
                    val src = resolveFile(session.workingDirectory, args[0])
                    val dest = resolveFile(session.workingDirectory, args[1])
                    if (src.exists()) {
                        try {
                            val targetDest = if (dest.isDirectory) File(dest, src.name) else dest
                            src.copyTo(targetDest, overwrite = true)
                        } catch (e: Exception) {
                            emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cp: cannot copy '${args[0]}' to '${args[1]}': ${e.message}", StreamType.STDERR))
                        }
                    } else {
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cp: cannot stat '${args[0]}': No such file or directory", StreamType.STDERR))
                    }
                }
            }
            "mv" -> {
                if (args.size < 2) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "mv: missing destination file operand after '${args.firstOrNull().orEmpty()}'", StreamType.STDERR))
                } else {
                    val src = resolveFile(session.workingDirectory, args[0])
                    val dest = resolveFile(session.workingDirectory, args[1])
                    if (src.exists()) {
                        val targetDest = if (dest.isDirectory) File(dest, src.name) else dest
                        if (!src.renameTo(targetDest)) {
                            try {
                                src.copyTo(targetDest, overwrite = true)
                                src.delete()
                            } catch (e: Exception) {
                                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "mv: cannot move '${args[0]}' to '${args[1]}': ${e.message}", StreamType.STDERR))
                            }
                        }
                    } else {
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "mv: cannot stat '${args[0]}': No such file or directory", StreamType.STDERR))
                    }
                }
            }
            "head" -> {
                val fileArg = args.lastOrNull { !it.startsWith("-") }
                if (fileArg == null) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "head: missing file operand", StreamType.STDERR))
                } else {
                    val file = resolveFile(session.workingDirectory, fileArg)
                    if (file.exists() && file.isFile) {
                        val lines = file.readLines().take(10).joinToString("\n")
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), lines, StreamType.STDOUT))
                    } else {
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "head: cannot open '${fileArg}': No such file or directory", StreamType.STDERR))
                    }
                }
            }
            "tail" -> {
                val fileArg = args.lastOrNull { !it.startsWith("-") }
                if (fileArg == null) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "tail: missing file operand", StreamType.STDERR))
                } else {
                    val file = resolveFile(session.workingDirectory, fileArg)
                    if (file.exists() && file.isFile) {
                        val lines = file.readLines().takeLast(10).joinToString("\n")
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), lines, StreamType.STDOUT))
                    } else {
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "tail: cannot open '${fileArg}': No such file or directory", StreamType.STDERR))
                    }
                }
            }
            "wc" -> {
                if (args.isEmpty()) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "wc: missing file operand", StreamType.STDERR))
                } else {
                    val file = resolveFile(session.workingDirectory, args[0])
                    if (file.exists() && file.isFile) {
                        val text = file.readText()
                        val lines = text.lines().size
                        val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
                        val bytes = text.toByteArray().size
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), " $lines  $words  $bytes ${file.name}", StreamType.STDOUT))
                    } else {
                        emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "wc: ${args[0]}: No such file or directory", StreamType.STDERR))
                    }
                }
            }
            "which" -> {
                val builtins = listOf("help", "clear", "echo", "pwd", "ls", "cat", "touch", "mkdir", "rm", "cp", "mv", "head", "tail", "wc", "which", "uname", "env", "date", "version", "whoami", "sh", "bash")
                val query = args.firstOrNull().orEmpty()
                if (query in builtins) {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "/bin/$query (vibespace built-in)", StreamType.STDOUT))
                } else {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "which: no $query in (/bin:/usr/bin)", StreamType.STDERR))
                }
            }
            "uname" -> {
                val osArch = System.getProperty("os.arch", "unknown")
                val osName = System.getProperty("os.name", "Linux")
                val osVersion = System.getProperty("os.version", "android")
                val output = if (args.contains("-a")) "$osName vibespace-android $osVersion #1 PREEMPT $osArch GNU/Linux" else osName
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), output, StreamType.STDOUT))
            }
            "env" -> {
                val envStr = buildString {
                    appendLine("USER=vibespace-developer")
                    appendLine("HOME=${session.workingDirectory}")
                    appendLine("SHELL=/bin/bash")
                    appendLine("TERM=xterm-256color")
                    appendLine("VIBESPACE_VERSION=0.1.0")
                    appendLine("PATH=/bin:/usr/bin:/usr/local/bin")
                }.trimEnd()
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), envStr, StreamType.STDOUT))
            }
            "cd" -> {
                val targetPath = args.firstOrNull() ?: "~"
                val newDir = resolveFile(session.workingDirectory, targetPath)
                if (newDir.exists() && newDir.isDirectory) {
                    val updatedSession = session.copy(workingDirectory = newDir.canonicalPath)
                    updateSession(updatedSession)
                } else {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "cd: no such file or directory: $targetPath", StreamType.STDERR))
                }
            }
            "sh", "bash" -> {
                if (args.size >= 2 && args[0] == "-c") {
                    val subCmd = args.drop(1).joinToString(" ").trim('"', '\'')
                    executeInternalCommand(sessionId, session, subCmd)
                } else {
                    emitLine(sessionId, TerminalLine(IdGenerator.shortId(), "bash-5.2$ (VibeSpace Built-in Shell - type commands or 'help'; for apk/apt install use Alpine/Termux runner)", StreamType.SYSTEM))
                }
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
        flow.tryEmit(line)

        var updatedSession: TerminalSession? = null
        _sessions.update { currentSessions ->
            currentSessions.map { session ->
                if (session.sessionId == sessionId) {
                    session.copy(lines = (session.lines + line).takeLast(MAX_LINES_PER_SESSION)).also {
                        if (_activeSession.value?.sessionId == sessionId) updatedSession = it
                    }
                } else session
            }
        }
        updatedSession?.let { _activeSession.value = it }
    }

    private fun updateSession(updated: TerminalSession) {
        _sessions.update { sessions -> sessions.map {
            if (it.sessionId == updated.sessionId) updated else it
        } }
        if (_activeSession.value?.sessionId == updated.sessionId) {
            _activeSession.value = updated
        }
    }

    private fun resolveFile(workingDir: String, path: String): File {
        return if (path.startsWith("/")) File(path)
        else if (path.startsWith("~")) File(System.getProperty("user.home", "/"), path.removePrefix("~").removePrefix("/"))
        else File(workingDir, path)
    }

    private companion object {
        const val MAX_LINES_PER_SESSION = 2000
    }
}
