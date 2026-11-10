package com.vibespace.core.runner

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.manager.DefaultRunnerManager
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.RunnerMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRunnerManagerTest {

    private val runnerManager = DefaultRunnerManager(dispatchers = TestDispatcherProvider())

    @Test
    fun triMode_runners_are_registered_by_default() {
        val runners = runnerManager.getAvailableRunners()
        assertEquals(3, runners.size)
        assertEquals(RunnerMode.EMBEDDED, runnerManager.activeRunnerMode.value)
    }

    @Test
    fun embeddedRunner_executes_python_print_statement() = runTest {
        val result = runnerManager.runCommand("python -c print(\"hello python\")", "vibe://projects/test")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()
        assertNotNull(exec)
        assertEquals(ExecutionState.SUCCEEDED, exec!!.state)
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("hello python") })
    }

    @Test
    fun embeddedRunner_executes_javascript_console_log() = runTest {
        val result = runnerManager.runCommand("node -e console.log(\"hello js\")", "vibe://projects/test")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()
        assertNotNull(exec)
        assertEquals(ExecutionState.SUCCEEDED, exec!!.state)
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("hello js") })
    }

    @Test
    fun runnerMode_can_be_switched_to_alpine() = runTest {
        runnerManager.setRunnerMode(RunnerMode.ALPINE)
        assertEquals(RunnerMode.ALPINE, runnerManager.activeRunnerMode.value)

        // Install alpine subsystem
        val alpineRunner = runnerManager.getRunnerForMode(RunnerMode.ALPINE) as AlpineLinuxRunner
        val installResult = alpineRunner.installSubsystem()
        assertTrue(installResult.isSuccess)

        // Execute pip install in alpine
        val runResult = runnerManager.runCommand("pip install requests", "vibe://projects/test")
        assertTrue(runResult.isSuccess)
        val exec = runResult.getOrNull()
        assertTrue(exec!!.outputs.any { it.line.contains("Successfully installed requests") })
    }

    @Test
    fun stopExecution_cancels_active_execution() = runTest {
        val runResult = runnerManager.runCommand("echo running", "vibe://projects/test")
        val execId = runResult.getOrNull()!!.executionId

        val stopResult = runnerManager.stopExecution(execId)
        assertTrue(stopResult.isSuccess)

        val updated = runnerManager.activeExecutions.value.first { it.executionId == execId }
        assertEquals(ExecutionState.CANCELLED, updated.state)
    }
}
