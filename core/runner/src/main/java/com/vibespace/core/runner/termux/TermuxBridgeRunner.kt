package com.vibespace.core.runner.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.manager.ExternalRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Runner that dispatches execution commands to Termux via the official RUN_COMMAND intent contract.
 * Allows VibeSpace to run native compilers, package managers, and binaries.
 * Accurately reports fire-and-forget state without fabricating synchronous completion.
 */
class TermuxBridgeRunner(
    private val context: Context? = null
) : ExternalRunner {

    override val runnerId: String = "termux"
    override val name: String = "Termux Bridge"

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val activeRuns = mutableMapOf<String, ProcessExecution>()

    override fun isAvailable(): Boolean {
        if (context == null) return false
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(TermuxContract.TERMUX_PACKAGE, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun execute(
        command: String,
        workingDirectory: String,
        environmentVariables: Map<String, String>
    ): VibeResult<ProcessExecution> {
        val executionId = IdGenerator.shortId("termux")
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

        val termuxContext = context
        if (termuxContext == null || !isAvailable()) {
            emitLine("[Termux Bridge] Termux app not detected on this device.", true)
            emitLine("To use Termux execution, install Termux from F-Droid (https://f-droid.org/packages/com.termux/).", false)
            emitLine("The embedded mode evaluates supported Python and JavaScript snippets. Set up Alpine Linux for the local Linux userspace runner.", false)

            val failed = execution.copy(
                state = ExecutionState.FAILED,
                exitCode = 1,
                endTime = System.currentTimeMillis(),
                outputs = outputs
            )
            activeRuns[executionId] = failed
            return VibeResult.Success(failed)
        }

        if (termuxContext.checkSelfPermission(TermuxContract.PERMISSION_RUN_COMMAND) != PackageManager.PERMISSION_GRANTED) {
            emitLine("VibeSpace is missing Termux's 'Run commands in Termux environment' permission.", true)
            emitLine("Grant it in Android Settings > Apps > VibeSpace > Permissions > Additional permissions.", false)
            val failed = execution.copy(
                state = ExecutionState.FAILED,
                exitCode = 1,
                endTime = System.currentTimeMillis(),
                outputs = outputs
            )
            activeRuns[executionId] = failed
            return VibeResult.Success(failed)
        }

        emitLine("[Termux Native Runner] Connecting to Termux service...", false)
        emitLine("[Termux RUN_COMMAND] $ $command", false)
        emitLine("Termux must have allow-external-apps=true in ~/.termux/termux.properties.", false)

        val termuxHome = "/data/data/${TermuxContract.TERMUX_PACKAGE}/files/home"
        val termuxWorkingDirectory = when {
            workingDirectory.isBlank() || workingDirectory == "~" -> termuxHome
            workingDirectory.startsWith("/data/data/${TermuxContract.TERMUX_PACKAGE}/") ||
                    workingDirectory.startsWith("/storage/emulated/0/") -> workingDirectory
            else -> {
                emitLine("Termux uses its own home directory; this VibeSpace project is not shared with Termux.", true)
                termuxHome
            }
        }

        var dispatchSucceeded = false
        try {
            val intent = Intent(TermuxContract.ACTION_RUN_COMMAND).apply {
                setClassName(TermuxContract.TERMUX_PACKAGE, TermuxContract.TERMUX_SERVICE)
                putExtra(TermuxContract.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-c", command))
                putExtra(TermuxContract.EXTRA_WORKDIR, termuxWorkingDirectory)
                putExtra(TermuxContract.EXTRA_BACKGROUND, true)
                putExtra(TermuxContract.EXTRA_SESSION_ACTION, "0")
            }
            if (context != null) {
                context.startService(intent)
                dispatchSucceeded = true
                emitLine("[Termux] Command dispatched to background Termux session.", false)
                emitLine("[Termux] Note: Fire-and-forget execution; command runs independently in Termux.", false)
            } else {
                emitLine("[Termux Bridge] Android context unavailable to send intent.", true)
            }
        } catch (e: Throwable) {
            emitLine("Failed to dispatch Termux command: ${e.message}", true)
            emitLine("Check the RUN_COMMAND permission and Termux's allow-external-apps setting.", false)
        }

        val resultState = if (dispatchSucceeded) ExecutionState.DISPATCHED else ExecutionState.FAILED
        val exitCode = if (dispatchSucceeded) null else 1
        val finalExecution = execution.copy(
            state = resultState,
            exitCode = exitCode,
            endTime = System.currentTimeMillis(),
            outputs = outputs
        )
        activeRuns[executionId] = finalExecution
        return VibeResult.Success(finalExecution)
    }

    override suspend fun stop(executionId: String): VibeResult<Unit> {
        val current = activeRuns[executionId] ?: return VibeResult.Error("Execution not found: $executionId")
        if (current.state != ExecutionState.RUNNING) {
            return VibeResult.Error("Termux execution is no longer running.")
        }
        return VibeResult.Error("Termux runs commands independently; VibeSpace cannot stop this process.")
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
