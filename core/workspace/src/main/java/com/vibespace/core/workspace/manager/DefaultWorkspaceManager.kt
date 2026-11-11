package com.vibespace.core.workspace.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.model.VirtualFile
import com.vibespace.core.workspace.repository.RecentProjectsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Production workspace implementation supporting physical disk storage, Android Storage Access Framework (SAF),
 * path traversal protection, hierarchical navigation, and clean demo isolation.
 */
class DefaultWorkspaceManager(
    private val context: Context? = null,
    private val recentProjectsRepository: RecentProjectsRepository? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : WorkspaceManager {

    constructor(dispatchers: DispatcherProvider) : this(null, null, dispatchers)
    constructor(recentProjectsRepository: RecentProjectsRepository?, dispatchers: DispatcherProvider) : this(null, recentProjectsRepository, dispatchers)

    private val _currentProject = MutableStateFlow<Project?>(null)
    override val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    // In-memory mapping cache for open buffers and virtual hierarchy
    private val fileContents = mutableMapOf<String, String>()
    private val directoryChildren = mutableMapOf<String, MutableList<VirtualFile>>()

    override suspend fun openProject(project: Project): VibeResult<Project> = withContext(dispatchers.io) {
        val normalizedRoot = normalizeUri(project.rootUri)

        // Persist SAF URI permissions and verify accessibility
        if (project.isStorageAccessFramework && project.rootUri.startsWith("content://") && context != null) {
            val uri = Uri.parse(project.rootUri)
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                val alreadyPersisted = context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
                if (!alreadyPersisted) {
                    return@withContext VibeResult.Error("Unable to retain access to '${project.name}': ${e.message}", e)
                }
            }

            try {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(project.rootUri))
                if (doc == null || !doc.exists() || !doc.isDirectory || !doc.canRead()) {
                    return@withContext VibeResult.Error("Storage permission revoked or project folder missing for '${project.name}'.")
                }
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Cannot access SAF project: ${e.message}", e)
            }
        }

        val projectRootOnDisk = toPhysicalFile(normalizedRoot)
        if (context != null && normalizedRoot.startsWith("vibe://") && projectRootOnDisk == null) {
            return@withContext VibeResult.Error("Invalid internal project location '${project.name}'.")
        }
        if (projectRootOnDisk != null && !projectRootOnDisk.exists()) {
            val isNewInternalProject = project.rootPath == null &&
                    (normalizedRoot.startsWith("vibe://projects/") || normalizedRoot == "vibe://demo")
            if (!isNewInternalProject) {
                return@withContext VibeResult.Error("Project location for '${project.name}' no longer exists.")
            }
            if (!projectRootOnDisk.mkdirs()) {
                return@withContext VibeResult.Error("Unable to create project directory '${project.name}'.")
            }
        }
        if (projectRootOnDisk != null && !projectRootOnDisk.isDirectory) {
            return@withContext VibeResult.Error("Project location for '${project.name}' is not a directory.")
        }
        val normalizedProject = project.copy(
            rootUri = normalizedRoot,
            rootPath = projectRootOnDisk?.absolutePath ?: project.rootPath
        )

        // Keep seeded demo workspaces clearly separate from real user projects
        val rootUri = normalizedRoot
        val isDemoProject = rootUri == "vibe://demo"
        val demoAlreadyHasFiles = projectRootOnDisk?.listFiles()?.isNotEmpty() == true

        if (isDemoProject && !directoryChildren.containsKey(rootUri) && !demoAlreadyHasFiles) {
            val rootChildren = mutableListOf<VirtualFile>()
            val srcDirUri = "$rootUri/src"
            val readmeUri = "$rootUri/README.md"
            val mainPyUri = "$rootUri/main.py"
            val indexJsUri = "$rootUri/index.js"
            val mainKtUri = "$srcDirUri/Main.kt"

            rootChildren.add(VirtualFile(uri = mainPyUri, name = "main.py", nodeType = FileNodeType.FILE, parentUri = rootUri))
            rootChildren.add(VirtualFile(uri = indexJsUri, name = "index.js", nodeType = FileNodeType.FILE, parentUri = rootUri))
            rootChildren.add(VirtualFile(uri = srcDirUri, name = "src", nodeType = FileNodeType.DIRECTORY, parentUri = rootUri))
            rootChildren.add(VirtualFile(uri = readmeUri, name = "README.md", nodeType = FileNodeType.FILE, parentUri = rootUri))
            directoryChildren[rootUri] = rootChildren

            val srcChildren = mutableListOf(
                VirtualFile(uri = mainKtUri, name = "Main.kt", nodeType = FileNodeType.FILE, parentUri = srcDirUri)
            )
            directoryChildren[srcDirUri] = srcChildren

            val demoFiles = linkedMapOf(
                readmeUri to "# ${project.name}\n\nBuilt with VibeSpace Mobile IDE.",
                mainPyUri to """
                # VibeSpace Python Runner
                name = "VibeSpace Mobile"
                print("Welcome to " + name + "!")
                print("Multiplication test:", 21 * 2)
            """.trimIndent(),
                indexJsUri to """
                // VibeSpace JavaScript Runner
                const app = "VibeSpace";
                console.log("Running in " + app);
                console.log("Addition test:", 2000 + 26);
            """.trimIndent(),
                mainKtUri to """
                fun main() {
                    println("Hello from VibeSpace on Android!")
                }
            """.trimIndent()
            )
            demoFiles.forEach { (uri, content) ->
                fileContents[uri] = content
                val destination = toPhysicalFile(uri)
                if (destination != null) {
                    try {
                        destination.parentFile?.mkdirs()
                        destination.writeText(content)
                    } catch (e: Exception) {
                        projectRootOnDisk?.deleteRecursively()
                        directoryChildren.remove(rootUri)
                        directoryChildren.remove(srcDirUri)
                        demoFiles.keys.forEach(fileContents::remove)
                        return@withContext VibeResult.Error("Unable to initialize the demo project: ${e.message}", e)
                    }
                }
            }
        }

        _currentProject.value = normalizedProject
        recentProjectsRepository?.addRecentProject(normalizedProject)
        VibeResult.Success(normalizedProject)
    }

    override suspend fun closeProject(): VibeResult<Unit> = withContext(dispatchers.io) {
        _currentProject.value = null
        VibeResult.Success(Unit)
    }

    private fun normalizeUri(uri: String?): String {
        if (uri.isNullOrEmpty()) return ""
        if (uri == "/" || uri == "file:///") return uri
        return uri.trimEnd('/')
    }

    private fun toPhysicalFile(uri: String): File? {
        if (uri.startsWith("content:")) return null
        if (uri.startsWith("vibe://")) {
            val appContext = context ?: return null
            return try {
                val parsed = Uri.parse(uri)
                val segments = parsed.pathSegments
                val root = when (parsed.host) {
                    "projects" -> File(appContext.filesDir, "projects")
                    "demo" -> File(appContext.filesDir, "demo")
                    else -> return null
                }.canonicalFile
                if (parsed.host == "projects" && segments.isEmpty()) return null
                val candidate = segments.fold(root) { parent, segment -> File(parent, segment) }.canonicalFile
                if ((parsed.host == "demo" && candidate == root) || candidate.path.startsWith(root.path + File.separator)) {
                    candidate
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        if (uri.startsWith("file:")) {
            return try {
                val u = java.net.URI.create(uri)
                File(u)
            } catch (_: Exception) {
                val raw = uri.replaceFirst(Regex("^file:/+"), "/")
                val p = if (raw.length >= 3 && raw[2] == ':') {
                    raw.substring(1)
                } else {
                    raw
                }
                File(p)
            }
        }
        val path = when {
            uri.startsWith("/") -> uri
            uri.length >= 3 && uri[1] == ':' && (uri[2] == '/' || uri[2] == '\\') -> uri
            else -> null
        } ?: return null
        return File(path)
    }

    private fun validateSafeName(name: String): VibeResult<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return VibeResult.Error("Name cannot be empty.")
        if (trimmed == "." || trimmed == "..") return VibeResult.Error("Invalid name: '$trimmed'.")
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("\u0000")) {
            return VibeResult.Error("Name cannot contain path separators or null bytes: '$trimmed'.")
        }
        return VibeResult.Success(Unit)
    }

    /**
     * Enforces strict workspace containment comparing canonical path components.
     * Prevents symlink escapes, sibling directory prefix bypasses, and operations escaping the project root.
     */
    private fun validateWithinProjectRoot(targetFile: File, rootDir: File): VibeResult<Unit> {
        return try {
            val targetNorm = targetFile.toPath().normalize().toAbsolutePath()
            val rootNorm = rootDir.toPath().normalize().toAbsolutePath()

            if (!targetNorm.startsWith(rootNorm)) {
                return VibeResult.Error("Security error: Target path '$targetNorm' escapes project root '$rootNorm'.")
            }

            if (rootDir.exists()) {
                val rootCanonical = rootDir.canonicalFile.toPath()
                var existingAncestor: File? = targetFile
                while (existingAncestor != null && !existingAncestor.exists()) {
                    existingAncestor = existingAncestor.parentFile
                }
                if (existingAncestor != null) {
                    val canonicalTarget = existingAncestor.canonicalFile.toPath()
                    if (!canonicalTarget.startsWith(rootCanonical)) {
                        return VibeResult.Error("Security error: Canonical target '$canonicalTarget' escapes canonical project root '$rootCanonical'.")
                    }
                }
            }

            VibeResult.Success(Unit)
        } catch (e: Exception) {
            VibeResult.Error("Failed to validate path containment: ${e.message}", e)
        }
    }

    private fun validatePhysicalWorkspacePath(target: File): VibeResult<Unit> {
        val project = _currentProject.value
            ?: return VibeResult.Error("Security error: No active project root to validate file containment.")
        val root = toPhysicalFile(normalizeUri(project.rootUri))
            ?: return VibeResult.Error("Security error: Active project root cannot be resolved to a physical path.")
        return validateWithinProjectRoot(target, root)
    }

    private fun isWithinActiveSafTree(targetUri: String): Boolean {
        val project = _currentProject.value ?: return false
        return try {
            val root = Uri.parse(project.rootUri)
            val target = Uri.parse(targetUri)
            val rootSegments = root.pathSegments
            val targetSegments = target.pathSegments
            root.authority == target.authority &&
                    targetSegments.size >= rootSegments.size &&
                    targetSegments.take(rootSegments.size) == rootSegments
        } catch (_: Exception) {
            false
        }
    }

    private val ignoredDirectories = setOf(
        ".git", ".gradle", "node_modules", "build", ".idea", "__pycache__", ".venv", "venv", ".next", "dist", ".target"
    )

    override suspend fun listFiles(parentDirectoryUri: String): VibeResult<List<VirtualFile>> =
        withContext(dispatchers.io) {
            val normParent = normalizeUri(parentDirectoryUri)

            // Support SAF DocumentFile listing
            if (normParent.startsWith("content://") && context != null) {
                if (!isWithinActiveSafTree(normParent)) {
                    return@withContext VibeResult.Error("Security error: Directory is outside the active SAF project.")
                }
                try {
                    val treeUri = Uri.parse(normParent)
                    val doc = DocumentFile.fromTreeUri(context, treeUri)
                    if (doc != null && doc.isDirectory) {
                        val children = doc.listFiles().filter { f ->
                            val n = f.name ?: ""
                            !ignoredDirectories.contains(n)
                        }.map { f ->
                            VirtualFile(
                                uri = f.uri.toString(),
                                name = f.name ?: "unnamed",
                                nodeType = if (f.isDirectory) FileNodeType.DIRECTORY else FileNodeType.FILE,
                                parentUri = normParent,
                                sizeBytes = if (f.isFile) f.length() else 0L,
                                lastModifiedTimestamp = f.lastModified()
                            )
                        }
                        directoryChildren[normParent] = children.toMutableList()
                        return@withContext VibeResult.Success(children.sortedWith(compareBy({ it.isFile }, { it.name })))
                    } else if (doc != null && !doc.canRead()) {
                        return@withContext VibeResult.Error("SAF permission revoked for directory '$normParent'")
                    }
                } catch (e: Throwable) {
                    return@withContext VibeResult.Error("Failed to list SAF directory: ${e.message}", e)
                }
            }

            val inMemory = directoryChildren[normParent]
            val diskDir = toPhysicalFile(normParent)
            if (diskDir != null && (!diskDir.exists() || !diskDir.isDirectory)) {
                return@withContext VibeResult.Error("Directory not found: $normParent")
            }
            if (diskDir != null) {
                val rootCheck = validatePhysicalWorkspacePath(diskDir)
                if (rootCheck is VibeResult.Error) return@withContext rootCheck
                val list = diskDir.listFiles() ?: emptyArray()
                val result = list.filter { !ignoredDirectories.contains(it.name) }.map { f ->
                    VirtualFile(
                        uri = childUri(normParent, f.name),
                        name = f.name,
                        nodeType = if (f.isDirectory) FileNodeType.DIRECTORY else FileNodeType.FILE,
                        parentUri = normParent,
                        sizeBytes = if (f.isFile) f.length() else 0L,
                        lastModifiedTimestamp = f.lastModified(),
                        path = f.absolutePath
                    )
                }.toMutableList()
                directoryChildren[normParent] = result
                return@withContext VibeResult.Success(result.sortedWith(compareBy({ it.isFile }, { it.name })))
            }
            if (inMemory != null && inMemory.isNotEmpty()) {
                return@withContext VibeResult.Success(inMemory.sortedWith(compareBy({ it.isFile }, { it.name })))
            }
            val fallback = inMemory ?: emptyList()
            VibeResult.Success(fallback.sortedWith(compareBy({ it.isFile }, { it.name })))
        }

    override suspend fun getAllProjectFiles(rootUri: String?): VibeResult<List<VirtualFile>> =
        withContext(dispatchers.io) {
            val rawRoot = rootUri ?: _currentProject.value?.rootUri
            if (rawRoot == null) {
                return@withContext VibeResult.Success(emptyList())
            }
            val targetRoot = normalizeUri(rawRoot)
            val resultList = mutableListOf<VirtualFile>()
            val queue = ArrayDeque<String>()
            queue.add(targetRoot)

            while (queue.isNotEmpty()) {
                val currentDir = queue.removeFirst()
                val children = directoryChildren[currentDir] ?: emptyList()
                for (child in children) {
                    if (child.isDirectory) {
                        if (!ignoredDirectories.contains(child.name)) {
                            resultList.add(child)
                            queue.add(child.uri)
                        }
                    } else {
                        resultList.add(child)
                    }
                }
            }

            // Also check if target is a physical directory on disk
            val rootDir = toPhysicalFile(targetRoot)
            if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
                val rootCheck = validatePhysicalWorkspacePath(rootDir)
                if (rootCheck is VibeResult.Error) return@withContext rootCheck
                data class DirectoryToVisit(val file: File, val uri: String)
                val fileQueue = ArrayDeque<DirectoryToVisit>()
                fileQueue.add(DirectoryToVisit(rootDir, targetRoot))
                while (fileQueue.isNotEmpty()) {
                    val current = fileQueue.removeFirst()
                    val list = current.file.listFiles() ?: emptyArray()
                    for (f in list) {
                        if (f.isDirectory) {
                            if (!ignoredDirectories.contains(f.name)) {
                                val childUri = childUri(current.uri, f.name)
                                val vf = VirtualFile(
                                    uri = childUri,
                                    name = f.name,
                                    nodeType = FileNodeType.DIRECTORY,
                                    parentUri = current.uri,
                                    path = f.absolutePath
                                )
                                if (resultList.none { it.uri == vf.uri }) {
                                    resultList.add(vf)
                                }
                                fileQueue.add(DirectoryToVisit(f, childUri))
                            }
                        } else {
                            val vf = VirtualFile(
                                uri = childUri(current.uri, f.name),
                                name = f.name,
                                nodeType = FileNodeType.FILE,
                                parentUri = current.uri,
                                sizeBytes = f.length(),
                                path = f.absolutePath
                            )
                            if (resultList.none { it.uri == vf.uri }) {
                                resultList.add(vf)
                            }
                        }
                    }
                }
            }

            // Also check if target is a SAF directory
            if (targetRoot.startsWith("content://") && context != null) {
                if (!isWithinActiveSafTree(targetRoot)) {
                    return@withContext VibeResult.Error("Security error: Directory is outside the active SAF project.")
                }
                try {
                    val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(targetRoot))
                    if (rootDoc != null && rootDoc.isDirectory) {
                        val docQueue = ArrayDeque<DocumentFile>()
                        docQueue.add(rootDoc)
                        while (docQueue.isNotEmpty()) {
                            val cur = docQueue.removeFirst()
                            val list = cur.listFiles()
                            for (f in list) {
                                val name = f.name ?: continue
                                if (!ignoredDirectories.contains(name)) {
                                    val vf = VirtualFile(
                                        uri = f.uri.toString(),
                                        name = name,
                                        nodeType = if (f.isDirectory) FileNodeType.DIRECTORY else FileNodeType.FILE,
                                        parentUri = cur.uri.toString(),
                                        sizeBytes = if (f.isFile) f.length() else 0L,
                                        lastModifiedTimestamp = f.lastModified()
                                    )
                                    if (resultList.none { it.uri == vf.uri }) {
                                        resultList.add(vf)
                                    }
                                    if (f.isDirectory) {
                                        docQueue.add(f)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            VibeResult.Success(resultList.sortedWith(compareBy({ it.isFile }, { it.name })))
        }

    override suspend fun readFile(fileUri: String): VibeResult<String> = withContext(dispatchers.io) {
        // Support SAF content:// URIs
        if (fileUri.startsWith("content://") && context != null) {
            if (!isWithinActiveSafTree(fileUri)) {
                return@withContext VibeResult.Error("Security error: File is outside the active SAF project.")
            }
            try {
                context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { input ->
                    val text = input.bufferedReader(StandardCharsets.UTF_8).readText()
                    fileContents[fileUri] = text
                    return@withContext VibeResult.Success(text)
                } ?: return@withContext VibeResult.Error("Unable to open SAF content stream for $fileUri")
            } catch (e: Throwable) {
                return@withContext VibeResult.Error("Failed to read SAF content: ${e.message}", e)
            }
        }

        val diskFile = toPhysicalFile(fileUri)
        if (diskFile != null) {
            val rootCheck = validatePhysicalWorkspacePath(diskFile)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck
            if (!diskFile.exists() || !diskFile.isFile) {
                return@withContext VibeResult.Error("File not found: $fileUri")
            }
            try {
                val text = diskFile.readText()
                fileContents[fileUri] = text
                return@withContext VibeResult.Success(text)
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to read file from disk: ${e.message}", e)
            }
        }
        fileContents[fileUri]?.let { return@withContext VibeResult.Success(it) }
        VibeResult.Error("File not found: $fileUri")
    }

    private fun childUri(parentUri: String, name: String): String =
        if (parentUri.startsWith("vibe://")) "$parentUri/${Uri.encode(name)}"
        else File(toPhysicalFile(parentUri) ?: File(parentUri), name).toURI().toString().trimEnd('/')

    override suspend fun writeFile(fileUri: String, content: String): VibeResult<Unit> =
        withContext(dispatchers.io) {
            // Support SAF content:// URIs
            if (fileUri.startsWith("content://") && context != null) {
                if (!isWithinActiveSafTree(fileUri)) {
                    return@withContext VibeResult.Error("Security error: File is outside the active SAF project.")
                }
                try {
                    context.contentResolver.openOutputStream(Uri.parse(fileUri), "wt")?.use { output ->
                        output.write(content.toByteArray(StandardCharsets.UTF_8))
                    } ?: return@withContext VibeResult.Error("Unable to open SAF write stream for $fileUri")
                    fileContents[fileUri] = content
                    return@withContext VibeResult.Success(Unit)
                } catch (e: Throwable) {
                    return@withContext VibeResult.Error("Failed to write SAF content: ${e.message}", e)
                }
            }

            val diskFile = toPhysicalFile(fileUri)
            if (diskFile != null) {
                val project = _currentProject.value
                    ?: return@withContext VibeResult.Error("Security error: No active project root to validate file containment.")
                val diskRoot = toPhysicalFile(normalizeUri(project.rootUri))
                    ?: return@withContext VibeResult.Error("Security error: Project root cannot be resolved to physical path.")
                val rootCheck = validateWithinProjectRoot(diskFile, diskRoot)
                if (rootCheck is VibeResult.Error) return@withContext rootCheck

                try {
                    diskFile.parentFile?.let { p ->
                        if (!p.exists()) {
                            val created = p.mkdirs()
                            if (!created && !p.exists()) {
                                return@withContext VibeResult.Error("Failed to create parent directories on disk for '${diskFile.absolutePath}'.")
                            }
                        }
                    }
                    diskFile.writeText(content)
                } catch (e: Exception) {
                    return@withContext VibeResult.Error("Failed to write to disk: ${e.message}", e)
                }
            }

            fileContents[fileUri] = content
            val parentUri = fileUri.substringBeforeLast('/', "")
            if (parentUri.isNotEmpty()) {
                val fileName = fileUri.substringAfterLast('/')
                val children = directoryChildren.getOrPut(parentUri) { mutableListOf() }
                if (children.none { it.name == fileName }) {
                    children.add(
                        VirtualFile(
                            uri = fileUri,
                            name = fileName,
                            nodeType = FileNodeType.FILE,
                            parentUri = parentUri,
                            sizeBytes = content.toByteArray().size.toLong(),
                            path = diskFile?.absolutePath
                        )
                    )
                }
            }
            VibeResult.Success(Unit)
        }

    override suspend fun createFile(
        parentDirectoryUri: String,
        fileName: String,
        initialContent: String
    ): VibeResult<VirtualFile> = withContext(dispatchers.io) {
        val nameValidation = validateSafeName(fileName)
        if (nameValidation is VibeResult.Error) return@withContext nameValidation

        val normParent = normalizeUri(parentDirectoryUri)

        // Support SAF document creation
        if (normParent.startsWith("content://") && context != null) {
            if (!isWithinActiveSafTree(normParent)) {
                return@withContext VibeResult.Error("Security error: Parent directory is outside the active SAF project.")
            }
            try {
                val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(normParent))
                    ?: return@withContext VibeResult.Error("SAF parent directory unavailable: $normParent")
                val mimeType = if (fileName.endsWith(".json")) "application/json" else "text/plain"
                val newDoc = parentDoc.createFile(mimeType, fileName)
                    ?: return@withContext VibeResult.Error("Failed to create SAF document '$fileName' in $normParent")

                val output = context.contentResolver.openOutputStream(newDoc.uri, "wt")
                    ?: run {
                        newDoc.delete()
                        return@withContext VibeResult.Error("Unable to open SAF write stream for '${newDoc.name}'.")
                    }
                output.use { out ->
                    out.write(initialContent.toByteArray(StandardCharsets.UTF_8))
                }
                val newFile = VirtualFile(
                    uri = newDoc.uri.toString(),
                    name = fileName,
                    nodeType = FileNodeType.FILE,
                    parentUri = normParent,
                    sizeBytes = initialContent.toByteArray().size.toLong()
                )
                directoryChildren.getOrPut(normParent) { mutableListOf() }.add(newFile)
                fileContents[newFile.uri] = initialContent
                return@withContext VibeResult.Success(newFile)
            } catch (e: Throwable) {
                return@withContext VibeResult.Error("Failed to create SAF file: ${e.message}", e)
            }
        }

        val fileUri = childUri(normParent, fileName)
        val diskParent = toPhysicalFile(normParent)
        val diskFile = if (diskParent != null) File(diskParent, fileName) else null

        if (diskFile != null) {
            val project = _currentProject.value
                ?: return@withContext VibeResult.Error("Security error: No active project root to validate file containment.")
            val diskRoot = toPhysicalFile(normalizeUri(project.rootUri))
                ?: return@withContext VibeResult.Error("Security error: Project root cannot be resolved to physical path.")
            val rootCheck = validateWithinProjectRoot(diskFile, diskRoot)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck

            if (diskFile.exists()) {
                return@withContext VibeResult.Error("A file with name '$fileName' already exists.")
            }
            try {
                diskFile.parentFile?.let { p ->
                    if (!p.exists()) {
                        val created = p.mkdirs()
                        if (!created && !p.exists()) {
                            return@withContext VibeResult.Error("Failed to create parent directory for '${diskFile.absolutePath}'.")
                        }
                    }
                }
                diskFile.writeText(initialContent)
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to create file on disk: ${e.message}", e)
            }
        }

        val children = directoryChildren.getOrPut(normParent) { mutableListOf() }
        if (children.any { it.name == fileName }) {
            return@withContext VibeResult.Error("A file with name '$fileName' already exists.")
        }

        val newFile = VirtualFile(
            uri = fileUri,
            name = fileName,
            nodeType = FileNodeType.FILE,
            parentUri = normParent,
            sizeBytes = initialContent.toByteArray().size.toLong(),
            path = diskFile?.absolutePath
        )

        children.add(newFile)
        fileContents[fileUri] = initialContent
        VibeResult.Success(newFile)
    }

    override suspend fun createDirectory(
        parentDirectoryUri: String,
        directoryName: String
    ): VibeResult<VirtualFile> = withContext(dispatchers.io) {
        val nameValidation = validateSafeName(directoryName)
        if (nameValidation is VibeResult.Error) return@withContext nameValidation

        val normParent = normalizeUri(parentDirectoryUri)

        // Support SAF directory creation
        if (normParent.startsWith("content://") && context != null) {
            if (!isWithinActiveSafTree(normParent)) {
                return@withContext VibeResult.Error("Security error: Parent directory is outside the active SAF project.")
            }
            try {
                val parentDoc = DocumentFile.fromTreeUri(context, Uri.parse(normParent))
                    ?: return@withContext VibeResult.Error("SAF parent directory unavailable: $normParent")
                val newDoc = parentDoc.createDirectory(directoryName)
                    ?: return@withContext VibeResult.Error("Failed to create SAF directory '$directoryName'")
                val newDir = VirtualFile(
                    uri = newDoc.uri.toString(),
                    name = directoryName,
                    nodeType = FileNodeType.DIRECTORY,
                    parentUri = normParent
                )
                directoryChildren.getOrPut(normParent) { mutableListOf() }.add(newDir)
                directoryChildren[newDir.uri] = mutableListOf()
                return@withContext VibeResult.Success(newDir)
            } catch (e: Throwable) {
                return@withContext VibeResult.Error("Failed to create SAF directory: ${e.message}", e)
            }
        }

        val dirUri = childUri(normParent, directoryName)
        val diskParent = toPhysicalFile(normParent)
        val diskDir = if (diskParent != null) File(diskParent, directoryName) else null

        if (diskDir != null) {
            val project = _currentProject.value
                ?: return@withContext VibeResult.Error("Security error: No active project root to validate file containment.")
            val diskRoot = toPhysicalFile(normalizeUri(project.rootUri))
                ?: return@withContext VibeResult.Error("Security error: Project root cannot be resolved to physical path.")
            val rootCheck = validateWithinProjectRoot(diskDir, diskRoot)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck

            if (diskDir.exists()) {
                return@withContext VibeResult.Error("A directory with name '$directoryName' already exists.")
            }
            val created = diskDir.mkdirs()
            if (!created && !diskDir.exists()) {
                return@withContext VibeResult.Error("Failed to create directory on disk at '${diskDir.absolutePath}'.")
            }
        }

        val children = directoryChildren.getOrPut(normParent) { mutableListOf() }
        if (children.any { it.name == directoryName }) {
            return@withContext VibeResult.Error("A directory with name '$directoryName' already exists.")
        }

        val newDir = VirtualFile(
            uri = dirUri,
            name = directoryName,
            nodeType = FileNodeType.DIRECTORY,
            parentUri = normParent,
            path = diskDir?.absolutePath
        )

        children.add(newDir)
        directoryChildren[dirUri] = mutableListOf()
        VibeResult.Success(newDir)
    }

    override suspend fun delete(targetUri: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val normTarget = normalizeUri(targetUri)
        if (normTarget == _currentProject.value?.rootUri) {
            return@withContext VibeResult.Error("The project root cannot be deleted from the explorer.")
        }

        // Support SAF document deletion
        if (normTarget.startsWith("content://") && context != null) {
            if (!isWithinActiveSafTree(normTarget)) {
                return@withContext VibeResult.Error("Security error: Target is outside the active SAF project.")
            }
            try {
                val uri = Uri.parse(normTarget)
                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                val deleted = doc?.delete() ?: false
                if (!deleted) {
                    return@withContext VibeResult.Error("Failed to delete SAF document at $normTarget")
                }
            } catch (e: Throwable) {
                return@withContext VibeResult.Error("Failed to delete SAF document: ${e.message}", e)
            }
        }

        val diskTarget = toPhysicalFile(normTarget)
        if (diskTarget != null && diskTarget.exists()) {
            val project = _currentProject.value
                ?: return@withContext VibeResult.Error("Security error: No active project root to validate file containment.")
            val diskRoot = toPhysicalFile(normalizeUri(project.rootUri))
                ?: return@withContext VibeResult.Error("Security error: Project root cannot be resolved to physical path.")
            val rootCheck = validateWithinProjectRoot(diskTarget, diskRoot)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck

            val deleted = diskTarget.deleteRecursively()
            if (!deleted && diskTarget.exists()) {
                return@withContext VibeResult.Error("Failed to delete physical path '${diskTarget.absolutePath}'.")
            }
        }

        // Recursively remove target and all nested descendants from in-memory maps
        val targetPrefix1 = "$normTarget/"
        val targetPrefix2 = "$targetUri/"
        fileContents.keys.filter { it == normTarget || it == targetUri || it.startsWith(targetPrefix1) || it.startsWith(targetPrefix2) }
            .toList().forEach { fileContents.remove(it) }

        directoryChildren.keys.filter { it == normTarget || it == targetUri || it.startsWith(targetPrefix1) || it.startsWith(targetPrefix2) }
            .toList().forEach { directoryChildren.remove(it) }

        // Remove from parent directory list
        directoryChildren.values.forEach { children ->
            children.removeAll { it.uri == normTarget || it.uri == targetUri }
        }

        VibeResult.Success(Unit)
    }

    override suspend fun rename(targetUri: String, newName: String): VibeResult<VirtualFile> =
        withContext(dispatchers.io) {
            val nameValidation = validateSafeName(newName)
            if (nameValidation is VibeResult.Error) return@withContext nameValidation

            val normTarget = normalizeUri(targetUri)
            var safRenamedUri: String? = null

            // Support SAF document rename
            if (normTarget.startsWith("content://") && context != null) {
                if (!isWithinActiveSafTree(normTarget)) {
                    return@withContext VibeResult.Error("Security error: Target is outside the active SAF project.")
                }
                try {
                    val uri = Uri.parse(normTarget)
                    val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                    val renamed = doc?.renameTo(newName) ?: false
                    if (!renamed) {
                        return@withContext VibeResult.Error("Failed to rename SAF document to '$newName'")
                    }
                    safRenamedUri = doc?.parentFile?.findFile(newName)?.uri?.toString() ?: doc?.uri?.toString()
                } catch (e: Throwable) {
                    return@withContext VibeResult.Error("Failed to rename SAF document: ${e.message}", e)
                }
            }

            val diskTarget = toPhysicalFile(normTarget)
            val diskDest = if (diskTarget != null && diskTarget.parentFile != null) {
                File(diskTarget.parentFile, newName)
            } else null

            if (diskTarget != null && diskTarget.exists() && diskDest != null) {
                val project = _currentProject.value
                    ?: return@withContext VibeResult.Error("Security error: No active project root to validate file containment.")
                val diskRoot = toPhysicalFile(normalizeUri(project.rootUri))
                    ?: return@withContext VibeResult.Error("Security error: Project root cannot be resolved to physical path.")
                val rootCheck = validateWithinProjectRoot(diskDest, diskRoot)
                if (rootCheck is VibeResult.Error) return@withContext rootCheck

                if (diskDest.exists()) {
                    return@withContext VibeResult.Error("A file or directory named '$newName' already exists.")
                }
                val renamed = diskTarget.renameTo(diskDest)
                if (!renamed) {
                    return@withContext VibeResult.Error("Failed to rename disk file to '$newName'")
                }
            }

            var foundFile: VirtualFile? = null
            for ((_, children) in directoryChildren) {
                val index = children.indexOfFirst { it.uri == normTarget || it.uri == targetUri }
                if (index != -1) {
                    val old = children[index]
                    val normParent = normalizeUri(old.parentUri)
                    val newUri = safRenamedUri ?: childUri(normParent, newName)
                    val updated = old.copy(uri = newUri, name = newName, path = diskDest?.absolutePath ?: old.path)
                    children[index] = updated
                    foundFile = updated

                    if (safRenamedUri != null) {
                        val oldPrefix = "$normTarget/"
                        fileContents.keys.filter { it == normTarget || it.startsWith(oldPrefix) }
                            .toList().forEach(fileContents::remove)
                        directoryChildren.keys.filter { it == normTarget || it.startsWith(oldPrefix) }
                            .toList().forEach(directoryChildren::remove)
                    } else if (old.isFile) {
                        val content = fileContents.remove(targetUri) ?: fileContents.remove(normTarget) ?: ""
                        fileContents[newUri] = content
                    } else {
                        // Directory rename: update all nested descendants in directoryChildren and fileContents
                        val oldPrefix = "$normTarget/"
                        val newPrefix = "$newUri/"

                        val matchingContents = fileContents.filterKeys { it.startsWith(oldPrefix) }.toMap()
                        matchingContents.forEach { (oldKey, v) ->
                            fileContents.remove(oldKey)
                            fileContents[oldKey.replaceFirst(oldPrefix, newPrefix)] = v
                        }

                        val matchingDirs = directoryChildren.filterKeys { it.startsWith(oldPrefix) || it == normTarget || it == targetUri }.toMap()
                        matchingDirs.forEach { (oldKey, childList) ->
                            directoryChildren.remove(oldKey)
                            val targetKey = if (oldKey == normTarget || oldKey == targetUri) newUri else oldKey.replaceFirst(oldPrefix, newPrefix)
                            val updatedChildList = childList.map { child ->
                                child.copy(
                                    uri = child.uri.replaceFirst(oldPrefix, newPrefix),
                                    parentUri = child.parentUri?.replaceFirst(oldPrefix, newPrefix)
                                )
                            }.toMutableList()
                            directoryChildren[targetKey] = updatedChildList
                        }
                    }
                    break
                }
            }

            if (foundFile != null) {
                VibeResult.Success(foundFile)
            } else {
                VibeResult.Error("Target file not found for rename: $targetUri")
            }
        }
}
