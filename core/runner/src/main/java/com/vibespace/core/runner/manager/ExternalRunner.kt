package com.vibespace.core.runner.manager

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import kotlinx.coroutines.flow.Flow

/**
 * Runner plug-in contract for specific execution targets (Termux, PRoot, Docker remote, local sandboxes).
 * Designed for Developer 4 (Execution Engineer).
 */
interface ExternalRunner {
    val runnerId: String
    val name: String
    fun isAvailable(): Boolean

    suspend fun execute(
        command: String,
        workingDirectory: String,
        environmentVariables: Map<String, String> = emptyMap()
    ): VibeResult<ProcessExecution>

    suspend fun stop(executionId: String): VibeResult<Unit>
    fun observeOutput(executionId: String): Flow<ProcessOutput>
}
