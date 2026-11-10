package com.vibespace.core.workspace.manager

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

/**
 * Default workspace implementation supporting project management, hierarchical navigation,
 * and virtualized/disk storage operations.
 */
class DefaultWorkspaceManager(
    private val recentProjectsRepository: RecentProjectsRepository? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : WorkspaceManager {

    private val _currentProject = MutableStateFlow<Project?>(null)
    override val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    // Virtual in-memory filesystem map for portable execution and initial workspaces
    // Key: file uri, Value: content
    private val fileContents = mutableMapOf<String, String>()
    // Key: directory uri, Value: list of child VirtualFile
    private val directoryChildren = mutableMapOf<String, MutableList<VirtualFile>>()

    override suspend fun openProject(project: Project): VibeResult<Project> = withContext(dispatchers.io) {
        val normalizedRoot = normalizeUri(project.rootUri)
        val normalizedProject = project.copy(rootUri = normalizedRoot)
        _currentProject.value = normalizedProject
        recentProjectsRepository?.addRecentProject(normalizedProject)

        // Seed basic project structure if empty
        val rootUri = normalizedRoot
        if (!directoryChildren.containsKey(rootUri)) {
            val rootChildren = mutableListOf<VirtualFile>()
            val srcDirUri = "$rootUri/src"
            val readmeUri = "$rootUri/README.md"
            val mainPyUri = "$rootUri/main.py"
            val indexJsUri = "$rootUri/index.js"
            val mainKtUri = "$srcDirUri/Main.kt"

            rootChildren.add(
                VirtualFile(
                    uri = mainPyUri,
                    name = "main.py",
                    nodeType = FileNodeType.FILE,
                    parentUri = rootUri
                )
            )
            rootChildren.add(
                VirtualFile(
                    uri = indexJsUri,
                    name = "index.js",
                    nodeType = FileNodeType.FILE,
                    parentUri = rootUri
                )
            )
            rootChildren.add(
                VirtualFile(
                    uri = srcDirUri,
                    name = "src",
                    nodeType = FileNodeType.DIRECTORY,
                    parentUri = rootUri
                )
            )
            rootChildren.add(
                VirtualFile(
                    uri = readmeUri,
                    name = "README.md",
                    nodeType = FileNodeType.FILE,
                    parentUri = rootUri
                )
            )
            directoryChildren[rootUri] = rootChildren

            val srcChildren = mutableListOf(
                VirtualFile(
                    uri = mainKtUri,
                    name = "Main.kt",
                    nodeType = FileNodeType.FILE,
                    parentUri = srcDirUri
                )
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

        VibeResult.Success(project)
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

    private fun toPhysicalFile(uri: String): java.io.File? {
        if (uri.startsWith("file:")) {
            return try {
                val u = java.net.URI.create(uri)
                java.io.File(u)
            } catch (_: Exception) {
                val raw = uri.replaceFirst(Regex("^file:/+"), "/")
                val p = if (raw.length >= 3 && raw[2] == ':') {
                    raw.substring(1)
                } else {
                    raw
                }
                java.io.File(p)
            }
        }
        val path = when {
            uri.startsWith("/") -> uri
            uri.length >= 3 && uri[1] == ':' && (uri[2] == '/' || uri[2] == '\\') -> uri
            else -> null
        } ?: return null
        return java.io.File(path)
    }

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

    private val ignoredDirectories = setOf(
        ".git", ".gradle", "node_modules", "build", ".idea", "__pycache__", ".venv", "venv", ".next", "dist", ".target"
    )

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
                    val fileQueue = ArrayDeque<java.io.File>()
                    fileQueue.add(rootDir)
                    while (fileQueue.isNotEmpty()) {
                        val current = fileQueue.removeFirst()
                        val list = current.listFiles() ?: emptyArray()
                        for (f in list) {
                            if (f.isDirectory) {
                                if (!ignoredDirectories.contains(f.name)) {
                                    val vf = VirtualFile(
                                        uri = f.toURI().toString(),
                                        name = f.name,
                                        nodeType = FileNodeType.DIRECTORY,
                                        parentUri = current.toURI().toString(),
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
                                    parentUri = current.toURI().toString(),
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
            fileContents[fileUri] = content
            val diskFile = toPhysicalFile(fileUri)
            if (diskFile != null) {
                try {
                    diskFile.parentFile?.mkdirs()
                    diskFile.writeText(content)
                } catch (e: Exception) {
                    return@withContext VibeResult.Error("Failed to write to disk: ${e.message}", e)
                }
            }
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
        val normParent = normalizeUri(parentDirectoryUri)
        val fileUri = "$normParent/$fileName"
        val diskParent = toPhysicalFile(normParent)
        val diskFile = if (diskParent != null) java.io.File(diskParent, fileName) else null
        if (diskFile != null) {
            try {
                diskFile.parentFile?.mkdirs()
                if (diskFile.exists()) {
                    return@withContext VibeResult.Error("A file with name '$fileName' already exists.")
                }
                diskFile.writeText(initialContent)
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to create file on disk: ${e.message}", e)
            }
        }
        val newFile = VirtualFile(
            uri = fileUri,
            name = fileName,
            nodeType = FileNodeType.FILE,
            parentUri = normParent,
            sizeBytes = initialContent.toByteArray().size.toLong(),
            path = diskFile?.absolutePath
        )

        val children = directoryChildren.getOrPut(normParent) { mutableListOf() }
        if (children.any { it.name == fileName }) {
            return@withContext VibeResult.Error("A file with name '$fileName' already exists.")
        }
        children.add(newFile)
        fileContents[fileUri] = initialContent
        VibeResult.Success(newFile)
    }

    override suspend fun createDirectory(
        parentDirectoryUri: String,
        directoryName: String
    ): VibeResult<VirtualFile> = withContext(dispatchers.io) {
        val normParent = normalizeUri(parentDirectoryUri)
        val dirUri = "$normParent/$directoryName"
        val diskParent = toPhysicalFile(normParent)
        val diskDir = if (diskParent != null) java.io.File(diskParent, directoryName) else null
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
        val newDir = VirtualFile(
            uri = dirUri,
            name = directoryName,
            nodeType = FileNodeType.DIRECTORY,
            parentUri = normParent,
            path = diskDir?.absolutePath
        )

        val children = directoryChildren.getOrPut(normParent) { mutableListOf() }
        if (children.any { it.name == directoryName }) {
            return@withContext VibeResult.Error("A directory with name '$directoryName' already exists.")
        }
        children.add(newDir)
        directoryChildren[dirUri] = mutableListOf()
        VibeResult.Success(newDir)
    }

    override suspend fun delete(targetUri: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val normTarget = normalizeUri(targetUri)
        fileContents.remove(normTarget)
        fileContents.remove(targetUri)
        directoryChildren.remove(normTarget)
        directoryChildren.remove(targetUri)

        val diskTarget = toPhysicalFile(normTarget)
        if (diskTarget != null && diskTarget.exists()) {
            try {
                diskTarget.deleteRecursively()
            } catch (e: Exception) {
                return@withContext VibeResult.Error("Failed to delete from disk: ${e.message}", e)
            }
        }

        // Remove from parent
        directoryChildren.values.forEach { children ->
            children.removeAll { it.uri == normTarget || it.uri == targetUri }
        }
        VibeResult.Success(Unit)
    }

    override suspend fun rename(targetUri: String, newName: String): VibeResult<VirtualFile> =
        withContext(dispatchers.io) {
            var foundFile: VirtualFile? = null
            var parentDir: String? = null
            val normTarget = normalizeUri(targetUri)

            val diskTarget = toPhysicalFile(normTarget)
            val diskDest = if (diskTarget != null && diskTarget.parentFile != null) {
                java.io.File(diskTarget.parentFile, newName)
            } else null
            if (diskTarget != null && diskTarget.exists() && diskDest != null) {
                try {
                    diskTarget.renameTo(diskDest)
                } catch (_: Exception) {}
            }

            for ((pUri, children) in directoryChildren) {
                val index = children.indexOfFirst { it.uri == normTarget || it.uri == targetUri }
                if (index != -1) {
                    val old = children[index]
                    val normParent = normalizeUri(old.parentUri)
                    val newUri = if (normParent.isNotEmpty()) "$normParent/$newName" else newName
                    val updated = old.copy(uri = newUri, name = newName, path = diskDest?.absolutePath ?: old.path)
                    children[index] = updated
                    foundFile = updated
                    parentDir = pUri

                    if (old.isFile) {
                        val content = fileContents.remove(targetUri) ?: fileContents.remove(normTarget) ?: ""
                        fileContents[newUri] = content
                    } else {
                        val childList = directoryChildren.remove(targetUri) ?: directoryChildren.remove(normTarget) ?: mutableListOf()
                        directoryChildren[newUri] = childList
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
