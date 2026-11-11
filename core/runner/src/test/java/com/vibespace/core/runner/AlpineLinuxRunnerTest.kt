package com.vibespace.core.runner

import com.vibespace.core.runner.alpine.AlpineInstallationState
import com.vibespace.core.runner.alpine.AlpineLinuxRunner
import com.vibespace.core.runner.alpine.AlpineProcessExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.GZIPOutputStream

class AlpineLinuxRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestTarGz(destFile: File) {
        destFile.parentFile?.mkdirs()
        GZIPOutputStream(FileOutputStream(destFile)).use { gzos ->
            writeTarEntry(gzos, "etc/alpine-release", "3.19.1\n".toByteArray())
            writeTarEntry(gzos, "bin/sh", "#!/bin/sh\n".toByteArray())
            gzos.write(ByteArray(1024))
        }
    }

    private fun writeTarEntry(out: OutputStream, name: String, content: ByteArray) {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)

        val mode = "0000755\u0000".toByteArray(Charsets.US_ASCII)
        System.arraycopy(mode, 0, header, 100, mode.size)

        val sizeStr = String.format(Locale.US, "%011o ", content.size).toByteArray(Charsets.US_ASCII)
        System.arraycopy(sizeStr, 0, header, 124, sizeStr.size)

        header[156] = '0'.code.toByte()

        val magic = "ustar\u000000".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, header, 257, magic.size)

        for (i in 148 until 156) {
            header[i] = ' '.code.toByte()
        }
        var sum = 0
        for (b in header) {
            sum += (b.toInt() and 0xFF)
        }
        val chkStr = String.format(Locale.US, "%06o\u0000 ", sum).toByteArray(Charsets.US_ASCII)
        System.arraycopy(chkStr, 0, header, 148, chkStr.size)

        out.write(header)
        out.write(content)
        val pad = (512 - (content.size % 512)) % 512
        if (pad in 1..511) {
            out.write(ByteArray(pad.toInt()))
        }
    }

    @Test
    fun installSubsystem_without_proot_does_not_download_rootfs() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine")
        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            downloadTransport = { _, destFile, progress ->
                createTestTarGz(destFile)
                progress(1.0f)
            }
        )

        assertEquals(AlpineInstallationState.NotInstalled, runner.installationState.value)

        val installResult = runner.installSubsystem()
        assertTrue(installResult is com.vibespace.core.common.result.VibeResult.Error)
        assertTrue(runner.installationState.value is AlpineInstallationState.Error)
        assertTrue(!runner.getRootfsDir().exists())
    }

    @Test
    fun installSubsystem_without_proot_reports_error_without_network_access() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_fail")
        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            downloadTransport = { _, _, _ ->
                throw java.io.IOException("Network unavailable")
            }
        )

        val installResult = runner.installSubsystem()
        assertTrue(installResult is com.vibespace.core.common.result.VibeResult.Error)
        assertTrue(runner.installationState.value is AlpineInstallationState.Error)
    }

    @Test
    fun execute_when_proot_is_missing_returns_friendly_guidance() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_uninstalled")
        val runner = AlpineLinuxRunner(rootDirectory = testBaseDir)

        val result = runner.execute("pip install numpy", "/")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(127, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("PRoot executable is missing") })
    }

    @Test
    fun execute_does_not_claim_installation_without_proot() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_no_proot")
        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            downloadTransport = { _, destFile, progress ->
                createTestTarGz(destFile)
                progress(1.0f)
            }
        )
        val install = runner.installSubsystem()
        assertTrue(install is com.vibespace.core.common.result.VibeResult.Error)

        val result = runner.execute("uname -a", "/")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(127, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("PRoot executable is missing") })
    }

    @Test
    fun execute_with_process_executor_dispatches_real_command() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_exec")
        val fakeExecutor = AlpineProcessExecutor { cmd, _, _, onLine ->
            onLine("Linux vibespace-proot 6.1.0-android #1 PREEMPT", false)
            0
        }
        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            downloadTransport = { _, destFile, progress ->
                createTestTarGz(destFile)
                progress(1.0f)
            },
            processExecutor = fakeExecutor
        )
        runner.installSubsystem()

        val result = runner.execute("uname -a", "/")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("vibespace-proot") })
    }
}
