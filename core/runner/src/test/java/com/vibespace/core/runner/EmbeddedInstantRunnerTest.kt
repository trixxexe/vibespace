package com.vibespace.core.runner

import com.vibespace.core.runner.embedded.EmbeddedInstantRunner
import com.vibespace.core.runner.model.ExecutionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedInstantRunnerTest {

    private val runner = EmbeddedInstantRunner()

    @Test
    fun runner_name_accurately_reflects_instant_evaluator() {
        assertEquals("Instant Evaluator (Basic Print & Arithmetic)", runner.name)
        assertTrue(runner.isAvailable())
    }

    @Test
    fun unknown_shell_command_returns_exit_code_127() = runTest {
        val result = runner.execute("curl https://example.com", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(127, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("sh: curl: command not found") })
    }

    @Test
    fun built_in_shell_commands_succeed() = runTest {
        val echoResult = runner.execute("echo Hello Mobile IDE", "/workspace")
        assertTrue(echoResult.isSuccess)
        val echoExec = echoResult.getOrNull()!!
        assertEquals(0, echoExec.exitCode)
        assertTrue(echoExec.outputs.any { it.line == "Hello Mobile IDE" })

        val pwdResult = runner.execute("pwd", "/workspace/src")
        assertTrue(pwdResult.isSuccess)
        val pwdExec = pwdResult.getOrNull()!!
        assertEquals(0, pwdExec.exitCode)
        assertTrue(pwdExec.outputs.any { it.line == "/workspace/src" })
    }

    @Test
    fun built_in_extended_shell_commands_succeed() = runTest {
        val unameResult = runner.execute("uname", "/workspace")
        assertTrue(unameResult.isSuccess)
        assertEquals(0, unameResult.getOrNull()!!.exitCode)

        val whoamiResult = runner.execute("whoami", "/workspace")
        assertTrue(whoamiResult.isSuccess)
        assertEquals(0, whoamiResult.getOrNull()!!.exitCode)

        val whichResult = runner.execute("which ls", "/workspace")
        assertTrue(whichResult.isSuccess)
        assertEquals(0, whichResult.getOrNull()!!.exitCode)
    }

    @Test
    fun unsupported_python_constructs_return_exit_code_1_with_clear_notice() = runTest {
        val result = runner.execute("python -c import math", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("unsupported in Instant Evaluator") })
    }

    @Test
    fun supported_python_print_and_arithmetic_succeed() = runTest {
        val result = runner.execute("python -c print(10 + 25)", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.SUCCEEDED, exec.state)
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("35") })
    }

    @Test
    fun unsupported_javascript_constructs_return_exit_code_1() = runTest {
        val result = runner.execute("node -e require('fs')", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("unsupported in Instant Evaluator") })
    }

    @Test
    fun supported_javascript_console_log_succeeds() = runTest {
        val result = runner.execute("node -e console.log(\"VibeSpace JS\")", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.SUCCEEDED, exec.state)
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("VibeSpace JS") })
    }

    @Test
    fun python_module_execution_returns_exit_code_1_and_guides_to_full_runtime() = runTest {
        val result = runner.execute("python -m pip install requests", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Running Python modules ('-m pip') is unsupported in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun python_generic_module_flag_returns_exit_code_1() = runTest {
        val result = runner.execute("python -m unittest", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Running Python modules ('-m unittest') is unsupported in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Alpine Linux") })
    }

    @Test
    fun direct_pip_invocation_returns_exit_code_127_and_guides_to_full_runtime() = runTest {
        val result = runner.execute("pip install requests", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(127, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("'pip' package manager is unavailable in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun direct_npm_invocation_returns_exit_code_127_and_guides_to_full_runtime() = runTest {
        val result = runner.execute("npm install express", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(127, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("'npm' package manager is unavailable in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun other_package_managers_return_exit_code_127_and_guide_to_full_runtime() = runTest {
        for (pkgTool in listOf("pip3", "npx", "yarn", "pnpm", "cargo", "gem", "go", "apk", "apt")) {
            val result = runner.execute("$pkgTool build", "/workspace")
            assertTrue(result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals(ExecutionState.FAILED, exec.state)
            assertEquals(127, exec.exitCode)
            assertTrue(exec.outputs.any { it.isError && it.line.contains("unavailable in Instant Evaluator") })
            assertTrue(exec.outputs.any { it.isError && it.line.contains("Alpine Linux") })
        }
    }

    @Test
    fun python_version_flags_return_exit_code_0_and_version_string() = runTest {
        for (flag in listOf("-V", "--version", "-v")) {
            val result = runner.execute("python $flag", "/workspace")
            assertTrue(result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals(ExecutionState.SUCCEEDED, exec.state)
            assertEquals(0, exec.exitCode)
            assertTrue(exec.outputs.any { !it.isError && it.line.contains("Python 3.12 (VibeSpace Instant Evaluator)") })
        }
    }

    @Test
    fun node_version_flags_return_exit_code_0_and_version_string() = runTest {
        for (flag in listOf("-v", "-V", "--version")) {
            val result = runner.execute("node $flag", "/workspace")
            assertTrue(result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals(ExecutionState.SUCCEEDED, exec.state)
            assertEquals(0, exec.exitCode)
            assertTrue(exec.outputs.any { !it.isError && it.line.contains("Node.js v20.18.0 (VibeSpace Instant Evaluator)") })
        }
    }
}
