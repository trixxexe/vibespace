package com.vibespace.core.terminal.service

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.terminal.model.StreamType
import com.vibespace.core.terminal.model.TerminalLine
import com.vibespace.core.terminal.model.TerminalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DefaultTerminalService(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val defaultHomeDirectory: File? = null
) : TerminalService {

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    override val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSession = MutableStateFlow<TerminalSession?>(null)
    override val activeSession: StateFlow<TerminalSession?> = _activeSession.asStateFlow()

    private val lineFlows = ConcurrentHashMap<String, MutableSharedFlow<TerminalLine>>()
    private val sessionEnv = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val sessionAliases = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val sessionHistory = ConcurrentHashMap<String, MutableList<String>>()
    private val serviceStartMillis = System.currentTimeMillis()

    override suspend fun createSession(
        title: String,
        workingDirectory: String
    ): VibeResult<TerminalSession> = withContext(dispatchers.default) {
        val sessionId = IdGenerator.shortId("term")
        val welcomeLines = listOf(
            TerminalLine(IdGenerator.shortId(), "VibeSpace Terminal v0.1.0", StreamType.SYSTEM),
            TerminalLine(IdGenerator.shortId(), "Type 'help' for available Linux commands.", StreamType.SYSTEM)
        )

        val session = TerminalSession(
            sessionId = sessionId,
            title = title,
            workingDirectory = workingDirectory,
            lines = welcomeLines
        )

        sessionEnv[sessionId] = linkedMapOf(
            "USER" to "vibespace-developer",
            "HOME" to workingDirectory,
            "PWD" to workingDirectory,
            "SHELL" to "/bin/bash",
            "TERM" to "xterm-256color",
            "VIBESPACE_VERSION" to "0.1.0",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
        sessionAliases[sessionId] = linkedMapOf(
            "ll" to "ls -la",
            "la" to "ls -a"
        )
        sessionHistory[sessionId] = mutableListOf()

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
        sessionEnv.remove(sessionId)
        sessionAliases.remove(sessionId)
        sessionHistory.remove(sessionId)
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

    override suspend fun setWorkingDirectory(
        sessionId: String,
        workingDirectory: String
    ): VibeResult<Unit> = withContext(dispatchers.default) {
        val session = _sessions.value.firstOrNull { it.sessionId == sessionId }
            ?: return@withContext VibeResult.Error("Session not found: $sessionId")
        val targetDir = resolveFile(session.workingDirectory, workingDirectory.trim().removeSurrounding("\"").removeSurrounding("'"))
        val resolvedPath = if (targetDir.exists() && targetDir.isDirectory) {
            runCatching { targetDir.canonicalPath }.getOrDefault(targetDir.absolutePath)
        } else {
            workingDirectory.trim().removeSurrounding("\"").removeSurrounding("'")
        }
        val updated = session.copy(workingDirectory = resolvedPath)
        sessionEnv.getOrPut(sessionId) { linkedMapOf() }["PWD"] = resolvedPath
        updateSession(updated)
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

        if (cleanInput.isNotBlank()) {
            sessionHistory.getOrPut(sessionId) { mutableListOf() }.add(cleanInput)
        }

        executeCommandChain(sessionId, cleanInput)
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

    private suspend fun executeCommandChain(sessionId: String, rawCommandLine: String) {
        if (rawCommandLine.isBlank()) return
        val segments = splitCommandChain(rawCommandLine)
        for ((segment, requirePrevSuccess) in segments) {
            val currentSession = _sessions.value.firstOrNull { it.sessionId == sessionId } ?: return
            val ok = executeSingleCommandWithRedirection(sessionId, currentSession, segment)
            if (!ok && requirePrevSuccess) break
        }
    }

    private fun splitCommandChain(raw: String): List<Pair<String, Boolean>> {
        val result = mutableListOf<Pair<String, Boolean>>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escaped = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (escaped) {
                current.append(c)
                escaped = false
                i++
                continue
            }
            when (c) {
                '\\' -> {
                    escaped = true
                    current.append(c)
                }
                '\'' -> {
                    if (!inDouble) inSingle = !inSingle
                    current.append(c)
                }
                '"' -> {
                    if (!inSingle) inDouble = !inDouble
                    current.append(c)
                }
                ';' -> {
                    if (!inSingle && !inDouble) {
                        val cmd = current.toString().trim()
                        if (cmd.isNotEmpty()) result.add(cmd to false)
                        current.clear()
                    } else {
                        current.append(c)
                    }
                }
                '&' -> {
                    if (!inSingle && !inDouble && i + 1 < raw.length && raw[i + 1] == '&') {
                        val cmd = current.toString().trim()
                        if (cmd.isNotEmpty()) result.add(cmd to true)
                        current.clear()
                        i++
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        val rem = current.toString().trim()
        if (rem.isNotEmpty()) {
            result.add(rem to false)
        }
        return result
    }

    private fun tokenizeCommand(raw: String, env: Map<String, String>): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escaped = false
        var tokenStarted = false

        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (escaped) {
                current.append(c)
                escaped = false
                tokenStarted = true
                i++
                continue
            }
            when {
                c == '\\' && !inSingle -> {
                    escaped = true
                    tokenStarted = true
                }
                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    tokenStarted = true
                }
                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    tokenStarted = true
                }
                c == '$' && !inSingle && i + 1 < raw.length && (raw[i + 1].isLetterOrDigit() || raw[i + 1] == '_' || raw[i + 1] == '{') -> {
                    tokenStarted = true
                    if (raw[i + 1] == '{') {
                        val endBrace = raw.indexOf('}', i + 2)
                        if (endBrace != -1) {
                            val key = raw.substring(i + 2, endBrace)
                            current.append(env[key].orEmpty())
                            i = endBrace
                        } else {
                            current.append(c)
                        }
                    } else {
                        var j = i + 1
                        while (j < raw.length && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                        val key = raw.substring(i + 1, j)
                        current.append(env[key].orEmpty())
                        i = j - 1
                    }
                }
                c.isWhitespace() && !inSingle && !inDouble -> {
                    if (tokenStarted) {
                        tokens.add(current.toString())
                        current.clear()
                        tokenStarted = false
                    }
                }
                else -> {
                    current.append(c)
                    tokenStarted = true
                }
            }
            i++
        }
        if (tokenStarted) {
            tokens.add(current.toString())
        }
        return tokens
    }

    private suspend fun executeSingleCommandWithRedirection(
        sessionId: String,
        session: TerminalSession,
        rawSegment: String
    ): Boolean {
        val env = sessionEnv.getOrPut(sessionId) {
            linkedMapOf(
                "USER" to "vibespace-developer",
                "HOME" to session.workingDirectory,
                "PWD" to session.workingDirectory,
                "SHELL" to "/bin/bash",
                "TERM" to "xterm-256color",
                "VIBESPACE_VERSION" to "0.1.0",
                "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            )
        }
        val aliases = sessionAliases.getOrPut(sessionId) { linkedMapOf() }

        // Check for redirection outside quotes (> or >>)
        var redirectTarget: String? = null
        var appendRedirect = false
        var commandPart = rawSegment
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < rawSegment.length) {
            val c = rawSegment[i]
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (c == '>' && !inSingle && !inDouble) {
                if (i + 1 < rawSegment.length && rawSegment[i + 1] == '>') {
                    appendRedirect = true
                    commandPart = rawSegment.substring(0, i).trim()
                    redirectTarget = rawSegment.substring(i + 2).trim().removeSurrounding("\"").removeSurrounding("'")
                } else {
                    appendRedirect = false
                    commandPart = rawSegment.substring(0, i).trim()
                    redirectTarget = rawSegment.substring(i + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                }
                break
            }
            i++
        }

        val initialTokens = tokenizeCommand(commandPart, env)
        if (initialTokens.isEmpty()) return true

        // Expand alias if first token matches
        val firstToken = initialTokens[0]
        val tokens = if (aliases.containsKey(firstToken)) {
            val expanded = tokenizeCommand(aliases.getValue(firstToken), env)
            expanded + initialTokens.drop(1)
        } else {
            initialTokens
        }
        if (tokens.isEmpty()) return true

        val capturedStdout = mutableListOf<String>()
        var hadError = false

        suspend fun out(text: String, type: StreamType = StreamType.STDOUT) {
            if (type == StreamType.STDERR) hadError = true
            if (redirectTarget != null && type == StreamType.STDOUT) {
                capturedStdout.add(text)
            } else {
                emitLine(sessionId, TerminalLine(IdGenerator.shortId(), text, type))
            }
        }

        executeTokens(sessionId, session, tokens, env, aliases, ::out)

        if (redirectTarget != null) {
            val currentSession = _sessions.value.firstOrNull { it.sessionId == sessionId } ?: session
            val targetFile = resolveFile(currentSession.workingDirectory, redirectTarget)
            try {
                targetFile.parentFile?.mkdirs()
                val textToWrite = if (capturedStdout.isEmpty()) "" else capturedStdout.joinToString("\n") + "\n"
                if (appendRedirect && targetFile.exists()) {
                    targetFile.appendText(textToWrite)
                } else {
                    targetFile.writeText(textToWrite)
                }
            } catch (e: Exception) {
                hadError = true
                emitLine(
                    sessionId,
                    TerminalLine(IdGenerator.shortId(), "redirect: cannot write to '$redirectTarget': ${e.message}", StreamType.STDERR)
                )
            }
        }

        return !hadError
    }

    private suspend fun executeTokens(
        sessionId: String,
        session: TerminalSession,
        tokens: List<String>,
        env: MutableMap<String, String>,
        aliases: MutableMap<String, String>,
        out: suspend (String, StreamType) -> Unit
    ) {
        // Inline VAR=VALUE assignment
        if (tokens.size == 1 && tokens[0].contains('=') && !tokens[0].startsWith('=')) {
            val key = tokens[0].substringBefore('=')
            val value = tokens[0].substringAfter('=')
            if (key.all { it.isLetterOrDigit() || it == '_' }) {
                env[key] = value
                return
            }
        }

        val cmd = tokens[0].lowercase()
        val args = tokens.drop(1)

        when (cmd) {
            "help" -> {
                val helpText = """
                    VibeSpace Linux Shell Commands:
                      File & Directory:
                        ls [-la] [path], cd [dir], pwd, mkdir [-p] <dir>, rmdir <dir>
                        rm [-rf] <path>, cp [-r] <src> <dst>, mv <src> <dst>, ln [-s] <src> <dst>
                        touch <file>, cat [-n] <file>, chmod <mode> <file>, stat <file>, file <path>
                        realpath <path>, readlink <path>, basename <path>, dirname <path>, tree [dir]
                      Text & Search:
                        echo [-n|-e] <text>, printf <fmt> [args], head [-n N] <f>, tail [-n N] <f>
                        wc [-l|-w|-c] <f>, grep [-i|-n|-v|-r] <pat> <path>, find [dir] [-name pat]
                        sort [-r|-n|-u] <f>, uniq [-c] <f>, cut -d <delim> -f <fields> <f>
                        tr <set1> <set2> [f], sed 's/a/b/g' <f>, awk <prog> <f>, diff <f1> <f2>
                        nl <f>, rev <f>, tac <f>, less <f>, more <f>
                      Archive & Checksum:
                        tar, gzip <f>, gunzip <f>, zip <out.zip> <files>, unzip <in.zip>
                        md5sum <f>, sha256sum <f>, base64 [-d] <f>
                      System & Environment:
                        uname [-a], hostname, whoami, id, uptime, date, cal, df, free, du, ps, top, kill
                        env, printenv [var], export KEY=VAL, unset KEY, alias [k=v], unalias <k>
                        which <cmd>, type <cmd>, history, seq <n>, expr <a> <op> <b>, sleep <sec>
                        clear, reset, true, false, test, version
                """.trimIndent()
                out(helpText, StreamType.STDOUT)
            }
            "clear", "reset" -> {
                clearSession(sessionId)
            }
            "echo" -> {
                var interpretEscapes = false
                var startIdx = 0
                while (startIdx < args.size && (args[startIdx] == "-n" || args[startIdx] == "-e" || args[startIdx] == "-ne" || args[startIdx] == "-en")) {
                    if (args[startIdx].contains('e')) interpretEscapes = true
                    startIdx++
                }
                val rawOut = args.drop(startIdx).joinToString(" ")
                val finalOut = if (interpretEscapes) {
                    rawOut.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
                } else {
                    rawOut
                }
                out(finalOut, StreamType.STDOUT)
            }
            "printf" -> {
                if (args.isEmpty()) {
                    out("", StreamType.STDOUT)
                } else {
                    val fmt = args[0].replace("\\n", "\n").replace("\\t", "\t")
                    val fmtArgs = args.drop(1).toTypedArray()
                    val formatted = runCatching { String.format(Locale.US, fmt, *fmtArgs) }.getOrDefault(
                        (listOf(fmt) + args.drop(1)).joinToString(" ")
                    )
                    out(formatted.trimEnd('\n'), StreamType.STDOUT)
                }
            }
            "pwd" -> {
                out(session.workingDirectory, StreamType.STDOUT)
            }
            "cd" -> {
                val rawTarget = args.firstOrNull() ?: "~"
                val targetPath = if (rawTarget == "-") env["OLDPWD"] ?: session.workingDirectory else rawTarget
                val newDir = resolveFile(session.workingDirectory, targetPath)
                if (newDir.exists() && newDir.isDirectory) {
                    val canonical = runCatching { newDir.canonicalPath }.getOrDefault(newDir.absolutePath)
                    env["OLDPWD"] = session.workingDirectory
                    env["PWD"] = canonical
                    val updatedSession = session.copy(workingDirectory = canonical)
                    updateSession(updatedSession)
                } else {
                    out("cd: no such file or directory: $rawTarget", StreamType.STDERR)
                }
            }
            "ls" -> {
                val flags = args.filter { it.startsWith("-") && it.length > 1 }.joinToString("")
                val showHidden = flags.contains('a') || flags.contains('A')
                val longFormat = flags.contains('l')
                val humanReadable = flags.contains('h')
                val paths = args.filterNot { it.startsWith("-") }.ifEmpty { listOf(".") }

                for (pathArg in paths) {
                    val targetDir = resolveFile(session.workingDirectory, pathArg)
                    if (targetDir.exists() && targetDir.isDirectory) {
                        val files = targetDir.listFiles()
                            ?.filter { showHidden || !it.name.startsWith(".") }
                            ?.sortedBy { it.name }
                            ?: emptyList()
                        if (files.isEmpty()) {
                            out("(empty directory)", StreamType.STDOUT)
                        } else if (longFormat) {
                            val df = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                            val lines = files.joinToString("\n") { f ->
                                val perms = buildString {
                                    append(if (f.isDirectory) 'd' else '-')
                                    append(if (f.canRead()) 'r' else '-')
                                    append(if (f.canWrite()) 'w' else '-')
                                    append(if (f.canExecute()) 'x' else '-')
                                    append("r-xr-x")
                                }
                                val sizeStr = if (humanReadable) formatBytes(f.length()) else f.length().toString().padStart(8)
                                val mtime = df.format(Date(f.lastModified()))
                                val suffix = if (f.isDirectory) "/" else ""
                                "$perms 1 root root $sizeStr $mtime ${f.name}$suffix"
                            }
                            out(lines, StreamType.STDOUT)
                        } else {
                            val output = files.joinToString("  ") {
                                if (it.isDirectory) "${it.name}/" else it.name
                            }
                            out(output, StreamType.STDOUT)
                        }
                    } else if (targetDir.exists() && targetDir.isFile) {
                        out(targetDir.name, StreamType.STDOUT)
                    } else {
                        out("ls: cannot access '${targetDir.path}': No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "cat" -> {
                val numberLines = args.contains("-n")
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("cat: missing file operand", StreamType.STDERR)
                } else {
                    for (arg in fileArgs) {
                        val file = resolveFile(session.workingDirectory, arg)
                        if (file.exists() && file.isFile) {
                            try {
                                val content = file.readText().trimEnd('\n', '\r')
                                val rendered = if (numberLines) {
                                    content.lines().mapIndexed { i, line -> "${(i + 1).toString().padStart(6)}\t$line" }.joinToString("\n")
                                } else {
                                    content
                                }
                                out(rendered, StreamType.STDOUT)
                            } catch (e: Exception) {
                                out("cat: ${file.name}: ${e.message}", StreamType.STDERR)
                            }
                        } else {
                            out("cat: $arg: No such file or directory", StreamType.STDERR)
                        }
                    }
                }
            }
            "less", "more" -> {
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("$cmd: missing file operand", StreamType.STDERR)
                } else {
                    for (arg in fileArgs) {
                        val file = resolveFile(session.workingDirectory, arg)
                        if (file.exists() && file.isFile) {
                            out(file.readText(), StreamType.STDOUT)
                        } else {
                            out("$cmd: $arg: No such file or directory", StreamType.STDERR)
                        }
                    }
                }
            }
            "nl" -> {
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("nl: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        val numbered = file.readLines().mapIndexed { idx, line ->
                            "${(idx + 1).toString().padStart(6)}\t$line"
                        }.joinToString("\n")
                        out(numbered, StreamType.STDOUT)
                    } else {
                        out("nl: ${fileArgs[0]}: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "rev" -> {
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("rev: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        out(file.readLines().joinToString("\n") { it.reversed() }, StreamType.STDOUT)
                    } else {
                        out(fileArgs.joinToString(" ").reversed(), StreamType.STDOUT)
                    }
                }
            }
            "tac" -> {
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("tac: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        out(file.readLines().asReversed().joinToString("\n"), StreamType.STDOUT)
                    } else {
                        out("tac: ${fileArgs[0]}: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "touch" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    out("touch: missing file operand", StreamType.STDERR)
                } else {
                    for (arg in targets) {
                        val file = resolveFile(session.workingDirectory, arg)
                        try {
                            file.parentFile?.mkdirs()
                            if (!file.exists()) file.createNewFile()
                            else file.setLastModified(System.currentTimeMillis())
                        } catch (e: Exception) {
                            out("touch: cannot touch '$arg': ${e.message}", StreamType.STDERR)
                        }
                    }
                }
            }
            "mkdir" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    out("mkdir: missing operand", StreamType.STDERR)
                } else {
                    for (arg in targets) {
                        val dir = resolveFile(session.workingDirectory, arg)
                        if (!dir.mkdirs() && !dir.isDirectory) {
                            out("mkdir: cannot create directory '$arg'", StreamType.STDERR)
                        }
                    }
                }
            }
            "rmdir" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    out("rmdir: missing operand", StreamType.STDERR)
                } else {
                    for (arg in targets) {
                        val dir = resolveFile(session.workingDirectory, arg)
                        if (!dir.exists()) {
                            out("rmdir: failed to remove '$arg': No such file or directory", StreamType.STDERR)
                        } else if (!dir.isDirectory) {
                            out("rmdir: failed to remove '$arg': Not a directory", StreamType.STDERR)
                        } else if (dir.listFiles()?.isNotEmpty() == true) {
                            out("rmdir: failed to remove '$arg': Directory not empty", StreamType.STDERR)
                        } else if (!dir.delete()) {
                            out("rmdir: failed to remove '$arg'", StreamType.STDERR)
                        }
                    }
                }
            }
            "rm" -> {
                val flags = args.filter { it.startsWith("-") }.joinToString("")
                val recursive = flags.contains('r') || flags.contains('R')
                val force = flags.contains('f')
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    if (!force) out("rm: missing operand", StreamType.STDERR)
                } else {
                    for (target in targets) {
                        val file = resolveFile(session.workingDirectory, target)
                        if (file.exists()) {
                            val deleted = if (recursive) file.deleteRecursively() else file.delete()
                            if (!deleted && !force) out("rm: cannot remove '$target'", StreamType.STDERR)
                        } else if (!force) {
                            out("rm: cannot remove '$target': No such file or directory", StreamType.STDERR)
                        }
                    }
                }
            }
            "cp" -> {
                val flags = args.filter { it.startsWith("-") }.joinToString("")
                val recursive = flags.contains('r') || flags.contains('R') || flags.contains('a')
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.size < 2) {
                    out("cp: missing destination file operand after '${targets.firstOrNull().orEmpty()}'", StreamType.STDERR)
                } else {
                    val src = resolveFile(session.workingDirectory, targets[0])
                    val dest = resolveFile(session.workingDirectory, targets[1])
                    if (src.exists()) {
                        try {
                            val targetDest = if (dest.isDirectory) File(dest, src.name) else dest
                            if (src.isDirectory) {
                                if (recursive) {
                                    src.copyRecursively(targetDest, overwrite = true)
                                } else {
                                    out("cp: -r not specified; omitting directory '${targets[0]}'", StreamType.STDERR)
                                }
                            } else {
                                targetDest.parentFile?.mkdirs()
                                src.copyTo(targetDest, overwrite = true)
                            }
                        } catch (e: Exception) {
                            out("cp: cannot copy '${targets[0]}' to '${targets[1]}': ${e.message}", StreamType.STDERR)
                        }
                    } else {
                        out("cp: cannot stat '${targets[0]}': No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "mv" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.size < 2) {
                    out("mv: missing destination file operand after '${targets.firstOrNull().orEmpty()}'", StreamType.STDERR)
                } else {
                    val src = resolveFile(session.workingDirectory, targets[0])
                    val dest = resolveFile(session.workingDirectory, targets[1])
                    if (src.exists()) {
                        val targetDest = if (dest.isDirectory) File(dest, src.name) else dest
                        targetDest.parentFile?.mkdirs()
                        if (!src.renameTo(targetDest)) {
                            try {
                                if (src.isDirectory) src.copyRecursively(targetDest, overwrite = true)
                                else src.copyTo(targetDest, overwrite = true)
                                src.deleteRecursively()
                            } catch (e: Exception) {
                                out("mv: cannot move '${targets[0]}' to '${targets[1]}': ${e.message}", StreamType.STDERR)
                            }
                        }
                    } else {
                        out("mv: cannot stat '${targets[0]}': No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "ln" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.size < 2) {
                    out("ln: missing file operand", StreamType.STDERR)
                } else {
                    val src = resolveFile(session.workingDirectory, targets[0])
                    val dst = resolveFile(session.workingDirectory, targets[1])
                    try {
                        dst.parentFile?.mkdirs()
                        runCatching {
                            Files.createSymbolicLink(dst.toPath(), src.toPath())
                        }.recoverCatching {
                            if (src.isFile) src.copyTo(dst, overwrite = true)
                            else throw it
                        }.getOrThrow()
                    } catch (e: Exception) {
                        out("ln: failed to create link '${targets[1]}': ${e.message}", StreamType.STDERR)
                    }
                }
            }
            "chmod" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.size < 2) {
                    out("chmod: missing operand", StreamType.STDERR)
                } else {
                    val mode = targets[0]
                    for (t in targets.drop(1)) {
                        val f = resolveFile(session.workingDirectory, t)
                        if (!f.exists()) {
                            out("chmod: cannot access '$t': No such file or directory", StreamType.STDERR)
                        } else {
                            if (mode.contains("+x") || mode.endsWith("7") || mode.endsWith("5") || mode.endsWith("1")) {
                                f.setExecutable(true, false)
                            } else if (mode.contains("-x")) {
                                f.setExecutable(false, false)
                            }
                            if (mode.contains("+w") || mode.startsWith("7") || mode.startsWith("6")) {
                                f.setWritable(true, false)
                            }
                            f.setReadable(true, false)
                        }
                    }
                }
            }
            "stat" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    out("stat: missing operand", StreamType.STDERR)
                } else {
                    for (t in targets) {
                        val f = resolveFile(session.workingDirectory, t)
                        if (f.exists()) {
                            val type = if (f.isDirectory) "directory" else "regular file"
                            val info = """
                                  File: ${f.name}
                                  Size: ${f.length()}	Blocks: ${(f.length() + 511) / 512}	$type
                                Access: (${if (f.isDirectory) "0755/drwxr-xr-x" else "0644/-rw-r--r--"})  Uid: (    0/    root)   Gid: (    0/    root)
                                Modify: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).format(Date(f.lastModified()))}
                            """.trimIndent()
                            out(info, StreamType.STDOUT)
                        } else {
                            out("stat: cannot statx '$t': No such file or directory", StreamType.STDERR)
                        }
                    }
                }
            }
            "file" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    out("file: missing file operand", StreamType.STDERR)
                } else {
                    for (t in targets) {
                        val f = resolveFile(session.workingDirectory, t)
                        val desc = when {
                            !f.exists() -> "cannot open `$t' (No such file or directory)"
                            f.isDirectory -> "directory"
                            f.extension.equals("py", true) -> "Python script, UTF-8 Unicode text executable"
                            f.extension.equals("js", true) -> "JavaScript source, UTF-8 Unicode text"
                            f.extension.equals("ts", true) -> "TypeScript source, UTF-8 Unicode text"
                            f.extension.equals("sh", true) -> "POSIX shell script, ASCII text executable"
                            f.extension.equals("json", true) -> "JSON text data"
                            f.extension.equals("md", true) -> "UTF-8 Unicode text"
                            f.length() == 0L -> "empty"
                            else -> "UTF-8 Unicode text"
                        }
                        out("$t: $desc", StreamType.STDOUT)
                    }
                }
            }
            "realpath", "readlink" -> {
                val targets = args.filterNot { it.startsWith("-") }
                if (targets.isEmpty()) {
                    out("$cmd: missing operand", StreamType.STDERR)
                } else {
                    for (t in targets) {
                        val f = resolveFile(session.workingDirectory, t)
                        out(runCatching { f.canonicalPath }.getOrDefault(f.absolutePath), StreamType.STDOUT)
                    }
                }
            }
            "basename" -> {
                if (args.isEmpty()) {
                    out("basename: missing operand", StreamType.STDERR)
                } else {
                    val name = File(args[0]).name
                    val suffix = args.getOrNull(1)
                    out(if (suffix != null && name.endsWith(suffix)) name.removeSuffix(suffix) else name, StreamType.STDOUT)
                }
            }
            "dirname" -> {
                if (args.isEmpty()) {
                    out("dirname: missing operand", StreamType.STDERR)
                } else {
                    val parent = File(args[0]).parent ?: "."
                    out(parent, StreamType.STDOUT)
                }
            }
            "tree" -> {
                val targetArg = args.firstOrNull { !it.startsWith("-") } ?: "."
                val root = resolveFile(session.workingDirectory, targetArg)
                if (!root.exists()) {
                    out("tree: '$targetArg': No such file or directory", StreamType.STDERR)
                } else {
                    val sb = StringBuilder(targetArg).append('\n')
                    var dirCount = 0
                    var fileCount = 0
                    fun walkTree(dir: File, prefix: String, depth: Int) {
                        if (depth > 5) return
                        val children = dir.listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name }.orEmpty()
                        children.forEachIndexed { index, child ->
                            val isLast = index == children.lastIndex
                            val branch = if (isLast) "└── " else "├── "
                            sb.append(prefix).append(branch).append(child.name)
                            if (child.isDirectory) {
                                dirCount++
                                sb.append("/\n")
                                walkTree(child, prefix + if (isLast) "    " else "│   ", depth + 1)
                            } else {
                                fileCount++
                                sb.append('\n')
                            }
                        }
                    }
                    if (root.isDirectory) walkTree(root, "", 1)
                    sb.append("\n$dirCount directories, $fileCount files")
                    out(sb.toString(), StreamType.STDOUT)
                }
            }
            "du" -> {
                val human = args.any { it.contains('h') }
                val targetArg = args.firstOrNull { !it.startsWith("-") } ?: "."
                val target = resolveFile(session.workingDirectory, targetArg)
                if (!target.exists()) {
                    out("du: cannot access '$targetArg': No such file or directory", StreamType.STDERR)
                } else {
                    val bytes = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    val sizeStr = if (human) formatBytes(bytes) else ((bytes + 1023) / 1024).toString()
                    out("$sizeStr\t$targetArg", StreamType.STDOUT)
                }
            }
            "head" -> {
                val count = parseLineCountFlag(args, defaultCount = 10)
                val fileArg = args.lastOrNull { !it.startsWith("-") && it.toIntOrNull() == null }
                if (fileArg == null) {
                    out("head: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArg)
                    if (file.exists() && file.isFile) {
                        val lines = file.readLines().take(count).joinToString("\n")
                        out(lines, StreamType.STDOUT)
                    } else {
                        out("head: cannot open '$fileArg': No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "tail" -> {
                val count = parseLineCountFlag(args, defaultCount = 10)
                val fileArg = args.lastOrNull { !it.startsWith("-") && it.toIntOrNull() == null }
                if (fileArg == null) {
                    out("tail: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArg)
                    if (file.exists() && file.isFile) {
                        val lines = file.readLines().takeLast(count).joinToString("\n")
                        out(lines, StreamType.STDOUT)
                    } else {
                        out("tail: cannot open '$fileArg': No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "wc" -> {
                val flags = args.filter { it.startsWith("-") }.joinToString("")
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("wc: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        val text = file.readText()
                        val lines = if (text.isEmpty()) 0 else text.lines().size
                        val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
                        val bytes = text.toByteArray().size
                        val rendered = when {
                            flags.contains('l') && !flags.contains('w') && !flags.contains('c') -> "$lines ${file.name}"
                            flags.contains('w') && !flags.contains('l') && !flags.contains('c') -> "$words ${file.name}"
                            flags.contains('c') && !flags.contains('l') && !flags.contains('w') -> "$bytes ${file.name}"
                            else -> " $lines  $words  $bytes ${file.name}"
                        }
                        out(rendered, StreamType.STDOUT)
                    } else {
                        out("wc: ${fileArgs[0]}: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "sort" -> {
                val flags = args.filter { it.startsWith("-") }.joinToString("")
                val reverse = flags.contains('r')
                val numeric = flags.contains('n')
                val unique = flags.contains('u')
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("sort: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        var lines = file.readLines()
                        lines = if (numeric) {
                            lines.sortedBy { it.trim().toDoubleOrNull() ?: Double.MAX_VALUE }
                        } else {
                            lines.sorted()
                        }
                        if (reverse) lines = lines.asReversed()
                        if (unique) lines = lines.distinct()
                        out(lines.joinToString("\n"), StreamType.STDOUT)
                    } else {
                        out("sort: cannot read: ${fileArgs[0]}: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "uniq" -> {
                val flags = args.filter { it.startsWith("-") }.joinToString("")
                val countMode = flags.contains('c')
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("uniq: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        val lines = file.readLines()
                        val grouped = mutableListOf<Pair<String, Int>>()
                        for (line in lines) {
                            val last = grouped.lastOrNull()
                            if (last != null && last.first == line) {
                                grouped[grouped.lastIndex] = line to (last.second + 1)
                            } else {
                                grouped.add(line to 1)
                            }
                        }
                        val rendered = grouped.joinToString("\n") { (line, cnt) ->
                            if (countMode) "${cnt.toString().padStart(7)} $line" else line
                        }
                        out(rendered, StreamType.STDOUT)
                    } else {
                        out("uniq: ${fileArgs[0]}: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "cut" -> {
                var delimiter = "\t"
                var fieldsSpec = "1"
                val fileArgs = mutableListOf<String>()
                var i = 0
                while (i < args.size) {
                    val a = args[i]
                    when {
                        a == "-d" && i + 1 < args.size -> {
                            delimiter = args[i + 1]
                            i += 2
                        }
                        a.startsWith("-d") && a.length > 2 -> {
                            delimiter = a.substring(2)
                            i++
                        }
                        a == "-f" && i + 1 < args.size -> {
                            fieldsSpec = args[i + 1]
                            i += 2
                        }
                        a.startsWith("-f") && a.length > 2 -> {
                            fieldsSpec = a.substring(2)
                            i++
                        }
                        else -> {
                            fileArgs.add(a)
                            i++
                        }
                    }
                }
                if (fileArgs.isEmpty()) {
                    out("cut: missing file operand", StreamType.STDERR)
                } else {
                    val file = resolveFile(session.workingDirectory, fileArgs[0])
                    if (file.exists() && file.isFile) {
                        val fieldIndices = fieldsSpec.split(',').mapNotNull { it.toIntOrNull()?.minus(1) }
                        val rendered = file.readLines().joinToString("\n") { line ->
                            val cols = line.split(delimiter)
                            fieldIndices.mapNotNull { cols.getOrNull(it) }.joinToString(delimiter)
                        }
                        out(rendered, StreamType.STDOUT)
                    } else {
                        out("cut: ${fileArgs[0]}: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "tr" -> {
                val cleanArgs = args.filterNot { it.startsWith("-") }
                if (cleanArgs.size < 2) {
                    out("tr: missing operand", StreamType.STDERR)
                } else {
                    val from = cleanArgs[0]
                    val to = cleanArgs[1]
                    val fileArg = cleanArgs.getOrNull(2)
                    val input = if (fileArg != null) {
                        val f = resolveFile(session.workingDirectory, fileArg)
                        if (f.exists() && f.isFile) f.readText() else ""
                    } else ""
                    var res = input
                    from.forEachIndexed { idx, ch ->
                        val rep = to.getOrNull(idx) ?: to.lastOrNull() ?: ch
                        res = res.replace(ch, rep)
                    }
                    out(res, StreamType.STDOUT)
                }
            }
            "sed" -> {
                val inPlace = args.contains("-i")
                val cleanArgs = args.filterNot { it.startsWith("-") }
                if (cleanArgs.size < 2) {
                    out("sed: usage: sed [-i] 's/pattern/replacement/g' <file>", StreamType.STDERR)
                } else {
                    val expr = cleanArgs[0]
                    val file = resolveFile(session.workingDirectory, cleanArgs[1])
                    if (!file.exists() || !file.isFile) {
                        out("sed: can't read ${cleanArgs[1]}: No such file or directory", StreamType.STDERR)
                    } else if (expr.startsWith("s") && expr.length >= 4) {
                        val sep = expr[1]
                        val parts = expr.substring(2).split(sep)
                        if (parts.size >= 2) {
                            val pat = parts[0]
                            val rep = parts[1]
                            val global = parts.getOrNull(2)?.contains('g') == true
                            val updated = file.readLines().joinToString("\n") { line ->
                                if (global) line.replace(pat, rep) else line.replaceFirst(pat, rep)
                            }
                            if (inPlace) file.writeText(updated) else out(updated, StreamType.STDOUT)
                        }
                    }
                }
            }
            "awk" -> {
                val cleanArgs = args.filterNot { it.startsWith("-") }
                if (cleanArgs.size < 2) {
                    out("awk: usage: awk '{print $1}' <file>", StreamType.STDERR)
                } else {
                    val prog = cleanArgs[0]
                    val file = resolveFile(session.workingDirectory, cleanArgs[1])
                    if (!file.exists() || !file.isFile) {
                        out("awk: can't open file ${cleanArgs[1]}", StreamType.STDERR)
                    } else {
                        val colMatch = Regex("\\$(\\d+)").find(prog)
                        val colIdx = colMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val rendered = file.readLines().joinToString("\n") { line ->
                            if (colIdx == null || colIdx == 0) line
                            else {
                                val cols = line.trim().split("\\s+".toRegex())
                                cols.getOrNull(colIdx - 1).orEmpty()
                            }
                        }
                        out(rendered, StreamType.STDOUT)
                    }
                }
            }
            "diff" -> {
                val cleanArgs = args.filterNot { it.startsWith("-") }
                if (cleanArgs.size < 2) {
                    out("diff: missing operand after 'diff'", StreamType.STDERR)
                } else {
                    val f1 = resolveFile(session.workingDirectory, cleanArgs[0])
                    val f2 = resolveFile(session.workingDirectory, cleanArgs[1])
                    if (!f1.exists()) {
                        out("diff: ${cleanArgs[0]}: No such file or directory", StreamType.STDERR)
                    } else if (!f2.exists()) {
                        out("diff: ${cleanArgs[1]}: No such file or directory", StreamType.STDERR)
                    } else {
                        val l1 = f1.readLines()
                        val l2 = f2.readLines()
                        if (l1 == l2) {
                            // identical -> silent in POSIX diff
                        } else {
                            val diffLines = mutableListOf<String>()
                            val max = maxOf(l1.size, l2.size)
                            for (idx in 0 until max) {
                                val a = l1.getOrNull(idx)
                                val b = l2.getOrNull(idx)
                                if (a != b) {
                                    if (a != null) diffLines.add("< $a")
                                    if (b != null) diffLines.add("> $b")
                                }
                            }
                            out(diffLines.joinToString("\n"), StreamType.STDOUT)
                        }
                    }
                }
            }
            "which", "type" -> {
                val builtins = SUPPORTED_SHELL_COMMANDS
                val query = args.firstOrNull().orEmpty()
                if (query in builtins) {
                    out("/bin/$query (vibespace built-in)", StreamType.STDOUT)
                } else {
                    out("which: no $query in (/bin:/usr/bin)", StreamType.STDERR)
                }
            }
            "grep" -> {
                if (args.isEmpty()) {
                    out("grep: missing pattern and file operand", StreamType.STDERR)
                } else {
                    val flags = args.filter { it.startsWith("-") }.joinToString("")
                    val caseInsensitive = flags.contains('i')
                    val lineNumbers = flags.contains('n')
                    val invertMatch = flags.contains('v')
                    val recursive = flags.contains('r') || flags.contains('R')
                    val cleanArgs = args.filterNot { it.startsWith("-") }
                    if (cleanArgs.size < 2) {
                        out("grep: missing file operand", StreamType.STDERR)
                    } else {
                        val pattern = cleanArgs[0]
                        val target = resolveFile(session.workingDirectory, cleanArgs[1])
                        if (target.exists()) {
                            val filesToSearch = if (target.isDirectory && recursive) {
                                target.walkTopDown().filter { it.isFile }.toList()
                            } else if (target.isFile) {
                                listOf(target)
                            } else {
                                emptyList()
                            }
                            val allMatches = mutableListOf<String>()
                            for (f in filesToSearch) {
                                runCatching {
                                    f.readLines().forEachIndexed { idx, line ->
                                        val hit = if (caseInsensitive) line.contains(pattern, ignoreCase = true) else line.contains(pattern)
                                        if (if (invertMatch) !hit else hit) {
                                            val prefix = buildString {
                                                if (filesToSearch.size > 1) append("${f.relativeToOrSelf(File(session.workingDirectory)).path}:")
                                                if (lineNumbers) append("${idx + 1}:")
                                            }
                                            allMatches.add("$prefix$line")
                                        }
                                    }
                                }
                            }
                            if (allMatches.isNotEmpty()) {
                                out(allMatches.joinToString("\n"), StreamType.STDOUT)
                            }
                        } else {
                            out("grep: ${cleanArgs[1]}: No such file or directory", StreamType.STDERR)
                        }
                    }
                }
            }
            "find" -> {
                val startDir = if (args.isNotEmpty() && !args[0].startsWith("-")) resolveFile(session.workingDirectory, args[0]) else File(session.workingDirectory)
                val nameIdx = args.indexOf("-name")
                val filter = if (nameIdx != -1 && nameIdx + 1 < args.size) args[nameIdx + 1].trim('"', '\'') else null
                val typeIdx = args.indexOf("-type")
                val typeFilter = if (typeIdx != -1 && typeIdx + 1 < args.size) args[typeIdx + 1] else null
                if (startDir.exists() && startDir.isDirectory) {
                    val found = startDir.walkTopDown().maxDepth(10).filter { f ->
                        val typeOk = when (typeFilter) {
                            "f" -> f.isFile
                            "d" -> f.isDirectory
                            else -> true
                        }
                        val nameOk = if (filter == null) true
                        else if (filter.startsWith("*") && filter.endsWith("*")) f.name.contains(filter.removeSurrounding("*"))
                        else if (filter.startsWith("*")) f.name.endsWith(filter.removePrefix("*"))
                        else if (filter.endsWith("*")) f.name.startsWith(filter.removeSuffix("*"))
                        else f.name == filter
                        typeOk && nameOk
                    }.map { it.relativeToOrSelf(File(session.workingDirectory)).path.replace('\\', '/').ifEmpty { "." } }
                    out(found.joinToString("\n"), StreamType.STDOUT)
                } else {
                    out("find: '${startDir.path}': No such file or directory", StreamType.STDERR)
                }
            }
            "gzip" -> {
                val targetArg = args.firstOrNull { !it.startsWith("-") }
                if (targetArg == null) {
                    out("gzip: missing file operand", StreamType.STDERR)
                } else {
                    val src = resolveFile(session.workingDirectory, targetArg)
                    if (!src.exists() || !src.isFile) {
                        out("gzip: $targetArg: No such file or directory", StreamType.STDERR)
                    } else {
                        val dst = File(src.parentFile, "${src.name}.gz")
                        GZIPOutputStream(FileOutputStream(dst)).use { gz ->
                            FileInputStream(src).use { it.copyTo(gz) }
                        }
                        if (!args.contains("-k")) src.delete()
                    }
                }
            }
            "gunzip" -> {
                val targetArg = args.firstOrNull { !it.startsWith("-") }
                if (targetArg == null) {
                    out("gunzip: missing file operand", StreamType.STDERR)
                } else {
                    val src = resolveFile(session.workingDirectory, targetArg)
                    if (!src.exists() || !src.isFile) {
                        out("gunzip: $targetArg: No such file or directory", StreamType.STDERR)
                    } else {
                        val dstName = src.name.removeSuffix(".gz")
                        val dst = File(src.parentFile, dstName)
                        GZIPInputStream(FileInputStream(src)).use { gz ->
                            FileOutputStream(dst).use { gz.copyTo(it) }
                        }
                        if (!args.contains("-k")) src.delete()
                    }
                }
            }
            "zip" -> {
                val cleanArgs = args.filterNot { it.startsWith("-") }
                if (cleanArgs.size < 2) {
                    out("zip: usage: zip <archive.zip> <file1> [file2...]", StreamType.STDERR)
                } else {
                    val zipFile = resolveFile(session.workingDirectory, cleanArgs[0])
                    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                        for (item in cleanArgs.drop(1)) {
                            val f = resolveFile(session.workingDirectory, item)
                            if (f.exists() && f.isFile) {
                                zos.putNextEntry(ZipEntry(f.name))
                                FileInputStream(f).use { it.copyTo(zos) }
                                zos.closeEntry()
                                out("  adding: ${f.name}", StreamType.STDOUT)
                            }
                        }
                    }
                }
            }
            "unzip" -> {
                val cleanArgs = args.filterNot { it.startsWith("-") }
                if (cleanArgs.isEmpty()) {
                    out("unzip: missing archive operand", StreamType.STDERR)
                } else {
                    val zipFile = resolveFile(session.workingDirectory, cleanArgs[0])
                    if (!zipFile.exists() || !zipFile.isFile) {
                        out("unzip: cannot find or open ${cleanArgs[0]}", StreamType.STDERR)
                    } else {
                        val outDir = File(session.workingDirectory)
                        ZipInputStream(FileInputStream(zipFile)).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val dest = File(outDir, entry.name)
                                if (entry.isDirectory) {
                                    dest.mkdirs()
                                } else {
                                    dest.parentFile?.mkdirs()
                                    FileOutputStream(dest).use { zis.copyTo(it) }
                                    out("  inflating: ${entry.name}", StreamType.STDOUT)
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                }
            }
            "tar" -> {
                out("tar: archive utility ready (use Alpine Linux runner for full GNU/BusyBox tar flags)", StreamType.STDOUT)
            }
            "md5sum", "sha256sum" -> {
                val algo = if (cmd == "md5sum") "MD5" else "SHA-256"
                val fileArgs = args.filterNot { it.startsWith("-") }
                if (fileArgs.isEmpty()) {
                    out("$cmd: missing file operand", StreamType.STDERR)
                } else {
                    for (arg in fileArgs) {
                        val f = resolveFile(session.workingDirectory, arg)
                        if (f.exists() && f.isFile) {
                            val digest = MessageDigest.getInstance(algo).digest(f.readBytes())
                            val hex = digest.joinToString("") { "%02x".format(it) }
                            out("$hex  $arg", StreamType.STDOUT)
                        } else {
                            out("$cmd: $arg: No such file or directory", StreamType.STDERR)
                        }
                    }
                }
            }
            "base64" -> {
                val decode = args.contains("-d") || args.contains("--decode")
                val fileArg = args.firstOrNull { !it.startsWith("-") }
                if (fileArg == null) {
                    out("base64: missing file operand", StreamType.STDERR)
                } else {
                    val f = resolveFile(session.workingDirectory, fileArg)
                    if (f.exists() && f.isFile) {
                        val result = if (decode) {
                            String(Base64.getMimeDecoder().decode(f.readText().trim()), StandardCharsets.UTF_8)
                        } else {
                            Base64.getEncoder().encodeToString(f.readBytes())
                        }
                        out(result, StreamType.STDOUT)
                    } else {
                        out("base64: $fileArg: No such file or directory", StreamType.STDERR)
                    }
                }
            }
            "df" -> {
                val dir = File(session.workingDirectory)
                val total = dir.totalSpace / (1024 * 1024)
                val free = dir.freeSpace / (1024 * 1024)
                val used = total - free
                val percent = if (total > 0) (used * 100 / total) else 0
                val outStr = "Filesystem      1M-blocks    Used Available Use% Mounted on\n/dev/root         ${total}M  ${used}M     ${free}M  ${percent}% /"
                out(outStr, StreamType.STDOUT)
            }
            "free" -> {
                val runtime = Runtime.getRuntime()
                val total = runtime.totalMemory() / (1024 * 1024)
                val free = runtime.freeMemory() / (1024 * 1024)
                val used = total - free
                val outStr = "               total        used        free\nMem:           ${total}M         ${used}M         ${free}M"
                out(outStr, StreamType.STDOUT)
            }
            "ps" -> {
                val outStr = """
                      PID TTY          TIME CMD
                        1 pts/0    00:00:00 sh
                       42 pts/0    00:00:00 ps
                """.trimIndent()
                out(outStr, StreamType.STDOUT)
            }
            "top" -> {
                val runtime = Runtime.getRuntime()
                val total = runtime.totalMemory() / (1024 * 1024)
                val free = runtime.freeMemory() / (1024 * 1024)
                val used = total - free
                val outStr = """
                    Mem: ${used}M used, ${free}M free, 0K shrd, 0K buff, ${total}M total
                    CPU:  2.1% usr  1.0% sys  0.0% nic 96.9% idle  0.0% io  0.0% irq  0.0% sirq
                      PID  PPID USER     STAT   VSZ %VSZ CPU %CPU COMMAND
                        1     0 root     S     4120  0.2   0  0.1 /bin/sh
                """.trimIndent()
                out(outStr, StreamType.STDOUT)
            }
            "kill" -> {
                if (args.isEmpty()) {
                    out("kill: usage: kill [-s sigspec | -n signum | -sigspec] pid | jobspec ...", StreamType.STDERR)
                }
            }
            "uname" -> {
                val osArch = System.getProperty("os.arch", "aarch64")
                val osName = System.getProperty("os.name", "Linux")
                val osVersion = System.getProperty("os.version", "android")
                val output = when {
                    args.contains("-a") -> "$osName vibespace-android $osVersion #1 PREEMPT $osArch GNU/Linux"
                    args.contains("-m") -> osArch
                    args.contains("-r") -> osVersion
                    else -> osName
                }
                out(output, StreamType.STDOUT)
            }
            "hostname" -> {
                out("vibespace-android", StreamType.STDOUT)
            }
            "whoami" -> {
                out("vibespace-developer", StreamType.STDOUT)
            }
            "id" -> {
                out("uid=0(root) gid=0(root) groups=0(root)", StreamType.STDOUT)
            }
            "uptime" -> {
                val elapsedSec = ((System.currentTimeMillis() - serviceStartMillis) / 1000).coerceAtLeast(1)
                val mins = elapsedSec / 60
                val secs = elapsedSec % 60
                val timeNow = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                out(" $timeNow up ${mins}m ${secs}s,  1 user,  load average: 0.08, 0.04, 0.01", StreamType.STDOUT)
            }
            "env", "printenv" -> {
                if (cmd == "printenv" && args.isNotEmpty()) {
                    val v = env[args[0]]
                    if (v != null) out(v, StreamType.STDOUT)
                } else {
                    val envStr = env.entries.joinToString("\n") { "${it.key}=${it.value}" }
                    out(envStr, StreamType.STDOUT)
                }
            }
            "export" -> {
                if (args.isEmpty()) {
                    out(env.entries.joinToString("\n") { "declare -x ${it.key}=\"${it.value}\"" }, StreamType.STDOUT)
                } else {
                    for (arg in args) {
                        if (arg.contains('=')) {
                            val key = arg.substringBefore('=').trim()
                            val value = arg.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                            if (key.isNotEmpty()) env[key] = value
                        }
                    }
                }
            }
            "unset" -> {
                for (arg in args) {
                    env.remove(arg)
                }
            }
            "alias" -> {
                if (args.isEmpty()) {
                    val rendered = aliases.entries.joinToString("\n") { "alias ${it.key}='${it.value}'" }
                    if (rendered.isNotEmpty()) out(rendered, StreamType.STDOUT)
                } else {
                    for (arg in args) {
                        if (arg.contains('=')) {
                            val k = arg.substringBefore('=').trim()
                            val v = arg.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                            if (k.isNotEmpty()) aliases[k] = v
                        } else {
                            val existing = aliases[arg]
                            if (existing != null) out("alias $arg='$existing'", StreamType.STDOUT)
                            else out("alias: $arg: not found", StreamType.STDERR)
                        }
                    }
                }
            }
            "unalias" -> {
                for (arg in args) {
                    aliases.remove(arg)
                }
            }
            "history" -> {
                val hist = sessionHistory[sessionId].orEmpty()
                val rendered = hist.mapIndexed { idx, h -> "${(idx + 1).toString().padStart(5)}  $h" }.joinToString("\n")
                out(rendered, StreamType.STDOUT)
            }
            "date" -> {
                val formatted = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).format(Date())
                out(formatted, StreamType.STDOUT)
            }
            "cal" -> {
                val cal = Calendar.getInstance()
                val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
                val today = cal.get(Calendar.DAY_OF_MONTH)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1
                val sb = StringBuilder()
                sb.appendLine("   $monthTitle")
                sb.appendLine("Su Mo Tu We Th Fr Sa")
                var col = 0
                repeat(firstDow) {
                    sb.append("   ")
                    col++
                }
                for (d in 1..maxDay) {
                    val cell = d.toString().padStart(2)
                    sb.append(if (d == today) cell else cell).append(' ')
                    col++
                    if (col == 7 && d != maxDay) {
                        sb.append('\n')
                        col = 0
                    }
                }
                out(sb.toString().trimEnd(), StreamType.STDOUT)
            }
            "sleep" -> {
                val sec = args.firstOrNull()?.toDoubleOrNull() ?: 0.0
                val ms = (sec * 1000).toLong().coerceIn(0L, 5000L)
                if (ms > 0) delay(ms)
            }
            "seq" -> {
                val nums = args.mapNotNull { it.toIntOrNull() }
                val range = when (nums.size) {
                    1 -> 1..nums[0]
                    2 -> nums[0]..nums[1]
                    3 -> nums[0]..nums[2] step nums[1].coerceAtLeast(1)
                    else -> IntRange.EMPTY
                }
                out(range.take(1000).joinToString("\n"), StreamType.STDOUT)
            }
            "expr" -> {
                if (args.size == 3) {
                    val a = args[0].toLongOrNull()
                    val op = args[1]
                    val b = args[2].toLongOrNull()
                    if (a != null && b != null) {
                        val res = when (op) {
                            "+" -> a + b
                            "-" -> a - b
                            "*", "\\*" -> a * b
                            "/" -> if (b != 0L) a / b else 0L
                            "%" -> if (b != 0L) a % b else 0L
                            else -> 0L
                        }
                        out(res.toString(), StreamType.STDOUT)
                    } else {
                        out(args.joinToString(" "), StreamType.STDOUT)
                    }
                } else {
                    out(args.joinToString(" "), StreamType.STDOUT)
                }
            }
            "true" -> Unit
            "false" -> {
                out("", StreamType.STDERR)
            }
            "yes" -> {
                val word = args.firstOrNull() ?: "y"
                out(List(10) { word }.joinToString("\n"), StreamType.STDOUT)
            }
            "test", "[" -> {
                val clean = if (cmd == "[" && args.lastOrNull() == "]") args.dropLast(1) else args
                val ok = when {
                    clean.size == 2 && clean[0] == "-f" -> resolveFile(session.workingDirectory, clean[1]).isFile
                    clean.size == 2 && clean[0] == "-d" -> resolveFile(session.workingDirectory, clean[1]).isDirectory
                    clean.size == 2 && clean[0] == "-e" -> resolveFile(session.workingDirectory, clean[1]).exists()
                    clean.size == 2 && clean[0] == "-n" -> clean[1].isNotEmpty()
                    clean.size == 2 && clean[0] == "-z" -> clean[1].isEmpty()
                    clean.size == 3 && (clean[1] == "=" || clean[1] == "==") -> clean[0] == clean[2]
                    clean.size == 3 && clean[1] == "!=" -> clean[0] != clean[2]
                    else -> clean.isNotEmpty()
                }
                if (!ok) out("", StreamType.STDERR)
            }
            "ping" -> {
                val host = args.lastOrNull { !it.startsWith("-") } ?: "127.0.0.1"
                out("PING $host ($host): 56 data bytes\n64 bytes from $host: seq=0 ttl=64 time=0.42 ms\n--- $host ping statistics ---\n1 packets transmitted, 1 packets received, 0% packet loss", StreamType.STDOUT)
            }
            "ip", "ifconfig" -> {
                out("lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536\n        inet 127.0.0.1  netmask 255.0.0.0", StreamType.STDOUT)
            }
            "curl", "wget" -> {
                out("$cmd: switch runner mode to 'Alpine Linux' in the terminal header for full network tools.", StreamType.STDERR)
            }
            "sh", "bash" -> {
                if (args.size >= 2 && args[0] == "-c") {
                    val subCmd = args.drop(1).joinToString(" ").trim('"', '\'')
                    executeCommandChain(sessionId, subCmd)
                } else {
                    out("VibeSpace Linux Shell active — type commands directly or 'help' for a list of commands.", StreamType.SYSTEM)
                }
            }
            "exit" -> {
                out("Session active. Use the terminal tab close button to close a terminal session.", StreamType.SYSTEM)
            }
            "version" -> {
                out("VibeSpace Mobile IDE v0.1.0-alpha", StreamType.STDOUT)
            }
            else -> {
                out(
                    "vibespace: command not found: $cmd. (Switch runner to 'Alpine Linux' for full Linux binaries)",
                    StreamType.STDERR
                )
            }
        }
    }

    private fun parseLineCountFlag(args: List<String>, defaultCount: Int): Int {
        val nIdx = args.indexOf("-n")
        if (nIdx != -1 && nIdx + 1 < args.size) {
            args[nIdx + 1].toIntOrNull()?.let { return it.coerceAtLeast(1) }
        }
        args.firstOrNull { it.startsWith("-") && it.drop(1).toIntOrNull() != null }
            ?.drop(1)?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
        return defaultCount
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1fG", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1fM", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1fK", bytes / 1024.0)
        else -> "${bytes}B"
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
        _sessions.update { sessions ->
            sessions.map {
                if (it.sessionId == updated.sessionId) updated else it
            }
        }
        if (_activeSession.value?.sessionId == updated.sessionId) {
            _activeSession.value = updated
        }
    }

    private fun resolveFile(workingDir: String, rawPath: String): File {
        val path = rawPath.trim().removeSurrounding("\"").removeSurrounding("'")
        val effectiveWorkingDir = if (workingDir.isBlank() || workingDir == "~" || !File(workingDir).exists()) {
            val appHome = defaultHomeDirectory?.takeIf { it.exists() || it.mkdirs() }?.absolutePath
            val userHome = System.getProperty("user.home")
            when {
                !appHome.isNullOrBlank() -> appHome
                !userHome.isNullOrBlank() && userHome != "/" && File(userHome).exists() -> userHome
                else -> System.getProperty("user.dir", ".")
            }
        } else {
            workingDir
        }

        return if (path == "." || path.isBlank()) {
            File(effectiveWorkingDir)
        } else if (path.startsWith("/")) {
            File(path)
        } else if (path.startsWith("~")) {
            val sub = path.removePrefix("~").removePrefix("/")
            if (sub.isEmpty()) File(effectiveWorkingDir) else File(effectiveWorkingDir, sub)
        } else {
            File(effectiveWorkingDir, path)
        }
    }

    companion object {
        private const val MAX_LINES_PER_SESSION = 2000

        val SUPPORTED_SHELL_COMMANDS: Set<String> = setOf(
            "help", "clear", "reset", "echo", "printf", "pwd", "cd", "ls", "cat", "less", "more",
            "nl", "rev", "tac", "touch", "mkdir", "rmdir", "rm", "cp", "mv", "ln", "chmod", "stat",
            "file", "realpath", "readlink", "basename", "dirname", "tree", "du", "head", "tail",
            "wc", "sort", "uniq", "cut", "tr", "sed", "awk", "diff", "which", "type", "grep",
            "find", "gzip", "gunzip", "zip", "unzip", "tar", "md5sum", "sha256sum", "base64",
            "df", "free", "ps", "top", "kill", "uname", "hostname", "whoami", "id", "uptime",
            "env", "printenv", "export", "unset", "alias", "unalias", "history", "date", "cal",
            "sleep", "seq", "expr", "true", "false", "yes", "test", "[", "ping", "ip", "ifconfig",
            "curl", "wget", "sh", "bash", "exit", "version"
        )
    }
}
