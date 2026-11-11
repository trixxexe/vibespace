package com.vibespace.feature.editor

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.engine.DefaultEditorEngine
import com.vibespace.core.language.detector.DefaultLanguageDetector
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.language.lsp.LanguageServerClient
import com.vibespace.core.workspace.manager.DefaultWorkspaceManager
import com.vibespace.core.workspace.model.FileNodeType
import com.vibespace.core.workspace.model.Project
import com.vibespace.core.workspace.model.VirtualFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelConcurrencyAdversarialTest {

    private fun createFakeLsp(
        completionCallCount: AtomicInteger,
        responsePrefix: String = "srv"
    ): LanguageServerClient {
        return object : LanguageServerClient {
            override val languageId: String = "python"
            override val isRunning: Boolean = true

            override suspend fun start(workspaceRootUri: String) = VibeResult.Success(Unit)
            override suspend fun stop() = VibeResult.Success(Unit)
            override suspend fun didOpen(documentUri: String, text: String) = VibeResult.Success(Unit)
            override suspend fun didChange(documentUri: String, newText: String, version: Int) = VibeResult.Success(Unit)
            override suspend fun didClose(documentUri: String) = VibeResult.Success(Unit)
            override fun observeDiagnostics(documentUri: String) = flowOf(emptyList<Diagnostic>())
            override suspend fun requestHover(documentUri: String, line: Int, column: Int) = VibeResult.Success(null)

            override suspend fun requestCompletion(documentUri: String, line: Int, column: Int): VibeResult<List<CompletionItem>> {
                completionCallCount.incrementAndGet()
                return VibeResult.Success(
                    listOf(
                        CompletionItem(
                            label = "${responsePrefix}_result_${completionCallCount.get()}",
                            kind = CompletionItemKind.FUNCTION
                        )
                    )
                )
            }

            override suspend fun requestDefinition(documentUri: String, line: Int, column: Int) =
                VibeResult.Success(emptyList<com.vibespace.core.language.model.DefinitionLocation>())

            override suspend fun formatDocument(documentUri: String) = VibeResult.Success("")
        }
    }

    @Test
    fun rapidCompletionRequests_debounceAndSettleCleanlyWithoutLeakingJobs() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val editorEngine = DefaultEditorEngine(dispatchers = dispatchers)
        val workspaceManager = DefaultWorkspaceManager(dispatchers = dispatchers)
        val languageDetector = DefaultLanguageDetector()
        val lspCalls = AtomicInteger(0)
        val fakeLsp = createFakeLsp(lspCalls, "rapid")

        val root = "vibe://projects/concurrency-test"
        workspaceManager.createFile(root, "main.py", "initial")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            lspClientProvider = { fakeLsp },
            dispatchers = dispatchers
        )

        val file = VirtualFile("$root/main.py", "main.py", FileNodeType.FILE, parentUri = root)
        viewModel.openFile(file)

        // Rapid keystroke simulation: 5 rapid completion requests spaced by 20ms (below 120ms debounce)
        viewModel.updateCompletions("a")
        advanceTimeBy(20)
        viewModel.updateCompletions("ab")
        advanceTimeBy(20)
        viewModel.updateCompletions("abc")
        advanceTimeBy(20)
        viewModel.updateCompletions("abcd")
        advanceTimeBy(20)
        viewModel.updateCompletions("abcde")

        // Prior to debounce settling, LSP completion should not have executed
        assertEquals("LSP should not have executed before debounce threshold", 0, lspCalls.get())

        // Advance until all pending scheduled debounce jobs settle
        advanceUntilIdle()

        // Only the final debounced request should have queried the LSP client
        assertEquals("Only one LSP call should execute after debouncing", 1, lspCalls.get())
        assertTrue(
            "State should contain final completion item",
            viewModel.uiState.value.completions.any { it.label.startsWith("rapid_result_") }
        )
    }

    @Test
    fun autoSaveDebounceAndManualSave_terminateDeterministically() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val editorEngine = DefaultEditorEngine(dispatchers = dispatchers)
        val workspaceManager = DefaultWorkspaceManager(dispatchers = dispatchers)
        val languageDetector = DefaultLanguageDetector()

        val root = "vibe://projects/autosave-concurrency"
        workspaceManager.createFile(root, "app.py", "content_v1")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            dispatchers = dispatchers
        )

        val file = VirtualFile("$root/app.py", "app.py", FileNodeType.FILE, parentUri = root)
        viewModel.openFile(file)

        // Modify content - autoSave is true by default, so an auto-save job is scheduled with 700ms debounce
        viewModel.onContentChanged("content_v2")
        assertTrue(viewModel.uiState.value.activeDocument?.isDirty == true)

        // User triggers manual save before the 700ms debounce completes
        var manualSaveCompleted = false
        viewModel.saveActiveDocument { success ->
            manualSaveCompleted = success
        }

        // Advance test scheduler
        advanceUntilIdle()

        assertTrue("Manual save callback should report success", manualSaveCompleted)
        assertFalse("Document should not be dirty after save", viewModel.uiState.value.activeDocument?.isDirty == true)
        assertEquals("content_v2", workspaceManager.readFile("$root/app.py").getOrNull())
    }

    @Test
    fun standardTestDispatcher_advancesAndSettlesAllCoroutinesDeterministically() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val editorEngine = DefaultEditorEngine(dispatchers = dispatchers)
        val workspaceManager = DefaultWorkspaceManager(dispatchers = dispatchers)
        val languageDetector = DefaultLanguageDetector()
        val lspCalls = AtomicInteger(0)
        val fakeLsp = createFakeLsp(lspCalls, "standard")

        val root = "vibe://projects/standard-dispatcher"
        workspaceManager.createFile(root, "script.py", "import os")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            lspClientProvider = { fakeLsp },
            dispatchers = dispatchers
        )

        val file = VirtualFile("$root/script.py", "script.py", FileNodeType.FILE, parentUri = root)
        viewModel.openFile(file)
        // With StandardTestDispatcher, openFile is queued on the dispatcher; advance scheduler so openFile completes
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeDocument)

        viewModel.updateCompletions("os")
        // Advance scheduler so debounce (120ms) and LSP completion settle
        advanceUntilIdle()

        assertEquals(1, lspCalls.get())
        assertTrue(viewModel.uiState.value.completions.any { it.label == "standard_result_1" })
    }

    @Test
    fun projectSwitchWhileLspActive_cancelsAndCleansUpGracefully() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val editorEngine = DefaultEditorEngine(dispatchers = dispatchers)
        val workspaceManager = DefaultWorkspaceManager(dispatchers = dispatchers)
        val languageDetector = DefaultLanguageDetector()
        val lspCalls = AtomicInteger(0)
        val fakeLsp = createFakeLsp(lspCalls, "switch")

        val project1 = Project("p1", "Project 1", "vibe://projects/p1")
        workspaceManager.openProject(project1)
        workspaceManager.createFile("vibe://projects/p1", "main.py", "print('p1')")

        val viewModel = EditorViewModel(
            editorEngine = editorEngine,
            workspaceManager = workspaceManager,
            languageDetector = languageDetector,
            lspClientProvider = { fakeLsp },
            dispatchers = dispatchers
        )

        val file1 = VirtualFile("vibe://projects/p1/main.py", "main.py", FileNodeType.FILE, parentUri = "vibe://projects/p1")
        viewModel.openFile(file1)
        viewModel.updateCompletions("pri")

        // Switch workspace project immediately while completion debounce is pending
        val project2 = Project("p2", "Project 2", "vibe://projects/p2")
        workspaceManager.createFile("vibe://projects/p2", "main.py", "print('p2')")
        workspaceManager.openProject(project2)

        advanceUntilIdle()

        // Verify no uncaught exceptions and no dangling documents from p1
        assertTrue(viewModel.uiState.value.openDocuments.all { it.uri.startsWith("vibe://projects/p2") })
    }

    private fun <T> VibeResult<T>.getOrNull(): T? = (this as? VibeResult.Success)?.data
}
