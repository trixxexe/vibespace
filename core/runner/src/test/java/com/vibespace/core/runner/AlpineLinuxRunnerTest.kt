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
    fun installSubsystem_without_supported_device_abi_fails_before_download() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine")
        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            downloadTransport = { _, destFile, progress ->
                createTestTarGz(destFile)
                progress(1.0f)
            }
        )

        assertTrue(runner.installationState.value is AlpineInstallationState.NotInstalled)

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
        assertTrue(exec.outputs.any { it.line.contains("Alpine Linux Subsystem Not Installed") })
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
        assertTrue(exec.outputs.any { it.line.contains("No PRoot build is available for this device architecture") })
    }

    @Test
    fun execute_with_process_executor_dispatches_real_command() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_exec")
        val fakeExecutor = AlpineProcessExecutor { cmd, workingDir, _, onLine ->
            if (cmd.contains("/sbin/apk")) {
                val script = File(workingDir, "usr/bin/script")
                script.parentFile?.mkdirs()
                script.writeText("#!/bin/sh\n")
                script.setExecutable(true, true)
                listOf("python3", "node").forEach { executable ->
                    File(workingDir, "usr/bin/$executable").apply {
                        parentFile?.mkdirs()
                        writeText("#!/bin/sh\n")
                        setExecutable(true, true)
                    }
                }
            }
            onLine("Linux vibespace-proot 6.1.0-android #1 PREEMPT", false)
            0
        }
        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            downloadTransport = { _, destFile, progress ->
                createTestTarGz(destFile)
                progress(1.0f)
            },
            prootArchitecture = "aarch64",
            processExecutor = fakeExecutor
        )
        runner.installSubsystem()

        val result = runner.execute("uname -a", "/")
        assertTrue(result.isSuccess)
        val exec = result.getOrNull()!!
        assertEquals(0, exec.exitCode)
        assertTrue(exec.outputs.any { it.line.contains("vibespace-proot") })
    }

    private fun writeTarSymlink(out: OutputStream, name: String, linkTarget: String) {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)

        val mode = "0000777\u0000".toByteArray(Charsets.US_ASCII)
        System.arraycopy(mode, 0, header, 100, mode.size)

        val sizeStr = String.format(Locale.US, "%011o ", 0).toByteArray(Charsets.US_ASCII)
        System.arraycopy(sizeStr, 0, header, 124, sizeStr.size)

        header[156] = '2'.code.toByte()

        val linkBytes = linkTarget.toByteArray(Charsets.US_ASCII)
        System.arraycopy(linkBytes, 0, header, 157, linkBytes.size)

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
    }

    @Test
    fun extractTarGz_preserves_relative_and_absolute_symlinks_and_passes_proot_loader_env() = runTest {
        val testBaseDir = tempFolder.newFolder("test_alpine_symlinks")
        val binDir = File(testBaseDir, "bin").apply { mkdirs() }
        val prootBin = File(binDir, "proot").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true, false)
        }
        val loaderBin = File(binDir, "loader").apply {
            writeText("loader")
            setExecutable(true, false)
        }
        val loader32Bin = File(binDir, "loader-m32").apply {
            writeText("loader32")
            setExecutable(true, false)
        }

        var capturedEnv: Map<String, String> = emptyMap()
        val fakeExecutor = AlpineProcessExecutor { _, _, env, onLine ->
            capturedEnv = env
            onLine("OK", false)
            0
        }

        val runner = AlpineLinuxRunner(
            rootDirectory = testBaseDir,
            prootBinaryPath = prootBin.absolutePath,
            prootArchitecture = "x86_64",
            processExecutor = fakeExecutor,
            downloadTransport = { _, destFile, progress ->
                destFile.parentFile?.mkdirs()
                GZIPOutputStream(FileOutputStream(destFile)).use { gzos ->
                    writeTarEntry(gzos, "etc/alpine-release", "3.20.3\n".toByteArray())
                    writeTarEntry(gzos, "bin/busybox", "#!/bin/sh\n".toByteArray())
                    writeTarSymlink(gzos, "bin/sh", "/bin/busybox")
                    writeTarEntry(gzos, "usr/bin/python3.12", "#!/bin/sh\n".toByteArray())
                    writeTarSymlink(gzos, "usr/bin/python3", "python3.12")
                    writeTarEntry(gzos, "usr/bin/node", "#!/bin/sh\n".toByteArray())
                    writeTarEntry(gzos, "usr/bin/script", "#!/bin/sh\n".toByteArray())
                    gzos.write(ByteArray(1024))
                }
                progress(1.0f)
            }
        )

        val installResult = runner.installSubsystem()
        assertTrue("Install failed: $installResult", installResult.isSuccess)
        assertTrue(File(runner.getRootfsDir(), "bin/sh").exists())
        assertTrue(File(runner.getRootfsDir(), "usr/bin/python3").exists())

        val execResult = runner.execute("python3 --version", "/")
        assertTrue(execResult.isSuccess)
        assertEquals(loaderBin.absolutePath, capturedEnv["PROOT_LOADER"])
        assertEquals(loader32Bin.absolutePath, capturedEnv["PROOT_LOADER_32"])
        assertEquals("1", capturedEnv["PROOT_NO_SECCOMP"])
        assertTrue(capturedEnv["PROOT_TMP_DIR"]?.isNotBlank() == true)
    }
}
