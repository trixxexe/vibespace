package com.vibespace.core.runner.embedded

import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.manager.ExternalRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import com.vibespace.core.workspace.manager.WorkspaceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in zero-setup runtime executing JavaScript, Python, Web/HTML, and Shell scripts
 * directly inside the Android app without downloading external packages.
 */
class EmbeddedInstantRunner(
    private val workspaceManager: WorkspaceManager? = null
) : ExternalRunner {

    override val runnerId: String = "embedded"
    override val name: String = "Instant Evaluator (Basic Print & Arithmetic)"

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val activeRuns = mutableMapOf<String, ProcessExecution>()

    override fun isAvailable(): Boolean = true

    override suspend fun execute(
        command: String,
        workingDirectory: String,
        environmentVariables: Map<String, String>
    ): VibeResult<ProcessExecution> {
        val executionId = IdGenerator.shortId("embed")
        val flow = MutableSharedFlow<ProcessOutput>(replay = 50)
        outputFlows[executionId] = flow

        val execution = ProcessExecution(
            executionId = executionId,
            command = command,
            workingDirectoryUri = workingDirectory,
            state = ExecutionState.RUNNING,
            startTime = System.currentTimeMillis()
        )
        activeRuns[executionId] = execution

        val outputs = mutableListOf<ProcessOutput>()

        fun emitLine(line: String, isError: Boolean = false) {
            val out = ProcessOutput(line = line, isError = isError)
            outputs.add(out)
            flow.tryEmit(out)
        }

        emitLine("[VibeSpace Instant Runner] Executing: $command")

        val trimmed = command.trim()
        val exitCode: Int

        when {
            // Python execution
            trimmed.startsWith("python") || trimmed.endsWith(".py") -> {
                exitCode = runPython(trimmed, workingDirectory, ::emitLine)
            }

            // JavaScript / Node execution
            trimmed.startsWith("node") || trimmed.startsWith("js") || trimmed.endsWith(".js") || trimmed.endsWith(".ts") -> {
                exitCode = runJavaScript(trimmed, workingDirectory, ::emitLine)
            }

            // Built-in Shell commands
            else -> {
                exitCode = runShell(trimmed, workingDirectory, ::emitLine)
            }
        }

        val completed = execution.copy(
            state = if (exitCode == 0) ExecutionState.SUCCEEDED else ExecutionState.FAILED,
            exitCode = exitCode,
            endTime = System.currentTimeMillis(),
            outputs = outputs
        )
        activeRuns[executionId] = completed

        emitLine("[Process exited with code $exitCode]")
        return VibeResult.Success(completed)
    }

    override suspend fun stop(executionId: String): VibeResult<Unit> {
        val current = activeRuns[executionId] ?: return VibeResult.Error("Execution not found: $executionId")
        return if (current.state == ExecutionState.RUNNING) {
            VibeResult.Error("The embedded evaluator cannot interrupt a command while it is running.")
        } else {
            VibeResult.Error("Execution is no longer running.")
        }
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }

    private suspend fun runPython(command: String, workingDirectory: String, emit: (String, Boolean) -> Unit): Int {
        emit("VibeSpace Instant Evaluator (Basic Print & Arithmetic)", false)
        emit("Lightweight evaluator only; loops, imports, and packages are not supported.", false)

        val codeOrFile = extractCodeFromCommand(command, "python3", "python")
        if (codeOrFile.isBlank()) {
            emit("Python interactive shell ready. Use 'Run' toolbar button to execute scripts.", false)
            return 0
        }

        if (codeOrFile in listOf("-V", "--version", "-v")) {
            emit("Python 3.12 (VibeSpace Instant Evaluator)", false)
            return 0
        }

        if (codeOrFile == "-m" || codeOrFile.startsWith("-m ") || codeOrFile.startsWith("-m\t") || (codeOrFile.startsWith("-m") && !codeOrFile.endsWith(".py", ignoreCase = true))) {
            val module = codeOrFile.removePrefix("-m").trim().split("\\s+".toRegex()).firstOrNull().orEmpty()
            val moduleSuffix = if (module.isNotBlank()) " ('-m $module')" else ""
            emit("Error: Running Python modules$moduleSuffix is unsupported in Instant Evaluator.", true)
            emit("Package management and external modules require a full runtime. Switch runner mode to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
            return 1
        }

        var codeToExecute = codeOrFile
        val fileArgument = codeOrFile.removeSurrounding("\"").removeSurrounding("'")
        if (fileArgument.endsWith(".py", ignoreCase = true)) {
            if (workspaceManager == null) {
                emit("FileNotFoundError: no workspace is attached to the embedded evaluator.", true)
                return 1
            }
            val fileContent = resolveFileContent(fileArgument, workingDirectory)
            if (fileContent != null) {
                emit("[Running file with the basic evaluator: $fileArgument]", false)
                codeToExecute = fileContent
            } else {
                emit("FileNotFoundError: [Errno 2] No such file or directory: '$fileArgument'", true)
                return 1
            }
        }

        return try {
            executePythonSnippet(codeToExecute, emit)
            0
        } catch (_: UnsupportedOperationException) {
            1
        } catch (e: Throwable) {
            emit("Traceback (most recent call last):", true)
            emit("  File \"<string>\", line 1, in <module>", true)
            emit("${e.javaClass.simpleName}: ${e.message}", true)
            1
        }
    }

    private suspend fun runJavaScript(command: String, workingDirectory: String, emit: (String, Boolean) -> Unit): Int {
        emit("VibeSpace Instant Evaluator (Basic Print & Arithmetic)", false)
        emit("Lightweight evaluator only; modules, classes, and packages are not supported.", false)

        val codeOrFile = extractCodeFromCommand(command, "node", "js")
        if (codeOrFile.isBlank()) {
            emit("JavaScript runtime ready. Use 'Run' toolbar button to execute scripts.", false)
            return 0
        }

        if (codeOrFile in listOf("-V", "--version", "-v")) {
            emit("Node.js v20.18.0 (VibeSpace Instant Evaluator)", false)
            return 0
        }

        var codeToExecute = codeOrFile
        val fileArgument = codeOrFile.removeSurrounding("\"").removeSurrounding("'")
        if (fileArgument.endsWith(".js", ignoreCase = true) || fileArgument.endsWith(".ts", ignoreCase = true)) {
            if (workspaceManager == null) {
                emit("Error: no workspace is attached to the embedded evaluator.", true)
                return 1
            }
            val fileContent = resolveFileContent(fileArgument, workingDirectory)
            if (fileContent != null) {
                emit("[Running file with the basic evaluator: $fileArgument]", false)
                codeToExecute = fileContent
            } else {
                emit("Error: Cannot find module '$fileArgument'", true)
                return 1
            }
        }

        return try {
            executeJavaScriptSnippet(codeToExecute, emit)
            0
        } catch (_: UnsupportedOperationException) {
            1
        } catch (e: Throwable) {
            emit("Uncaught ${e.javaClass.simpleName}: ${e.message}", true)
            1
        }
    }

    private suspend fun resolveFileContent(fileName: String, workingDirectory: String): String? {
        val manager = workspaceManager ?: return null
        val project = manager.currentProject.value
        val candidates = listOf(
            fileName,
            "$workingDirectory/$fileName",
            project?.let { "${it.rootUri}/$fileName" },
            project?.let { "${it.rootUri}/src/$fileName" }
        ).filterNotNull()

        for (candidate in candidates) {
            val read = manager.readFile(candidate)
            if (read is VibeResult.Success) {
                return read.data
            }
        }
        return null
    }

    private fun runShell(command: String, workingDir: String, emit: (String, Boolean) -> Unit): Int {
        val parts = command.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.isEmpty()) return 0

        val binary = parts[0].lowercase()
        val args = parts.drop(1)

        return when (binary) {
            "echo" -> {
                emit(args.joinToString(" "), false)
                0
            }
            "pwd" -> {
                emit(workingDir, false)
                0
            }
            "date" -> {
                val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
                emit(dateFormat.format(Date()), false)
                0
            }
            "help" -> {
                emit("VibeSpace Safe Command Built-ins:", false)
                emit("  echo <text>   - Print text to console", false)
                emit("  pwd           - Print active working directory", false)
                emit("  ls [path]     - List directory contents", false)
                emit("  cat <file>    - Print file contents", false)
                emit("  touch <file>  - Create empty file", false)
                emit("  mkdir <dir>   - Create new directory", false)
                emit("  rm <path>     - Remove file or directory (-r)", false)
                emit("  cp <src> <dst>- Copy file", false)
                emit("  mv <src> <dst>- Move or rename file", false)
                emit("  head / tail   - Display file beginning or end", false)
                emit("  wc <file>     - Count lines, words, bytes", false)
                emit("  grep <pat> <f>- Search pattern in file", false)
                emit("  find [path]   - Find files by name", false)
                emit("  which <cmd>   - Locate command", false)
                emit("  uname / env   - System architecture and environment", false)
                emit("  whoami / date - Current user and system date", false)
                emit("  sh / bash     - Safe shell command dispatcher", false)
                emit("  python <code> - Run Python 3 snippet or script", false)
                emit("  node <code>   - Run JavaScript / TypeScript snippet or script", false)
                emit("  clear         - Clear terminal screen", false)
                emit("", false)
                emit("Active Mode: Instant Embedded. For apt/apk or pip install, switch runner to 'Alpine Linux' or 'Termux'.", false)
                0
            }
            "ls" -> {
                val targetDir = if (args.isNotEmpty()) resolvePath(workingDir, args[0]) else File(workingDir)
                if (targetDir.exists() && targetDir.isDirectory) {
                    val files = targetDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                    val output = if (files.isEmpty()) "(empty directory)" else files.joinToString("  ") {
                        if (it.isDirectory) "${it.name}/" else it.name
                    }
                    emit(output, false)
                    0
                } else if (targetDir.exists() && targetDir.isFile) {
                    emit(targetDir.name, false)
                    0
                } else {
                    emit("ls: cannot access '${targetDir.path}': No such file or directory", true)
                    1
                }
            }
            "cat" -> {
                if (args.isEmpty()) {
                    emit("cat: missing file operand", true)
                    1
                } else {
                    var code = 0
                    for (arg in args) {
                        val file = resolvePath(workingDir, arg)
                        if (file.exists() && file.isFile) {
                            try {
                                emit(file.readText(), false)
                            } catch (e: Exception) {
                                emit("cat: ${file.name}: ${e.message}", true)
                                code = 1
                            }
                        } else {
                            emit("cat: $arg: No such file or directory", true)
                            code = 1
                        }
                    }
                    code
                }
            }
            "touch" -> {
                if (args.isEmpty()) {
                    emit("touch: missing file operand", true)
                    1
                } else {
                    for (arg in args) {
                        val file = resolvePath(workingDir, arg)
                        file.parentFile?.mkdirs()
                        if (!file.exists()) file.createNewFile()
                        else file.setLastModified(System.currentTimeMillis())
                    }
                    0
                }
            }
            "mkdir" -> {
                if (args.isEmpty()) {
                    emit("mkdir: missing operand", true)
                    1
                } else {
                    var code = 0
                    for (arg in args) {
                        val dir = resolvePath(workingDir, arg)
                        if (!dir.mkdirs() && !dir.isDirectory) {
                            emit("mkdir: cannot create directory '$arg'", true)
                            code = 1
                        }
                    }
                    code
                }
            }
            "rm" -> {
                if (args.isEmpty()) {
                    emit("rm: missing operand", true)
                    1
                } else {
                    val recursive = args.contains("-r") || args.contains("-rf") || args.contains("-R")
                    val targets = args.filterNot { it.startsWith("-") }
                    var code = 0
                    for (target in targets) {
                        val file = resolvePath(workingDir, target)
                        if (file.exists()) {
                            val deleted = if (recursive) file.deleteRecursively() else file.delete()
                            if (!deleted) {
                                emit("rm: cannot remove '$target'", true)
                                code = 1
                            }
                        } else {
                            emit("rm: cannot remove '$target': No such file or directory", true)
                            code = 1
                        }
                    }
                    code
                }
            }
            "cp" -> {
                val clean = args.filterNot { it.startsWith("-") }
                if (clean.size < 2) {
                    emit("cp: missing file operand", true)
                    1
                } else {
                    val src = resolvePath(workingDir, clean[0])
                    val dest = resolvePath(workingDir, clean[1])
                    if (!src.exists()) {
                        emit("cp: cannot stat '${clean[0]}': No such file or directory", true)
                        1
                    } else {
                        val target = if (dest.isDirectory) File(dest, src.name) else dest
                        src.copyRecursively(target, overwrite = true)
                        0
                    }
                }
            }
            "mv" -> {
                val clean = args.filterNot { it.startsWith("-") }
                if (clean.size < 2) {
                    emit("mv: missing destination file operand", true)
                    1
                } else {
                    val src = resolvePath(workingDir, clean[0])
                    val dest = resolvePath(workingDir, clean[1])
                    if (!src.exists()) {
                        emit("mv: cannot stat '${clean[0]}': No such file or directory", true)
                        1
                    } else {
                        val target = if (dest.isDirectory) File(dest, src.name) else dest
                        if (src.renameTo(target)) 0
                        else {
                            src.copyRecursively(target, overwrite = true)
                            src.deleteRecursively()
                            0
                        }
                    }
                }
            }
            "head" -> {
                val fileArg = args.lastOrNull { !it.startsWith("-") }
                if (fileArg == null) {
                    emit("head: missing file operand", true)
                    1
                } else {
                    val file = resolvePath(workingDir, fileArg)
                    if (file.exists() && file.isFile) {
                        emit(file.readLines().take(10).joinToString("\n"), false)
                        0
                    } else {
                        emit("head: cannot open '$fileArg': No such file or directory", true)
                        1
                    }
                }
            }
            "tail" -> {
                val fileArg = args.lastOrNull { !it.startsWith("-") }
                if (fileArg == null) {
                    emit("tail: missing file operand", true)
                    1
                } else {
                    val file = resolvePath(workingDir, fileArg)
                    if (file.exists() && file.isFile) {
                        emit(file.readLines().takeLast(10).joinToString("\n"), false)
                        0
                    } else {
                        emit("tail: cannot open '$fileArg': No such file or directory", true)
                        1
                    }
                }
            }
            "wc" -> {
                if (args.isEmpty()) {
                    emit("wc: missing file operand", true)
                    1
                } else {
                    val file = resolvePath(workingDir, args[0])
                    if (file.exists() && file.isFile) {
                        val text = file.readText()
                        val lines = text.lines().size
                        val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
                        val bytes = text.toByteArray().size
                        emit(" $lines  $words  $bytes ${file.name}", false)
                        0
                    } else {
                        emit("wc: ${args[0]}: No such file or directory", true)
                        1
                    }
                }
            }
            "grep" -> {
                val clean = args.filterNot { it.startsWith("-") }
                if (clean.size < 2) {
                    emit("grep: missing pattern or file", true)
                    1
                } else {
                    val caseInsensitive = args.contains("-i")
                    val pattern = clean[0]
                    val file = resolvePath(workingDir, clean[1])
                    if (file.exists() && file.isFile) {
                        val matches = file.readLines().filter {
                            if (caseInsensitive) it.contains(pattern, ignoreCase = true) else it.contains(pattern)
                        }
                        if (matches.isNotEmpty()) emit(matches.joinToString("\n"), false)
                        0
                    } else {
                        emit("grep: ${clean[1]}: No such file or directory", true)
                        1
                    }
                }
            }
            "find" -> {
                val startDir = if (args.isNotEmpty() && !args[0].startsWith("-")) resolvePath(workingDir, args[0]) else File(workingDir)
                val nameIdx = args.indexOf("-name")
                val filter = if (nameIdx != -1 && nameIdx + 1 < args.size) args[nameIdx + 1].trim('"', '\'') else null
                if (startDir.exists() && startDir.isDirectory) {
                    val found = startDir.walkTopDown().maxDepth(10).filter { f ->
                        if (filter == null) true
                        else if (filter.startsWith("*") && filter.endsWith("*")) f.name.contains(filter.removeSurrounding("*"))
                        else if (filter.startsWith("*")) f.name.endsWith(filter.removePrefix("*"))
                        else f.name == filter
                    }.map { it.relativeToOrSelf(File(workingDir)).path.replace('\\', '/') }
                    emit(found.joinToString("\n"), false)
                    0
                } else {
                    emit("find: '${startDir.path}': No such file or directory", true)
                    1
                }
            }
            "uname" -> {
                val osArch = System.getProperty("os.arch", "unknown")
                val osName = System.getProperty("os.name", "Linux")
                val osVersion = System.getProperty("os.version", "android")
                val output = if (args.contains("-a")) "$osName vibespace-android $osVersion #1 PREEMPT $osArch GNU/Linux" else osName
                emit(output, false)
                0
            }
            "env" -> {
                val envStr = "USER=vibespace-developer\nHOME=$workingDir\nSHELL=/bin/bash\nTERM=xterm-256color\nVIBESPACE_VERSION=0.1.0\nPATH=/bin:/usr/bin"
                emit(envStr, false)
                0
            }
            "whoami" -> {
                emit("vibespace-developer", false)
                0
            }
            "which" -> {
                val builtins = listOf("help", "clear", "echo", "pwd", "ls", "cat", "touch", "mkdir", "rm", "cp", "mv", "head", "tail", "wc", "grep", "find", "which", "uname", "env", "date", "whoami", "sh", "bash", "python", "node")
                val query = args.firstOrNull().orEmpty()
                if (query in builtins) {
                    emit("/bin/$query (vibespace built-in)", false)
                    0
                } else {
                    emit("which: no $query in (/bin:/usr/bin)", true)
                    1
                }
            }
            "sh", "bash" -> {
                if (args.size >= 2 && args[0] == "-c") {
                    val subCmd = args.drop(1).joinToString(" ").trim('"', '\'')
                    runShell(subCmd, workingDir, emit)
                } else {
                    emit("bash-5.2$ (VibeSpace Built-in Shell ready - type 'help' for commands)", false)
                    0
                }
            }
            "clear" -> {
                emit("\u001b[2J\u001b[H", false)
                0
            }
            "pip", "pip3" -> {
                emit("Error: '$binary' package manager is unavailable in Instant Evaluator.", true)
                emit("Package management requires a full runtime. Switch runner mode to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
                127
            }
            "npm", "npx", "yarn", "pnpm" -> {
                emit("Error: '$binary' package manager is unavailable in Instant Evaluator.", true)
                emit("Package management requires a full runtime. Switch runner mode to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
                127
            }
            "cargo", "gem", "go", "apk", "apt" -> {
                emit("Error: '$binary' package manager / tool is unavailable in Instant Evaluator.", true)
                emit("Package management and tools require a full runtime. Switch runner mode to 'Alpine Linux' or 'Termux' using the runner menu in the terminal toolbar.", true)
                127
            }
            else -> {
                emit("sh: $binary: command not found", true)
                127
            }
        }
    }

    private fun extractCodeFromCommand(command: String, vararg prefixes: String): String {
        for (prefix in prefixes) {
            if (command.startsWith(prefix)) {
                val remainder = command.removePrefix(prefix).trim()
                if (remainder.startsWith("-c")) {
                    return remainder.removePrefix("-c").trim().trim('"', '\'')
                }
                if (remainder.startsWith("-e")) {
                    return remainder.removePrefix("-e").trim().trim('"', '\'')
                }
                return remainder
            }
        }
        return command
    }

    private fun splitArguments(raw: String): List<String> {
        val results = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false

        for (char in raw) {
            when (char) {
                '\'' -> {
                    if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                    current.append(char)
                }
                '"' -> {
                    if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                    current.append(char)
                }
                ',' -> {
                    if (!inSingleQuote && !inDoubleQuote) {
                        results.add(current.toString().trim())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotBlank()) {
            results.add(current.toString().trim())
        }
        return results
    }

    private fun executePythonSnippet(code: String, emit: (String, Boolean) -> Unit) {
        val lines = code.lines()
        val variables = mutableMapOf<String, Any>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val firstWord = line.substringBefore('(').substringBefore(' ').trim()
            if (firstWord in listOf("import", "from", "def", "class", "while", "for", "with", "try")) {
                emit("SyntaxError: '$firstWord' statement is unsupported in Instant Evaluator. Switch runner to 'Alpine Linux' or 'Termux' for full language execution.", true)
                throw UnsupportedOperationException("Unsupported construct: $firstWord")
            }

            // print(...)
            if (line.startsWith("print(") && line.endsWith(")")) {
                val content = line.substring(6, line.length - 1).trim()
                val args = splitArguments(content)
                val evaluated = args.joinToString(" ") { evaluateExpression(it, variables) }
                emit(evaluated, false)
                continue
            }

            // Simple assignment: var = expr
            if (line.contains("=") && !line.contains("==") && !line.startsWith("if")) {
                val parts = line.split("=", limit = 2)
                val varName = parts[0].trim()
                val expr = parts[1].trim()
                variables[varName] = evaluateExpression(expr, variables)
                continue
            }

            // Fallback evaluation
            emit(evaluateExpression(line, variables), false)
        }
    }

    private fun executeJavaScriptSnippet(code: String, emit: (String, Boolean) -> Unit) {
        val lines = code.lines()
        val scope = mutableMapOf<String, Any>()

        for (rawLine in lines) {
            val line = rawLine.trim().removeSuffix(";")
            if (line.isEmpty() || line.startsWith("//")) continue

            val firstWord = line.substringBefore('(').substringBefore(' ').trim()
            if (firstWord in listOf("require", "import", "function", "class", "while", "for", "try", "switch")) {
                emit("ReferenceError: '$firstWord' statement is unsupported in Instant Evaluator. Switch runner to 'Alpine Linux' or 'Termux' for full Node.js execution.", true)
                throw UnsupportedOperationException("Unsupported construct: $firstWord")
            }

            // console.log(...)
            if (line.startsWith("console.log(") && line.endsWith(")")) {
                val content = line.substring(12, line.length - 1).trim()
                val args = splitArguments(content)
                val evaluated = args.joinToString(" ") { evaluateExpression(it, scope) }
                emit(evaluated, false)
                continue
            }

            // console.error(...)
            if (line.startsWith("console.error(") && line.endsWith(")")) {
                val content = line.substring(14, line.length - 1).trim()
                val args = splitArguments(content)
                val evaluated = args.joinToString(" ") { evaluateExpression(it, scope) }
                emit(evaluated, true)
                continue
            }

            // Simple assignment: let/const/var x = ...
            val cleanDecl = line.removePrefix("let ").removePrefix("const ").removePrefix("var ")
            if (cleanDecl.contains("=") && !cleanDecl.contains("==")) {
                val parts = cleanDecl.split("=", limit = 2)
                val varName = parts[0].trim()
                val expr = parts[1].trim()
                scope[varName] = evaluateExpression(expr, scope)
                continue
            }

            emit(evaluateExpression(line, scope), false)
        }
    }

    private fun evaluateExpression(expr: String, scope: Map<String, Any>): String {
        val trimmed = expr.trim()

        // String literals (must be a single self-contained literal)
        if (isStringLiteral(trimmed)) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Scope lookup
        if (scope.containsKey(trimmed)) {
            return scope[trimmed].toString()
        }

        // Addition: a + b (split outside quotes)
        val plusParts = splitByOperator(trimmed, '+')
        if (plusParts.size > 1) {
            val parts = plusParts.map { evaluateExpression(it.trim(), scope) }
            val numericValues = parts.mapNotNull { it.toDoubleOrNull() }
            if (numericValues.size == parts.size) {
                val sum = numericValues.sum()
                return if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
            }
            return parts.joinToString("")
        }

        // Multiplication: a * b
        val multParts = splitByOperator(trimmed, '*')
        if (multParts.size > 1) {
            val parts = multParts.map { evaluateExpression(it.trim(), scope) }
            val nums = parts.mapNotNull { it.toDoubleOrNull() }
            if (nums.size == 2) {
                val prod = nums[0] * nums[1]
                return if (prod % 1.0 == 0.0) prod.toLong().toString() else prod.toString()
            }
        }

        // Subtraction: a - b
        if (trimmed.contains("-") && !trimmed.startsWith("-")) {
            val subParts = splitByOperator(trimmed, '-')
            if (subParts.size == 2) {
                val parts = subParts.map { evaluateExpression(it.trim(), scope) }
                val nums = parts.mapNotNull { it.toDoubleOrNull() }
                if (nums.size == 2) {
                    val diff = nums[0] - nums[1]
                    return if (diff % 1.0 == 0.0) diff.toLong().toString() else diff.toString()
                }
            }
        }

        // Division: a / b
        val divParts = splitByOperator(trimmed, '/')
        if (divParts.size == 2) {
            val parts = divParts.map { evaluateExpression(it.trim(), scope) }
            val nums = parts.mapNotNull { it.toDoubleOrNull() }
            if (nums.size == 2 && nums[1] != 0.0) {
                val quotient = nums[0] / nums[1]
                return if (quotient % 1.0 == 0.0) quotient.toLong().toString() else quotient.toString()
            }
        }

        return trimmed
    }

    private fun isStringLiteral(s: String): Boolean {
        if (s.length < 2) return false
        val quote = s[0]
        if (quote != '"' && quote != '\'') return false
        if (s.last() != quote) return false

        var escaped = false
        for (i in 1 until s.length - 1) {
            if (escaped) {
                escaped = false
                continue
            }
            if (s[i] == '\\') {
                escaped = true
                continue
            }
            if (s[i] == quote) {
                return false
            }
        }
        return true
    }

    private fun splitByOperator(expr: String, op: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false

        for (char in expr) {
            when (char) {
                '\'' -> {
                    if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                    current.append(char)
                }
                '"' -> {
                    if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                    current.append(char)
                }
                op -> {
                    if (!inSingleQuote && !inDoubleQuote) {
                        parts.add(current.toString())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        return parts
    }

    private fun resolvePath(workingDir: String, path: String): File {
        return if (path.startsWith("/")) File(path)
        else if (path.startsWith("~")) File(System.getProperty("user.home", "/"), path.removePrefix("~").removePrefix("/"))
        else File(workingDir, path)
    }
}
