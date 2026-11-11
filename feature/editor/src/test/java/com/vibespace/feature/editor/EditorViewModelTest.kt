package com.vibespace.feature.editor

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.engine.DefaultEditorEngine
import com.vibespace.core.language.detector.DefaultLanguageDetector
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun opening_an_empty_project_does_not_invent_a_main_file() = runTest {
        val project = com.vibespace.core.workspace.model.Project("proj-auto", "AutoApp", "vibe://projects/auto")
        workspaceManager.openProject(project)

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = TestDispatcherProvider()
        )

        assertEquals(null, viewModel.uiState.value.activeDocument)
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

    @Test
    fun lsp_client_requestHover_and_requestDefinition_work() = runTest {
        val root = "vibe://projects/test-lsp"
        workspaceManager.createFile(root, "main.py", "def add(x, y): return x + y\n")

        val fakeLsp = object : com.vibespace.core.language.lsp.LanguageServerClient {
            override val languageId: String = "python"
            override val isRunning: Boolean = true
            override suspend fun start(workspaceRootUri: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun stop() = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun didOpen(documentUri: String, text: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun didChange(documentUri: String, newText: String, version: Int) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun didClose(documentUri: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override fun observeDiagnostics(documentUri: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.vibespace.core.language.model.Diagnostic>())
            override suspend fun requestHover(documentUri: String, line: Int, column: Int) =
                com.vibespace.core.common.result.VibeResult.Success(com.vibespace.core.language.model.HoverInfo("Adds two numbers"))
            override suspend fun requestCompletion(documentUri: String, line: Int, column: Int) =
                com.vibespace.core.common.result.VibeResult.Success(emptyList<com.vibespace.core.language.model.CompletionItem>())
            override suspend fun requestDefinition(documentUri: String, line: Int, column: Int) =
                com.vibespace.core.common.result.VibeResult.Success(listOf(com.vibespace.core.language.model.DefinitionLocation(documentUri, 1, 5)))
            override suspend fun formatDocument(documentUri: String) =
                com.vibespace.core.common.result.VibeResult.Success("def add(x, y):\n    return x + y\n")
        }

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            lspClientProvider = { fakeLsp },
            dispatchers = TestDispatcherProvider()
        )

        val file = VirtualFile(
            uri = "$root/main.py",
            name = "main.py",
            nodeType = FileNodeType.FILE,
            parentUri = root
        )

        viewModel.openFile(file)
        viewModel.requestHover(1, 5)

        assertEquals("Adds two numbers", viewModel.uiState.value.hoverInfo?.contents)

        viewModel.requestDefinition(1, 5)
        assertEquals(1, viewModel.uiState.value.definitionLocations.size)
        assertEquals(5, viewModel.uiState.value.definitionLocations[0].column)

        viewModel.formatDocument()
        assertEquals("def add(x, y):\n    return x + y\n", viewModel.uiState.value.activeDocument?.content)

        viewModel.clearHover()
        assertEquals(null, viewModel.uiState.value.hoverInfo)
    }

    @Test
    fun completion_suggestions_include_real_lsp_results() = runTest {
        val root = "vibe://projects/test-lsp-completions"
        workspaceManager.createFile(root, "main.py", "pri")
        val serverCompletion = com.vibespace.core.language.model.CompletionItem(
            label = "print_from_server",
            kind = com.vibespace.core.language.model.CompletionItemKind.FUNCTION
        )
        val fakeLsp = object : com.vibespace.core.language.lsp.LanguageServerClient {
            override val languageId = "python"
            override val isRunning = true
            override suspend fun start(workspaceRootUri: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun stop() = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun didOpen(documentUri: String, text: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun didChange(documentUri: String, newText: String, version: Int) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun didClose(documentUri: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override fun observeDiagnostics(documentUri: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.vibespace.core.language.model.Diagnostic>())
            override suspend fun requestHover(documentUri: String, line: Int, column: Int) =
                com.vibespace.core.common.result.VibeResult.Success(null)
            override suspend fun requestCompletion(documentUri: String, line: Int, column: Int) =
                com.vibespace.core.common.result.VibeResult.Success(listOf(serverCompletion))
            override suspend fun requestDefinition(documentUri: String, line: Int, column: Int) =
                com.vibespace.core.common.result.VibeResult.Success(emptyList<com.vibespace.core.language.model.DefinitionLocation>())
            override suspend fun formatDocument(documentUri: String) =
                com.vibespace.core.common.result.VibeResult.Success("")
        }
        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            lspClientProvider = { fakeLsp },
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler))
        )
        val file = VirtualFile("$root/main.py", "main.py", FileNodeType.FILE, parentUri = root)

        viewModel.openFile(file)
        viewModel.updateCompletions("pri")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.completions.any { it.label == "print_from_server" })
    }

    @Test
    fun saveActiveDocument_triggers_gitManager_refreshStatus() = runTest {
        val root = "vibe://projects/test-save-git"
        val project = com.vibespace.core.workspace.model.Project("proj-git", "GitApp", root)
        workspaceManager.createFile(root, "main.py", "print('before save')")
        workspaceManager.openProject(project)

        var refreshStatusCalled = false
        val fakeGit = object : com.vibespace.core.workspace.git.GitManager {
            override val status = kotlinx.coroutines.flow.MutableStateFlow(com.vibespace.core.workspace.git.GitWorkspaceStatus())
            override suspend fun refreshStatus(): com.vibespace.core.common.result.VibeResult<com.vibespace.core.workspace.git.GitWorkspaceStatus> {
                refreshStatusCalled = true
                return com.vibespace.core.common.result.VibeResult.Success(status.value)
            }
            override suspend fun stageFile(path: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun unstageFile(path: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun stageAll() = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun unstageAll() = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun discardChanges(path: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun commit(message: String) = com.vibespace.core.common.result.VibeResult.Error("stub")
            override suspend fun checkoutBranch(branchName: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun createBranch(branchName: String) = com.vibespace.core.common.result.VibeResult.Success(Unit)
            override suspend fun getFileDiff(path: String) = com.vibespace.core.common.result.VibeResult.Error("stub")
            override suspend fun push() = com.vibespace.core.common.result.VibeResult.Success("Pushed")
            override suspend fun pull() = com.vibespace.core.common.result.VibeResult.Success("Pulled")
        }

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            gitManager = fakeGit,
            dispatchers = TestDispatcherProvider()
        )

        val file = VirtualFile(
            uri = "$root/main.py",
            name = "main.py",
            nodeType = FileNodeType.FILE,
            parentUri = root
        )

        viewModel.openFile(file)
        viewModel.onContentChanged("print('changed for git')")
        viewModel.saveActiveDocument()

        assertTrue(refreshStatusCalled)
        assertEquals("Saved main.py", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun lsp_unavailable_reports_clear_status_message() = runTest {
        val root = "vibe://projects/test-no-lsp"
        workspaceManager.createFile(root, "main.py", "def add(x, y): return x + y\n")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            lspClientProvider = null,
            dispatchers = TestDispatcherProvider()
        )

        val file = VirtualFile(
            uri = "$root/main.py",
            name = "main.py",
            nodeType = FileNodeType.FILE,
            parentUri = root
        )

        viewModel.openFile(file)
        viewModel.requestHover(1, 5)
        assertEquals("Language server unavailable for Python", viewModel.uiState.value.statusMessage)
    }
}
