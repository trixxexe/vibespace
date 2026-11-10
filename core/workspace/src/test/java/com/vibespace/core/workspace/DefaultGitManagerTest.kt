package com.vibespace.core.workspace

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.workspace.git.DefaultGitManager
import com.vibespace.core.workspace.git.DiffLineType
import com.vibespace.core.workspace.git.GitStatusType
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGitManagerTest {

    private val dispatchers = TestDispatcherProvider()
    private val workspaceManager = DefaultWorkspaceManager(dispatchers = dispatchers)
    private val gitManager = DefaultGitManager(workspaceManager = workspaceManager, dispatchers = dispatchers)

    @Test
    fun projectInit_creates_clean_baseline() = runTest {
        val root = "vibe://projects/git-test"
        workspaceManager.openProject(Project("g1", "GitProject", root))

        gitManager.refreshStatus()
        val status = gitManager.status.value
        assertEquals("main", status.currentBranch)
        assertTrue(status.stagedFiles.isEmpty())
        assertTrue(status.unstagedFiles.isEmpty())
        assertTrue(status.isClean)
    }

    @Test
    fun modifying_file_detects_modified_status_and_staging_works() = runTest {
        val root = "vibe://projects/git-mod"
        workspaceManager.openProject(Project("g2", "GitMod", root))

        val readmeUri = "$root/README.md"
        workspaceManager.writeFile(readmeUri, "# Modified Content\nExtra line")

        gitManager.refreshStatus()
        val status = gitManager.status.value
        assertFalse(status.isClean)
        assertEquals(1, status.unstagedFiles.size)
        assertEquals(GitStatusType.MODIFIED, status.unstagedFiles[0].statusType)

        // Stage file
        gitManager.stageFile(readmeUri)
        val stagedStatus = gitManager.status.value
        assertEquals(1, stagedStatus.stagedFiles.size)
        assertEquals(0, stagedStatus.unstagedFiles.size)

        // Unstage file
        gitManager.unstageFile(readmeUri)
        val unstageStatus = gitManager.status.value
        assertEquals(0, unstageStatus.stagedFiles.size)
        assertEquals(1, unstageStatus.unstagedFiles.size)
    }

    @Test
    fun commit_persists_staged_changes_and_records_history() = runTest {
        val root = "vibe://projects/git-commit"
        workspaceManager.openProject(Project("g3", "GitCommit", root))

        val appPyUri = "$root/main.py"
        workspaceManager.writeFile(appPyUri, "print('Commit Test')")

        gitManager.refreshStatus()
        gitManager.stageAll()
        val commitResult = gitManager.commit("Update main.py for release")
        assertTrue(commitResult.isSuccess)

        val statusAfter = gitManager.status.value
        assertTrue(statusAfter.isClean)
        assertTrue(statusAfter.stagedFiles.isEmpty())
        assertEquals("Update main.py for release", statusAfter.commits.first().message)
    }

    @Test
    fun getFileDiff_calculates_line_insertions_and_deletions() = runTest {
        val root = "vibe://projects/git-diff"
        workspaceManager.openProject(Project("g4", "GitDiff", root))

        val pyUri = "$root/main.py"
        workspaceManager.writeFile(pyUri, "print('Line 1')\nprint('Line 2')")

        val diffResult = gitManager.getFileDiff(pyUri)
        assertTrue(diffResult.isSuccess)
        val diff = diffResult.getOrNull()
        assertNotNull(diff)
        assertTrue(diff!!.lines.any { it.type == DiffLineType.INSERT })
    }

    @Test
    fun branch_creation_and_checkout_updates_active_branch() = runTest {
        val root = "vibe://projects/git-branch"
        workspaceManager.openProject(Project("g5", "GitBranch", root))

        val createResult = gitManager.createBranch("feature/dark-mode")
        assertTrue(createResult.isSuccess)
        assertEquals("feature/dark-mode", gitManager.status.value.currentBranch)

        val checkoutResult = gitManager.checkoutBranch("main")
        assertTrue(checkoutResult.isSuccess)
        assertEquals("main", gitManager.status.value.currentBranch)
    }

    @Test
    fun push_and_pull_truthfully_report_unavailable_remote_transport() = runTest {
        val root = "vibe://projects/git-push"
        workspaceManager.openProject(Project("g6", "GitPush", root))

        val appUri = "$root/app.py"
        workspaceManager.writeFile(appUri, "print('push test')")

        gitManager.stageAll()
        gitManager.commit("Commit ahead")

        val pushResult = gitManager.push()
        assertTrue(pushResult.isFailure)
        assertTrue(pushResult.toString().contains("unavailable") || pushResult.toString().contains("sandbox"))

        val pullResult = gitManager.pull()
        assertTrue(pullResult.isFailure)
        assertTrue(pullResult.toString().contains("unavailable") || pullResult.toString().contains("sandbox"))
    }

    @Test
    fun getFileDiff_bounds_large_files_and_prevents_oom() = runTest {
        val root = "vibe://projects/git-large-diff"
        workspaceManager.openProject(Project("g7", "GitLargeDiff", root))

        val bigFileUri = "$root/large.txt"
        val largeContent = (1..1500).joinToString("\n") { "line $it" }
        workspaceManager.writeFile(bigFileUri, largeContent)

        // Seed baseline then modify
        gitManager.stageAll()
        gitManager.commit("Baseline large")

        val modifiedContent = (1..1500).joinToString("\n") { "modified line $it" }
        workspaceManager.writeFile(bigFileUri, modifiedContent)

        val diffResult = gitManager.getFileDiff(bigFileUri)
        assertTrue(diffResult.isSuccess)
        val diff = diffResult.getOrNull()
        assertNotNull(diff)
        assertTrue(diff!!.lines.any { it.text.contains("Diff truncated") })
    }

    @Test
    fun getFileDiff_propagates_read_errors_for_missing_files() = runTest {
        val root = "vibe://projects/git-err-diff"
        workspaceManager.openProject(Project("g8", "GitErrDiff", root))

        val diffResult = gitManager.getFileDiff("vibe://projects/git-err-diff/does_not_exist.txt")
        assertTrue(diffResult.isFailure)
    }
}
