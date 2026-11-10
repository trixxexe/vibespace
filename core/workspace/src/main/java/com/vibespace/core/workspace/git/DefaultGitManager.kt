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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mobile Git source control manager.
 * Tracks file modifications, staging, commits, branch management, and provides line diffs.
 * Truthful: Remote operations fail closed with clear status explaining mobile sandbox boundaries.
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
        branches.clear()
        branches.add(GitBranch("main", isCurrent = true))
        commits.clear()

        // Attempt to load persisted git state
        val stateUri = "$rootUri/.vibespace/git_state.json"
        val stateRead = workspaceManager.readFile(stateUri)
        var restoredFromState = false
        if (stateRead is VibeResult.Success) {
            try {
                val json = JSONObject(stateRead.data)
                aheadCount = json.optInt("aheadCount", 0)
                behindCount = json.optInt("behindCount", 0)

                val branchesArray = json.optJSONArray("branches")
                if (branchesArray != null && branchesArray.length() > 0) {
                    branches.clear()
                    for (i in 0 until branchesArray.length()) {
                        val bObj = branchesArray.getJSONObject(i)
                        branches.add(
                            GitBranch(
                                name = bObj.getString("name"),
                                isCurrent = bObj.optBoolean("isCurrent", false)
                            )
                        )
                    }
                }

                val commitsArray = json.optJSONArray("commits")
                if (commitsArray != null) {
                    for (i in 0 until commitsArray.length()) {
                        val cObj = commitsArray.getJSONObject(i)
                        commits.add(
                            GitCommit(
                                id = cObj.getString("id"),
                                shortId = cObj.optString("shortId", cObj.getString("id").take(7)),
                                message = cObj.getString("message"),
                                author = cObj.optString("author", "VibeSpace Developer"),
                                timestamp = cObj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val baselineObj = json.optJSONObject("baseline")
                if (baselineObj != null) {
                    val keys = baselineObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        baselineFiles[key] = baselineObj.getString(key)
                    }
                }
                restoredFromState = true
            } catch (_: Throwable) {}
        }

        if (!restoredFromState) {
            val allFiles = workspaceManager.getAllProjectFiles(rootUri).getOrNull() ?: emptyList()
            for (f in allFiles) {
                if (!f.isDirectory && !f.uri.contains(".vibespace/")) {
                    val content = workspaceManager.readFile(f.uri).getOrNull() ?: ""
                    baselineFiles[f.uri] = content
                }
            }

            // Seed initial commit
            val initialCommit = GitCommit(
                id = "init-" + IdGenerator.shortId("commit"),
                shortId = "0000001",
                message = "Initial workspace snapshot",
                author = "VibeSpace Developer",
                timestamp = System.currentTimeMillis()
            )
            commits.add(initialCommit)
            saveGitState(rootUri)
        }

        refreshStatusInternal(rootUri)
    }

    private suspend fun saveGitState(rootUri: String) = withContext(dispatchers.io) {
        try {
            val json = JSONObject()
            json.put("aheadCount", aheadCount)
            json.put("behindCount", behindCount)

            val branchesArray = JSONArray()
            for (b in branches) {
                val bObj = JSONObject()
                bObj.put("name", b.name)
                bObj.put("isCurrent", b.isCurrent)
                branchesArray.put(bObj)
            }
            json.put("branches", branchesArray)

            val commitsArray = JSONArray()
            for (c in commits) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("shortId", c.shortId)
                cObj.put("message", c.message)
                cObj.put("author", c.author)
                cObj.put("timestamp", c.timestamp)
                commitsArray.put(cObj)
            }
            json.put("commits", commitsArray)

            val baselineObj = JSONObject()
            for ((k, v) in baselineFiles) {
                if (!k.contains(".vibespace/")) {
                    baselineObj.put(k, v)
                }
            }
            json.put("baseline", baselineObj)

            workspaceManager.writeFile("$rootUri/.vibespace/git_state.json", json.toString())
        } catch (_: Throwable) {}
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
            if (!f.isDirectory && !f.uri.contains(".vibespace/")) {
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
            if (!uri.contains(".vibespace/") && !currentFilesMap.containsKey(uri)) {
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
        refreshStatus()
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

        val project = workspaceManager.currentProject.value
        if (project != null) {
            saveGitState(project.rootUri)
        }

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
        if (branches.any { it.name == cleanName }) {
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
        val readResult = workspaceManager.readFile(path)
        if (readResult is VibeResult.Error) {
            return@withContext VibeResult.Error("Failed to read file for diff: ${readResult.message}", readResult.throwable)
        }
        val newContent = (readResult as VibeResult.Success).data

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
        val branch = branches.firstOrNull { it.isCurrent }?.name ?: "main"
        VibeResult.Error("Remote Git push to origin/$branch is unavailable. Mobile sandbox lacks configured SSH/HTTPS remote transport; use Alpine PRoot or Termux runner with git push.")
    }

    override suspend fun pull(): VibeResult<String> = withContext(dispatchers.io) {
        val branch = branches.firstOrNull { it.isCurrent }?.name ?: "main"
        VibeResult.Error("Remote Git pull from origin/$branch is unavailable. Mobile sandbox lacks configured SSH/HTTPS remote transport; use Alpine PRoot or Termux runner with git pull.")
    }

    private fun computeDiffLines(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
        val maxLines = 1000
        val isTruncated = oldLines.size > maxLines || newLines.size > maxLines
        val boundedOld = if (oldLines.size > maxLines) oldLines.take(maxLines) else oldLines
        val boundedNew = if (newLines.size > maxLines) newLines.take(maxLines) else newLines

        val n = boundedOld.size
        val m = boundedNew.size

        // Longest Common Subsequence DP table bounded to maxLines
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                if (boundedOld[i] == boundedNew[j]) {
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
            if (i > 0 && j > 0 && boundedOld[i - 1] == boundedNew[j - 1]) {
                temp.add(
                    DiffLine(
                        type = DiffLineType.EQUAL,
                        oldLineNumber = i,
                        newLineNumber = j,
                        text = boundedOld[i - 1]
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
                        text = boundedNew[j - 1]
                    )
                )
                j--
            } else if (i > 0) {
                temp.add(
                    DiffLine(
                        type = DiffLineType.DELETE,
                        oldLineNumber = i,
                        newLineNumber = null,
                        text = boundedOld[i - 1]
                    )
                )
                i--
            }
        }

        temp.reverse()
        if (isTruncated) {
            temp.add(
                DiffLine(
                    type = DiffLineType.EQUAL,
                    oldLineNumber = null,
                    newLineNumber = null,
                    text = "... [Diff truncated: file exceeds $maxLines lines to preserve mobile memory] ..."
                )
            )
        }
        return temp
    }
}
