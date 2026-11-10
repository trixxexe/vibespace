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
        assertTrue(files.any { it.name == "main.py" && it.isFile })
        assertTrue(files.any { it.name == "index.js" && it.isFile })

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

    @Test
    fun getAllProjectFiles_recursively_returns_nested_files() = runTest {
        val root = "vibe://projects/proj-nested"
        val project = Project(id = "proj-nested", name = "NestedTest", rootUri = root)
        workspaceManager.openProject(project)

        val allFiles = workspaceManager.getAllProjectFiles(root).getOrNull()
        assertNotNull(allFiles)
        // Should contain root files and also src/Main.kt which is nested inside src
        assertTrue(allFiles!!.any { it.name == "Main.kt" })
        assertTrue(allFiles.any { it.name == "main.py" })
        assertTrue(allFiles.any { it.name == "index.js" })
    }

    @Test
    fun disk_operations_create_read_write_delete_work_correctly() = runTest {
        val tempDir = java.io.File.createTempFile("vibe_test_disk_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val rootUri = tempDir.toURI().toString()
            val project = Project(id = "disk-proj", name = "DiskTest", rootUri = rootUri)
            workspaceManager.openProject(project)

            // Create file on disk
            val createResult = workspaceManager.createFile(rootUri, "disk_script.py", "print('on disk')")
            assertTrue(createResult.isSuccess)
            val physicalFile = java.io.File(tempDir, "disk_script.py")
            assertTrue(physicalFile.exists())
            assertEquals("print('on disk')", physicalFile.readText())

            // Write updated content
            val writeResult = workspaceManager.writeFile(createResult.getOrNull()!!.uri, "print('updated on disk')")
            assertTrue(writeResult.isSuccess)
            assertEquals("print('updated on disk')", physicalFile.readText())

            // Read via manager
            val readResult = workspaceManager.readFile(createResult.getOrNull()!!.uri)
            assertTrue(readResult.isSuccess)
            assertEquals("print('updated on disk')", readResult.getOrNull())

            // Rename file
            val renameResult = workspaceManager.rename(createResult.getOrNull()!!.uri, "disk_renamed.py")
            assertTrue(renameResult.isSuccess)
            val renamedPhysical = java.io.File(tempDir, "disk_renamed.py")
            assertTrue(renamedPhysical.exists())
            assertTrue(!physicalFile.exists())

            // List files from disk
            val listResult = workspaceManager.listFiles(rootUri)
            assertTrue(listResult.isSuccess)
            assertTrue(listResult.getOrNull()!!.any { it.name == "disk_renamed.py" })

            // Delete file
            val deleteResult = workspaceManager.delete(renameResult.getOrNull()!!.uri)
            assertTrue(deleteResult.isSuccess)
            assertTrue(!renamedPhysical.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun disk_operation_rejects_sibling_path_sharing_prefix() = runTest {
        val baseDir = java.io.File.createTempFile("vibe_base_", "").apply {
            delete()
            mkdirs()
        }
        val projectDir = java.io.File(baseDir, "project").apply { mkdirs() }
        val siblingDir = java.io.File(baseDir, "project-sibling").apply { mkdirs() }
        val siblingFile = java.io.File(siblingDir, "stolen.txt").apply { writeText("secret") }

        try {
            val project = Project(id = "safe-proj", name = "SafeProject", rootUri = projectDir.toURI().toString())
            workspaceManager.openProject(project)

            // Attempt to write to sibling directory which shares prefix with projectDir
            val siblingFileUri = siblingFile.toURI().toString()
            val writeResult = workspaceManager.writeFile(siblingFileUri, "attack")
            assertTrue(writeResult.isFailure)
            assertTrue(writeResult.toString().contains("Security error") || writeResult.toString().contains("escapes"))
            assertEquals("secret", siblingFile.readText())

            // Attempt to delete sibling file
            val deleteResult = workspaceManager.delete(siblingFileUri)
            assertTrue(deleteResult.isFailure)
            assertTrue(siblingFile.exists())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun createFile_rejects_null_bytes_and_path_separators_in_name() = runTest {
        val tempDir = java.io.File.createTempFile("vibe_name_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val rootUri = tempDir.toURI().toString()
            val project = Project(id = "name-proj", name = "NameTest", rootUri = rootUri)
            workspaceManager.openProject(project)

            val resNullByte = workspaceManager.createFile(rootUri, "evil\u0000file.py")
            assertTrue(resNullByte.isFailure)

            val resSlash = workspaceManager.createFile(rootUri, "../traversal.py")
            assertTrue(resSlash.isFailure)

            val resDotDot = workspaceManager.createFile(rootUri, "..")
            assertTrue(resDotDot.isFailure)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun disk_operation_fails_when_no_active_project_is_open() = runTest {
        workspaceManager.closeProject()
        val tempFile = java.io.File.createTempFile("vibe_orphan_", ".txt")
        try {
            val res = workspaceManager.writeFile(tempFile.toURI().toString(), "data")
            assertTrue(res.isFailure)
            assertTrue(res.toString().contains("active project"))
        } finally {
            tempFile.delete()
        }
    }
}
