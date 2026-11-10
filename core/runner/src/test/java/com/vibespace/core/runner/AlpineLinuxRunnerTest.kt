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
}
