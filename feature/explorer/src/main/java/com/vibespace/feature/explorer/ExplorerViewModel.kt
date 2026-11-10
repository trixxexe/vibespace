package com.vibespace.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val workspaceManager: WorkspaceManager,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val expandedDirs = mutableSetOf<String>()

    init {
        observeCurrentProject()
    }

    private fun observeCurrentProject() {
        viewModelScope.launch(dispatchers.io) {
            workspaceManager.currentProject.collect { project ->
                _uiState.update { it.copy(currentProject = project) }
                if (project != null) {
                    refreshFiles()
                } else {
                    _uiState.update { it.copy(rootNodes = emptyList()) }
                }
            }
        }
    }

    fun refreshFiles() {
        val project = _uiState.value.currentProject ?: return
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val nodes = loadDirectoryNodes(project.rootUri, 0)
            _uiState.update { it.copy(isLoading = false, rootNodes = nodes) }
        }
    }

    private suspend fun loadDirectoryNodes(directoryUri: String, level: Int): List<ExplorerNode> {
        val result = workspaceManager.listFiles(directoryUri)
        val files = result.getOrNull() ?: return emptyList()

        return files.map { file ->
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

    fun createNewFile(fileName: String, parentUri: String? = null) {
        val targetParent = parentUri ?: _uiState.value.currentProject?.rootUri ?: return
        viewModelScope.launch(dispatchers.io) {
            val result = workspaceManager.createFile(targetParent, fileName)
            result.onSuccess {
                refreshFiles()
            }.onError { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun createNewFolder(folderName: String, parentUri: String? = null) {
        val targetParent = parentUri ?: _uiState.value.currentProject?.rootUri ?: return
        viewModelScope.launch(dispatchers.io) {
            val result = workspaceManager.createDirectory(targetParent, folderName)
            result.onSuccess {
                refreshFiles()
            }.onError { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun deleteFile(fileUri: String) {
        viewModelScope.launch(dispatchers.io) {
            workspaceManager.delete(fileUri)
            refreshFiles()
        }
    }
}
