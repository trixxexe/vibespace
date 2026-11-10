package com.vibespace.core.runner.manager

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * RunnerManager contract orchestrating language runtimes, execution tasks, and terminal links.
 */
interface RunnerManager {
    val activeExecutions: StateFlow<List<ProcessExecution>>

    fun registerRunner(runner: ExternalRunner)
    fun getAvailableRunners(): List<ExternalRunner>

    suspend fun runCommand(
        command: String,
        workingDirectory: String,
        preferredRunnerId: String? = null
    ): VibeResult<ProcessExecution>

    suspend fun stopExecution(executionId: String): VibeResult<Unit>
    fun observeOutput(executionId: String): Flow<ProcessOutput>
}
