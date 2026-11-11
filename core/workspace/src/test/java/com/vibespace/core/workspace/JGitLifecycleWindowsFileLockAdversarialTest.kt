package com.vibespace.core.workspace

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.git.JGitWorkspaceManager
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class JGitLifecycleWindowsFileLockAdversarialTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun projectSwitch_closesOldRepoAndReleasesAllFileLocksOnWindows() = runTest {
        val dispatchers = TestDispatcherProvider()
        val workspace = DefaultWorkspaceManager(dispatchers = dispatchers)
        val storageDir = temporaryFolder.newFolder("git_storage_lifecycle")

        // Project 1
        val p1Root = temporaryFolder.newFolder("proj1_workspace")
        val p1File = File(p1Root, "code.py").apply { writeText("print('proj1')\n") }
        val p1 = Project("proj-1", "Project 1", p1Root.toURI().toString())
        assertTrue(workspace.openProject(p1).isSuccess)

        val git = JGitWorkspaceManager(
            workspaceManager = workspace,
            storageDirectory = storageDir,
            dispatchers = dispatchers
        )

        // Init and commit on Project 1
        assertTrue(git.refreshStatus().isSuccess)
        assertTrue(git.configureIdentity("Tester 1", "tester1@example.com").isSuccess)
        assertTrue(git.stageAll().isSuccess)
        val commit1 = git.commit("Initial P1 commit")
        assertTrue("Commit 1 must succeed", commit1.isSuccess)

        // Verify diff does not hold open object reader locks
        val diffResult = git.getFileDiff("code.py")
        // code.py is committed and clean, so diff may be empty or error (no diff), which is expected
        assertNotNull(diffResult)

        val p1StorageRepoDir = File(storageDir, sha256("proj-1"))
        assertTrue("Project 1 repo directory must exist", p1StorageRepoDir.exists())

        // Switch to Project 2 (this should invoke active.getAndSet(ctx)?.repo?.close())
        val p2Root = temporaryFolder.newFolder("proj2_workspace")
        File(p2Root, "index.js").writeText("console.log('p2');\n")
        val p2 = Project("proj-2", "Project 2", p2Root.toURI().toString())
        assertTrue(workspace.openProject(p2).isSuccess)

        // Trigger prepare() on Project 2
        assertTrue(git.refreshStatus().isSuccess)
        assertTrue(git.configureIdentity("Tester 2", "tester2@example.com").isSuccess)
        assertTrue(git.stageAll().isSuccess)
        val commit2 = git.commit("Initial P2 commit")
        assertTrue("Commit 2 must succeed", commit2.isSuccess)

        // EMPIRICAL WINDOWS FILE LOCK TEST:
        // Because Project 1's repository was closed during project switch,
        // all file handles to Project 1's git repo must be released on Windows.
        // On Windows, if any lock or handle is retained, deleteRecursively() will return false.
        val deletedP1 = p1StorageRepoDir.deleteRecursively()
        assertTrue("Project 1 storage directory must be cleanly deletable without Windows file lock errors", deletedP1)
        assertTrue("Project 1 storage directory should no longer exist", !p1StorageRepoDir.exists())
    }

    @Test
    fun multipleConsecutiveCommits_operateDeterministicallyWithoutLockContention() = runTest {
        val dispatchers = TestDispatcherProvider()
        val workspace = DefaultWorkspaceManager(dispatchers = dispatchers)
        val storageDir = temporaryFolder.newFolder("git_storage_stress")

        val root = temporaryFolder.newFolder("workspace_stress")
        val source = File(root, "file.txt").apply { writeText("version 0\n") }
        val project = Project("stress-project", "Stress Project", root.toURI().toString())
        assertTrue(workspace.openProject(project).isSuccess)

        val git = JGitWorkspaceManager(
            workspaceManager = workspace,
            storageDirectory = storageDir,
            dispatchers = dispatchers
        )

        assertTrue(git.refreshStatus().isSuccess)
        assertTrue(git.configureIdentity("Stress Tester", "stress@example.com").isSuccess)

        // Perform 5 consecutive edit -> stage -> commit -> status cycles
        for (i in 1..5) {
            source.writeText("version $i\n")
            // Sync workspace to git by staging
            assertTrue("Stage cycle $i must succeed", git.stageAll().isSuccess)
            val commitResult = git.commit("Commit version $i")
            assertTrue("Commit cycle $i must succeed", commitResult is VibeResult.Success)
            val statusResult = git.refreshStatus()
            assertTrue("Status cycle $i must succeed", statusResult is VibeResult.Success)
            val status = (statusResult as VibeResult.Success).data
            assertTrue("Workspace must be clean after commit $i", status.isClean)
            assertEquals("Author must match configured identity", "Stress Tester", status.authorName)
        }

        // Verify git log contains all 5 commits
        val finalStatus = (git.refreshStatus() as VibeResult.Success).data
        assertEquals("Log must contain 5 commits", 5, finalStatus.commits.size)
    }

    @Test
    fun reloadingSameProjectWorkspace_reusesRepoContextCleanly() = runTest {
        val dispatchers = TestDispatcherProvider()
        val workspace = DefaultWorkspaceManager(dispatchers = dispatchers)
        val storageDir = temporaryFolder.newFolder("git_storage_reload")

        val root = temporaryFolder.newFolder("workspace_reload")
        File(root, "hello.txt").writeText("hello\n")
        val project = Project("reload-proj", "Reload Project", root.toURI().toString())
        assertTrue(workspace.openProject(project).isSuccess)

        val git = JGitWorkspaceManager(
            workspaceManager = workspace,
            storageDirectory = storageDir,
            dispatchers = dispatchers
        )

        assertTrue(git.refreshStatus().isSuccess)
        assertTrue(git.configureIdentity("Reload Author", "author@reload.test").isSuccess)
        assertTrue(git.stageAll().isSuccess)
        assertTrue(git.commit("First commit").isSuccess)

        // Re-open same project in workspace
        assertTrue(workspace.openProject(project).isSuccess)
        val reloadedStatus = git.refreshStatus()
        assertTrue(reloadedStatus is VibeResult.Success)
        val statusData = (reloadedStatus as VibeResult.Success).data
        assertEquals("Reload Author", statusData.authorName)
        assertTrue(statusData.isClean)
    }
}
