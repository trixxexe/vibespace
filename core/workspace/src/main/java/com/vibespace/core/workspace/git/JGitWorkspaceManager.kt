package com.vibespace.core.workspace.git

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.manager.WorkspaceManager
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Git implementation backed by Eclipse JGit. Each project has a durable private working tree;
 * WorkspaceManager copies files between that tree and the user-selected SAF/disk workspace.
 * Authentication secrets are held only in memory and must be entered again after process death.
 */
class JGitWorkspaceManager(
    private val workspaceManager: WorkspaceManager,
    private val storageDirectory: File,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : GitManager {
    private val _status = MutableStateFlow(GitWorkspaceStatus())
    override val status: StateFlow<GitWorkspaceStatus> = _status.asStateFlow()
    private val active = AtomicReference<RepositoryContext?>(null)
    private var credentials: CredentialsProvider? = null

    private data class RepositoryContext(
        val project: Project,
        val workTree: File,
        val repo: Repository,
        val uriToRelative: MutableMap<String, String>,
        val relativeToUri: MutableMap<String, String>
    )

    private suspend fun prepare(): VibeResult<RepositoryContext> = withContext(dispatchers.io) {
        val project = workspaceManager.currentProject.value
            ?: return@withContext VibeResult.Error("Open a workspace before using Git.")
        val existing = active.get()
        if (existing?.project?.id == project.id) {
            val sync = syncWorkspaceToGit(existing)
            if (sync is VibeResult.Error) return@withContext sync
            return@withContext VibeResult.Success(existing)
        }

        try {
            storageDirectory.mkdirs()
            val dir = File(storageDirectory, project.id.toSha256())
            dir.mkdirs()
            val gitDir = File(dir, ".git")
            val repo = if (gitDir.exists()) {
                FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(dir).readEnvironment().findGitDir().build()
            } else {
                Git.init().setDirectory(dir).setInitialBranch("main").call().repository
            }
            val ctx = RepositoryContext(project, dir, repo, mutableMapOf(), mutableMapOf())
            loadPathMap(ctx)
            val sync = syncWorkspaceToGit(ctx)
            if (sync is VibeResult.Error) {
                repo.close()
                return@withContext sync
            }
            active.getAndSet(ctx)?.repo?.close()
            VibeResult.Success(ctx)
        } catch (failure: Exception) {
            VibeResult.Error("Could not open the local Git repository: ${failure.message}", failure)
        }
    }

    private suspend fun syncWorkspaceToGit(ctx: RepositoryContext): VibeResult<Unit> {
        return try {
            val files = when (val result = workspaceManager.getAllProjectFiles(ctx.project.rootUri)) {
                is VibeResult.Success -> result.data
                is VibeResult.Error -> return result
                else -> return VibeResult.Error("Workspace files could not be enumerated.")
            }
            val currentUris = mutableSetOf<String>()
            for (file in files.filter { it.isFile }) {
                val relative = relativePath(file, files, ctx.project.rootUri)
                    ?: return VibeResult.Error("Cannot determine the workspace-relative path for '${file.name}'.")
                if (!isSafeGitPath(relative)) return VibeResult.Error("Unsafe workspace path rejected: $relative")
                val bytes = when (val result = workspaceManager.readBytes(file.uri)) {
                    is VibeResult.Success -> result.data
                    is VibeResult.Error -> return VibeResult.Error("Could not read '${file.name}' for Git: ${result.message}", result.throwable)
                    else -> return VibeResult.Error("Could not read '${file.name}' for Git.")
                }
                val destination = safeFile(ctx.workTree, relative)
                    ?: return VibeResult.Error("Workspace path escapes the Git working tree: $relative")
                if (!destination.exists() || destination.readBytes().contentEquals(bytes).not()) {
                    destination.parentFile?.mkdirs()
                    destination.writeBytes(bytes)
                }
                ctx.uriToRelative[file.uri] = relative
                ctx.relativeToUri[relative] = file.uri
                currentUris.add(file.uri)
            }

            // Remove deleted workspace files from the mirror, but never touch Git metadata.
            val deleted = ctx.uriToRelative.keys.filter { it !in currentUris }
            deleted.forEach { uri ->
                val rel = ctx.uriToRelative[uri] ?: return@forEach
                safeFile(ctx.workTree, rel)?.let { if (it.isFile) it.delete() }
            }
            savePathMap(ctx)
            VibeResult.Success(Unit)
        } catch (failure: Exception) {
            VibeResult.Error("Could not synchronize workspace files into Git: ${failure.message}", failure)
        }
    }

    private fun relativePath(file: VirtualFile, allFiles: List<VirtualFile>, rootUri: String): String? {
        val parentByUri = allFiles.associateBy { it.uri }
        val parts = mutableListOf(file.name)
        var parent = file.parentUri ?: return null
        val seen = mutableSetOf<String>()
        while (parent != rootUri) {
            if (!seen.add(parent)) return null
            val parentFile = parentByUri[parent] ?: return null
            if (parentFile.nodeType != FileNodeType.DIRECTORY) return null
            parts.add(parentFile.name)
            parent = parentFile.parentUri ?: return null
        }
        return parts.asReversed().joinToString("/")
    }

    private fun safeFile(root: File, relative: String): File? {
        val rootCanonical = root.canonicalFile
        val result = File(rootCanonical, relative).canonicalFile
        return result.takeIf { it.path.startsWith(rootCanonical.path + File.separator) }
    }

    private fun isSafeGitPath(path: String): Boolean = path.isNotBlank() &&
        !path.startsWith('/') && !path.startsWith('\\') &&
        path.split('/', '\\').none { it.isBlank() || it == "." || it == ".." }

    private fun pathMapFile(ctx: RepositoryContext) = File(ctx.workTree.parentFile, "${ctx.workTree.name}.paths.json")

    private fun loadPathMap(ctx: RepositoryContext) {
        val file = pathMapFile(ctx)
        if (!file.isFile) return
        runCatching {
            val json = JSONObject(file.readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val uri = keys.next()
                val relative = json.getString(uri)
                if (isSafeGitPath(relative)) {
                    ctx.uriToRelative[uri] = relative
                    ctx.relativeToUri[relative] = uri
                }
            }
        }
    }

    private fun savePathMap(ctx: RepositoryContext) {
        val json = JSONObject()
        ctx.uriToRelative.forEach { (uri, path) -> json.put(uri, path) }
        pathMapFile(ctx).writeText(json.toString())
    }

    private suspend fun <T> withRepository(block: suspend (RepositoryContext) -> VibeResult<T>): VibeResult<T> = withContext(dispatchers.io) {
        when (val context = prepare()) {
            is VibeResult.Success -> try { block(context.data) }
            catch (failure: Exception) { VibeResult.Error(failure.message ?: "Git operation failed.", failure) }
            is VibeResult.Error -> context
            else -> VibeResult.Error("Git repository is unavailable.")
        }
    }

    override suspend fun refreshStatus(): VibeResult<GitWorkspaceStatus> = withRepository { ctx ->
        val status = Git.wrap(ctx.repo).status().call()
        val stagedSet = status.added + status.changed + status.removed + status.missing
        val unstagedSet = status.modified + status.missing + status.untracked + status.conflicting
        fun item(path: String, staged: Boolean): GitFileStatus {
            val uri = ctx.relativeToUri[path] ?: path
            val type = when {
                path in status.removed || path in status.missing -> GitStatusType.DELETED
                path in status.added || path in status.untracked -> GitStatusType.ADDED
                else -> GitStatusType.MODIFIED
            }
            return GitFileStatus(uri, path.substringAfterLast('/'), type, staged)
        }
        val branches = Git.wrap(ctx.repo).branchList().call().map { ref ->
            val name = Repository.shortenRefName(ref.name)
            GitBranch(name, name == ctx.repo.branch)
        }.sortedBy { it.name }
        val commits = runCatching {
            Git.wrap(ctx.repo).log().setMaxCount(40).call().map { commit ->
                GitCommit(commit.name, commit.name.take(7), commit.shortMessage,
                    commit.authorIdent.name, commit.commitTime * 1000L)
            }.toList()
        }.getOrDefault(emptyList())
        val remote = ctx.repo.config.getString("remote", "origin", "url")
        val updated = GitWorkspaceStatus(
            currentBranch = ctx.repo.branch ?: "HEAD",
            branches = branches,
            stagedFiles = stagedSet.distinct().sorted().map { item(it, true) },
            unstagedFiles = unstagedSet.distinct().sorted().map { item(it, false) },
            commits = commits,
            remoteUrl = remote,
            remoteBranch = ctx.repo.config.getString("branch", ctx.repo.branch, "remote"),
            isClean = status.isClean,
            errorMessage = null
        )
        _status.value = updated
        VibeResult.Success(updated)
    }

    private fun resolvePath(ctx: RepositoryContext, path: String): String? =
        if (isSafeGitPath(path)) path else ctx.uriToRelative[path]

    override suspend fun stageFile(path: String): VibeResult<Unit> = withRepository { ctx ->
        val rel = resolvePath(ctx, path) ?: return@withRepository VibeResult.Error("Unknown workspace path: $path")
        if (safeFile(ctx.workTree, rel)?.exists() == true) Git.wrap(ctx.repo).add().addFilepattern(rel).call()
        else Git.wrap(ctx.repo).add().setUpdate(true).addFilepattern(rel).call()
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun unstageFile(path: String): VibeResult<Unit> = withRepository { ctx ->
        val rel = resolvePath(ctx, path) ?: return@withRepository VibeResult.Error("Unknown workspace path: $path")
        if (ctx.repo.resolve(Constants.HEAD) != null) Git.wrap(ctx.repo).reset().addPath(rel).call()
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun stageAll(): VibeResult<Unit> = withRepository { ctx ->
        val git = Git.wrap(ctx.repo)
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call()
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun unstageAll(): VibeResult<Unit> = withRepository { ctx ->
        if (ctx.repo.resolve(Constants.HEAD) != null) Git.wrap(ctx.repo).reset().call()
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun discardChanges(path: String): VibeResult<Unit> = withRepository { ctx ->
        val rel = resolvePath(ctx, path) ?: return@withRepository VibeResult.Error("Unknown workspace path: $path")
        val file = safeFile(ctx.workTree, rel) ?: return@withRepository VibeResult.Error("Invalid Git path: $rel")
        val head = ctx.repo.resolve(Constants.HEAD)
        if (head == null || rel in Git.wrap(ctx.repo).status().call().untracked) file.delete()
        else Git.wrap(ctx.repo).checkout().addPath(rel).call()
        ctx.relativeToUri[rel]?.let { uri ->
            if (file.exists()) {
                when (val result = workspaceManager.writeBytes(uri, file.readBytes())) {
                    is VibeResult.Error -> return@withRepository result
                    else -> Unit
                }
            } else {
                when (val result = workspaceManager.delete(uri)) {
                    is VibeResult.Error -> return@withRepository result
                    else -> Unit
                }
            }
        }
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun commit(message: String): VibeResult<GitCommit> = withRepository { ctx ->
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return@withRepository VibeResult.Error("Commit message cannot be empty.")
        val status = Git.wrap(ctx.repo).status().call()
        if (status.added.isEmpty() && status.changed.isEmpty() && status.removed.isEmpty()) {
            return@withRepository VibeResult.Error("No staged changes to commit.")
        }
        val config = ctx.repo.config
        config.setString("user", null, "name", config.getString("user", null, "name") ?: "VibeSpace User")
        config.setString("user", null, "email", config.getString("user", null, "email") ?: "vibespace@local.invalid")
        config.save()
        val commit = Git.wrap(ctx.repo).commit().setMessage(trimmed)
            .setAuthor(PersonIdent(config.getString("user", null, "name"), config.getString("user", null, "email"))).call()
        val model = GitCommit(commit.name, commit.name.take(7), commit.shortMessage, commit.authorIdent.name, commit.commitTime * 1000L)
        refreshStatusInContext(ctx)
        VibeResult.Success(model)
    }

    override suspend fun checkoutBranch(branchName: String): VibeResult<Unit> = withRepository { ctx ->
        if (!Git.wrap(ctx.repo).status().call().isClean) return@withRepository VibeResult.Error("Commit, stage, or discard workspace changes before switching branches.")
        val before = snapshot(ctx)
        Git.wrap(ctx.repo).checkout().setName(branchName).call()
        val applied = applyChangedFiles(ctx, before)
        if (applied is VibeResult.Error) return@withRepository applied
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun createBranch(branchName: String): VibeResult<Unit> = withRepository { ctx ->
        val name = branchName.trim()
        if (name.isEmpty() || name.startsWith("-") || name.contains("..")) return@withRepository VibeResult.Error("Enter a valid branch name.")
        Git.wrap(ctx.repo).branchCreate().setName(name).call()
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun getFileDiff(path: String): VibeResult<FileDiff> = withRepository { ctx ->
        val rel = resolvePath(ctx, path) ?: return@withRepository VibeResult.Error("Unknown workspace path: $path")
        val out = ByteArrayOutputStream()
        val formatter = DiffFormatter(out)
        formatter.setRepository(ctx.repo)
        val entries = Git.wrap(ctx.repo).diff().setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(rel)).call()
        if (entries.isEmpty()) return@withRepository VibeResult.Error("No Git diff is available for '$rel'.")
        val entry = entries.first()
        formatter.format(entry)
        formatter.flush()
        val lines = parseUnifiedDiff(out.toString(Charsets.UTF_8.name()))
        VibeResult.Success(FileDiff(path, lines, lines.count { it.type == DiffLineType.INSERT }, lines.count { it.type == DiffLineType.DELETE }))
    }

    override suspend fun configureRemote(url: String, username: String?, passwordOrToken: String?): VibeResult<Unit> = withRepository { ctx ->
        val cleaned = url.trim()
        val uri = runCatching { java.net.URI(cleaned) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
            return@withRepository VibeResult.Error("Enter a valid HTTPS or HTTP Git remote URL.")
        }
        if (uri.userInfo != null) return@withRepository VibeResult.Error("Do not embed credentials in the remote URL; enter them in the credential fields.")
        if (uri.scheme == "http" && (!username.isNullOrBlank() || !passwordOrToken.isNullOrBlank())) {
            return@withRepository VibeResult.Error("Credentials require an HTTPS remote.")
        }
        ctx.repo.config.setString("remote", "origin", "url", cleaned)
        ctx.repo.config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        ctx.repo.config.save()
        credentials = if (!username.isNullOrBlank() && !passwordOrToken.isNullOrBlank()) {
            UsernamePasswordCredentialsProvider(username, passwordOrToken)
        } else null
        refreshStatusInContext(ctx)
        VibeResult.Success(Unit)
    }

    override suspend fun push(): VibeResult<String> = withRepository { ctx ->
        val remote = ctx.repo.config.getString("remote", "origin", "url")
            ?: return@withRepository VibeResult.Error("Configure an HTTP(S) Git remote before pushing.")
        if (ctx.repo.resolve(Constants.HEAD) == null) return@withRepository VibeResult.Error("Create a commit before pushing.")
        val branch = ctx.repo.branch
        val results = Git.wrap(ctx.repo).push().setRemote(remote).setCredentialsProvider(credentials)
            .add("refs/heads/$branch").call()
        val rejected = results.flatMap { it.remoteUpdates }.firstOrNull { it.status.name.contains("REJECT") || it.status.name.contains("ERROR") }
        if (rejected != null) return@withRepository VibeResult.Error("Push failed (${rejected.status}): ${rejected.message ?: "remote rejected the update"}")
        refreshStatusInContext(ctx)
        VibeResult.Success("Pushed $branch to origin.")
    }

    override suspend fun pull(): VibeResult<String> = withRepository { ctx ->
        val remote = ctx.repo.config.getString("remote", "origin", "url")
            ?: return@withRepository VibeResult.Error("Configure an HTTP(S) Git remote before pulling.")
        if (!Git.wrap(ctx.repo).status().call().isClean) return@withRepository VibeResult.Error("Commit, stage, or discard workspace changes before pulling.")
        val before = snapshot(ctx)
        if (ctx.repo.resolve(Constants.HEAD) == null) {
            Git.wrap(ctx.repo).fetch().setRemote(remote).setCredentialsProvider(credentials).call()
            val remoteBranches = Git.wrap(ctx.repo).branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call()
                .map { Repository.shortenRefName(it.name) }
                .filter { it.startsWith("origin/") }
                .map { it.removePrefix("origin/") }
            val preferred = listOf("main", "master") + remoteBranches
            val remoteBranch = preferred.firstOrNull { it in remoteBranches }
                ?: return@withRepository VibeResult.Error("The remote has no discoverable branch.")
            Git.wrap(ctx.repo).checkout().setCreateBranch(true).setName(remoteBranch)
                .setStartPoint("refs/remotes/origin/$remoteBranch")
                .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK).call()
        }
        val result = Git.wrap(ctx.repo).pull().setRemote(remote).setCredentialsProvider(credentials).call()
        if (!result.isSuccessful) return@withRepository VibeResult.Error("Pull did not complete cleanly (${result.mergeResult?.mergeStatus ?: "unknown result"}). Resolve conflicts with a full Git client, then retry.")
        val applied = applyChangedFiles(ctx, before)
        if (applied is VibeResult.Error) return@withRepository applied
        refreshStatusInContext(ctx)
        VibeResult.Success("Pulled ${ctx.repo.branch} from origin.")
    }

    private suspend fun refreshStatusInContext(ctx: RepositoryContext) {
        when (val result = refreshStatus()) { is VibeResult.Error -> Unit; else -> Unit }
    }

    private fun snapshot(ctx: RepositoryContext): Map<String, ByteArray> = ctx.workTree.walkTopDown()
        .filter { it.isFile && it.relativeTo(ctx.workTree).invariantSeparatorsPath != ".git" && !it.relativeTo(ctx.workTree).invariantSeparatorsPath.startsWith(".git/") && it != pathMapFile(ctx) }
        .associate { it.relativeTo(ctx.workTree).invariantSeparatorsPath to it.readBytes() }

    private suspend fun applyChangedFiles(ctx: RepositoryContext, before: Map<String, ByteArray>): VibeResult<Unit> {
        val after = snapshot(ctx)
        val changed = (before.keys + after.keys).filter { path ->
            val a = before[path]
            val b = after[path]
            a == null || b == null || !a.contentEquals(b)
        }
        for (relative in changed) {
            if (!isSafeGitPath(relative)) continue
            val uri = resolveWorkspaceUri(ctx, relative, after.containsKey(relative))
                ?: return VibeResult.Error("Could not map Git path '$relative' into the active workspace.")
            if (after[relative] == null) {
                val result = workspaceManager.delete(uri)
                if (result is VibeResult.Error && !result.message.contains("not found", true)) return result
                ctx.uriToRelative[uri] = relative
                ctx.relativeToUri[relative] = uri
            } else {
                when (val result = workspaceManager.writeBytes(uri, after.getValue(relative))) {
                    is VibeResult.Error -> return VibeResult.Error("Could not apply '$relative' to the workspace: ${result.message}", result.throwable)
                    else -> Unit
                }
                ctx.uriToRelative[uri] = relative
                ctx.relativeToUri[relative] = uri
            }
        }
        savePathMap(ctx)
        return VibeResult.Success(Unit)
    }

    private suspend fun resolveWorkspaceUri(ctx: RepositoryContext, relative: String, createFile: Boolean): String? {
        ctx.relativeToUri[relative]?.let { return it }
        val segments = relative.split('/')
        var parent = ctx.project.rootUri
        for (segment in segments.dropLast(1)) {
            val listing = workspaceManager.listFiles(parent)
            val existing = (listing as? VibeResult.Success)?.data?.firstOrNull { it.isDirectory && it.name == segment }
            parent = existing?.uri ?: when (val created = workspaceManager.createDirectory(parent, segment)) {
                is VibeResult.Success -> created.data.uri
                else -> return null
            }
        }
        if (!createFile) {
            val existing = (workspaceManager.listFiles(parent) as? VibeResult.Success)?.data?.firstOrNull { it.name == segments.last() }
            return existing?.uri
        }
        return when (val created = workspaceManager.createFile(parent, segments.last())) {
            is VibeResult.Success -> created.data.uri
            is VibeResult.Error -> (workspaceManager.listFiles(parent) as? VibeResult.Success)?.data?.firstOrNull { it.name == segments.last() }?.uri
            else -> null
        }
    }

    private fun parseUnifiedDiff(raw: String): List<DiffLine> {
        val lines = mutableListOf<DiffLine>()
        var oldLine: Int? = null
        var newLine: Int? = null
        val hunk = Regex("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@")
        raw.lineSequence().forEach { line ->
            val match = hunk.find(line)
            if (match != null) {
                oldLine = match.groupValues[1].toInt()
                newLine = match.groupValues[2].toInt()
            } else if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("\\")) {
                Unit
            } else if (line.startsWith("+")) {
                lines += DiffLine(DiffLineType.INSERT, null, newLine, line.drop(1)); newLine = newLine?.plus(1)
            } else if (line.startsWith("-")) {
                lines += DiffLine(DiffLineType.DELETE, oldLine, null, line.drop(1)); oldLine = oldLine?.plus(1)
            } else if (line.startsWith(" ")) {
                lines += DiffLine(DiffLineType.EQUAL, oldLine, newLine, line.drop(1)); oldLine = oldLine?.plus(1); newLine = newLine?.plus(1)
            }
        }
        return lines
    }

    private fun String.toSha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).joinToString("") { "%02x".format(it) }
}
