package com.vibespace.feature.explorer

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.workspace.git.DefaultGitManager
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import com.vibespace.feature.explorer.git.GitViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitViewModelTest {

    private val dispatchers = TestDispatcherProvider()
    private val workspaceManager = DefaultWorkspaceManager(dispatchers = dispatchers)
    private val gitManager = DefaultGitManager(workspaceManager = workspaceManager, dispatchers = dispatchers)
    private val viewModel = GitViewModel(gitManager = gitManager, dispatchers = dispatchers)

    @Test
    fun commitMessage_updates_state() {
        viewModel.onCommitMessageChange("feat: initial commit")
        assertEquals("feat: initial commit", viewModel.uiState.value.commitMessage)
    }

    @Test
    fun staging_and_commit_flow_succeeds() = runTest {
        val root = "vibe://projects/vm-git"
        workspaceManager.openProject(Project("vm1", "VMProject", root))

        workspaceManager.writeFile("$root/main.py", "print('hello git')")
        viewModel.refreshStatus()
        viewModel.stageAll()

        viewModel.onCommitMessageChange("Initial commit from VM")
        viewModel.commit()

        val state = viewModel.uiState.value
        assertEquals("", state.commitMessage)
        assertTrue(state.status.isClean)
    }

    @Test
    fun openDiff_populates_activeDiff() = runTest {
        val root = "vibe://projects/vm-diff"
        workspaceManager.openProject(Project("vm2", "DiffProject", root))

        val fileUri = "$root/main.py"
        workspaceManager.writeFile(fileUri, "print('diff test')")

        viewModel.openDiff(fileUri)
        val state = viewModel.uiState.value
        assertEquals(fileUri, state.activeDiffFile)
        assertNotNull(state.activeDiff)

        viewModel.closeDiff()
        assertEquals(null, viewModel.uiState.value.activeDiff)
    }

    @Test
    fun dialog_toggles_and_sync_operations_work() = runTest {
        viewModel.toggleBranchDialog(true)
        assertTrue(viewModel.uiState.value.showBranchDialog)
        viewModel.toggleBranchDialog(false)
        assertFalse(viewModel.uiState.value.showBranchDialog)

        viewModel.toggleCommitDialog(true)
        assertTrue(viewModel.uiState.value.showCommitDialog)
        viewModel.toggleCommitDialog(false)
        assertFalse(viewModel.uiState.value.showCommitDialog)

        val root = "vibe://projects/vm-sync"
        workspaceManager.openProject(Project("vm3", "SyncProject", root))

        viewModel.pull()
        assertNotNull(viewModel.uiState.value.syncMessage)

        viewModel.push()
        assertNotNull(viewModel.uiState.value.syncMessage)
    }
}
