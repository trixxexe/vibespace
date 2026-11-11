package com.vibespace.core.workspace.git

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.VirtualFile
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
 * Tracks workspace-local modifications, staged snapshots, snapshot labels, and line diffs.
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
    private val stagedSnapshots = mutableMapOf<String, String?>()
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
                    stagedSnapshots.clear()
                    commits.clear()
                    _status.value = GitWorkspaceStatus()
                }
            }
        }
    }

    private suspend fun initRepositoryForProject(rootUri: String) = withContext(dispatchers.io) {
        _status.value = GitWorkspaceStatus(errorMessage = "Loading local snapshots…")
        baselineFiles.clear()
        stagedPaths.clear()
        stagedSnapshots.clear()
        aheadCount = 0
        behindCount = 0
        branches.clear()
        branches.add(GitBranch("main", isCurrent = true))
        commits.clear()

        // Attempt to load persisted git state
        val stateFile = when (val result = locateGitStateFile(rootUri, create = false)) {
            is VibeResult.Success -> result.data
            is VibeResult.Error -> {
                _status.value = GitWorkspaceStatus(errorMessage = "Cannot inspect local snapshot history: ${result.message}")
                return@withContext
            }
            else -> null
        }
        val stateRead = stateFile?.let { workspaceManager.readFile(it.uri) }
        var restoredFromState = false
        if (stateRead is VibeResult.Error) {
            _status.value = GitWorkspaceStatus(errorMessage = "Cannot read local snapshot history: ${stateRead.message}")
            return@withContext
        }
        if (stateFile != null && stateRead !is VibeResult.Success) {
            _status.value = GitWorkspaceStatus(errorMessage = "Local snapshot history could not be read; existing data was left untouched.")
            return@withContext
        }
        if (stateRead is VibeResult.Success) {
            try {
                val json = JSONObject(stateRead.data)
                if (!json.has("baseline") || !json.has("commits")) {
                    _status.value = GitWorkspaceStatus(errorMessage = "Local snapshot history is incomplete and was left untouched.")
                    return@withContext
                }
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

                val stagedArray = json.optJSONArray("staged")
                if (stagedArray != null) {
                    for (i in 0 until stagedArray.length()) {
                        stagedPaths.add(stagedArray.getString(i))
                    }
                }

                val indexObj = json.optJSONObject("index")
                if (indexObj != null) {
                    val keys = indexObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        stagedSnapshots[key] = if (indexObj.isNull(key)) null else indexObj.getString(key)
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _status.value = GitWorkspaceStatus(errorMessage = "Local snapshot history is invalid and was left untouched: ${failure.message}")
                return@withContext
            }
        }

        if (!restoredFromState) {
            val allFiles = when (val result = workspaceManager.getAllProjectFiles(rootUri)) {
                is VibeResult.Success -> result.data
                is VibeResult.Error -> {
                    _status.value = _status.value.copy(errorMessage = "Cannot initialize local history: ${result.message}")
                    return@withContext
                }
                else -> {
                    _status.value = _status.value.copy(errorMessage = "Cannot initialize local history: workspace files are unavailable.")
                    return@withContext
                }
            }
            for (f in allFiles) {
                if (!f.isDirectory && f.name != "git_state.json") {
                    when (val read = workspaceManager.readFile(f.uri)) {
                        is VibeResult.Success -> baselineFiles[f.uri] = read.data
                        is VibeResult.Error -> {
                            _status.value = _status.value.copy(errorMessage = "Cannot initialize local history for ${f.name}: ${read.message}")
                            return@withContext
                        }
                        else -> {
                            _status.value = _status.value.copy(errorMessage = "Cannot initialize local history for ${f.name}.")
                            return@withContext
                        }
                    }
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

    private suspend fun locateGitStateFile(rootUri: String, create: Boolean): VibeResult<VirtualFile?> {
        val rootContents = when (val result = workspaceManager.listFiles(rootUri)) {
            is VibeResult.Success -> result.data
            is VibeResult.Error -> return result
            else -> return VibeResult.Error("Could not inspect the workspace for local snapshot metadata.")
        }
        var metadataDirectory = rootContents.firstOrNull { it.name == ".vibespace" && it.nodeType == FileNodeType.DIRECTORY }
        if (metadataDirectory == null && create) {
            metadataDirectory = when (val result = workspaceManager.createDirectory(rootUri, ".vibespace")) {
                is VibeResult.Success -> result.data
                is VibeResult.Error -> return VibeResult.Error("Could not create local snapshot storage: ${result.message}", result.throwable)
                else -> return VibeResult.Error("Could not create local snapshot storage.")
            }
        }
        if (metadataDirectory == null) return VibeResult.Success(null)

        val stateFiles = when (val result = workspaceManager.listFiles(metadataDirectory.uri)) {
            is VibeResult.Success -> result.data
            is VibeResult.Error -> return VibeResult.Error("Could not inspect local snapshot storage: ${result.message}", result.throwable)
            else -> return VibeResult.Error("Could not inspect local snapshot storage.")
        }
        val stateFile = stateFiles.firstOrNull { it.name == "git_state.json" && !it.isDirectory }
        if (stateFile != null || !create) return VibeResult.Success(stateFile)

        return when (val result = workspaceManager.createFile(metadataDirectory.uri, "git_state.json", "{}")) {
            is VibeResult.Success -> VibeResult.Success(result.data)
            is VibeResult.Error -> VibeResult.Error("Could not create local snapshot state: ${result.message}", result.throwable)
            else -> VibeResult.Error("Could not create local snapshot state.")
        }
    }

    private suspend fun saveGitState(rootUri: String): VibeResult<Unit> = withContext(dispatchers.io) {
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

            val stagedArray = JSONArray()
            for (s in stagedPaths) {
                stagedArray.put(s)
            }
            json.put("staged", stagedArray)

            val indexObj = JSONObject()
            for (path in stagedPaths) {
                if (stagedSnapshots.containsKey(path)) {
                    indexObj.put(path, stagedSnapshots[path] ?: JSONObject.NULL)
                }
            }
            json.put("index", indexObj)

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

            val stateFile = when (val result = locateGitStateFile(rootUri, create = true)) {
                is VibeResult.Success -> result.data ?: return@withContext VibeResult.Error("Could not create local snapshot state file.")
                is VibeResult.Error -> return@withContext result
                else -> return@withContext VibeResult.Error("Could not create local snapshot state file.")
            }
            workspaceManager.writeFile(stateFile.uri, json.toString())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            VibeResult.Error("Failed to save local snapshot state: ${e.message}", e)
        }
    }

    override suspend fun refreshStatus(): VibeResult<GitWorkspaceStatus> = withContext(dispatchers.io) {
        val project = workspaceManager.currentProject.value
            ?: return@withContext VibeResult.Success(_status.value)
        val updated = refreshStatusInternal(project.rootUri)
        if (updated.errorMessage != null) VibeResult.Error(updated.errorMessage) else VibeResult.Success(updated)
    }

    private suspend fun refreshStatusInternal(rootUri: String): GitWorkspaceStatus {
        val currentFiles = when (val result = workspaceManager.getAllProjectFiles(rootUri)) {
            is VibeResult.Success -> result.data
            is VibeResult.Error -> {
                val errorStatus = _status.value.copy(errorMessage = "Could not refresh local changes: ${result.message}")
                _status.value = errorStatus
                return errorStatus
            }
            else -> {
                val errorStatus = _status.value.copy(errorMessage = "Could not refresh local changes: workspace files are unavailable.")
                _status.value = errorStatus
                return errorStatus
            }
        }
        val currentFilesMap = mutableMapOf<String, String>()
        for (f in currentFiles) {
            if (!f.isDirectory && f.name != "git_state.json") {
                when (val read = workspaceManager.readFile(f.uri)) {
                    is VibeResult.Success -> currentFilesMap[f.uri] = read.data
                    is VibeResult.Error -> {
                        val errorStatus = _status.value.copy(errorMessage = "Could not read ${f.name} while refreshing local changes: ${read.message}")
                        _status.value = errorStatus
                        return errorStatus
                    }
                    else -> {
                        val errorStatus = _status.value.copy(errorMessage = "Could not read ${f.name} while refreshing local changes.")
                        _status.value = errorStatus
                        return errorStatus
                    }
                }
            }
        }

        val allPaths = (currentFilesMap.keys + baselineFiles.keys + stagedPaths).toSet()
        val staged = stagedPaths.mapNotNull { path ->
            val indexContent = if (stagedSnapshots.containsKey(path)) stagedSnapshots[path] else currentFilesMap[path]
            val baselineContent = baselineFiles[path]
            if (indexContent == baselineContent) return@mapNotNull null
            GitFileStatus(
                path = path,
                fileName = path.substringAfterLast('/'),
                statusType = when {
                    indexContent == null -> GitStatusType.DELETED
                    baselineContent == null -> GitStatusType.ADDED
                    else -> GitStatusType.MODIFIED
                },
                isStaged = true
            )
        }
        val unstaged = allPaths.mapNotNull { path ->
            val expectedContent = if (stagedPaths.contains(path)) {
                if (stagedSnapshots.containsKey(path)) stagedSnapshots[path] else currentFilesMap[path]
            } else {
                baselineFiles[path]
            }
            val currentContent = currentFilesMap[path]
            if (currentContent == expectedContent) return@mapNotNull null
            GitFileStatus(
                path = path,
                fileName = path.substringAfterLast('/'),
                statusType = when {
                    currentContent == null -> GitStatusType.DELETED
                    expectedContent == null -> GitStatusType.UNTRACKED
                    else -> GitStatusType.MODIFIED
                }
            )
        }
        val currentBranch = branches.firstOrNull { it.isCurrent }?.name ?: "main"

        val newStatus = GitWorkspaceStatus(
            currentBranch = currentBranch,
            branches = branches.toList(),
            stagedFiles = staged,
            unstagedFiles = unstaged,
            commits = commits.toList(),
            aheadCount = 0,
            behindCount = 0,
            remoteBranch = null,
            isClean = staged.isEmpty() && unstaged.isEmpty(),
            errorMessage = null
        )
        _status.value = newStatus
        return newStatus
    }

    override suspend fun stageFile(path: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val before = refreshStatus()
        if (before is VibeResult.Error) return@withContext before
        val status = _status.value.stagedFiles.firstOrNull { it.path == path }
            ?: _status.value.unstagedFiles.firstOrNull { it.path == path }
            ?: return@withContext VibeResult.Error("No changed workspace file found at '$path'.")
        val snapshot = when (val result = workspaceManager.readFile(path)) {
            is VibeResult.Success -> result.data
            is VibeResult.Error -> if (status.statusType == GitStatusType.DELETED) null else {
                return@withContext VibeResult.Error("Could not stage '$path': ${result.message}", result.throwable)
            }
            else -> return@withContext VibeResult.Error("Could not stage '$path': file contents are unavailable.")
        }
        val oldSnapshot = stagedSnapshots[path]
        val hadSnapshot = stagedSnapshots.containsKey(path)
        stagedPaths.add(path)
        stagedSnapshots[path] = snapshot
        val saveResult = workspaceManager.currentProject.value?.let { saveGitState(it.rootUri) }
            ?: VibeResult.Error("No active workspace is available.")
        if (saveResult is VibeResult.Error) {
            stagedPaths.remove(path)
            if (hadSnapshot) stagedSnapshots[path] = oldSnapshot else stagedSnapshots.remove(path)
            return@withContext saveResult
        }
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun unstageFile(path: String): VibeResult<Unit> = withContext(dispatchers.io) {
        if (!stagedPaths.contains(path)) return@withContext VibeResult.Error("'$path' is not staged.")
        val hadSnapshot = stagedSnapshots.containsKey(path)
        val oldSnapshot = stagedSnapshots.remove(path)
        stagedPaths.remove(path)
        val saveResult = workspaceManager.currentProject.value?.let { saveGitState(it.rootUri) }
            ?: VibeResult.Error("No active workspace is available.")
        if (saveResult is VibeResult.Error) {
            stagedPaths.add(path)
            if (hadSnapshot) stagedSnapshots[path] = oldSnapshot
            return@withContext saveResult
        }
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun stageAll(): VibeResult<Unit> = withContext(dispatchers.io) {
        when (val refresh = refreshStatus()) {
            is VibeResult.Error -> return@withContext refresh
            else -> Unit
        }
        val candidatePaths = (_status.value.stagedFiles + _status.value.unstagedFiles).map { it.path }.toSet()
        val previousPaths = stagedPaths.toSet()
        val previousSnapshots = stagedSnapshots.toMap()
        for (path in candidatePaths) {
            val result = workspaceManager.readFile(path)
            stagedSnapshots[path] = when (result) {
                is VibeResult.Success -> result.data
                is VibeResult.Error -> if (_status.value.unstagedFiles.any { it.path == path && it.statusType == GitStatusType.DELETED }) null else {
                    stagedPaths.clear(); stagedPaths.addAll(previousPaths)
                    stagedSnapshots.clear(); stagedSnapshots.putAll(previousSnapshots)
                    return@withContext VibeResult.Error("Could not stage '$path': ${result.message}", result.throwable)
                }
                else -> {
                    stagedPaths.clear(); stagedPaths.addAll(previousPaths)
                    stagedSnapshots.clear(); stagedSnapshots.putAll(previousSnapshots)
                    return@withContext VibeResult.Error("Could not stage '$path': file contents are unavailable.")
                }
            }
            stagedPaths.add(path)
        }
        val saveResult = workspaceManager.currentProject.value?.let { saveGitState(it.rootUri) }
            ?: VibeResult.Error("No active workspace is available.")
        if (saveResult is VibeResult.Error) {
            stagedPaths.clear(); stagedPaths.addAll(previousPaths)
            stagedSnapshots.clear(); stagedSnapshots.putAll(previousSnapshots)
            return@withContext saveResult
        }
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun unstageAll(): VibeResult<Unit> = withContext(dispatchers.io) {
        val previousPaths = stagedPaths.toSet()
        val previousSnapshots = stagedSnapshots.toMap()
        stagedPaths.clear()
        stagedSnapshots.clear()
        val saveResult = workspaceManager.currentProject.value?.let { saveGitState(it.rootUri) }
            ?: VibeResult.Error("No active workspace is available.")
        if (saveResult is VibeResult.Error) {
            stagedPaths.addAll(previousPaths)
            stagedSnapshots.putAll(previousSnapshots)
            return@withContext saveResult
        }
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun discardChanges(path: String): VibeResult<Unit> = withContext(dispatchers.io) {
        val refresh = refreshStatus()
        if (refresh is VibeResult.Error) return@withContext refresh
        if (_status.value.stagedFiles.none { it.path == path } && _status.value.unstagedFiles.none { it.path == path }) {
            return@withContext VibeResult.Error("No local changes found for '$path'.")
        }
        val restoreContent = when {
            stagedSnapshots.containsKey(path) -> stagedSnapshots[path]
            else -> baselineFiles[path]
        }
        val restoreResult = if (restoreContent != null) {
            workspaceManager.writeFile(path, restoreContent)
        } else {
            workspaceManager.delete(path)
        }
        if (restoreResult is VibeResult.Error) return@withContext restoreResult
        if (!stagedSnapshots.containsKey(path)) stagedPaths.remove(path)
        val saveResult = workspaceManager.currentProject.value?.let { saveGitState(it.rootUri) }
            ?: VibeResult.Error("No active workspace is available.")
        if (saveResult is VibeResult.Error) return@withContext saveResult
        refreshStatus()
        VibeResult.Success(Unit)
    }

    override suspend fun commit(message: String): VibeResult<GitCommit> = withContext(dispatchers.io) {
        val trimmedMsg = message.trim()
        if (trimmedMsg.isEmpty()) {
            return@withContext VibeResult.Error("Commit message cannot be empty")
        }

        when (val refresh = refreshStatus()) {
            is VibeResult.Error -> return@withContext refresh
            else -> Unit
        }
        val staged = _status.value.stagedFiles
        if (staged.isEmpty()) {
            return@withContext VibeResult.Error("No changes staged to commit")
        }

        val previousBaseline = baselineFiles.toMap()
        val previousPaths = stagedPaths.toSet()
        val previousSnapshots = stagedSnapshots.toMap()
        val previousCommits = commits.toList()

        // Apply the captured staged snapshot, not any later edits in the working file.
        for (item in staged) {
            val snapshot = if (stagedSnapshots.containsKey(item.path)) stagedSnapshots[item.path] else {
                when (val read = workspaceManager.readFile(item.path)) {
                    is VibeResult.Success -> read.data
                    is VibeResult.Error -> if (item.statusType == GitStatusType.DELETED) null else {
                        return@withContext VibeResult.Error("Could not save snapshot for '${item.fileName}': ${read.message}", read.throwable)
                    }
                    else -> return@withContext VibeResult.Error("Could not save snapshot for '${item.fileName}'.")
                }
            }
            if (snapshot == null) {
                baselineFiles.remove(item.path)
            } else {
                baselineFiles[item.path] = snapshot
            }
        }
        stagedPaths.clear()
        stagedSnapshots.clear()

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
        val project = workspaceManager.currentProject.value
        if (project == null) {
            baselineFiles.clear(); baselineFiles.putAll(previousBaseline)
            stagedPaths.addAll(previousPaths)
            stagedSnapshots.putAll(previousSnapshots)
            commits.clear(); commits.addAll(previousCommits)
            return@withContext VibeResult.Error("No active workspace is available.")
        }
        when (val saveResult = saveGitState(project.rootUri)) {
            is VibeResult.Error -> {
                baselineFiles.clear(); baselineFiles.putAll(previousBaseline)
                stagedPaths.addAll(previousPaths)
                stagedSnapshots.putAll(previousSnapshots)
                commits.clear(); commits.addAll(previousCommits)
                return@withContext saveResult
            }
            else -> Unit
        }

        refreshStatus()
        VibeResult.Success(newCommit)
    }

    override suspend fun checkoutBranch(branchName: String): VibeResult<Unit> = withContext(dispatchers.io) {
        VibeResult.Error("Branch checkout is unavailable because local snapshots are not a Git repository.")
    }

    override suspend fun createBranch(branchName: String): VibeResult<Unit> = withContext(dispatchers.io) {
        VibeResult.Error("Branch creation is unavailable because local snapshots are not a Git repository.")
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
        VibeResult.Error("Remote push is unavailable because VibeSpace local snapshots are not a Git repository.")
    }

    override suspend fun pull(): VibeResult<String> = withContext(dispatchers.io) {
        VibeResult.Error("Remote pull is unavailable because VibeSpace local snapshots are not a Git repository.")
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
