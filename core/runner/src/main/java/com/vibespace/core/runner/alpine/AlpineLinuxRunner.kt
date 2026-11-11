package com.vibespace.core.runner.alpine

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.runner.manager.ExternalRunner
import com.vibespace.core.runner.model.ExecutionState
import com.vibespace.core.runner.model.ProcessExecution
import com.vibespace.core.runner.model.ProcessOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed interface AlpineInstallationState {
    data object NotInstalled : AlpineInstallationState
    data class Downloading(val progress: Float) : AlpineInstallationState
    data object Extracting : AlpineInstallationState
    data class Installed(val packages: List<String>) : AlpineInstallationState
    data class Error(val message: String) : AlpineInstallationState
}

fun interface AlpineProcessExecutor {
    suspend fun execute(
        command: List<String>,
        workingDir: File,
        environment: Map<String, String>,
        onOutputLine: (line: String, isError: Boolean) -> Unit
    ): Int
}

class DefaultAlpineProcessExecutor(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : AlpineProcessExecutor {
    override suspend fun execute(
        command: List<String>,
        workingDir: File,
        environment: Map<String, String>,
        onOutputLine: (line: String, isError: Boolean) -> Unit
    ): Int = withContext(dispatchers.io) {
        val pb = ProcessBuilder(command)
        pb.directory(workingDir)
        pb.environment().putAll(environment)
        val process = pb.start()

        try {
            kotlinx.coroutines.coroutineScope {
                val stdoutJob = launch {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { onOutputLine(it, false) }
                    }
                }
                val stderrJob = launch {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { onOutputLine(it, true) }
                    }
                }
                while (!process.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                }
                stdoutJob.join()
                stderrJob.join()
                process.exitValue()
            }
        } finally {
            process.destroyForcibly()
        }
    }
}

/**
 * In-app Alpine Linux userspace runner using a checksum-pinned Android PRoot runtime.
 * PRoot is not an Android security sandbox; launched code retains this app's Android UID.
 */
class AlpineLinuxRunner(
    private val rootDirectory: File? = null,
    private val downloadTransport: (suspend (url: String, destFile: File, onProgress: (Float) -> Unit) -> Unit)? = null,
    private val prootBinaryPath: String? = null,
    /** Android ABI name mapped to the PRoot release architecture (aarch64, arm, x86_64, or x86). */
    private val prootArchitecture: String? = null,
    private val dnsServersProvider: () -> List<String> = { emptyList() },
    private val processExecutor: AlpineProcessExecutor? = null,
    private val bundledRuntimeArchiveProvider: ((String) -> InputStream?)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ExternalRunner {

    override val runnerId: String = "alpine"
    override val name: String = "Alpine Linux Subsystem"

    private val _installationState = MutableStateFlow<AlpineInstallationState>(AlpineInstallationState.NotInstalled)
    val installationState: StateFlow<AlpineInstallationState> = _installationState.asStateFlow()

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val activeRuns = mutableMapOf<String, ProcessExecution>()

    private val alpineVersion = "3.24.0"
    private val alpineArchitecture = when (prootArchitecture) {
        "aarch64" -> "aarch64"
        "arm" -> "armv7"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> null
    }
    private val prootReleaseVersion = "v26.10.08-a179d3e"
    private val prootChecksums = mapOf(
        "aarch64" to "d621d71fb68bf07accbada37ffc4208cf4a963eed70916ee68f848cbd9555cc3",
        "arm" to "0c382c0512c3d536dc0685f3f6264b326073c071ebdd961e3346799aae8dd5b3",
        "x86" to "640da6a26dec3e86d3bdf4f3b9d8f6dd0466ba7be0de36b1ca6e508229610f36",
        "x86_64" to "ef06aeb47f5bb9c96bb9019fe8efa320ed199151020428b2335af6035c05f166"
    )

    init {
        val rootfsDir = getRootfsDir()
        _installationState.value = when {
            rootfsDir.exists() && File(rootfsDir, "etc/alpine-release").exists() && hasExecutableProot() -> AlpineInstallationState.Installed(emptyList())
            else -> AlpineInstallationState.NotInstalled
        }
    }

    private fun getBaseDir(): File {
        return rootDirectory ?: File(System.getProperty("java.io.tmpdir", "."), "vibespace_alpine")
    }

    private fun prootFile(): File = prootBinaryPath?.let(::File) ?: File(getBaseDir(), "bin/proot")

    private fun hasExecutableProot(): Boolean =
        processExecutor != null || (prootFile().exists() && prootFile().canExecute())

    private fun prootUnavailableMessage(): String =
        "No PRoot build is available for this device architecture. Use a supported ARM or x86 Android device."

    fun getRootfsDir(): File {
        return File(getBaseDir(), "rootfs")
    }

    fun hasBundledRuntime(): Boolean = alpineArchitecture?.let { architecture ->
        runCatching { bundledRuntimeArchiveProvider?.invoke(architecture)?.use { true } ?: false }.getOrDefault(false)
    } ?: false

    override fun isAvailable(): Boolean {
        return File(getRootfsDir(), "etc/alpine-release").exists() && hasExecutableProot()
    }

    fun isLanguageServerInstalled(languageId: String): Boolean {
        val root = getRootfsDir()
        return when (languageId.lowercase()) {
            "python" -> File(root, "usr/bin/pylsp").isFile && File(root, "usr/bin/python3").isFile
            "javascript", "typescript" -> File(root, "usr/bin/typescript-language-server").isFile && File(root, "usr/bin/node").exists()
            else -> false
        }
    }

    /** Start one of the explicitly supported LSP processes inside the Alpine userspace. */
    fun startLanguageServer(languageId: String, workspaceDirectory: File): Process {
        if (!isAvailable()) throw IllegalStateException("Install Alpine Linux before starting a language server.")
        if (!workspaceDirectory.isDirectory) throw IllegalStateException("The language server workspace directory is unavailable.")
        if (!isLanguageServerInstalled(languageId)) throw IllegalStateException("The $languageId language server is not installed. Install language servers from the terminal panel first.")
        val executable = when (languageId.lowercase()) {
            "python" -> listOf("/usr/bin/pylsp")
            "javascript", "typescript" -> listOf("/usr/bin/typescript-language-server", "--stdio")
            else -> throw IllegalArgumentException("No managed language server is available for '$languageId'.")
        }
        val command = listOf(prootFile().absolutePath, "-0", "-r", getRootfsDir().absolutePath) +
            listOf("-b", "/dev:/dev", "-b", "/proc:/proc", "-b", "${workspaceDirectory.canonicalPath}:/workspace", "-w", "/workspace") + executable
        return ProcessBuilder(command).directory(getRootfsDir()).apply {
            environment()["HOME"] = "/root"
            environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        }.start()
    }

    /** Start a real interactive shell through util-linux script, which gives the child a PTY. */
    fun startInteractiveShell(workspaceDirectory: File?): Process {
        if (!isAvailable()) throw IllegalStateException("Install the Alpine runtime before starting its terminal.")
        val script = File(getRootfsDir(), "usr/bin/script")
        if (!script.isFile) throw IllegalStateException("The Alpine runtime is missing util-linux script, required for an interactive terminal.")
        val binds = mutableListOf("-b", "/dev:/dev", "-b", "/proc:/proc")
        val containerDirectory = workspaceDirectory?.takeIf { it.isDirectory }?.let { workspace ->
            binds += listOf("-b", "${workspace.canonicalPath}:/workspace")
            File(getRootfsDir(), "workspace").mkdirs()
            "/workspace"
        } ?: "/root"
        val command = listOf(prootFile().absolutePath, "-0", "-r", getRootfsDir().absolutePath) +
            binds + listOf("-w", containerDirectory, "/usr/bin/script", "-q", "-f", "-c", "/bin/sh -i", "/dev/null")
        return ProcessBuilder(command).directory(getRootfsDir()).apply {
            environment()["HOME"] = "/root"
            environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            environment()["TERM"] = "xterm-256color"
            environment()["PS1"] = "__VIBESPACE_PROMPT__\n"
        }.start()
    }

    suspend fun installSubsystem(): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            val baseDir = getBaseDir()
            baseDir.mkdirs()
            val archiveFile = File(baseDir, "alpine-minirootfs.tar.gz")
            val rootfsDir = getRootfsDir()

            if (!File(rootfsDir, "etc/alpine-release").exists()) {
                val architecture = alpineArchitecture ?: throw IllegalStateException(prootUnavailableMessage())
                val bundled = bundledRuntimeArchiveProvider?.invoke(architecture)
                if (bundled != null) {
                    _installationState.value = AlpineInstallationState.Extracting
                    val bundleFile = File(baseDir, "bundled-runtime.tar.gz")
                    bundled.use { input -> FileOutputStream(bundleFile).use { output -> input.copyTo(output) } }
                    extractTarGz(bundleFile, baseDir)
                    bundleFile.delete()
                    if (!File(rootfsDir, "etc/alpine-release").isFile || !hasExecutableProot()) {
                        throw IllegalStateException("The bundled Python and Node runtime archive is incomplete.")
                    }
                }
                else {
                    val alpineUrl = "https://dl-cdn.alpinelinux.org/alpine/v$alpineVersion/releases/$architecture/alpine-minirootfs-$alpineVersion-$architecture.tar.gz"
                    _installationState.value = AlpineInstallationState.Downloading(0.05f)
                    if (downloadTransport != null) {
                        downloadTransport.invoke(alpineUrl, archiveFile) { progress ->
                            _installationState.value = AlpineInstallationState.Downloading(progress)
                        }
                    } else {
                        downloadArchive(alpineUrl, archiveFile) { progress ->
                            _installationState.value = AlpineInstallationState.Downloading(progress)
                        }
                    }
                    _installationState.value = AlpineInstallationState.Extracting
                    rootfsDir.mkdirs()
                    extractTarGz(archiveFile, rootfsDir)
                    if (!hasExecutableProot()) installProot(baseDir)
                }
            }

            if (!hasExecutableProot()) installProot(baseDir)

            configureRootfsDns(rootfsDir)
            if (!File(rootfsDir, "usr/bin/script").isFile) {
                _installationState.value = AlpineInstallationState.Extracting
                val prootCommand = listOf(prootFile().absolutePath, "-0", "-r", rootfsDir.absolutePath,
                    "-b", "/dev:/dev", "-b", "/proc:/proc", "/sbin/apk", "add", "--no-cache", "util-linux-misc")
                val executor = processExecutor ?: DefaultAlpineProcessExecutor(dispatchers)
                val exitCode = executor.execute(prootCommand, rootfsDir, emptyMap()) { _, _ -> }
                if (exitCode != 0 || !File(rootfsDir, "usr/bin/script").isFile) {
                    throw IllegalStateException("Alpine could not install util-linux script, required for the interactive terminal.")
                }
            }

            val missingRuntimePackages = buildList {
                if (!File(rootfsDir, "usr/bin/python3").isFile) add("python3")
                if (!File(rootfsDir, "usr/bin/node").isFile) {
                    add("nodejs")
                    add("npm")
                }
            }
            if (missingRuntimePackages.isNotEmpty()) {
                _installationState.value = AlpineInstallationState.Extracting
                val prootCommand = listOf(prootFile().absolutePath, "-0", "-r", rootfsDir.absolutePath,
                    "-b", "/dev:/dev", "-b", "/proc:/proc", "/sbin/apk", "add", "--no-cache") + missingRuntimePackages
                val executor = processExecutor ?: DefaultAlpineProcessExecutor(dispatchers)
                val exitCode = executor.execute(prootCommand, rootfsDir, emptyMap()) { _, _ -> }
                if (exitCode != 0 || !File(rootfsDir, "usr/bin/python3").isFile || !File(rootfsDir, "usr/bin/node").isFile) {
                    throw IllegalStateException("Alpine could not install the missing Python and Node.js runtimes. Check the network, then run 'apk add python3 nodejs npm' in the Alpine terminal.")
                }
            }

            val bundledPackages = buildList {
                if (File(rootfsDir, "usr/bin/python3").isFile) add("python3")
                if (File(rootfsDir, "usr/bin/node").isFile) add("nodejs")
            }
            _installationState.value = AlpineInstallationState.Installed(bundledPackages)
            VibeResult.Success(Unit)
        } catch (e: Throwable) {
            val errMsg = e.message ?: "Failed to set up Alpine Linux rootfs"
            _installationState.value = AlpineInstallationState.Error(errMsg)
            VibeResult.Error("Alpine setup failed: $errMsg", e)
        }
    }

    private fun configureRootfsDns(rootfsDir: File) {
        val servers = dnsServersProvider().mapNotNull { address ->
            address.takeIf { it.matches(Regex("[0-9a-fA-F:.]+")) }
        }.distinct()
        if (servers.isEmpty()) return
        val resolver = File(rootfsDir, "etc/resolv.conf")
        Files.deleteIfExists(resolver.toPath())
        resolver.parentFile?.mkdirs()
        resolver.writeText(servers.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" })
    }

    private suspend fun installProot(baseDir: File) {
        val architecture = prootArchitecture ?: throw IllegalStateException(prootUnavailableMessage())
        val expectedHash = prootChecksums[architecture] ?: throw IllegalStateException(prootUnavailableMessage())
        val binDirectory = File(baseDir, "bin")
        if (!binDirectory.exists() && !binDirectory.mkdirs()) throw IllegalStateException("Could not create PRoot runtime directory.")
        val archive = File(binDirectory, "proot-$architecture.zip")
        val url = "https://github.com/ahmed-alnassif/proot/releases/download/$prootReleaseVersion/proot-$architecture.zip"
        _installationState.value = AlpineInstallationState.Downloading(0f)
        if (downloadTransport != null) {
            downloadTransport.invoke(url, archive) { progress -> _installationState.value = AlpineInstallationState.Downloading(progress * 0.2f) }
        } else {
            downloadArchive(url, archive) { progress -> _installationState.value = AlpineInstallationState.Downloading(progress * 0.2f) }
        }
        val actualHash = sha256(archive)
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            archive.delete()
            throw SecurityException("Downloaded PRoot checksum did not match the pinned release checksum.")
        }
        extractProotZip(archive, binDirectory)
        archive.delete()
        val executable = prootFile()
        if (!executable.isFile || !executable.setExecutable(true, true) || !executable.canExecute()) {
            throw IllegalStateException("The PRoot runtime could not be installed for execution on this device.")
        }
    }

    private fun extractProotZip(zipFile: File, destination: File) {
        val root = destination.canonicalFile.toPath()
        var totalBytes = 0L
        var entries = 0
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > 200 || entry.size > 20L * 1024 * 1024) throw IllegalStateException("PRoot package exceeds its expected limits.")
                val out = File(destination, entry.name).canonicalFile
                if (!out.toPath().startsWith(root)) throw SecurityException("PRoot package contained an unsafe path.")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > 40L * 1024 * 1024) throw IllegalStateException("PRoot package exceeds its uncompressed size limit.")
                            output.write(buffer, 0, count)
                        }
                    }
                    if (out.name == "proot" || out.name.startsWith("loader")) out.setExecutable(true, true)
                }
                zip.closeEntry()
            }
        }
        if (!prootFile().isFile) throw IllegalStateException("The PRoot archive did not contain its executable.")
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun uninstallSubsystem(): VibeResult<Unit> = withContext(dispatchers.io) {
        try {
            val baseDir = getBaseDir()
            if (baseDir.exists()) {
                baseDir.deleteRecursively()
            }
            _installationState.value = AlpineInstallationState.NotInstalled
            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to uninstall Alpine Linux: ${e.message}", e)
        }
    }

    private fun downloadArchive(urlStr: String, destFile: File, onProgress: (Float) -> Unit) {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 25000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VibeSpace-Android/1.0")
        }
        connection.connect()
        val contentLength = connection.contentLengthLong
        destFile.parentFile?.mkdirs()

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val progress = (bytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }
        }
    }

    private fun extractTarGz(tarGzFile: File, destDir: File) {
        destDir.mkdirs()
        val destNorm = destDir.toPath().normalize().toAbsolutePath()
        val maxTotalBytes = 300 * 1024 * 1024L // 300 MB total limit
        val maxEntryBytes = 100 * 1024 * 1024L // 100 MB per file
        val maxEntries = 10000
        var totalBytesRead = 0L
        var entryCount = 0
        val symlinks = linkedMapOf<String, String>()
        val symlinkLinkTargets = linkedMapOf<String, String>()
        val globalPax = linkedMapOf<String, String>()
        var localPax = emptyMap<String, String>()
        var longName: String? = null
        var longLink: String? = null

        GZIPInputStream(FileInputStream(tarGzFile)).use { gzis ->
            val header = ByteArray(512)
            while (true) {
                val bytesRead = readFully(gzis, header)
                if (bytesRead < 512) break
                // Tar EOF marker is two consecutive 512 zero blocks
                if (header.all { it == 0.toByte() }) break

                entryCount++
                if (entryCount > maxEntries) {
                    throw IllegalStateException("Archive exceeds maximum entry limit ($maxEntries entries).")
                }

                val baseName = String(header, 0, 100, Charsets.US_ASCII).trim('\u0000', ' ')
                val prefix = String(header, 345, 155, Charsets.US_ASCII).trim('\u0000', ' ')
                val headerName = if (prefix.isEmpty()) baseName else "$prefix/$baseName"

                val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ')
                val fileSize = if (sizeStr.isNotEmpty()) sizeStr.toLongOrNull(8) ?: 0L else 0L
                val typeFlag = header[156].toInt().toChar()
                val modeText = String(header, 100, 8, Charsets.US_ASCII).trim('\u0000', ' ')
                val mode = modeText.toIntOrNull(8) ?: 0

                if (fileSize > maxEntryBytes) {
                    throw IllegalStateException("Archive entry '$headerName' exceeds per-entry limit of $maxEntryBytes bytes.")
                }

                if (typeFlag in setOf('x', 'g', 'L', 'K')) {
                    val payload = ByteArray(fileSize.toInt())
                    if (readFully(gzis, payload) != payload.size) throw IllegalStateException("Archive metadata entry is truncated.")
                    skipTarPadding(gzis, fileSize)
                    when (typeFlag) {
                        'x' -> localPax = parsePaxHeaders(payload)
                        'g' -> globalPax.putAll(parsePaxHeaders(payload))
                        'L' -> longName = String(payload, Charsets.UTF_8).trimEnd('\u0000', '\n')
                        'K' -> longLink = String(payload, Charsets.UTF_8).trimEnd('\u0000', '\n')
                    }
                    continue
                }

                val pax = globalPax + localPax
                val name = longName ?: pax["path"] ?: headerName
                val linkNameFromHeader = String(header, 157, 100, Charsets.US_ASCII).trim('\u0000', ' ')
                val linkName = longLink ?: pax["linkpath"] ?: linkNameFromHeader
                longName = null
                longLink = null
                localPax = emptyMap()
                if (name.isEmpty()) continue

                val resolvedName = resolveTarEntryName(name, symlinks)
                val entryFile = File(destDir, resolvedName)
                val entryNorm = entryFile.toPath().normalize().toAbsolutePath()
                // Component-based path containment
                if (!entryNorm.startsWith(destNorm)) {
                    skipTarEntry(gzis, fileSize)
                    continue
                }

                if (typeFlag == '2') {
                    if (linkName.isBlank()) throw IllegalStateException("Archive symlink '$name' has no target.")
                    val normalizedTarget = if (linkName.startsWith('/')) {
                        linkName.trimStart('/')
                    } else {
                        Paths.get(name).parent?.resolve(linkName)?.normalize()?.toString()?.replace('\\', '/') ?: linkName
                    }
                    if (normalizedTarget == ".." || normalizedTarget.startsWith("../") || Paths.get(normalizedTarget).isAbsolute) {
                        throw SecurityException("Archive symlink '$name' escapes the Linux root filesystem.")
                    }
                    val normalizedName = name.trimEnd('/')
                    symlinks[normalizedName] = normalizedTarget
                    symlinkLinkTargets[normalizedName] = if (linkName.startsWith('/')) linkName else normalizedTarget
                } else if (typeFlag == '5' || name.endsWith("/")) {
                    entryFile.mkdirs()
                    entryFile.setReadable(true, true)
                    entryFile.setWritable(true, true)
                    entryFile.setExecutable(true, true)
                } else if (typeFlag == '0' || typeFlag == '\u0000') {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { fos ->
                        var remaining = fileSize
                        val buffer = ByteArray(4096)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val r = gzis.read(buffer, 0, toRead)
                            if (r <= 0) break
                            fos.write(buffer, 0, r)
                            remaining -= r
                            totalBytesRead += r
                            if (totalBytesRead > maxTotalBytes) {
                                throw IllegalStateException("Total uncompressed size exceeds limit of $maxTotalBytes bytes.")
                            }
                        }
                    }
                    entryFile.setReadable(true, true)
                    entryFile.setWritable(true, true)
                    if ((mode and 0b001001001) != 0) entryFile.setExecutable(true, true)
                } else {
                    // Skip unsupported archive entry types (fifos, block/char devices, etc.)
                    skipTarEntry(gzis, fileSize)
                    continue
                }

                // Skip padding to 512-byte boundary
                skipTarPadding(gzis, fileSize)
            }
        }
        symlinkLinkTargets.forEach { (name, target) ->
            val link = File(destDir, name).canonicalFile
            if (!link.toPath().startsWith(destNorm)) throw SecurityException("Archive symlink path escapes the Linux root filesystem.")
            link.parentFile?.mkdirs()
            if (link.exists() && !link.delete()) throw IllegalStateException("Cannot replace root filesystem path with symlink: $name")
            Files.createSymbolicLink(link.toPath(), Paths.get(target))
        }
    }

    private fun resolveTarEntryName(name: String, symlinks: Map<String, String>): String {
        var candidate = name.trimStart('/')
        repeat(20) {
            val parts = candidate.split('/').toMutableList()
            var changed = false
            for (index in parts.indices) {
                val prefix = parts.take(index + 1).joinToString("/")
                val target = symlinks[prefix] ?: continue
                candidate = (listOf(target) + parts.drop(index + 1)).joinToString("/")
                changed = true
                break
            }
            if (!changed) return candidate
        }
        throw SecurityException("Archive contains a cyclic symlink path: $name")
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }

    private fun skipTarEntry(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) throw IllegalStateException("Archive ended while skipping an entry.")
                remaining--
            } else remaining -= skipped
        }
        skipTarPadding(input, size)
    }

    private fun skipTarPadding(input: InputStream, size: Long) {
        var remaining = (512 - (size % 512)) % 512
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) throw IllegalStateException("Archive ended while skipping tar padding.")
                remaining--
            } else remaining -= skipped
        }
    }

    private fun parsePaxHeaders(payload: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var offset = 0
        while (offset < payload.size) {
            val space = payload.indexOf(' '.code.toByte(), offset)
            if (space < 0) break
            val recordLength = String(payload, offset, space - offset, Charsets.US_ASCII).toIntOrNull() ?: break
            val recordEnd = offset + recordLength
            if (recordLength <= 0 || recordEnd > payload.size) break
            val record = String(payload, space + 1, recordEnd - space - 2, Charsets.UTF_8)
            val separator = record.indexOf('=')
            if (separator > 0) result[record.substring(0, separator)] = record.substring(separator + 1)
            offset = recordEnd
        }
        return result
    }

    override suspend fun execute(
        command: String,
        workingDirectory: String,
        environmentVariables: Map<String, String>
    ): VibeResult<ProcessExecution> {
        val executionId = IdGenerator.shortId("alpine")
        val flow = MutableSharedFlow<ProcessOutput>(replay = 50)
        outputFlows[executionId] = flow

        val execution = ProcessExecution(
            executionId = executionId,
            command = command,
            workingDirectoryUri = workingDirectory,
            state = ExecutionState.RUNNING,
            startTime = System.currentTimeMillis()
        )
        activeRuns[executionId] = execution

        val outputs = mutableListOf<ProcessOutput>()

        fun emitLine(line: String, isError: Boolean = false) {
            val out = ProcessOutput(line = line, isError = isError)
            outputs.add(out)
            flow.tryEmit(out)
        }

        if (_installationState.value !is AlpineInstallationState.Installed) {
            val unavailable = _installationState.value as? AlpineInstallationState.Error
            emitLine(unavailable?.message ?: "[Alpine Linux Subsystem Not Installed]", true)
            if (unavailable == null) {
                emitLine("Install Alpine Linux and select the Alpine runner before executing Linux commands.", false)
            }

            val failed = execution.copy(
                state = ExecutionState.FAILED,
                exitCode = 127,
                endTime = System.currentTimeMillis(),
                outputs = outputs
            )
            activeRuns[executionId] = failed
            return VibeResult.Success(failed)
        }

        emitLine("[Alpine Linux PRoot Container] $ $command", false)
        val rootfsDir = getRootfsDir()
        val prootFile = prootFile()
        val hostWorkingDirectory = File(workingDirectory)
        val canBindProject = hostWorkingDirectory.isDirectory
        val containerWorkingDirectory = if (canBindProject) "/workspace" else "/root"
        if (canBindProject) File(rootfsDir, "workspace").mkdirs()
        val workspaceBindArgs = if (canBindProject) {
            listOf("-b", "${hostWorkingDirectory.canonicalPath}:/workspace")
        } else {
            emptyList()
        }
        val systemBindArgs = listOf("-b", "/dev:/dev", "-b", "/proc:/proc")

        val exitCode: Int
        val shellCommand = environmentVariables["VIBESPACE_TERMINAL_SESSION_ID"]
            ?.takeIf { it.isNotBlank() }
            ?.let { persistentShellCommand(it, command) }
            ?: command
        if (processExecutor != null) {
            val prootCommand = listOf(prootFile.absolutePath, "-0", "-r", rootfsDir.absolutePath) +
                    systemBindArgs + workspaceBindArgs + listOf("-w", containerWorkingDirectory, "/bin/sh", "-c", shellCommand)
            exitCode = processExecutor.execute(
                prootCommand,
                rootfsDir,
                environmentVariables
            ) { line, isError ->
                emitLine(line, isError)
            }
        } else if (prootFile.exists() && prootFile.canExecute()) {
            val prootCommand = listOf(prootFile.absolutePath, "-0", "-r", rootfsDir.absolutePath) +
                    systemBindArgs + workspaceBindArgs + listOf("-w", containerWorkingDirectory, "/bin/sh", "-c", shellCommand)
            val defaultExecutor = DefaultAlpineProcessExecutor(dispatchers)
            exitCode = defaultExecutor.execute(
                prootCommand,
                rootfsDir,
                environmentVariables
            ) { line, isError ->
                emitLine(line, isError)
            }
        } else {
            emitLine("[Alpine Linux Error: PRoot is unavailable at ${prootFile.absolutePath}. Reinstall the Alpine subsystem to repair it.]", true)
            val failed = execution.copy(
                state = ExecutionState.FAILED,
                exitCode = 126,
                endTime = System.currentTimeMillis(),
                outputs = outputs
            )
            activeRuns[executionId] = failed
            return VibeResult.Success(failed)
        }

        val completed = execution.copy(
            state = if (exitCode == 0) ExecutionState.SUCCEEDED else ExecutionState.FAILED,
            exitCode = exitCode,
            endTime = System.currentTimeMillis(),
            outputs = outputs
        )
        activeRuns[executionId] = completed
        emitLine("[Process finished with exit code $exitCode]", exitCode != 0)
        return VibeResult.Success(completed)
    }

    /** Preserve shell variables and `cd` between terminal submissions, per terminal session. */
    private fun persistentShellCommand(sessionId: String, command: String): String {
        val sessionHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sessionId.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
        val stateFile = "/root/.vibespace_sessions/$sessionHash.sh"
        return listOf(
            "umask 077",
            "mkdir -p /root/.vibespace_sessions || exit 125",
            "__vibespace_state=${shellQuote(stateFile)}",
            "if [ -f \"\$__vibespace_state\" ]; then . \"\$__vibespace_state\"; fi",
            "case \"\${VIBESPACE_SESSION_CWD:-}\" in /*) cd \"\$VIBESPACE_SESSION_CWD\" 2>/dev/null || cd /root ;; esac",
            "eval ${shellQuote(command)}",
            "__vibespace_status=\$?",
            "VIBESPACE_SESSION_CWD=\"\$(pwd)\"; export VIBESPACE_SESSION_CWD",
            "export -p > \"\$__vibespace_state.tmp\"",
            "mv \"\$__vibespace_state.tmp\" \"\$__vibespace_state\"",
            "exit \"\$__vibespace_status\""
        ).joinToString("\n")
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    override suspend fun stop(executionId: String): VibeResult<Unit> {
        val current = activeRuns[executionId] ?: return VibeResult.Error("Execution not found: $executionId")
        return if (current.state == ExecutionState.RUNNING) {
            VibeResult.Error("The Alpine process executor does not currently support interruption.")
        } else {
            VibeResult.Error("Execution is no longer running.")
        }
    }

    override fun observeOutput(executionId: String): Flow<ProcessOutput> {
        return outputFlows.getOrPut(executionId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
}
