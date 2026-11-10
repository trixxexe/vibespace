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
}
