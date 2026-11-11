package com.vibespace.feature.search

data class SearchUiState(
    val query: String = "",
    val replacement: String = "",
    val matchCase: Boolean = false,
    val matchCount: Int = 0,
    val activeMatchIndex: Int = 0,
    val isReplaceMode: Boolean = false,
    val scope: SearchScope = SearchScope.FILE,
    val useRegex: Boolean = false,
    val workspaceMatches: List<WorkspaceSearchMatch> = emptyList(),
    val isSearchingWorkspace: Boolean = false,
    val searchError: String? = null,
    val skippedFileCount: Int = 0,
    val resultsTruncated: Boolean = false
)

enum class SearchScope { FILE, WORKSPACE }

data class WorkspaceSearchMatch(
    val file: com.vibespace.core.workspace.model.VirtualFile,
    val relativePath: String,
    val lineNumber: Int,
    val column: Int,
    val lineText: String
)
