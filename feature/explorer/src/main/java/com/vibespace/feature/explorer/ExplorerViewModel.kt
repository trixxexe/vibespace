package com.vibespace.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.workspace.git.GitManager
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val workspaceManager: WorkspaceManager,
    private val gitManager: GitManager? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val expandedDirs = mutableSetOf<String>()

    init {
        observeCurrentProject()
        observeGitStatus()
    }

    private fun observeGitStatus() {
        if (gitManager == null) return
        viewModelScope.launch(dispatchers.default) {
            gitManager.status.collect { gitStatus ->
                val statusMap = (gitStatus.stagedFiles + gitStatus.unstagedFiles)
                    .associate { it.path to it.statusType }
                _uiState.update { it.copy(fileGitStatuses = statusMap) }
            }
        }
    }

    private fun observeCurrentProject() {
        viewModelScope.launch(dispatchers.io) {
            workspaceManager.currentProject.collect { project ->
                _uiState.update { it.copy(currentProject = project) }
                if (project != null) {
                    refreshFiles()
                } else {
                    _uiState.update { it.copy(rootNodes = emptyList(), allProjectFiles = emptyList()) }
                }
            }
        }
    }

    fun refreshFiles() {
        val project = _uiState.value.currentProject ?: return
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val nodes = loadDirectoryNodes(project.rootUri, 0)
            val allFilesResult = workspaceManager.getAllProjectFiles(project.rootUri)
            when (allFilesResult) {
                is com.vibespace.core.common.result.VibeResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        rootNodes = nodes,
                        allProjectFiles = allFilesResult.data.filterNot { file -> file.name == ".vibespace" || file.name == "git_state.json" }
                    )
                }
                is com.vibespace.core.common.result.VibeResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = allFilesResult.message)
                }
                else -> _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load project files.") }
            }
        }
    }

    private suspend fun loadDirectoryNodes(directoryUri: String, level: Int): List<ExplorerNode> {
        val result = workspaceManager.listFiles(directoryUri)
        val files = when (result) {
            is com.vibespace.core.common.result.VibeResult.Success -> result.data
            is com.vibespace.core.common.result.VibeResult.Error -> {
                _uiState.update { it.copy(errorMessage = result.message) }
                return emptyList()
            }
            else -> return emptyList()
        }

        return files.filterNot { it.name == ".vibespace" || it.name == "git_state.json" }.map { file ->
            val isExpanded = expandedDirs.contains(file.uri)
            val children = if (file.isDirectory && isExpanded) {
                loadDirectoryNodes(file.uri, level + 1)
            } else {
                emptyList()
            }
            ExplorerNode(
                file = file,
                level = level,
                isExpanded = isExpanded,
                children = children
            )
        }
    }

    fun toggleFolder(folderUri: String) {
        if (expandedDirs.contains(folderUri)) {
            expandedDirs.remove(folderUri)
        } else {
            expandedDirs.add(folderUri)
        }
        refreshFiles()
    }

    fun selectFile(file: VirtualFile, onOpenFile: (VirtualFile) -> Unit) {
        if (file.isDirectory) {
            toggleFolder(file.uri)
        } else {
            _uiState.update { it.copy(selectedFileUri = file.uri) }
            onOpenFile(file)
        }
    }

    fun createNewFile(
        fileName: String,
        parentUri: String? = null,
        onFileCreated: ((VirtualFile) -> Unit)? = null
    ) {
        val trimmedName = fileName.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch(dispatchers.io) {
            val targetParent = parentUri ?: ensureCurrentProjectRootUri()
            when (val result = workspaceManager.createFile(targetParent, trimmedName)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    val createdFile = result.data
                    _uiState.update { it.copy(selectedFileUri = createdFile.uri, errorMessage = null) }
                    refreshFiles()
                    gitManager?.refreshStatus()
                    if (onFileCreated != null) {
                        kotlinx.coroutines.withContext(dispatchers.main) {
                            onFileCreated(createdFile)
                        }
                    }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun createNewFolder(folderName: String, parentUri: String? = null) {
        val trimmedName = folderName.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch(dispatchers.io) {
            val targetParent = parentUri ?: ensureCurrentProjectRootUri()
            when (val result = workspaceManager.createDirectory(targetParent, trimmedName)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    _uiState.update { it.copy(errorMessage = null) }
                    refreshFiles()
                    gitManager?.refreshStatus()
                }
                is com.vibespace.core.common.result.VibeResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    private suspend fun ensureCurrentProjectRootUri(): String {
        _uiState.value.currentProject?.rootUri?.let { return it }
        workspaceManager.currentProject.value?.rootUri?.let { return it }
        val defaultProject = com.vibespace.core.workspace.model.Project(
            id = "default-workspace",
            name = "Workspace",
            rootUri = "vibe://projects/default-workspace"
        )
        workspaceManager.openProject(defaultProject)
        _uiState.update { it.copy(currentProject = defaultProject) }
        return defaultProject.rootUri
    }

    fun deleteFile(fileUri: String) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = workspaceManager.delete(fileUri)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    refreshFiles()
                    gitManager?.refreshStatus()
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                else -> _uiState.update { it.copy(errorMessage = "Could not delete the selected item.") }
            }
        }
    }

    fun renameFile(fileUri: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(dispatchers.io) {
            when (val result = workspaceManager.rename(fileUri, newName.trim())) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    refreshFiles()
                    gitManager?.refreshStatus()
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                else -> _uiState.update { it.copy(errorMessage = "Could not rename the selected item.") }
            }
        }
    }

    fun duplicateFile(fileUri: String) {
        viewModelScope.launch(dispatchers.io) {
            val bytesResult = workspaceManager.readBytes(fileUri)
            if (bytesResult is com.vibespace.core.common.result.VibeResult.Success) {
                val originalName = fileUri.substringAfterLast('/')
                val dotIndex = originalName.lastIndexOf('.')
                val copyName = if (dotIndex > 0) {
                    "${originalName.substring(0, dotIndex)}_copy.${originalName.substring(dotIndex + 1)}"
                } else {
                    "${originalName}_copy"
                }
                val parentUri = fileUri.substringBeforeLast('/')
                when (val createResult = workspaceManager.createFile(parentUri, copyName)) {
                    is com.vibespace.core.common.result.VibeResult.Success -> {
                        workspaceManager.writeBytes(createResult.data.uri, bytesResult.data)
                        refreshFiles()
                        gitManager?.refreshStatus()
                    }
                    is com.vibespace.core.common.result.VibeResult.Error -> {
                        _uiState.update { it.copy(errorMessage = createResult.message) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun importExternalFile(
        fileName: String,
        bytes: ByteArray,
        onFileImported: ((VirtualFile) -> Unit)? = null
    ) {
        val cleanName = fileName.trim().substringAfterLast('/').ifBlank { "imported_file.txt" }
        viewModelScope.launch(dispatchers.io) {
            val targetParent = ensureCurrentProjectRootUri()
            when (val created = workspaceManager.createFile(targetParent, cleanName)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    workspaceManager.writeBytes(created.data.uri, bytes)
                    _uiState.update { it.copy(selectedFileUri = created.data.uri, errorMessage = null) }
                    refreshFiles()
                    gitManager?.refreshStatus()
                    if (onFileImported != null) {
                        kotlinx.coroutines.withContext(dispatchers.main) {
                            onFileImported(created.data)
                        }
                    }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = created.message) }
                }
                else -> Unit
            }
        }
    }

    fun readFileBytesForExport(
        fileUri: String,
        onBytesReady: (ByteArray) -> Unit
    ) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = workspaceManager.readBytes(fileUri)) {
                is com.vibespace.core.common.result.VibeResult.Success -> {
                    kotlinx.coroutines.withContext(dispatchers.main) {
                        onBytesReady(result.data)
                    }
                }
                is com.vibespace.core.common.result.VibeResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
                else -> Unit
            }
        }
    }
}
