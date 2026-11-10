package com.vibespace.core.workspace

import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.repository.InMemoryRecentProjectsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentProjectsRepositoryTest {

    private val repository = InMemoryRecentProjectsRepository()

    @Test
    fun addRecentProject_prepends_project_and_avoids_duplicates() = runTest {
        val p1 = Project("1", "App1", "vibe://projects/1")
        val p2 = Project("2", "App2", "vibe://projects/2")

        repository.addRecentProject(p1)
        repository.addRecentProject(p2)

        val list = repository.getRecentProjects().first()
        assertEquals(2, list.size)
        assertEquals("2", list[0].id)
        assertEquals("1", list[1].id)

        // Adding p1 again moves it to top
        repository.addRecentProject(p1)
        val list2 = repository.getRecentProjects().first()
        assertEquals(2, list2.size)
        assertEquals("1", list2[0].id)
    }

    @Test
    fun removeRecentProject_removes_from_list() = runTest {
        val p1 = Project("1", "App1", "vibe://projects/1")
        repository.addRecentProject(p1)
        assertEquals(1, repository.getRecentProjects().first().size)

        repository.removeRecentProject("1")
        assertTrue(repository.getRecentProjects().first().isEmpty())
    }

    @Test
    fun clearRecentProjects_clears_all() = runTest {
        repository.addRecentProject(Project("1", "App1", "vibe://projects/1"))
        repository.addRecentProject(Project("2", "App2", "vibe://projects/2"))
        repository.clearRecentProjects()
        assertTrue(repository.getRecentProjects().first().isEmpty())
    }
}
