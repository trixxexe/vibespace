package com.vibespace.core.language

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.language.lsp.InMemoryLspTransport
import com.vibespace.core.language.lsp.LspFramer
import com.vibespace.core.language.lsp.StdioLanguageServerClient
import com.vibespace.core.language.model.DiagnosticSeverity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StdioLanguageServerClientTest {

    private val dispatchers = TestDispatcherProvider()

    @Test
    fun lspFramer_framesAndDeframesPayloadCorrectly() {
        val payload = """{"jsonrpc":"2.0","method":"test"}"""
        val framedBytes = LspFramer.frame(payload)

        val framedString = String(framedBytes, Charsets.UTF_8)
        assertTrue(framedString.startsWith("Content-Length: 33\r\n\r\n"))
        assertTrue(framedString.endsWith(payload))

        val decoder = LspFramer.StreamDecoder()
        val decoded = decoder.feed(framedString)
        assertEquals(1, decoded.size)
        assertEquals(payload, decoded[0])
    }

    @Test
    fun lspClient_startInitializesSuccessfully() = runTest {
        val transport = InMemoryLspTransport()
        val client = StdioLanguageServerClient("python", transport, dispatchers)

        // Mock server side responder in background
        val job = launch {
            val request = transport.clientToServer.receive()
            assertTrue(request.contains("initialize"))
            // Send response with id 1
            transport.emitFromServer("""{"jsonrpc":"2.0","id":1,"result":{"capabilities":{}}}""")
            // Expect initialized notification
            val notification = transport.clientToServer.receive()
            assertTrue(notification.contains("initialized"))
        }

        val result = client.start("file:///workspace")
        assertTrue(result.isSuccess)
        assertTrue(client.isRunning)

        client.stop()
        assertFalse(client.isRunning)
        job.cancel()
    }

    @Test
    fun lspClient_publishesDiagnosticsNotification() = runTest {
        val transport = InMemoryLspTransport()
        val client = StdioLanguageServerClient("python", transport, dispatchers)

        val docUri = "file:///workspace/main.py"
        val diagJson = """
            {
                "jsonrpc": "2.0",
                "method": "textDocument/publishDiagnostics",
                "params": {
                    "uri": "$docUri",
                    "diagnostics": [
                        {
                            "message": "Undefined variable 'foo'",
                            "severity": 1,
                            "range": {
                                "start": {"line": 10, "character": 4},
                                "end": {"line": 10, "character": 7}
                            }
                        }
                    ]
                }
            }
        """.trimIndent()

        transport.emitFromServer(diagJson)

        val diagnostics = client.observeDiagnostics(docUri).first { it.isNotEmpty() }
        assertEquals(1, diagnostics.size)
        assertEquals("Undefined variable 'foo'", diagnostics[0].message)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
        assertEquals(10, diagnostics[0].range.start.line)
        assertEquals(4, diagnostics[0].range.start.column)
    }

    @Test
    fun lspClient_requestHover_returnsDocumentation() = runTest {
        val transport = InMemoryLspTransport()
        val client = StdioLanguageServerClient("python", transport, dispatchers)

        launch {
            val req = transport.clientToServer.receive()
            assertTrue(req.contains("textDocument/hover"))
            transport.emitFromServer("""{"jsonrpc":"2.0","id":1,"result":{"contents":"def print(*values: object) -> None"}}""")
        }

        val hover = client.requestHover("file:///workspace/main.py", 5, 2)
        assertTrue(hover.isSuccess)
        val info = hover.getOrNull()
        assertNotNull(info)
        assertEquals("def print(*values: object) -> None", info?.contents)
    }

    @Test
    fun lspClient_requestCompletion_returnsItems() = runTest {
        val transport = InMemoryLspTransport()
        val client = StdioLanguageServerClient("python", transport, dispatchers)

        launch {
            val req = transport.clientToServer.receive()
            assertTrue(req.contains("textDocument/completion"))
            transport.emitFromServer("""{"jsonrpc":"2.0","id":1,"result":[{"label":"append"},{"label":"extend"}]}""")
        }

        val completions = client.requestCompletion("file:///workspace/main.py", 3, 5)
        assertTrue(completions.isSuccess)
        val items = completions.getOrNull()!!
        assertEquals(2, items.size)
        assertEquals("append", items[0].label)
        assertEquals("extend", items[1].label)
    }
}
