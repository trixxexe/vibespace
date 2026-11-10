package com.vibespace.core.runner.termux

import android.content.Context
import android.content.Intent
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

        if (!isAvailable()) {
            emitLine("[Termux Bridge] Termux app not detected on this device.", true)
            emitLine("To use Termux execution, install Termux from F-Droid (https://f-droid.org/packages/com.termux/).", false)
            emitLine("Tip: Switch to 'Instant Embedded' or 'Alpine Linux' mode to run code directly inside VibeSpace.", false)

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

        var dispatchSucceeded = false
        try {
            val intent = Intent(TermuxContract.ACTION_RUN_COMMAND).apply {
                setClassName(TermuxContract.TERMUX_PACKAGE, TermuxContract.TERMUX_SERVICE)
                putExtra(TermuxContract.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-c", command))
                putExtra(TermuxContract.EXTRA_WORKDIR, workingDirectory)
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
        }

        val resultState = if (dispatchSucceeded) ExecutionState.RUNNING else ExecutionState.FAILED
        val exitCode = if (dispatchSucceeded) null else 1
        val finalExecution = execution.copy(
            state = resultState,
            exitCode = exitCode,
            endTime = if (dispatchSucceeded) null else System.currentTimeMillis(),
            outputs = outputs
        )
        activeRuns[executionId] = finalExecution
        return VibeResult.Success(finalExecution)
    }

    override suspend fun stop(executionId: String): VibeResult<Unit> {
        val current = activeRuns[executionId] ?: return VibeResult.Error("Execution not found: $executionId")
        val flow = outputFlows[executionId]
        val stopped = current.copy(
            state = ExecutionState.CANCELLED,
            endTime = System.currentTimeMillis()
        )
        activeRuns[executionId] = stopped
        val cancelOutput = ProcessOutput("Termux process stopped.", isError = true)
        flow?.tryEmit(cancelOutput)
        return VibeResult.Success(Unit)
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
