package com.vibespace.core.runner

import com.vibespace.core.runner.alpine.AlpineInstallationState
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlpineLinuxRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun installSubsystem_extracts_rootfs_and_marks_installed() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine")
        val runner = AlpineLinuxRunner(rootDirectory = testBaseDir)

        assertEquals(AlpineInstallationState.NotInstalled, runner.installationState.value)

        val installResult = runner.installSubsystem()
        assertTrue(installResult.isSuccess)
        assertTrue(runner.installationState.value is AlpineInstallationState.Installed)
        assertTrue(runner.isAvailable())

        // Verify rootfs was physically extracted to disk
        val rootfsDir = runner.getRootfsDir()
        assertTrue(rootfsDir.exists())
        val releaseFile = File(rootfsDir, "etc/alpine-release")
        assertTrue(releaseFile.exists())
        val releaseText = releaseFile.readText()
        assertTrue(releaseText.contains("3.19.1"))

        val shFile = File(rootfsDir, "bin/sh")
        assertTrue(shFile.exists())
    }

    @Test
    fun execute_when_not_installed_returns_friendly_guidance() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_uninstalled")
        val runner = AlpineLinuxRunner(rootDirectory = testBaseDir)

        val result = runner.execute("pip install numpy", "/")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(127, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("Alpine Linux Subsystem Not Installed") })
    }

    @Test
    fun execute_when_installed_runs_pip_and_system_queries() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_installed")
        val runner = AlpineLinuxRunner(rootDirectory = testBaseDir)
        runner.installSubsystem()

        // Test pip install
        val pipResult = runner.execute("pip install flask", "/")
        assertTrue(pipResult.isSuccess)
        val pipExec = pipResult.getOrNull()!!
        assertEquals(0, pipExec.exitCode)
        assertTrue(pipExec.outputs.any { it.line.contains("Successfully installed flask") })

        // Test pip list
        val listResult = runner.execute("pip list", "/")
        assertTrue(listResult.isSuccess)
        val listExec = listResult.getOrNull()!!
        assertEquals(0, listExec.exitCode)
        assertTrue(listExec.outputs.any { it.line.contains("flask") })

        // Test uname -a
        val unameResult = runner.execute("uname -a", "/")
        assertTrue(unameResult.isSuccess)
        val unameExec = unameResult.getOrNull()!!
        assertEquals(0, unameExec.exitCode)
        assertTrue(unameExec.outputs.any { it.line.contains("Linux") })

        // Test python --version
        val pyResult = runner.execute("python --version", "/")
        assertTrue(pyResult.isSuccess)
        val pyExec = pyResult.getOrNull()!!
        assertEquals(0, pyExec.exitCode)
        assertTrue(pyExec.outputs.any { it.line.contains("Python 3.11") })
    }
}
