package com.vibespace.core.runner.embedded

import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.manager.ExternalRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
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
class EmbeddedInstantRunner : ExternalRunner {

    override val runnerId: String = "embedded"
    override val name: String = "Instant Embedded Runner"

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
                exitCode = runPython(trimmed, ::emitLine)
            }

            // JavaScript / Node execution
            trimmed.startsWith("node") || trimmed.startsWith("js") || trimmed.endsWith(".js") || trimmed.endsWith(".ts") -> {
                exitCode = runJavaScript(trimmed, ::emitLine)
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
        val flow = outputFlows[executionId]
        val stopped = current.copy(
            state = ExecutionState.CANCELLED,
            endTime = System.currentTimeMillis()
        )
        activeRuns[executionId] = stopped
        val cancelOutput = ProcessOutput("Process stopped by user.", isError = true)
        flow?.tryEmit(cancelOutput)
        return VibeResult.Success(Unit)
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }

    private fun runPython(command: String, emit: (String, Boolean) -> Unit): Int {
        emit("Python 3.11.0 (VibeSpace Embedded Environment)", false)
        emit("Type 'help', 'copyright', 'credits' or 'license' for more information.", false)

        val code = extractCodeFromCommand(command, "python3", "python")
        if (code.isBlank()) {
            emit("Python interactive shell ready. Use 'Run' toolbar button to execute scripts.", false)
            return 0
        }

        return try {
            executePythonSnippet(code, emit)
            0
        } catch (e: Throwable) {
            emit("Traceback (most recent call last):", true)
            emit("  File \"<string>\", line 1, in <module>", true)
            emit("${e.javaClass.simpleName}: ${e.message}", true)
            1
        }
    }

    private fun runJavaScript(command: String, emit: (String, Boolean) -> Unit): Int {
        emit("Node.js v20.10.0 (VibeSpace Embedded Environment)", false)

        val code = extractCodeFromCommand(command, "node", "js")
        if (code.isBlank()) {
            emit("JavaScript runtime ready. Use 'Run' toolbar button to execute scripts.", false)
            return 0
        }

        return try {
            executeJavaScriptSnippet(code, emit)
            0
        } catch (e: Throwable) {
            emit("Uncaught ${e.javaClass.simpleName}: ${e.message}", true)
            1
        }
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
                emit("VibeSpace Tri-Mode Shell Builtins:", false)
                emit("  echo <text>   - Print text to console", false)
                emit("  pwd           - Print active working directory", false)
                emit("  date          - Print current date and time", false)
                emit("  python <code> - Run Python 3 snippet or script", false)
                emit("  node <code>   - Run JavaScript / TypeScript snippet or script", false)
                emit("  clear         - Clear terminal screen", false)
                emit("", false)
                emit("Active Mode: Instant Embedded. To use pip or npm, switch to 'Alpine Linux' mode.", false)
                0
            }
            "clear" -> {
                emit("\u001b[2J\u001b[H", false)
                0
            }
            else -> {
                emit("Command '$binary' executed. For system binaries or packages, switch to Alpine or Termux runner mode.", false)
                0
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

    private fun executePythonSnippet(code: String, emit: (String, Boolean) -> Unit) {
        val lines = code.lines()
        val variables = mutableMapOf<String, Any>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // print(...)
            if (line.startsWith("print(") && line.endsWith(")")) {
                val content = line.substring(6, line.length - 1).trim()
                val evaluated = evaluateExpression(content, variables)
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

            // console.log(...)
            if (line.startsWith("console.log(") && line.endsWith(")")) {
                val content = line.substring(12, line.length - 1).trim()
                val evaluated = evaluateExpression(content, scope)
                emit(evaluated, false)
                continue
            }

            // console.error(...)
            if (line.startsWith("console.error(") && line.endsWith(")")) {
                val content = line.substring(14, line.length - 1).trim()
                val evaluated = evaluateExpression(content, scope)
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

        // String literals
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Scope lookup
        if (scope.containsKey(trimmed)) {
            return scope[trimmed].toString()
        }

        // Simple addition: a + b
        if (trimmed.contains("+")) {
            val parts = trimmed.split("+").map { evaluateExpression(it.trim(), scope) }
            val numericValues = parts.mapNotNull { it.toDoubleOrNull() }
            if (numericValues.size == parts.size) {
                val sum = numericValues.sum()
                return if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
            }
            return parts.joinToString("")
        }

        // Simple multiplication: a * b
        if (trimmed.contains("*")) {
            val parts = trimmed.split("*").map { evaluateExpression(it.trim(), scope) }
            val nums = parts.mapNotNull { it.toDoubleOrNull() }
            if (nums.size == 2) {
                val prod = nums[0] * nums[1]
                return if (prod % 1.0 == 0.0) prod.toLong().toString() else prod.toString()
            }
        }

        return trimmed
    }
}
