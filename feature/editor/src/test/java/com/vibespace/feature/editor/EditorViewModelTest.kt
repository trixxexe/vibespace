package com.vibespace.feature.editor

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.engine.DefaultEditorEngine
import com.vibespace.core.language.detector.DefaultLanguageDetector
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorViewModelTest {

    private val editorEngine = DefaultEditorEngine(dispatchers = TestDispatcherProvider())
    private val workspaceManager = DefaultWorkspaceManager(dispatchers = TestDispatcherProvider())
    private val languageDetector = DefaultLanguageDetector()

    @Test
    fun openFile_loads_content_and_sets_active_doc() = runTest {
        val root = "vibe://projects/test"
        workspaceManager.createFile(root, "app.py", "print('hello')")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = TestDispatcherProvider()
        )

        val file = VirtualFile(
            uri = "$root/app.py",
            name = "app.py",
            nodeType = FileNodeType.FILE,
            parentUri = root
        )

        viewModel.openFile(file)
        val activeDoc = viewModel.uiState.value.activeDocument
        assertNotNull(activeDoc)
        assertEquals("app.py", activeDoc!!.fileName)
        assertEquals("print('hello')", activeDoc.content)
        assertEquals("Python", activeDoc.language.displayName)
    }

    @Test
    fun onContentChanged_updates_content_and_dirty_flag() = runTest {
        val root = "vibe://projects/test2"
        workspaceManager.createFile(root, "index.js", "const a = 1;")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = TestDispatcherProvider()
        )

        val file = VirtualFile(
            uri = "$root/index.js",
            name = "index.js",
            nodeType = FileNodeType.FILE,
            parentUri = root
        )

        viewModel.openFile(file)
        viewModel.onContentChanged("const a = 2;")

        val active = viewModel.uiState.value.activeDocument
        assertEquals("const a = 2;", active!!.content)
        assertTrue(active.isDirty)
    }

    @Test
    fun workspace_project_open_auto_opens_main_py() = runTest {
        val project = com.vibespace.core.workspace.model.Project("proj-auto", "AutoApp", "vibe://projects/auto")
        workspaceManager.openProject(project)

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = TestDispatcherProvider()
        )

        val active = viewModel.uiState.value.activeDocument
        assertNotNull(active)
        assertEquals("main.py", active!!.fileName)
    }

    @Test
    fun setEditorTheme_updates_themeId_in_ui_state() = runTest {
        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = TestDispatcherProvider()
        )

        assertEquals("vibe_dark", viewModel.uiState.value.themeId)
        viewModel.setEditorTheme("dracula")
        assertEquals("dracula", viewModel.uiState.value.themeId)
    }

    @Test
    fun onContentChanged_generates_diagnostics_and_completions() = runTest {
        val root = "vibe://projects/test-diag"
        workspaceManager.createFile(root, "test.py", "def broken(\n")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = TestDispatcherProvider()
        )

        val file = VirtualFile(
            uri = "$root/test.py",
            name = "test.py",
            nodeType = FileNodeType.FILE,
            parentUri = root
        )

        viewModel.openFile(file)
        viewModel.onContentChanged("def broken(\n")

        val state = viewModel.uiState.value
        assertTrue(state.diagnostics.isNotEmpty())
    }
}
