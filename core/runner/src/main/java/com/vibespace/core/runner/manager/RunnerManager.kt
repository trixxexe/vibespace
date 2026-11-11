package com.vibespace.core.runner.manager

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import com.vibespace.core.runner.model.RunnerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * RunnerManager contract orchestrating language runtimes, execution tasks, and terminal links.
 * Coordinates embedded evaluation, an optional Alpine subsystem, and the Termux bridge.
 * Runtime availability depends on the current build and device configuration.
 */
interface RunnerManager {
    val activeExecutions: StateFlow<List<ProcessExecution>>
    val activeRunnerMode: StateFlow<RunnerMode>

    fun registerRunner(runner: ExternalRunner)
    fun getAvailableRunners(): List<ExternalRunner>
    fun setRunnerMode(mode: RunnerMode)
    fun getRunnerForMode(mode: RunnerMode): ExternalRunner?

    suspend fun runCommand(
        command: String,
        workingDirectory: String,
        preferredRunnerId: String? = null,
        sessionId: String? = null
    ): VibeResult<ProcessExecution>

    suspend fun stopExecution(executionId: String): VibeResult<Unit>
    fun observeOutput(executionId: String): Flow<ProcessOutput>
}
