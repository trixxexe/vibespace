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
        val fakeExecutor = com.vibespace.core.runner.alpine.AlpineProcessExecutor { command, workingDir, _, onLine ->
            if (command.contains("/sbin/apk")) {
                val script = java.io.File(workingDir, "usr/bin/script")
                script.parentFile?.mkdirs()
                script.writeText("#!/bin/sh\n")
                script.setExecutable(true, true)
            }
            onLine("Successfully installed requests", false)
            0
        }
        val customAlpine = AlpineLinuxRunner(
            downloadTransport = { _, destFile, progress ->
                destFile.parentFile?.mkdirs()
                java.util.zip.GZIPOutputStream(java.io.FileOutputStream(destFile)).use { gzos ->
                    val header = ByteArray(512)
                    val nameBytes = "etc/alpine-release".toByteArray(Charsets.US_ASCII)
                    System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
                    val mode = "0000755\u0000".toByteArray(Charsets.US_ASCII)
                    System.arraycopy(mode, 0, header, 100, mode.size)
                    val content = "3.19.1\n".toByteArray()
                    val sizeStr = String.format(java.util.Locale.US, "%011o ", content.size).toByteArray(Charsets.US_ASCII)
                    System.arraycopy(sizeStr, 0, header, 124, sizeStr.size)
                    header[156] = '0'.code.toByte()
                    val magic = "ustar\u000000".toByteArray(Charsets.US_ASCII)
                    System.arraycopy(magic, 0, header, 257, magic.size)
                    for (i in 148 until 156) header[i] = ' '.code.toByte()
                    var sum = 0
                    for (b in header) sum += (b.toInt() and 0xFF)
                    val chkStr = String.format(java.util.Locale.US, "%06o\u0000 ", sum).toByteArray(Charsets.US_ASCII)
                    System.arraycopy(chkStr, 0, header, 148, chkStr.size)
                    gzos.write(header)
                    gzos.write(content)
                    val pad = (512 - (content.size % 512)) % 512
                    if (pad in 1..511) gzos.write(ByteArray(pad))
                    gzos.write(ByteArray(1024))
                }
                progress(1.0f)
            },
            prootArchitecture = "aarch64",
            processExecutor = fakeExecutor
        )
        val manager = DefaultRunnerManager(
            dispatchers = TestDispatcherProvider(),
            alpineRunner = customAlpine
        )
        manager.setRunnerMode(RunnerMode.ALPINE)
        assertEquals(RunnerMode.ALPINE, manager.activeRunnerMode.value)

        val installResult = customAlpine.installSubsystem()
        assertTrue(installResult.isSuccess)

        val runResult = manager.runCommand("pip install requests", "vibe://projects/test")
        assertTrue(runResult.isSuccess)
        val exec = runResult.getOrNull()
        assertTrue(exec!!.outputs.any { it.line.contains("Successfully installed requests") })
    }

    @Test
    fun stopExecution_does_not_mark_a_completed_execution_as_cancelled() = runTest {
        val runResult = runnerManager.runCommand("echo running", "vibe://projects/test")
        val execId = runResult.getOrNull()!!.executionId

        val stopResult = runnerManager.stopExecution(execId)
        assertTrue(stopResult.isError)

        val updated = runnerManager.activeExecutions.value.first { it.executionId == execId }
        assertEquals(ExecutionState.SUCCEEDED, updated.state)
    }

    @Test
    fun embeddedRunner_executes_python_file_from_workspace() = runTest {
        val workspace = com.vibespace.core.workspace.manager.DefaultWorkspaceManager(dispatchers = TestDispatcherProvider())
        val project = com.vibespace.core.workspace.model.Project("p1", "TestPy", "vibe://projects/p1")
        workspace.openProject(project)
        workspace.createFile(project.rootUri, "main.py", "print(\"Welcome to VibeSpace Mobile!\")")

        val managerWithWorkspace = DefaultRunnerManager(
            workspaceManager = workspace,
            dispatchers = TestDispatcherProvider()
        )

        val runResult = managerWithWorkspace.runCommand("python \"main.py\"", "vibe://projects/p1")
        assertTrue(runResult.isSuccess)
        val exec = runResult.getOrNull()
        assertNotNull(exec)
        assertEquals(ExecutionState.SUCCEEDED, exec!!.state)
        assertTrue(exec.outputs.any { it.line.contains("Welcome to VibeSpace Mobile!") })
    }
}
