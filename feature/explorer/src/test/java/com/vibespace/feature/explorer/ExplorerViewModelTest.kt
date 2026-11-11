package com.vibespace.feature.explorer

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerViewModelTest {

    private val workspaceManager = DefaultWorkspaceManager(dispatchers = TestDispatcherProvider())

    @Test
    fun project_open_loads_nodes_and_supports_create_file() = runTest {
        val project = Project("p1", "ExpTest", "vibe://projects/exptest")
        workspaceManager.createFile(project.rootUri, "README.md", "# Project")
        workspaceManager.openProject(project)

        val viewModel = ExplorerViewModel(
            workspaceManager = workspaceManager,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.refreshFiles()
        assertTrue(viewModel.uiState.value.rootNodes.isNotEmpty())

        viewModel.createNewFile("Script.py")
        viewModel.refreshFiles()
        assertTrue(viewModel.uiState.value.rootNodes.any { it.file.name == "Script.py" })
    }

    @Test
    fun toggleFolder_expands_folder_and_loads_children() = runTest {
        val project = Project("p2", "ToggleTest", "vibe://projects/toggletest")
        workspaceManager.createDirectory(project.rootUri, "src")
        workspaceManager.createFile("${project.rootUri}/src", "Main.kt", "fun main() = Unit")
        workspaceManager.openProject(project)

        val viewModel = ExplorerViewModel(
            workspaceManager = workspaceManager,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.refreshFiles()
        val srcNode = viewModel.uiState.value.rootNodes.first { it.file.name == "src" }

        viewModel.toggleFolder(srcNode.file.uri)
        val updatedSrc = viewModel.uiState.value.rootNodes.first { it.file.name == "src" }
        assertTrue(updatedSrc.isExpanded)
        assertTrue(updatedSrc.children.any { it.file.name == "Main.kt" })
    }

    @Test
    fun gitManager_updates_fileGitStatuses_in_explorer() = runTest {
        val project = Project("p3", "GitStatusTest", "vibe://projects/gitstatustest")
        workspaceManager.openProject(project)

        val gitManager = com.vibespace.core.workspace.git.DefaultGitManager(
            workspaceManager = workspaceManager,
            dispatchers = TestDispatcherProvider()
        )

        val viewModel = ExplorerViewModel(
            workspaceManager = workspaceManager,
            gitManager = gitManager,
            dispatchers = TestDispatcherProvider()
        )

        workspaceManager.writeFile("vibe://projects/gitstatustest/new_file.py", "print('hello')")
        gitManager.refreshStatus()

        val statuses = viewModel.uiState.value.fileGitStatuses
        assertTrue(statuses.containsKey("vibe://projects/gitstatustest/new_file.py"))
        assertEquals(com.vibespace.core.workspace.git.GitStatusType.UNTRACKED, statuses["vibe://projects/gitstatustest/new_file.py"])
    }
}
