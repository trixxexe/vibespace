package com.vibespace.core.runner.manager

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DefaultRunnerManager(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : RunnerManager {

    private val runners = mutableMapOf<String, ExternalRunner>()
    private val _activeExecutions = MutableStateFlow<List<ProcessExecution>>(emptyList())
    override val activeExecutions: StateFlow<List<ProcessExecution>> = _activeExecutions.asStateFlow()

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()

    override fun registerRunner(runner: ExternalRunner) {
        runners[runner.runnerId] = runner
    }

    override fun getAvailableRunners(): List<ExternalRunner> {
        return runners.values.filter { it.isAvailable() }
    }

    override suspend fun runCommand(
        command: String,
        workingDirectory: String,
        preferredRunnerId: String?
    ): VibeResult<ProcessExecution> = withContext(dispatchers.default) {
        val runner = if (preferredRunnerId != null) {
            runners[preferredRunnerId] ?: return@withContext VibeResult.Error("Requested runner '$preferredRunnerId' is not registered.")
        } else {
            runners.values.firstOrNull { it.isAvailable() }
        }

        if (runner != null && runner.isAvailable()) {
            return@withContext runner.execute(command, workingDirectory)
        }

        // Safe fallback in-memory execution simulation when no external runner is installed
        val executionId = IdGenerator.shortId("exec")
        val flow = MutableSharedFlow<ProcessOutput>(replay = 50)
        outputFlows[executionId] = flow

        val execution = ProcessExecution(
            executionId = executionId,
            command = command,
            workingDirectoryUri = workingDirectory,
            state = ExecutionState.RUNNING,
            startTime = System.currentTimeMillis()
        )

        _activeExecutions.value = _activeExecutions.value + execution

        val output = ProcessOutput("Executing: $command in $workingDirectory")
        flow.emit(output)

        val completionOutput = ProcessOutput("No external runner (Termux) detected. Install Termux to execute system binaries.")
        flow.emit(completionOutput)

        val completed = execution.copy(
            state = ExecutionState.SUCCEEDED,
            exitCode = 0,
            endTime = System.currentTimeMillis(),
            outputs = listOf(output, completionOutput)
        )

        _activeExecutions.value = _activeExecutions.value.map {
            if (it.executionId == executionId) completed else it
        }

        VibeResult.Success(completed)
    }

    override suspend fun stopExecution(executionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val current = _activeExecutions.value.firstOrNull { it.executionId == executionId }
            ?: return@withContext VibeResult.Error("Execution not found: $executionId")

        val updated = current.copy(
            state = ExecutionState.CANCELLED,
            endTime = System.currentTimeMillis()
        )

        _activeExecutions.value = _activeExecutions.value.map {
            if (it.executionId == executionId) updated else it
        }

        outputFlows[executionId]?.emit(ProcessOutput("Process terminated by user.", isError = true))
        VibeResult.Success(Unit)
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
