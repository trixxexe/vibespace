package com.vibespace.feature.home

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.repository.InMemoryRecentProjectsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private val workspaceManager = DefaultWorkspaceManager(dispatchers = TestDispatcherProvider())
    private val recentRepo = InMemoryRecentProjectsRepository()

    @Test
    fun createNewProject_opens_project_and_updates_state() = runTest {
        val viewModel = HomeViewModel(
            workspaceManager = workspaceManager,
            recentProjectsRepository = recentRepo,
            dispatchers = TestDispatcherProvider()
        )

        var openedProject: Project? = null
        viewModel.createNewProject("VibeTest") {
            openedProject = it
        }

        assertNotNull(openedProject)
        assertEquals("VibeTest", openedProject!!.name)
        assertEquals(openedProject, workspaceManager.currentProject.value)
    }

    @Test
    fun openDemoProject_uses_a_dedicated_seeded_workspace() = runTest {
        val viewModel = HomeViewModel(
            workspaceManager = workspaceManager,
            recentProjectsRepository = recentRepo,
            dispatchers = TestDispatcherProvider()
        )

        var openedProject: Project? = null
        viewModel.openDemoProject { openedProject = it }

        assertNotNull(openedProject)
        assertEquals("demo", openedProject!!.id)
        assertEquals("vibe://demo", openedProject!!.rootUri)
        assertTrue(workspaceManager.listFiles("vibe://demo").getOrNull().orEmpty().any { it.name == "main.py" })
    }

    @Test
    fun loadRecentProjects_populates_list() = runTest {
        val project = Project("p1", "Recent", "vibe://recent")
        recentRepo.addRecentProject(project)

        val viewModel = HomeViewModel(
            workspaceManager = workspaceManager,
            recentProjectsRepository = recentRepo,
            dispatchers = TestDispatcherProvider()
        )

        viewModel.loadRecentProjects()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.recentProjects.size)
        assertEquals("Recent", viewModel.uiState.value.recentProjects[0].name)
    }
}
