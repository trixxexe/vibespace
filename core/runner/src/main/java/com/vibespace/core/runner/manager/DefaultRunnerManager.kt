package com.vibespace.core.runner.manager

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.embedded.EmbeddedInstantRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import com.vibespace.core.runner.model.RunnerMode
import com.vibespace.core.runner.termux.TermuxBridgeRunner
import com.vibespace.core.workspace.manager.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefaultRunnerManager(
    private val workspaceManager: WorkspaceManager? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    alpineRunner: AlpineLinuxRunner? = null
) : RunnerManager {

    constructor(dispatchers: DispatcherProvider) : this(null, dispatchers, null)

    private val runners = mutableMapOf<String, ExternalRunner>()
    private val _activeExecutions = MutableStateFlow<List<ProcessExecution>>(emptyList())
    override val activeExecutions: StateFlow<List<ProcessExecution>> = _activeExecutions.asStateFlow()

    private val _activeRunnerMode = MutableStateFlow(RunnerMode.EMBEDDED)
    override val activeRunnerMode: StateFlow<RunnerMode> = _activeRunnerMode.asStateFlow()

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val runnerScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val collectionJobs = mutableMapOf<String, Job>()

    init {
        // Register out-of-the-box Tri-Mode Runners
        registerRunner(EmbeddedInstantRunner(workspaceManager = workspaceManager))
        registerRunner(alpineRunner ?: AlpineLinuxRunner())
        registerRunner(TermuxBridgeRunner())
    }

    override fun registerRunner(runner: ExternalRunner) {
        runners[runner.runnerId] = runner
    }

    override fun getAvailableRunners(): List<ExternalRunner> {
        return runners.values.toList()
    }

    override fun setRunnerMode(mode: RunnerMode) {
        _activeRunnerMode.value = mode
    }

    override fun getRunnerForMode(mode: RunnerMode): ExternalRunner? {
        return runners[mode.id]
    }

    override suspend fun runCommand(
        command: String,
        workingDirectory: String,
        preferredRunnerId: String?
    ): VibeResult<ProcessExecution> = withContext(dispatchers.default) {
        val targetRunnerId = preferredRunnerId ?: _activeRunnerMode.value.id
        val runner = runners[targetRunnerId] ?: runners["embedded"]
            ?: return@withContext VibeResult.Error("No runner available for target: $targetRunnerId")

        val result = runner.execute(command, workingDirectory)

        if (result is VibeResult.Success) {
            val execution = result.data
            _activeExecutions.value = _activeExecutions.value + execution

            // Pipe output from runner to output flow
            val combinedFlow = outputFlows.getOrPut(execution.executionId) { MutableSharedFlow(replay = 50) }
            val runnerOutputFlow = runner.observeOutput(execution.executionId)

            val job = runnerScope.launch {
                runnerOutputFlow.collect { chunk ->
                    combinedFlow.emit(chunk)
                }
            }
            collectionJobs[execution.executionId]?.cancel()
            collectionJobs[execution.executionId] = job
        }

        result
    }

    override suspend fun stopExecution(executionId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        val current = _activeExecutions.value.firstOrNull { it.executionId == executionId }
            ?: return@withContext VibeResult.Error("Execution not found: $executionId")

        // Cancel output collection job
        collectionJobs.remove(executionId)?.cancel()

        // Notify runners
        runners.values.forEach { it.stop(executionId) }

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
        val runner = runners[_activeRunnerMode.value.id]
        if (runner != null) {
            return runner.observeOutput(executionId)
        }
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
