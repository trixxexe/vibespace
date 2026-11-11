package com.vibespace.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.manager.DefaultRunnerManager
import com.vibespace.core.runner.model.RunnerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the same bundled PRoot, Alpine rootfs, Python, Node, and PTY shipped in the APK. */
@RunWith(AndroidJUnit4::class)
class BundledRuntimeAcceptanceTest {
    private lateinit var runner: AlpineLinuxRunner
    private lateinit var manager: DefaultRunnerManager
    private lateinit var workspace: File
    private val sessionId = "runtime-acceptance"

    @Before
    fun installBundledRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtimeRoot = File(context.filesDir, "runtime-acceptance/alpine")
        runtimeRoot.deleteRecursively()
        runner = AlpineLinuxRunner(
            rootDirectory = runtimeRoot,
            prootBinaryPath = File(runtimeRoot, "bin/proot").absolutePath,
            prootArchitecture = "x86_64",
            bundledRuntimeArchiveProvider = { architecture ->
                if (architecture == "x86_64") context.assets.open("runtime/x86_64.tar.gz") else null
            }
        )
        val install = runner.installSubsystem()
        assertTrue("Bundled runtime failed to install: $install", install is VibeResult.Success)
        assertTrue("Python was not bundled", File(runner.getRootfsDir(), "usr/bin/python3").isFile)
        assertTrue("Node.js was not bundled", File(runner.getRootfsDir(), "usr/bin/node").isFile)

        workspace = File(context.filesDir, "runtime-acceptance/workspace").apply {
            deleteRecursively()
            resolve("src").mkdirs()
        }
        File(workspace, "src/main.py").writeText("print('NESTED_PYTHON_OK')\n")
        File(workspace, "src/main.js").writeText("console.log('NESTED_NODE_OK')\n")

        manager = DefaultRunnerManager(
            alpineRunner = runner,
            runnerWorkspaceDirectory = File(context.filesDir, "runtime-acceptance/mirrors")
        )
        manager.setRunnerMode(RunnerMode.ALPINE)
    }

    @After
    fun stopRuntime() = runBlocking {
        if (::manager.isInitialized) manager.closeInteractiveTerminal(sessionId)
    }

    @Test
    fun nestedPythonAndNodeFilesExecuteAndTerminalAcceptsInput() = runBlocking {
        val python = manager.runCommand("python3 '/workspace/src/main.py'", workspace.absolutePath, RunnerMode.ALPINE.id)
        val pythonExecution = (python as? VibeResult.Success)?.data
            ?: error("Python command failed: $python")
        assertEquals(0, pythonExecution.exitCode)
        assertTrue(pythonExecution.outputs.any { it.line.contains("NESTED_PYTHON_OK") })

        val node = manager.runCommand("node '/workspace/src/main.js'", workspace.absolutePath, RunnerMode.ALPINE.id)
        val nodeExecution = (node as? VibeResult.Success)?.data
            ?: error("Node command failed: $node")
        assertEquals(0, nodeExecution.exitCode)
        assertTrue(nodeExecution.outputs.any { it.line.contains("NESTED_NODE_OK") })

        val started = manager.startInteractiveTerminal(sessionId, workspace.absolutePath)
        assertTrue("Interactive shell failed to start: $started", started is VibeResult.Success)
        val output = manager.observeInteractiveOutput(sessionId)
        val sent = manager.sendTerminalInput(sessionId, "python3 -c \"print('PTY_INPUT_OK')\"")
        assertTrue("Terminal rejected input: $sent", sent is VibeResult.Success)
        val received = withTimeout(30_000) { output.first { it.line.contains("PTY_INPUT_OK") } }
        assertTrue(received.line.contains("PTY_INPUT_OK"))
    }
}
