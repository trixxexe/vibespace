package com.vibespace.core.workspace.git

enum class GitStatusType {
    MODIFIED,
    ADDED,
    DELETED,
    UNTRACKED
}

data class GitFileStatus(
    val path: String,
    val fileName: String,
    val statusType: GitStatusType,
    val isStaged: Boolean = false
)

data class GitBranch(
    val name: String,
    val isCurrent: Boolean = false
)

data class GitCommit(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val timestamp: Long
)

enum class DiffLineType {
    EQUAL,
    INSERT,
    DELETE
}

data class DiffLine(
    val type: DiffLineType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String
)

data class FileDiff(
    val filePath: String,
    val lines: List<DiffLine>,
    val addedCount: Int,
    val deletedCount: Int
)

data class GitWorkspaceStatus(
    val currentBranch: String = "main",
    val branches: List<GitBranch> = listOf(GitBranch("main", isCurrent = true)),
    val stagedFiles: List<GitFileStatus> = emptyList(),
    val unstagedFiles: List<GitFileStatus> = emptyList(),
    val commits: List<GitCommit> = emptyList(),
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val remoteBranch: String? = null,
    val isClean: Boolean = stagedFiles.isEmpty() && unstagedFiles.isEmpty(),
    val errorMessage: String? = null
)
