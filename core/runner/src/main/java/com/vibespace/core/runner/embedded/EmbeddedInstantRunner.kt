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
                emit("  date          - Print current date and time", false)
                emit("  python <code> - Run Python 3 snippet or script", false)
                emit("  node <code>   - Run JavaScript / TypeScript snippet or script", false)
                emit("  clear         - Clear terminal screen", false)
                emit("", false)
                emit("Active Mode: Instant Embedded. This evaluator does not install packages or run external tools.", false)
                0
            }
            "clear" -> {
                emit("\u001b[2J\u001b[H", false)
                0
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
}
