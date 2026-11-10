package com.vibespace.core.runner

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.runner.manager.DefaultRunnerManager
import com.vibespace.core.runner.model.ExecutionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRunnerManagerTest {

    private val runnerManager = DefaultRunnerManager(dispatchers = TestDispatcherProvider())

    @Test
    fun runCommand_creates_execution_and_finishes_safely() = runTest {
        val result = runnerManager.runCommand("echo test", "vibe://projects/test")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()
        assertNotNull(exec)
        assertEquals(ExecutionState.SUCCEEDED, exec!!.state)
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.isNotEmpty())
    }

    @Test
    fun stopExecution_cancels_active_execution() = runTest {
        val runResult = runnerManager.runCommand("sleep 10", "vibe://projects/test")
        val execId = runResult.getOrNull()!!.executionId

        val stopResult = runnerManager.stopExecution(execId)
        assertTrue(stopResult.isSuccess)

        val updated = runnerManager.activeExecutions.value.first { it.executionId == execId }
        assertEquals(ExecutionState.CANCELLED, updated.state)
    }
}
