package com.vibespace.core.workspace.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val normalizedProject = project.copy(rootUri = normalizedRoot)
        _currentProject.value = normalizedProject
        recentProjectsRepository?.addRecentProject(normalizedProject)

        // Persist SAF URI permissions where applicable
        if (project.isStorageAccessFramework && project.rootUri.startsWith("content://") && context != null) {
            try {
                val uri = Uri.parse(project.rootUri)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Throwable) {}
        }

        // Keep seeded demo workspaces clearly separate from real user projects
        val rootUri = normalizedRoot
        val isDemoProject = project.id == "demo" ||
                project.name.contains("Demo", ignoreCase = true) ||
                project.rootUri.startsWith("vibe://demo") ||
                project.rootUri.startsWith("vibe://projects")

        if (isDemoProject && !directoryChildren.containsKey(rootUri)) {
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

            fileContents[readmeUri] = "# ${project.name}\n\nBuilt with VibeSpace Mobile IDE."
            fileContents[mainPyUri] = """
                # VibeSpace Python Runner
                name = "VibeSpace Mobile"
                print("Welcome to " + name + "!")
                print("Multiplication test:", 21 * 2)
            """.trimIndent()
            fileContents[indexJsUri] = """
                // VibeSpace JavaScript Runner
                const app = "VibeSpace";
                console.log("Running in " + app);
                console.log("Addition test:", 2000 + 26);
            """.trimIndent()
            fileContents[mainKtUri] = """
                fun main() {
                    println("Hello from VibeSpace on Android!")
                }
            """.trimIndent()
        }

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

    private fun validateWithinProjectRoot(targetFile: File, rootDir: File): VibeResult<Unit> {
        try {
            val canonicalTarget = targetFile.canonicalPath
            val canonicalRoot = rootDir.canonicalPath
            if (!canonicalTarget.startsWith(canonicalRoot)) {
                return VibeResult.Error("Security error: Target path '$canonicalTarget' escapes project root '$canonicalRoot'.")
            }
        } catch (e: Exception) {
            return VibeResult.Error("Failed to validate path traversal: ${e.message}", e)
        }
        return VibeResult.Success(Unit)
    }

    private val ignoredDirectories = setOf(
        ".git", ".gradle", "node_modules", "build", ".idea", "__pycache__", ".venv", "venv", ".next", "dist", ".target"
    )

    override suspend fun listFiles(parentDirectoryUri: String): VibeResult<List<VirtualFile>> =
        withContext(dispatchers.io) {
            val normParent = normalizeUri(parentDirectoryUri)
            val inMemory = directoryChildren[normParent]
            if (inMemory != null && inMemory.isNotEmpty()) {
                return@withContext VibeResult.Success(inMemory.sortedWith(compareBy({ it.isFile }, { it.name })))
            }
            val diskDir = toPhysicalFile(normParent)
            if (diskDir != null && diskDir.exists() && diskDir.isDirectory) {
                val list = diskDir.listFiles() ?: emptyArray()
                val result = list.filter { !ignoredDirectories.contains(it.name) }.map { f ->
                    VirtualFile(
                        uri = f.toURI().toString().trimEnd('/'),
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
                val fileQueue = ArrayDeque<File>()
                fileQueue.add(rootDir)
                while (fileQueue.isNotEmpty()) {
                    val current = fileQueue.removeFirst()
                    val list = current.listFiles() ?: emptyArray()
                    for (f in list) {
                        if (f.isDirectory) {
                            if (!ignoredDirectories.contains(f.name)) {
                                val vf = VirtualFile(
                                    uri = f.toURI().toString().trimEnd('/'),
                                    name = f.name,
                                    nodeType = FileNodeType.DIRECTORY,
                                    parentUri = current.toURI().toString().trimEnd('/'),
                                    path = f.absolutePath
                                )
                                if (resultList.none { it.uri == vf.uri }) {
                                    resultList.add(vf)
                                }
                                fileQueue.add(f)
                            }
                        } else {
                            val vf = VirtualFile(
                                uri = f.toURI().toString(),
                                name = f.name,
                                nodeType = FileNodeType.FILE,
                                parentUri = current.toURI().toString().trimEnd('/'),
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

            VibeResult.Success(resultList.sortedWith(compareBy({ it.isFile }, { it.name })))
        }

    override suspend fun readFile(fileUri: String): VibeResult<String> = withContext(dispatchers.io) {
        val content = fileContents[fileUri]
        if (content != null) {
            return@withContext VibeResult.Success(content)
        }

        // Support SAF content:// URIs
        if (fileUri.startsWith("content://") && context != null) {
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
        if (diskFile != null && diskFile.exists() && diskFile.isFile) {
            try {
                val text = diskFile.readText()
                fileContents[fileUri] = text
                return@withContext VibeResult.Success(text)
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to read file from disk: ${e.message}", e)
            }
        }
        VibeResult.Error("File not found: $fileUri")
    }

    override suspend fun writeFile(fileUri: String, content: String): VibeResult<Unit> =
        withContext(dispatchers.io) {
            // Support SAF content:// URIs
            if (fileUri.startsWith("content://") && context != null) {
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
            val project = _currentProject.value
            val diskRoot = project?.rootUri?.let { toPhysicalFile(normalizeUri(it)) }

            if (diskFile != null && diskRoot != null && diskRoot.exists()) {
                val rootCheck = validateWithinProjectRoot(diskFile, diskRoot)
                if (rootCheck is VibeResult.Error) return@withContext rootCheck
            }

            if (diskFile != null) {
                try {
                    diskFile.parentFile?.mkdirs()
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
        val fileUri = "$normParent/$fileName"
        val diskParent = toPhysicalFile(normParent)
        val project = _currentProject.value
        val diskRoot = project?.rootUri?.let { toPhysicalFile(normalizeUri(it)) }

        val diskFile = if (diskParent != null) File(diskParent, fileName) else null
        if (diskFile != null && diskRoot != null && diskRoot.exists()) {
            val rootCheck = validateWithinProjectRoot(diskFile, diskRoot)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck
        }

        if (diskFile != null) {
            try {
                if (diskFile.exists()) {
                    return@withContext VibeResult.Error("A file with name '$fileName' already exists.")
                }
                diskFile.parentFile?.mkdirs()
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
        val dirUri = "$normParent/$directoryName"
        val diskParent = toPhysicalFile(normParent)
        val project = _currentProject.value
        val diskRoot = project?.rootUri?.let { toPhysicalFile(normalizeUri(it)) }

        val diskDir = if (diskParent != null) File(diskParent, directoryName) else null
        if (diskDir != null && diskRoot != null && diskRoot.exists()) {
            val rootCheck = validateWithinProjectRoot(diskDir, diskRoot)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck
        }

        if (diskDir != null) {
            try {
                if (diskDir.exists()) {
                    return@withContext VibeResult.Error("A directory with name '$directoryName' already exists.")
                }
                diskDir.mkdirs()
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to create directory on disk: ${e.message}", e)
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
        val project = _currentProject.value
        val diskRoot = project?.rootUri?.let { toPhysicalFile(normalizeUri(it)) }
        val diskTarget = toPhysicalFile(normTarget)

        if (diskTarget != null && diskRoot != null && diskTarget.exists()) {
            val rootCheck = validateWithinProjectRoot(diskTarget, diskRoot)
            if (rootCheck is VibeResult.Error) return@withContext rootCheck
            try {
                diskTarget.deleteRecursively()
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to delete from disk: ${e.message}", e)
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
            val project = _currentProject.value
            val diskRoot = project?.rootUri?.let { toPhysicalFile(normalizeUri(it)) }
            val diskTarget = toPhysicalFile(normTarget)
            val diskDest = if (diskTarget != null && diskTarget.parentFile != null) {
                File(diskTarget.parentFile, newName)
            } else null

            if (diskTarget != null && diskRoot != null && diskTarget.exists() && diskDest != null) {
                val rootCheck = validateWithinProjectRoot(diskDest, diskRoot)
                if (rootCheck is VibeResult.Error) return@withContext rootCheck
                if (diskDest.exists()) {
                    return@withContext VibeResult.Error("A file or directory named '$newName' already exists.")
                }
                try {
                    val renamed = diskTarget.renameTo(diskDest)
                    if (!renamed) {
                        return@withContext VibeResult.Error("Failed to rename disk file to '$newName'")
                    }
                } catch (e: Exception) {
                    return@withContext VibeResult.Error("Failed to rename on disk: ${e.message}", e)
                }
            }

            var foundFile: VirtualFile? = null
            for ((_, children) in directoryChildren) {
                val index = children.indexOfFirst { it.uri == normTarget || it.uri == targetUri }
                if (index != -1) {
                    val old = children[index]
                    val normParent = normalizeUri(old.parentUri)
                    val newUri = if (normParent.isNotEmpty()) "$normParent/$newName" else newName
                    val updated = old.copy(uri = newUri, name = newName, path = diskDest?.absolutePath ?: old.path)
                    children[index] = updated
                    foundFile = updated

                    if (old.isFile) {
                        val content = fileContents.remove(targetUri) ?: fileContents.remove(normTarget) ?: ""
                        fileContents[newUri] = content
                    } else {
                        // Directory rename: update all nested descendants in directoryChildren and fileContents!
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
