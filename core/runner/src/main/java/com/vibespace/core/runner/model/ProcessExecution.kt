package com.vibespace.core.runner.model

enum class ExecutionState {
    IDLE,
    STARTING,
    RUNNING,
    /** The command was handed to an external process manager and can no longer be observed here. */
    DISPATCHED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class ProcessOutput(
    val line: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProcessExecution(
    val executionId: String,
    val command: String,
    val workingDirectoryUri: String,
    val state: ExecutionState = ExecutionState.IDLE,
    val exitCode: Int? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val outputs: List<ProcessOutput> = emptyList()
)
