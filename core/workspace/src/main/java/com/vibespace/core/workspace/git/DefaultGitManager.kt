package com.vibespace.core.workspace.git

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.manager.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mobile Git source control manager.
 * Tracks file modifications, staging, commits, branch management, and provides line diffs.
 */
class DefaultGitManager(
    private val workspaceManager: WorkspaceManager,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : GitManager {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _status = MutableStateFlow(GitWorkspaceStatus())
    override val status: StateFlow<GitWorkspaceStatus> = _status.asStateFlow()

    // Baseline snapshots of files at last commit (path -> content)
    private val baselineFiles = mutableMapOf<String, String>()
    private val stagedPaths = mutableSetOf<String>()
    private val branches = mutableListOf(GitBranch("main", isCurrent = true))
    private val commits = mutableListOf<GitCommit>()
    private var aheadCount = 0
    private var behindCount = 0

    init {
        observeWorkspaceProject()
    }

    private fun observeWorkspaceProject() {
        scope.launch(dispatchers.io) {
            workspaceManager.currentProject.collect { project ->
                if (project != null) {
                    initRepositoryForProject(project.rootUri)
                } else {
                    baselineFiles.clear()
                    stagedPaths.clear()
                    commits.clear()
                    _status.value = GitWorkspaceStatus()
                }
            }
        }
    }

    private suspend fun initRepositoryForProject(rootUri: String) = withContext(dispatchers.io) {
        baselineFiles.clear()
        stagedPaths.clear()
        aheadCount = 0
        behindCount = 0
        val allFiles = workspaceManager.getAllProjectFiles(rootUri).getOrNull() ?: emptyList()
        for (f in allFiles) {
            if (!f.isDirectory) {
                val content = workspaceManager.readFile(f.uri).getOrNull() ?: ""
                baselineFiles[f.uri] = content
            }
        }

        if (commits.isEmpty()) {
            val initCommit = GitCommit(
                id = IdGenerator.shortId("commit"),
                shortId = "init01",
                message = "Initial workspace commit",
                author = "VibeSpace Developer",
                timestamp = System.currentTimeMillis()
            )
            commits.add(initCommit)
        }

        refreshStatusInternal(rootUri)
    }

    override suspend fun refreshStatus(): VibeResult<GitWorkspaceStatus> = withContext(dispatchers.io) {
        val project = workspaceManager.currentProject.value
            ?: return@withContext VibeResult.Success(_status.value)
        val updated = refreshStatusInternal(project.rootUri)
        VibeResult.Success(updated)
    }

    private suspend fun refreshStatusInternal(rootUri: String): GitWorkspaceStatus {
        val currentFiles = workspaceManager.getAllProjectFiles(rootUri).getOrNull() ?: emptyList()
        val currentFilesMap = mutableMapOf<String, String>()
        for (f in currentFiles) {
            if (!f.isDirectory) {
                val content = workspaceManager.readFile(f.uri).getOrNull() ?: ""
                currentFilesMap[f.uri] = content
            }
        }

        val allDetected = mutableListOf<GitFileStatus>()

        // Check for modified and untracked files
        for ((uri, content) in currentFilesMap) {
            val fileName = uri.substringAfterLast('/')
            val baseline = baselineFiles[uri]
            if (baseline == null) {
                val isStaged = stagedPaths.contains(uri)
                allDetected.add(
                    GitFileStatus(
                        path = uri,
                        fileName = fileName,
                        statusType = if (isStaged) GitStatusType.ADDED else GitStatusType.UNTRACKED,
                        isStaged = isStaged
                    )
                )
            } else if (baseline != content) {
                allDetected.add(
                    GitFileStatus(
                        path = uri,
                        fileName = fileName,
                        statusType = GitStatusType.MODIFIED,
                        isStaged = stagedPaths.contains(uri)
                    )
                )
            }
        }

        // Check for deleted files
        for ((uri, _) in baselineFiles) {
            if (!currentFilesMap.containsKey(uri)) {
                val fileName = uri.substringAfterLast('/')
                allDetected.add(
                    GitFileStatus(
                        path = uri,
                        fileName = fileName,
                        statusType = GitStatusType.DELETED,
                        isStaged = stagedPaths.contains(uri)
                    )
                )
            }
        }

        val staged = allDetected.filter { it.isStaged }
        val unstaged = allDetected.filter { !it.isStaged }
        val currentBranch = branches.firstOrNull { it.isCurrent }?.name ?: "main"

        val newStatus = GitWorkspaceStatus(
            currentBranch = currentBranch,
            branches = branches.toList(),
            stagedFiles = staged,
            unstagedFiles = unstaged,
            commits = commits.toList(),
            aheadCount = aheadCount,
            behindCount = behindCount,
            remoteBranch = "origin/$currentBranch",
            isClean = staged.isEmpty() && unstaged.isEmpty()
        )
        _status.value = newStatus
        return newStatus
    }

    override suspend fun stageFile(path: String): VibeResult<Unit> = withContext(dispatchers.io) {
        stagedPaths.add(path)
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun unstageFile(path: String): VibeResult<Unit> = withContext(dispatchers.io) {
        stagedPaths.remove(path)
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun stageAll(): VibeResult<Unit> = withContext(dispatchers.io) {
        val unstaged = _status.value.unstagedFiles
        for (f in unstaged) {
            stagedPaths.add(f.path)
        }
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun unstageAll(): VibeResult<Unit> = withContext(dispatchers.io) {
        stagedPaths.clear()
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun discardChanges(path: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val baseline = baselineFiles[path]
        if (baseline != null) {
            workspaceManager.writeFile(path, baseline)
        } else {
            workspaceManager.delete(path)
        }
        stagedPaths.remove(path)
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun commit(message: String): VibeResult<GitCommit> = withContext(dispatchers.io) {
        val trimmedMsg = message.trim()
        if (trimmedMsg.isEmpty()) {
            return@withContext VibeResult.Error("Commit message cannot be empty")
        }

        val staged = _status.value.stagedFiles
        if (staged.isEmpty()) {
            return@withContext VibeResult.Error("No changes staged to commit")
        }

        // Apply staged changes to baseline
        for (item in staged) {
            if (item.statusType == GitStatusType.DELETED) {
                baselineFiles.remove(item.path)
            } else {
                val currentContent = workspaceManager.readFile(item.path).getOrNull() ?: ""
                baselineFiles[item.path] = currentContent
            }
        }
        stagedPaths.clear()

        val commitId = IdGenerator.shortId("commit")
        val shortId = commitId.take(7)
        val newCommit = GitCommit(
            id = commitId,
            shortId = shortId,
            message = trimmedMsg,
            author = "VibeSpace Developer",
            timestamp = System.currentTimeMillis()
        )
        commits.add(0, newCommit)
        aheadCount++

        refreshStatus()
        VibeResult.Success(newCommit)
    }

    override suspend fun checkoutBranch(branchName: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val index = branches.indexOfFirst { it.name == branchName }
        if (index == -1) {
            return@withContext VibeResult.Error("Branch '$branchName' not found")
        }
        for (i in branches.indices) {
            branches[i] = branches[i].copy(isCurrent = branches[i].name == branchName)
        }
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun createBranch(branchName: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val cleanName = branchName.trim()
        if (cleanName.isEmpty()) {
            return@withContext VibeResult.Error("Branch name cannot be empty")
        }
        if (branches.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return@withContext VibeResult.Error("Branch '$cleanName' already exists")
        }
        for (i in branches.indices) {
            branches[i] = branches[i].copy(isCurrent = false)
        }
        branches.add(GitBranch(name = cleanName, isCurrent = true))
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun getFileDiff(path: String): VibeResult<FileDiff> = withContext(dispatchers.io) {
        val oldContent = baselineFiles[path] ?: ""
        val newContent = workspaceManager.readFile(path).getOrNull() ?: ""

        val oldLines = if (oldContent.isEmpty()) emptyList() else oldContent.split("\n")
        val newLines = if (newContent.isEmpty()) emptyList() else newContent.split("\n")

        val diffLines = computeDiffLines(oldLines, newLines)
        val addedCount = diffLines.count { it.type == DiffLineType.INSERT }
        val deletedCount = diffLines.count { it.type == DiffLineType.DELETE }

        VibeResult.Success(
            FileDiff(
                filePath = path,
                lines = diffLines,
                addedCount = addedCount,
                deletedCount = deletedCount
            )
        )
    }

    override suspend fun push(): VibeResult<String> = withContext(dispatchers.io) {
        val project = workspaceManager.currentProject.value
            ?: return@withContext VibeResult.Error("No active project to push")
        val branch = branches.firstOrNull { it.isCurrent }?.name ?: "main"
        val count = aheadCount
        aheadCount = 0
        refreshStatus()
        if (count == 0) {
            VibeResult.Success("Everything up-to-date on origin/$branch")
        } else {
            VibeResult.Success("Pushed $count commit(s) to origin/$branch")
        }
    }

    override suspend fun pull(): VibeResult<String> = withContext(dispatchers.io) {
        val project = workspaceManager.currentProject.value
            ?: return@withContext VibeResult.Error("No active project to pull")
        val branch = branches.firstOrNull { it.isCurrent }?.name ?: "main"
        behindCount = 0
        refreshStatus()
        VibeResult.Success("Already up-to-date with origin/$branch")
    }

    private fun computeDiffLines(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
        val result = mutableListOf<DiffLine>()
        val n = oldLines.size
        val m = newLines.size

        // Longest Common Subsequence DP table
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        var i = n
        var j = m
        val temp = mutableListOf<DiffLine>()

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1]) {
                temp.add(
                    DiffLine(
                        type = DiffLineType.EQUAL,
                        oldLineNumber = i,
                        newLineNumber = j,
                        text = oldLines[i - 1]
                    )
                )
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                temp.add(
                    DiffLine(
                        type = DiffLineType.INSERT,
                        oldLineNumber = null,
                        newLineNumber = j,
                        text = newLines[j - 1]
                    )
                )
                j--
            } else if (i > 0) {
                temp.add(
                    DiffLine(
                        type = DiffLineType.DELETE,
                        oldLineNumber = i,
                        newLineNumber = null,
                        text = oldLines[i - 1]
                    )
                )
                i--
            }
        }

        temp.reverse()
        return temp
    }
}
