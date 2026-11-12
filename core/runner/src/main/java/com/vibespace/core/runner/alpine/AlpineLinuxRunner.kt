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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
        sanitizeAndroidEnvironment(pb.environment())
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

private fun sanitizeAndroidEnvironment(env: MutableMap<String, String>) {
    val androidKeysToRemove = listOf(
        "LD_PRELOAD",
        "LD_LIBRARY_PATH",
        "CLASSPATH",
        "BOOTCLASSPATH",
        "DEX2OATBOOTCLASSPATH",
        "SYSTEMSERVERCLASSPATH",
        "STANDALONE_SYSTEMSERVER_JARS",
        "ANDROID_ROOT",
        "ANDROID_DATA",
        "ANDROID_STORAGE",
        "ANDROID_ART_ROOT",
        "ANDROID_I18N_ROOT",
        "ANDROID_TZDATA_ROOT",
        "ANDROID_SOCKET_zygote",
        "ANDROID_SOCKET_usap_pool_primary"
    )
    androidKeysToRemove.forEach(env::remove)
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
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val nativeLibDir: File? = null
) : ExternalRunner {

    override val runnerId: String = "alpine"
    override val name: String = "Alpine Linux Subsystem"

    private val _installationState = MutableStateFlow<AlpineInstallationState>(AlpineInstallationState.NotInstalled)
    val installationState: StateFlow<AlpineInstallationState> = _installationState.asStateFlow()
    private val installMutex = kotlinx.coroutines.sync.Mutex()

    private val outputFlows = mutableMapOf<String, MutableSharedFlow<ProcessOutput>>()
    private val activeRuns = mutableMapOf<String, ProcessExecution>()

    private val alpineVersion = "3.20.3"
    private val alpineReleaseBranch = "v3.20"
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
            rootfsDir.exists() && isRootfsComplete(rootfsDir) && hasExecutableProot() -> {
                AlpineInstallationState.Installed(detectInstalledPackages(rootfsDir))
            }
            else -> AlpineInstallationState.NotInstalled
        }
    }

    private fun detectInstalledPackages(rootfsDir: File): List<String> = buildList {
        if (File(rootfsDir, "usr/bin/python3").exists()) add("python3")
        if (File(rootfsDir, "usr/bin/node").exists()) add("nodejs")
        if (File(rootfsDir, "usr/bin/pylsp").exists()) add("pylsp")
        if (File(rootfsDir, "usr/bin/typescript-language-server").exists()) add("typescript-language-server")
        if (File(rootfsDir, "usr/bin/git").exists()) add("git")
        if (File(rootfsDir, "bin/bash").exists()) add("bash")
        if (File(rootfsDir, "usr/bin/curl").exists()) add("curl")
    }

    private fun isRootfsComplete(rootfsDir: File): Boolean {
        if (!File(rootfsDir, "etc/alpine-release").isFile) return false
        if (hasBundledRuntime()) {
            return File(rootfsDir, BUNDLED_RUNTIME_MARKER).isFile &&
                File(rootfsDir, "bin/sh").exists() &&
                File(rootfsDir, "usr/bin/python3").exists() &&
                File(rootfsDir, "usr/bin/node").exists() &&
                File(rootfsDir, "usr/bin/pylsp").exists() &&
                File(rootfsDir, "usr/bin/typescript-language-server").exists()
        }
        return true
    }

    private fun getBaseDir(): File {
        return rootDirectory ?: File(System.getProperty("java.io.tmpdir", "."), "vibespace_alpine")
    }

    private fun prootFile(): File {
        nativeLibDir?.let { dir ->
            val nativeProot = File(dir, "libproot.so")
            if (nativeProot.exists() && nativeProot.canExecute()) return nativeProot
        }
        prootBinaryPath?.let { path ->
            val configured = File(path)
            if (configured.exists()) return configured
        }
        return prootBinaryPath?.let(::File) ?: File(getBaseDir(), "bin/proot")
    }

    private fun prootLoaderFile(): File? {
        val candidates = buildList {
            nativeLibDir?.let { add(File(it, "libproot_loader.so")) }
            prootFile().parentFile?.let {
                add(File(it, "libproot_loader.so"))
                add(File(it, "loader"))
            }
            add(File(getBaseDir(), "bin/loader"))
        }
        return candidates.firstOrNull { it.isFile }
    }

    private fun prootLoader32File(): File? {
        val candidates = buildList {
            nativeLibDir?.let { add(File(it, "libproot_loader32.so")) }
            prootFile().parentFile?.let {
                add(File(it, "libproot_loader32.so"))
                add(File(it, "loader-m32"))
            }
            add(File(getBaseDir(), "bin/loader-m32"))
        }
        return candidates.firstOrNull { it.isFile }
    }

    private fun buildProotEnvironment(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val baseDir = getBaseDir()
        val rootfsDir = getRootfsDir()
        val hostTmpDir = File(baseDir, "tmp").apply { mkdirs() }
        val l2sDir = File(baseDir, "l2s").apply { mkdirs() }
        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "root").mkdirs()

        return buildMap {
            put("HOME", "/root")
            put("USER", "root")
            put("LOGNAME", "root")
            put("SHELL", if (File(rootfsDir, "bin/bash").exists()) "/bin/bash" else "/bin/sh")
            put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("TERM", "xterm-256color")
            put("LANG", "C.UTF-8")
            put("TMPDIR", "/tmp")
            put("PYTHONUNBUFFERED", "1")
            put("PROOT_TMP_DIR", hostTmpDir.absolutePath)
            put("PROOT_NO_SECCOMP", "1")
            put("PROOT_L2S_DIR", l2sDir.absolutePath)
            prootLoaderFile()?.let { put("PROOT_LOADER", it.absolutePath) }
            prootLoader32File()?.let { put("PROOT_LOADER_32", it.absolutePath) }
            if (File(rootfsDir, "etc/ssl/certs/ca-certificates.crt").exists()) {
                put("SSL_CERT_FILE", "/etc/ssl/certs/ca-certificates.crt")
                put("NODE_EXTRA_CA_CERTS", "/etc/ssl/certs/ca-certificates.crt")
            }
            putAll(extra)
        }
    }

    private fun ensureBinariesExecutable() {
        val binDir = File(getBaseDir(), "bin")
        if (binDir.isDirectory) {
            binDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.setReadable(true, false)
                    file.setExecutable(true, false)
                }
            }
        }
        prootBinaryPath?.let(::File)?.takeIf { it.isFile }?.apply {
            setReadable(true, false)
            setExecutable(true, false)
        }
    }

    private fun hasExecutableProot(): Boolean {
        ensureBinariesExecutable()
        return processExecutor != null || (prootFile().exists() && prootFile().canExecute())
    }

    private fun prootUnavailableMessage(): String =
        "No PRoot build is available for this device architecture. Use a supported ARM or x86 Android device."

    fun getRootfsDir(): File {
        return File(getBaseDir(), "rootfs")
    }

    fun hasBundledRuntime(): Boolean = alpineArchitecture?.let { architecture ->
        runCatching { bundledRuntimeArchiveProvider?.invoke(architecture)?.use { true } ?: false }.getOrDefault(false)
    } ?: false

    override fun isAvailable(): Boolean {
        return isRootfsComplete(getRootfsDir()) && hasExecutableProot()
    }

    fun isLanguageServerInstalled(languageId: String): Boolean {
        val root = getRootfsDir()
        return when (languageId.lowercase()) {
            "python" -> File(root, "usr/bin/pylsp").exists() && File(root, "usr/bin/python3").exists()
            "javascript", "typescript" -> File(root, "usr/bin/typescript-language-server").exists() && File(root, "usr/bin/node").exists()
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
        val hostTmpDir = File(getBaseDir(), "tmp").apply { mkdirs() }
        val command = listOf(prootFile().absolutePath, "--link2symlink", "-0", "-r", getRootfsDir().absolutePath) +
            listOf("-b", "/dev:/dev", "-b", "/proc:/proc", "-b", "${hostTmpDir.absolutePath}:/tmp", "-b", "${workspaceDirectory.canonicalPath}:/workspace", "-w", "/workspace") + executable
        return ProcessBuilder(command).directory(getRootfsDir()).apply {
            sanitizeAndroidEnvironment(environment())
            environment().putAll(buildProotEnvironment())
        }.start()
    }

    /** Start a real interactive shell through util-linux script (PTY) or direct fallback shell. */
    fun startInteractiveShell(workspaceDirectory: File?): Process {
        if (!isAvailable()) throw IllegalStateException("Install the Alpine runtime before starting its terminal.")
        val script = File(getRootfsDir(), "usr/bin/script")
        val hostTmpDir = File(getBaseDir(), "tmp").apply { mkdirs() }
        val binds = mutableListOf("-b", "/dev:/dev", "-b", "/proc:/proc", "-b", "${hostTmpDir.absolutePath}:/tmp")
        val containerDirectory = workspaceDirectory?.takeIf { it.isDirectory }?.let { workspace ->
            binds += listOf("-b", "${workspace.canonicalPath}:/workspace")
            File(getRootfsDir(), "workspace").mkdirs()
            "/workspace"
        } ?: "/root"
        val shellCmd = if (File(getRootfsDir(), "bin/bash").exists()) "/bin/bash --norc --noprofile -i" else "/bin/sh -i"
        val command = if (script.exists()) {
            listOf(prootFile().absolutePath, "--link2symlink", "-0", "-r", getRootfsDir().absolutePath) +
                binds + listOf(
                    "-w", containerDirectory,
                    "/bin/sh", "-c",
                    "if /usr/bin/script -q -f -c 'true' /dev/null >/dev/null 2>&1; then exec /usr/bin/script -q -f -c \"$shellCmd\" /dev/null; else exec $shellCmd; fi"
                )
        } else {
            listOf(prootFile().absolutePath, "--link2symlink", "-0", "-r", getRootfsDir().absolutePath) +
                binds + listOf("-w", containerDirectory, "/bin/sh", "-c", "exec $shellCmd")
        }
        return ProcessBuilder(command).directory(getRootfsDir()).redirectErrorStream(true).apply {
            sanitizeAndroidEnvironment(environment())
            environment().putAll(
                buildProotEnvironment(
                    mapOf("PS1" to "__VIBESPACE_PROMPT__\n")
                )
            )
        }.start()
    }

    suspend fun installSubsystem(): VibeResult<Unit> = withContext(dispatchers.io) {
        installMutex.withLock {
            if (isAvailable() && _installationState.value is AlpineInstallationState.Installed) {
                return@withLock VibeResult.Success(Unit)
            }
            try {
                val baseDir = getBaseDir()
                baseDir.mkdirs()
                val archiveFile = File(baseDir, "alpine-minirootfs.tar.gz")
                val rootfsDir = getRootfsDir()

                if (!isRootfsComplete(rootfsDir)) {
                    val architecture = alpineArchitecture ?: throw IllegalStateException(prootUnavailableMessage())
                    val bundled = bundledRuntimeArchiveProvider?.invoke(architecture)
                    if (bundled != null) {
                        _installationState.value = AlpineInstallationState.Extracting
                        if (rootfsDir.exists()) {
                            rootfsDir.deleteRecursively()
                        }
                        rootfsDir.mkdirs()
                        bundled.use { input ->
                            extractTarGz(input, baseDir)
                        }
                        val bundledRootfs = File(baseDir, "rootfs.tar.gz")
                        if (bundledRootfs.isFile) {
                            _installationState.value = AlpineInstallationState.Extracting
                            extractTarGz(bundledRootfs, rootfsDir)
                            bundledRootfs.delete()
                        }
                        repairCriticalRootfsLinks(rootfsDir)
                        ensureBinariesExecutable()
                        if (!File(rootfsDir, "etc/alpine-release").isFile ||
                            !File(rootfsDir, "usr/bin/python3").exists() ||
                            !File(rootfsDir, "usr/bin/node").exists() ||
                            !hasExecutableProot()
                        ) {
                            throw IllegalStateException("The bundled Python and Node runtime archive is incomplete.")
                        }
                        File(rootfsDir, BUNDLED_RUNTIME_MARKER).writeText("3\n")
                    } else {
                        val mirrors = listOf(
                            "dl-cdn.alpinelinux.org",
                            "uk.alpinelinux.org",
                            "mirrors.edge.kernel.org"
                        )
                        val urlsToTry = mirrors.flatMap { host ->
                            listOf(
                                "https://$host/alpine/$alpineReleaseBranch/releases/$architecture/alpine-minirootfs-$alpineVersion-$architecture.tar.gz",
                                "https://$host/alpine/latest-stable/releases/$architecture/alpine-minirootfs-$alpineVersion-$architecture.tar.gz",
                                "https://$host/alpine/v3.20/releases/$architecture/alpine-minirootfs-3.20.3-$architecture.tar.gz",
                                "https://$host/alpine/v3.21/releases/$architecture/alpine-minirootfs-3.21.3-$architecture.tar.gz"
                            )
                        }.distinct()
                        _installationState.value = AlpineInstallationState.Downloading(0.05f)
                        var downloaded = false
                        var lastDownloadError: Throwable? = null

                        for (url in urlsToTry) {
                            for (retry in 0..1) {
                                try {
                                    if (downloadTransport != null) {
                                        downloadTransport.invoke(url, archiveFile) { progress ->
                                            _installationState.value = AlpineInstallationState.Downloading(progress)
                                        }
                                    } else {
                                        downloadArchive(url, archiveFile) { progress ->
                                            _installationState.value = AlpineInstallationState.Downloading(progress)
                                        }
                                    }
                                    downloaded = true
                                    break
                                } catch (e: Throwable) {
                                    lastDownloadError = e
                                }
                            }
                            if (downloaded) break
                        }

                        if (!downloaded || !archiveFile.exists() || archiveFile.length() == 0L) {
                            throw IllegalStateException("Failed to download Alpine minirootfs: ${lastDownloadError?.message}", lastDownloadError)
                        }
                        _installationState.value = AlpineInstallationState.Extracting
                        rootfsDir.mkdirs()
                        extractTarGz(archiveFile, rootfsDir)
                        archiveFile.delete()
                        repairCriticalRootfsLinks(rootfsDir)
                        if (!hasExecutableProot()) installProot(baseDir)
                    }
                }

                if (!hasExecutableProot()) installProot(baseDir)
                ensureBinariesExecutable()

                configureRootfsDns(rootfsDir)
                val prootEnv = buildProotEnvironment()
                val hostTmpDir = File(baseDir, "tmp").apply { mkdirs() }
                if (!File(rootfsDir, "usr/bin/script").exists()) {
                    _installationState.value = AlpineInstallationState.Extracting
                    val prootCommand = listOf(
                        prootFile().absolutePath, "--link2symlink", "-0", "-r", rootfsDir.absolutePath,
                        "-b", "/dev:/dev", "-b", "/proc:/proc", "-b", "${hostTmpDir.absolutePath}:/tmp",
                        "/sbin/apk", "add", "--no-cache", "util-linux-misc"
                    )
                    val executor = processExecutor ?: DefaultAlpineProcessExecutor(dispatchers)
                    runCatching {
                        executor.execute(prootCommand, rootfsDir, prootEnv) { _, _ -> }
                    }
                }

                val missingRuntimePackages = buildList {
                    if (!File(rootfsDir, "usr/bin/python3").exists()) add("python3")
                    if (!File(rootfsDir, "usr/bin/node").exists()) {
                        add("nodejs")
                        add("npm")
                    }
                }
                if (missingRuntimePackages.isNotEmpty()) {
                    _installationState.value = AlpineInstallationState.Extracting
                    val prootCommand = listOf(
                        prootFile().absolutePath, "--link2symlink", "-0", "-r", rootfsDir.absolutePath,
                        "-b", "/dev:/dev", "-b", "/proc:/proc", "-b", "${hostTmpDir.absolutePath}:/tmp",
                        "/sbin/apk", "add", "--no-cache"
                    ) + missingRuntimePackages
                    val executor = processExecutor ?: DefaultAlpineProcessExecutor(dispatchers)
                    runCatching {
                        executor.execute(prootCommand, rootfsDir, prootEnv) { _, _ -> }
                    }
                }

                val bundledPackages = detectInstalledPackages(rootfsDir)
                _installationState.value = AlpineInstallationState.Installed(bundledPackages)
                VibeResult.Success(Unit)
            } catch (e: Throwable) {
                val errMsg = e.message ?: "Failed to set up Alpine Linux rootfs"
                _installationState.value = AlpineInstallationState.Error(errMsg)
                VibeResult.Error("Alpine setup failed: $errMsg", e)
            }
        }
    }

    private fun repairCriticalRootfsLinks(rootfsDir: File) {
        val python312 = File(rootfsDir, "usr/bin/python3.12")
        val python3 = File(rootfsDir, "usr/bin/python3")
        val python = File(rootfsDir, "usr/bin/python")
        if (python312.isFile) {
            python312.setReadable(true, false)
            python312.setExecutable(true, false)
            if (!python3.exists() || !python3.isFile) {
                runCatching {
                    Files.deleteIfExists(python3.toPath())
                    python312.copyTo(python3, overwrite = true)
                    python3.setReadable(true, false)
                    python3.setExecutable(true, false)
                }
            }
            if (!python.exists() || !python.isFile) {
                runCatching {
                    Files.deleteIfExists(python.toPath())
                    python312.copyTo(python, overwrite = true)
                    python.setReadable(true, false)
                    python.setExecutable(true, false)
                }
            }
        }
        val busybox = File(rootfsDir, "bin/busybox")
        val sh = File(rootfsDir, "bin/sh")
        if (busybox.isFile) {
            busybox.setReadable(true, false)
            busybox.setExecutable(true, false)
            if (!sh.exists() || !sh.isFile) {
                runCatching {
                    Files.deleteIfExists(sh.toPath())
                    busybox.copyTo(sh, overwrite = true)
                    sh.setReadable(true, false)
                    sh.setExecutable(true, false)
                }
            }
        }
    }

    private fun configureRootfsDns(rootfsDir: File) {
        val configuredServers = dnsServersProvider().mapNotNull { address ->
            address.takeIf { it.matches(Regex("[0-9a-fA-F:.]+")) }
        }.distinct()
        val servers = configuredServers.ifEmpty { listOf("8.8.8.8", "1.1.1.1") }
        val resolver = File(rootfsDir, "etc/resolv.conf")
        runCatching { Files.deleteIfExists(resolver.toPath()) }
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
        ensureBinariesExecutable()
        val executable = prootFile()
        if (!executable.isFile || !executable.setExecutable(true, false) || !executable.canExecute()) {
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
                    if (out.name == "proot" || out.name.startsWith("loader")) {
                        out.setReadable(true, false)
                        out.setExecutable(true, false)
                    }
                }
                zip.closeEntry()
            }
        }
        if (!File(destination, "proot").isFile && !prootFile().isFile) {
            throw IllegalStateException("The PRoot archive did not contain its executable.")
        }
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
        var currentUrl = urlStr
        var connection: HttpURLConnection? = null
        val maxRedirects = 5
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "VibeSpace-Android/1.0")
            }
            connection.connect()
            val status = connection.responseCode
            if (status in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 303, 307, 308)) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (newUrl != null) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl else URL(url, newUrl).toString()
                    redirectCount++
                    continue
                }
            }
            break
        }

        val activeConn = connection ?: throw IOException("Could not establish connection to $urlStr")
        val responseCode = activeConn.responseCode
        if (responseCode !in 200..299) {
            activeConn.disconnect()
            throw IOException("HTTP $responseCode received while fetching $urlStr")
        }
        val contentLength = activeConn.contentLengthLong
        destFile.parentFile?.mkdirs()

        activeConn.inputStream.use { input ->
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
        FileInputStream(tarGzFile).use { fis ->
            extractTarGz(fis, destDir)
        }
    }

    private fun extractTarGz(rawInputStream: InputStream, destDir: File) {
        destDir.mkdirs()
        val destNorm = destDir.toPath().normalize().toAbsolutePath()
        val maxTotalBytes = 800L * 1024 * 1024L // 800 MB total limit
        val maxEntryBytes = 250L * 1024 * 1024L // 250 MB per file
        val maxEntries = 100_000
        var totalBytesRead = 0L
        var entryCount = 0
        val symlinks = linkedMapOf<String, String>()
        val symlinkLinkTargets = linkedMapOf<String, String>()
        val hardlinks = linkedMapOf<String, String>()
        val globalPax = linkedMapOf<String, String>()
        var localPax = emptyMap<String, String>()
        var longName: String? = null
        var longLink: String? = null
        val copyBuffer = ByteArray(64 * 1024)

        GZIPInputStream(java.io.BufferedInputStream(rawInputStream, 64 * 1024), 64 * 1024).use { gzis ->
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
                val rawName = longName ?: pax["path"] ?: headerName
                val name = rawName.removePrefix("./").trimStart('/')
                val linkNameFromHeader = String(header, 157, 100, Charsets.US_ASCII).trim('\u0000', ' ')
                val linkName = longLink ?: pax["linkpath"] ?: linkNameFromHeader
                longName = null
                longLink = null
                localPax = emptyMap()
                if (name.isEmpty() || name == ".") {
                    skipTarEntry(gzis, fileSize)
                    continue
                }

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
                    val cleanLinkName = linkName.removePrefix("./")
                    val normalizedTarget = if (cleanLinkName.startsWith('/')) {
                        cleanLinkName.trimStart('/')
                    } else {
                        Paths.get(name).parent?.resolve(cleanLinkName)?.normalize()?.toString()?.replace('\\', '/') ?: cleanLinkName
                    }
                    if (normalizedTarget == ".." || normalizedTarget.startsWith("../") || Paths.get(normalizedTarget).isAbsolute) {
                        throw SecurityException("Archive symlink '$name' escapes the Linux root filesystem.")
                    }
                    val normalizedName = name.trimEnd('/')
                    symlinks[normalizedName] = normalizedTarget
                    val parentPath = Paths.get(normalizedName).parent
                    val relativeLinkTarget = if (parentPath != null) {
                        parentPath.relativize(Paths.get(normalizedTarget)).toString().replace('\\', '/')
                    } else {
                        normalizedTarget
                    }
                    symlinkLinkTargets[normalizedName] = relativeLinkTarget
                } else if (typeFlag == '1') {
                    val cleanTarget = linkName.removePrefix("./").trimStart('/')
                    if (cleanTarget.isNotBlank()) {
                        val normalizedTarget = Paths.get(cleanTarget).normalize().toString().replace('\\', '/')
                        if (normalizedTarget != ".." && !normalizedTarget.startsWith("../") && !Paths.get(normalizedTarget).isAbsolute) {
                            hardlinks[name.trimEnd('/')] = normalizedTarget
                        }
                    }
                } else if (typeFlag == '5' || rawName.endsWith("/")) {
                    entryFile.mkdirs()
                    entryFile.setReadable(true, false)
                    entryFile.setWritable(true, true)
                    entryFile.setExecutable(true, false)
                } else if (typeFlag == '0' || typeFlag == '\u0000') {
                    entryFile.parentFile?.mkdirs()
                    if (Files.isSymbolicLink(entryFile.toPath())) {
                        Files.deleteIfExists(entryFile.toPath())
                    }
                    java.io.BufferedOutputStream(FileOutputStream(entryFile), 64 * 1024).use { fos ->
                        var remaining = fileSize
                        while (remaining > 0) {
                            val toRead = minOf(remaining, copyBuffer.size.toLong()).toInt()
                            val r = gzis.read(copyBuffer, 0, toRead)
                            if (r <= 0) break
                            fos.write(copyBuffer, 0, r)
                            remaining -= r
                            totalBytesRead += r
                            if (totalBytesRead > maxTotalBytes) {
                                throw IllegalStateException("Total uncompressed size exceeds limit of $maxTotalBytes bytes.")
                            }
                        }
                    }
                    entryFile.setReadable(true, false)
                    entryFile.setWritable(true, true)
                    if ((mode and 0b001001001) != 0 || entryFile.name == "proot" || entryFile.name.startsWith("loader")) {
                        entryFile.setExecutable(true, false)
                    }
                } else {
                    // Skip unsupported archive entry types (fifos, block/char devices, etc.)
                    skipTarEntry(gzis, fileSize)
                    continue
                }

                // Skip padding to 512-byte boundary
                skipTarPadding(gzis, fileSize)
            }
        }
        hardlinks.forEach { (name, target) ->
            val linkFile = File(destDir, name)
            val targetFile = File(destDir, target)
            val linkNorm = linkFile.toPath().normalize().toAbsolutePath()
            val targetNorm = targetFile.toPath().normalize().toAbsolutePath()
            if (linkNorm.startsWith(destNorm) && targetNorm.startsWith(destNorm) && targetFile.isFile) {
                linkFile.parentFile?.mkdirs()
                runCatching {
                    Files.deleteIfExists(linkFile.toPath())
                    try {
                        Files.createLink(linkFile.toPath(), targetFile.toPath())
                    } catch (_: Exception) {
                        targetFile.copyTo(linkFile, overwrite = true)
                        if (targetFile.canExecute()) linkFile.setExecutable(true, false)
                    }
                }
            }
        }
        symlinkLinkTargets.forEach { (name, target) ->
            val link = File(destDir, name)
            val linkNorm = link.toPath().normalize().toAbsolutePath()
            if (!linkNorm.startsWith(destNorm)) throw SecurityException("Archive symlink '$name' escapes the Linux root filesystem.")
            link.parentFile?.mkdirs()
            runCatching { Files.deleteIfExists(link.toPath()) }
            if (link.exists()) {
                val deleted = if (link.isDirectory) link.deleteRecursively() else link.delete()
                if (!deleted && link.exists()) throw IllegalStateException("Cannot replace root filesystem path with symlink: $name")
            }
            try {
                Files.createSymbolicLink(link.toPath(), Paths.get(target))
            } catch (e: Exception) {
                runCatching {
                    val resolvedRel = resolveTarEntryName(name, symlinks)
                    val resolvedTarget = File(destDir, resolvedRel)
                    if (resolvedTarget.exists() && !resolvedTarget.isDirectory) {
                        resolvedTarget.copyTo(link, overwrite = true)
                        if (resolvedTarget.canExecute()) link.setExecutable(true, false)
                    }
                }
            }
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
            var space = offset
            while (space < payload.size && payload[space] != ' '.code.toByte()) space++
            if (space == payload.size) break
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

        if ((_installationState.value !is AlpineInstallationState.Installed || !isRootfsComplete(getRootfsDir())) &&
            (hasBundledRuntime() || _installationState.value is AlpineInstallationState.Extracting)
        ) {
            installSubsystem()
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
        val hostTmpDir = File(getBaseDir(), "tmp").apply { mkdirs() }
        val systemBindArgs = listOf("-b", "/dev:/dev", "-b", "/proc:/proc", "-b", "${hostTmpDir.absolutePath}:/tmp")

        val exitCode: Int
        val shellCommand = environmentVariables["VIBESPACE_TERMINAL_SESSION_ID"]
            ?.takeIf { it.isNotBlank() }
            ?.let { persistentShellCommand(it, command, containerWorkingDirectory) }
            ?: command
        val mergedEnv = buildProotEnvironment(environmentVariables)
        if (processExecutor != null) {
            val prootCommand = listOf(prootFile.absolutePath, "--link2symlink", "-0", "-r", rootfsDir.absolutePath) +
                    systemBindArgs + workspaceBindArgs + listOf("-w", containerWorkingDirectory, "/bin/sh", "-c", shellCommand)
            exitCode = processExecutor.execute(
                prootCommand,
                rootfsDir,
                mergedEnv
            ) { line, isError ->
                emitLine(line, isError)
            }
        } else if (prootFile.exists() && prootFile.canExecute()) {
            val prootCommand = listOf(prootFile.absolutePath, "--link2symlink", "-0", "-r", rootfsDir.absolutePath) +
                    systemBindArgs + workspaceBindArgs + listOf("-w", containerWorkingDirectory, "/bin/sh", "-c", shellCommand)
            val defaultExecutor = DefaultAlpineProcessExecutor(dispatchers)
            exitCode = defaultExecutor.execute(
                prootCommand,
                rootfsDir,
                mergedEnv
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
        if (exitCode != 0) {
            emitLine("[Process exited with code $exitCode]", true)
        }
        return VibeResult.Success(completed)
    }

    /** Preserve shell variables and `cd` between terminal submissions, per terminal session. */
    private fun persistentShellCommand(
        sessionId: String,
        command: String,
        defaultCwd: String = "/workspace"
    ): String {
        val sessionHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sessionId.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
        val stateFile = "/root/.vibespace_sessions_v3/$sessionHash.sh"
        return listOf(
            "umask 077",
            "mkdir -p /root/.vibespace_sessions_v3 || exit 125",
            "__vibespace_state=${shellQuote(stateFile)}",
            "if [ -f \"\$__vibespace_state\" ]; then . \"\$__vibespace_state\" 2>/dev/null || true; fi",
            "export PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\"",
            "case \"\${VIBESPACE_SESSION_CWD:-}\" in /*) cd \"\$VIBESPACE_SESSION_CWD\" 2>/dev/null || cd ${shellQuote(defaultCwd)} 2>/dev/null || cd /root ;; *) cd ${shellQuote(defaultCwd)} 2>/dev/null || cd /root ;; esac",
            "eval ${shellQuote(command)}",
            "__vibespace_status=\$?",
            "VIBESPACE_SESSION_CWD=\"\$(pwd)\"; export VIBESPACE_SESSION_CWD",
            "export -p > \"\$__vibespace_state.tmp\" 2>/dev/null || true",
            "mv \"\$__vibespace_state.tmp\" \"\$__vibespace_state\" 2>/dev/null || true",
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

    private companion object {
        const val BUNDLED_RUNTIME_MARKER = ".vibespace-bundled-v3"
    }
}
