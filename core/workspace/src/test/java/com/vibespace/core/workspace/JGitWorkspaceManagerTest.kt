package com.vibespace.core.workspace

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.workspace.git.JGitWorkspaceManager
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitWorkspaceManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun repositoryStagesAndCommitsWithExplicitIdentity() = runTest {
        val root = temporaryFolder.newFolder("workspace")
        val source = File(root, "src/main.py")
        source.parentFile?.mkdirs()
        source.writeText("print('hello')\n")

        val dispatchers = TestDispatcherProvider()
        val workspace = DefaultWorkspaceManager(dispatchers = dispatchers)
        val project = Project("git-project", "Git project", root.toURI().toString())
        assertTrue(workspace.openProject(project).isSuccess)

        val git = JGitWorkspaceManager(
            workspaceManager = workspace,
            storageDirectory = temporaryFolder.newFolder("repositories"),
            dispatchers = dispatchers
        )

        assertTrue(git.stageAll().isSuccess)
        val withoutIdentity = git.commit("Add source")
        assertTrue(withoutIdentity is VibeResult.Error)
        assertTrue(git.configureIdentity("Test Author", "author@example.test").isSuccess)
        assertTrue(git.commit("Add source") is VibeResult.Success)

        val status = git.refreshStatus()
        assertTrue(status is VibeResult.Success)
        val success = status as VibeResult.Success
        assertEquals(true, success.data.isClean)
        assertEquals("Test Author", success.data.commits.first().author)
    }
}
