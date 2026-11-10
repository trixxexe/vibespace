package com.vibespace.feature.explorer

import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.model.VirtualFile

data class ExplorerNode(
    val file: VirtualFile,
    val level: Int = 0,
    val isExpanded: Boolean = false,
    val children: List<ExplorerNode> = emptyList()
)

data class ExplorerUiState(
    val currentProject: Project? = null,
    val rootNodes: List<ExplorerNode> = emptyList(),
    val allProjectFiles: List<VirtualFile> = emptyList(),
    val selectedFileUri: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
