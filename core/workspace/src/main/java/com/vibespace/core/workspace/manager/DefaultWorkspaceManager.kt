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
        _currentProject.value = project
        recentProjectsRepository?.addRecentProject(project)

        // Seed basic project structure if empty
        val rootUri = project.rootUri
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

    override suspend fun listFiles(parentDirectoryUri: String): VibeResult<List<VirtualFile>> =
        withContext(dispatchers.io) {
            val children = directoryChildren[parentDirectoryUri] ?: emptyList()
            VibeResult.Success(children.sortedWith(compareBy({ it.isFile }, { it.name })))
        }

    private val ignoredDirectories = setOf(
        ".git", ".gradle", "node_modules", "build", ".idea", "__pycache__", ".venv", "venv", ".next", "dist", ".target"
    )

    override suspend fun getAllProjectFiles(rootUri: String?): VibeResult<List<VirtualFile>> =
        withContext(dispatchers.io) {
            val targetRoot = rootUri ?: _currentProject.value?.rootUri
            if (targetRoot == null) {
                return@withContext VibeResult.Success(emptyList())
            }

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
            val physicalPath = if (targetRoot.startsWith("file://")) {
                targetRoot.removePrefix("file://")
            } else if (targetRoot.startsWith("/")) {
                targetRoot
            } else {
                null
            }

            if (physicalPath != null) {
                val rootDir = java.io.File(physicalPath)
                if (rootDir.exists() && rootDir.isDirectory) {
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
            }

            VibeResult.Success(resultList.sortedWith(compareBy({ it.isFile }, { it.name })))
        }

    override suspend fun readFile(fileUri: String): VibeResult<String> = withContext(dispatchers.io) {
        val content = fileContents[fileUri]
        if (content != null) {
            VibeResult.Success(content)
        } else {
            VibeResult.Error("File not found: $fileUri")
        }
    }

    override suspend fun writeFile(fileUri: String, content: String): VibeResult<Unit> =
        withContext(dispatchers.io) {
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
                            sizeBytes = content.toByteArray().size.toLong()
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
        val fileUri = "$parentDirectoryUri/$fileName"
        val newFile = VirtualFile(
            uri = fileUri,
            name = fileName,
            nodeType = FileNodeType.FILE,
            parentUri = parentDirectoryUri,
            sizeBytes = initialContent.toByteArray().size.toLong()
        )

        val children = directoryChildren.getOrPut(parentDirectoryUri) { mutableListOf() }
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
        val dirUri = "$parentDirectoryUri/$directoryName"
        val newDir = VirtualFile(
            uri = dirUri,
            name = directoryName,
            nodeType = FileNodeType.DIRECTORY,
            parentUri = parentDirectoryUri
        )

        val children = directoryChildren.getOrPut(parentDirectoryUri) { mutableListOf() }
        if (children.any { it.name == directoryName }) {
            return@withContext VibeResult.Error("A directory with name '$directoryName' already exists.")
        }
        children.add(newDir)
        directoryChildren[dirUri] = mutableListOf()
        VibeResult.Success(newDir)
    }

    override suspend fun delete(targetUri: String): VibeResult<Unit> = withContext(dispatchers.io) {
        fileContents.remove(targetUri)
        directoryChildren.remove(targetUri)

        // Remove from parent
        directoryChildren.values.forEach { children ->
            children.removeAll { it.uri == targetUri }
        }
        VibeResult.Success(Unit)
    }

    override suspend fun rename(targetUri: String, newName: String): VibeResult<VirtualFile> =
        withContext(dispatchers.io) {
            var foundFile: VirtualFile? = null
            var parentDir: String? = null

            for ((pUri, children) in directoryChildren) {
                val index = children.indexOfFirst { it.uri == targetUri }
                if (index != -1) {
                    val old = children[index]
                    val newUri = "${old.parentUri}/$newName"
                    val updated = old.copy(uri = newUri, name = newName)
                    children[index] = updated
                    foundFile = updated
                    parentDir = pUri

                    if (old.isFile) {
                        val content = fileContents.remove(targetUri) ?: ""
                        fileContents[newUri] = content
                    } else {
                        val childList = directoryChildren.remove(targetUri) ?: mutableListOf()
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
