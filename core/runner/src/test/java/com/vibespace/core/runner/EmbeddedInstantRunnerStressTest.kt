package com.vibespace.core.runner

import com.vibespace.core.runner.embedded.EmbeddedInstantRunner
import com.vibespace.core.runner.model.ExecutionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial stress tests for EmbeddedInstantRunner.
 * Verifies package/module interception, version flags, shell fallbacks, and instant snippet execution.
 */
class EmbeddedInstantRunnerStressTest {

    private val runner = EmbeddedInstantRunner()

    @Test
    fun stress_test_python_m_pip_install_with_quiet_flag() = runTest {
        val result = runner.execute("python -m pip install requests -q", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Running Python modules ('-m pip') is unsupported in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun stress_test_python3_m_pip_install() = runTest {
        val result = runner.execute("python3 -m pip install requests", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Running Python modules ('-m pip') is unsupported in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun stress_test_python_m_venv() = runTest {
        val result = runner.execute("python -m venv .venv", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Running Python modules ('-m venv') is unsupported in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun stress_test_python3_m_venv() = runTest {
        val result = runner.execute("python3 -m venv .venv", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(1, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Running Python modules ('-m venv') is unsupported in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Alpine Linux") && it.line.contains("Termux") })
    }

    @Test
    fun stress_test_pip_install_upgrade_pip() = runTest {
        val result = runner.execute("pip install --upgrade pip", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(127, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("'pip' package manager is unavailable in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun stress_test_pip3_install_upgrade() = runTest {
        val result = runner.execute("pip3 install --upgrade requests", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(127, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("'pip3' package manager is unavailable in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun stress_test_npm_run_build() = runTest {
        val result = runner.execute("npm run build", "/workspace")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(ExecutionState.FAILED, exec.state)
        assertEquals(127, exec.exitCode)
        assertNotEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.isError && it.line.contains("'npm' package manager is unavailable in Instant Evaluator") })
        assertTrue(exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
    }

    @Test
    fun stress_test_various_package_managers_and_tools() = runTest {
        val testCases = listOf(
            "yarn build" to "yarn",
            "pnpm install" to "pnpm",
            "npx create-react-app app" to "npx",
            "cargo test" to "cargo",
            "gem install bundler" to "gem",
            "go run main.go" to "go",
            "apk add python3" to "apk",
            "apt install build-essential" to "apt"
        )
        for ((cmd, tool) in testCases) {
            val result = runner.execute(cmd, "/workspace")
            assertTrue(result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals("Failed for command: $cmd", ExecutionState.FAILED, exec.state)
            assertEquals("Failed for command: $cmd", 127, exec.exitCode)
            assertNotEquals("Failed for command: $cmd", 0, exec.exitCode)
            assertTrue("Expected tool '$tool' in output for: $cmd", exec.outputs.any { it.isError && it.line.contains("'$tool'") })
            assertTrue("Expected guidance in output for: $cmd", exec.outputs.any { it.isError && it.line.contains("Switch runner mode to 'Alpine Linux' or 'Termux'") })
        }
    }

    @Test
    fun stress_test_python_and_node_version_flags() = runTest {
        val pythonCases = listOf("python -V", "python --version", "python -v", "python3 -V", "python3 --version", "python3 -v")
        for (cmd in pythonCases) {
            val result = runner.execute(cmd, "/workspace")
            assertTrue("Failed for $cmd", result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals("Exit code 0 for $cmd", 0, exec.exitCode)
            assertEquals("State SUCCEEDED for $cmd", ExecutionState.SUCCEEDED, exec.state)
            assertTrue("Output contains Python version for $cmd", exec.outputs.any { it.line.contains("Python 3.12 (VibeSpace Instant Evaluator)") })
        }

        val nodeCases = listOf("node -v", "node -V", "node --version", "js -v", "js -V", "js --version")
        for (cmd in nodeCases) {
            val result = runner.execute(cmd, "/workspace")
            assertTrue("Failed for $cmd", result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals("Exit code 0 for $cmd", 0, exec.exitCode)
            assertEquals("State SUCCEEDED for $cmd", ExecutionState.SUCCEEDED, exec.state)
            assertTrue("Output contains Node version for $cmd", exec.outputs.any { it.line.contains("Node.js v20.18.0 (VibeSpace Instant Evaluator)") })
        }
    }

    @Test
    fun stress_test_arbitrary_shell_commands() = runTest {
        val arbitraryCommands = listOf(
            "ls -la",
            "cat /etc/passwd",
            "curl -X GET https://example.com",
            "wget https://example.com/file",
            "rm -rf /",
            "git status",
            "unknown_command_12345 foo bar"
        )
        for (cmd in arbitraryCommands) {
            val result = runner.execute(cmd, "/workspace")
            assertTrue("Failed for $cmd", result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals("Exit code 127 for $cmd", 127, exec.exitCode)
            assertEquals("State FAILED for $cmd", ExecutionState.FAILED, exec.state)
            val binary = cmd.split(" ").first()
            assertTrue("Error output contains command not found for $cmd", exec.outputs.any { it.isError && it.line.contains("sh: $binary: command not found") })
        }
    }

    @Test
    fun stress_test_safe_shell_builtins() = runTest {
        val echoRes = runner.execute("echo test 123", "/workspace")
        assertEquals(0, echoRes.getOrNull()!!.exitCode)
        assertTrue(echoRes.getOrNull()!!.outputs.any { it.line == "test 123" })

        val pwdRes = runner.execute("pwd", "/custom/work/dir")
        assertEquals(0, pwdRes.getOrNull()!!.exitCode)
        assertTrue(pwdRes.getOrNull()!!.outputs.any { it.line == "/custom/work/dir" })

        val dateRes = runner.execute("date", "/workspace")
        assertEquals(0, dateRes.getOrNull()!!.exitCode)

        val helpRes = runner.execute("help", "/workspace")
        assertEquals(0, helpRes.getOrNull()!!.exitCode)
        assertTrue(helpRes.getOrNull()!!.outputs.any { it.line.contains("VibeSpace Safe Command Built-ins:") })

        val clearRes = runner.execute("clear", "/workspace")
        assertEquals(0, clearRes.getOrNull()!!.exitCode)
    }

    @Test
    fun stress_test_basic_arithmetic_and_instant_python_snippets() = runTest {
        // print("hello")
        val printRes = runner.execute("python -c print(\"hello\")", "/workspace")
        assertTrue(printRes.isSuccess)
        val printExec = printRes.getOrNull()!!
        assertEquals(0, printExec.exitCode)
        assertEquals(ExecutionState.SUCCEEDED, printExec.state)
        assertTrue(printExec.outputs.any { it.line == "hello" })

        // Single quoted print
        val printSingleRes = runner.execute("python -c print('hello')", "/workspace")
        assertTrue(printSingleRes.isSuccess)
        val printSingleExec = printSingleRes.getOrNull()!!
        assertEquals(0, printSingleExec.exitCode)
        assertTrue(printSingleExec.outputs.any { it.line == "hello" })

        // Arithmetic: x = 1 + 2
        val multiLineScript = "python -c x = 1 + 2\nprint(x)"
        val multiLineRes = runner.execute(multiLineScript, "/workspace")
        assertTrue(multiLineRes.isSuccess)
        val multiLineExec = multiLineRes.getOrNull()!!
        assertEquals(0, multiLineExec.exitCode)
        assertTrue(multiLineExec.outputs.any { it.line == "3" })

        // Operations: +, -, *, /
        val mathRes = runner.execute("python -c print(10 + 20, 50 - 15, 6 * 7, 20 / 4)", "/workspace")
        assertTrue(mathRes.isSuccess)
        val mathExec = mathRes.getOrNull()!!
        assertEquals(0, mathExec.exitCode)
        assertTrue(mathExec.outputs.any { it.line.contains("30") && it.line.contains("35") && it.line.contains("42") && it.line.contains("5") })
    }

    @Test
    fun stress_test_basic_javascript_snippets() = runTest {
        val logRes = runner.execute("node -e console.log(\"hello\")", "/workspace")
        assertTrue(logRes.isSuccess)
        val logExec = logRes.getOrNull()!!
        assertEquals(0, logExec.exitCode)
        assertEquals(ExecutionState.SUCCEEDED, logExec.state)
        assertTrue(logExec.outputs.any { it.line == "hello" })

        val mathRes = runner.execute("node -e console.log(100 + 200)", "/workspace")
        assertTrue(mathRes.isSuccess)
        val mathExec = mathRes.getOrNull()!!
        assertEquals(0, mathExec.exitCode)
        assertTrue(mathExec.outputs.any { it.line == "300" })
    }

    @Test
    fun stress_test_python_unsupported_keywords_fail_with_guidance() = runTest {
        val keywords = listOf("import os", "from sys import version", "def foo():", "class Bar:", "while True:", "for i in range(1):", "with open('f'):", "try :")
        for (kw in keywords) {
            val result = runner.execute("python -c $kw", "/workspace")
            assertTrue("Result was error for $kw", result.isSuccess)
            val exec = result.getOrNull()!!
            assertEquals("Expected exitCode 1 for $kw", 1, exec.exitCode)
            assertEquals("Expected state FAILED for $kw", ExecutionState.FAILED, exec.state)
            assertTrue("Expected guidance for $kw", exec.outputs.any { it.isError && it.line.contains("Switch runner to 'Alpine Linux' or 'Termux'") })
        }
    }
}
