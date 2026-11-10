package com.vibespace.core.language

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.language.lsp.InMemoryLspTransport
import com.vibespace.core.language.lsp.LocalLspServerResponder
import com.vibespace.core.language.lsp.StdioLanguageServerClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalLspServerResponderTest {

    private val dispatchers = TestDispatcherProvider()
    private lateinit var transport: InMemoryLspTransport
    private lateinit var responder: LocalLspServerResponder
    private lateinit var client: StdioLanguageServerClient

    @Before
    fun setUp() {
        transport = InMemoryLspTransport()
        responder = LocalLspServerResponder("python", transport, dispatchers)
        responder.start()
        client = StdioLanguageServerClient("python", transport, dispatchers)
    }

    @After
    fun tearDown() {
        responder.stop()
        transport.close()
    }

    @Test
    fun client_start_successfully_initializes_with_responder() = runTest {
        val result = client.start("file:///workspace")
        assertTrue(result.isSuccess)
        assertTrue(client.isRunning)
    }

    @Test
    fun client_didOpen_triggers_diagnostics_publishing() = runTest {
        client.start("file:///workspace")

        val docUri = "file:///workspace/main.py"
        client.didOpen(docUri, "def broken(\n")

        val diagnostics = client.observeDiagnostics(docUri).first { it.isNotEmpty() }
        assertTrue(diagnostics.isNotEmpty())
    }

    @Test
    fun client_requestHover_returns_rich_python_documentation() = runTest {
        client.start("file:///workspace")

        val docUri = "file:///workspace/main.py"
        client.didOpen(docUri, "print('hello')\n")

        val hoverResult = client.requestHover(docUri, 1, 2)
        assertTrue(hoverResult.isSuccess)
        val hover = hoverResult.getOrNull()
        assertNotNull(hover)
        assertTrue(hover!!.contents.contains("print"))
        assertTrue(hover.contents.contains("sys.stdout"))
    }

    @Test
    fun client_requestDefinition_finds_function_definition_line() = runTest {
        client.start("file:///workspace")

        val docUri = "file:///workspace/app.py"
        val code = """
            # Header
            import os

            def calculate_total(items):
                return sum(items)

            result = calculate_total([1, 2, 3])
        """.trimIndent()
        client.didOpen(docUri, code)

        val defResult = client.requestDefinition(docUri, 7, 12)
        assertTrue(defResult.isSuccess)
        val locations = defResult.getOrNull()
        assertNotNull(locations)
        assertTrue(locations!!.isNotEmpty())
        assertEquals(4, locations[0].line)
        assertEquals(docUri, locations[0].uri)
    }

    @Test
    fun client_formatDocument_cleans_trailing_whitespace_and_normalizes_newlines() = runTest {
        client.start("file:///workspace")

        val docUri = "file:///workspace/script.py"
        val unformatted = "x = 1   \ny = 2 \n\n\n"
        client.didOpen(docUri, unformatted)

        val formatResult = client.formatDocument(docUri)
        assertTrue(formatResult.isSuccess)
        val formatted = formatResult.getOrNull()
        assertNotNull(formatted)
        assertEquals("x = 1\ny = 2\n", formatted)
    }

    @Test
    fun client_requestCompletion_returns_python_keywords_and_symbols() = runTest {
        client.start("file:///workspace")

        val docUri = "file:///workspace/module.py"
        client.didOpen(docUri, "my_custom_var = 42\nmy_")

        val completionResult = client.requestCompletion(docUri, 2, 3)
        assertTrue(completionResult.isSuccess)
        val items = completionResult.getOrNull()
        assertNotNull(items)
        assertTrue(items!!.any { it.label == "my_custom_var" })
    }
}
