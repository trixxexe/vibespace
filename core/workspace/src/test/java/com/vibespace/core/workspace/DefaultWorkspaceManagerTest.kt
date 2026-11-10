package com.vibespace.core.workspace

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.Project
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWorkspaceManagerTest {

    private val workspaceManager = DefaultWorkspaceManager(dispatchers = TestDispatcherProvider())

    @Test
    fun openProject_initializes_project_structure_and_can_read_files() = runTest {
        val project = Project(
            id = "proj-1",
            name = "TestProject",
            rootUri = "vibe://projects/proj-1"
        )

        val openResult = workspaceManager.openProject(project)
        assertTrue(openResult.isSuccess)
        assertEquals(project, workspaceManager.currentProject.value)

        val listResult = workspaceManager.listFiles("vibe://projects/proj-1")
        assertTrue(listResult.isSuccess)
        val files = listResult.getOrNull()
        assertNotNull(files)
        assertTrue(files!!.any { it.name == "src" && it.isDirectory })
        assertTrue(files.any { it.name == "README.md" && it.isFile })

        val readResult = workspaceManager.readFile("vibe://projects/proj-1/README.md")
        assertTrue(readResult.isSuccess)
        assertTrue(readResult.getOrNull()!!.contains("TestProject"))
    }

    @Test
    fun createFile_and_writeFile_persists_content() = runTest {
        val root = "vibe://projects/proj-create"
        val project = Project(id = "proj-2", name = "CreateTest", rootUri = root)
        workspaceManager.openProject(project)

        val createResult = workspaceManager.createFile(root, "app.py", "print('hello')")
        assertTrue(createResult.isSuccess)

        val readBefore = workspaceManager.readFile("$root/app.py")
        assertEquals("print('hello')", readBefore.getOrNull())

        workspaceManager.writeFile("$root/app.py", "print('updated')")
        val readAfter = workspaceManager.readFile("$root/app.py")
        assertEquals("print('updated')", readAfter.getOrNull())
    }
}
