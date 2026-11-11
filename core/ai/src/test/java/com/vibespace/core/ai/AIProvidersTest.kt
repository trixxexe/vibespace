package com.vibespace.core.ai

import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.provider.GeminiAIProvider
import com.vibespace.core.ai.provider.OpenAIAIProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AIProvidersTest {

    @Test
    fun geminiProvider_without_key_is_not_configured() = runTest {
        val provider = GeminiAIProvider(apiKey = "")
        assertFalse(provider.isConfigured)

        val request = AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "hello")))
        val result = provider.generateResponse(request)
        assertTrue(result.isError)
    }

    @Test
    fun geminiProvider_with_fake_transport_parses_success_response_and_uses_header_transport() = runTest {
        val fakeJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": "Hello from Gemini 3.8 Flash!"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val provider = GeminiAIProvider(
            apiKey = "test-gemini-key",
            activeModel = "gemini-3.8-flash",
            httpTransport = { url, method, headers, body ->
                assertTrue(url.contains("gemini-3.8-flash"))
                // API key is securely transported via header rather than query string
                assertFalse(url.contains("key="))
                assertEquals("test-gemini-key", headers["x-goog-api-key"])
                assertEquals("POST", method)
                assertTrue(body.contains("Explain this code"))
                assertTrue(body.contains("Active File: main.py"))
                Pair(200, fakeJson)
            }
        )

        assertTrue(provider.isConfigured)
        val ctx = CodeContext("vibe://main.py", "main.py", "Python", fullContent = "print(1)")
        val request = AIRequest(
            messages = listOf(
                AIMessage("0", AIRole.USER, "Prior turn"),
                AIMessage("1", AIRole.ASSISTANT, "Prior reply"),
                AIMessage("2", AIRole.USER, "Explain this code")
            ),
            codeContext = ctx
        )

        val result = provider.generateResponse(request)
        assertTrue(result.isSuccess)
        assertEquals("Hello from Gemini 3.8 Flash!", result.getOrNull()!!.message.content)
    }

    @Test
    fun openaiProvider_with_fake_transport_parses_choice_content() = runTest {
        val fakeJson = """
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "Hello from OpenAI GPT-4o!"
                        }
                    }
                ]
            }
        """.trimIndent()

        val provider = OpenAIAIProvider(
            apiKey = "sk-test-key",
            activeModel = "gpt-4o",
            httpTransport = { url, method, headers, body ->
                assertEquals("Bearer sk-test-key", headers["Authorization"])
                assertTrue(body.contains("Prior message"))
                assertTrue(body.contains("Hi"))
                Pair(200, fakeJson)
            }
        )

        assertTrue(provider.isConfigured)
        val request = AIRequest(
            messages = listOf(
                AIMessage("0", AIRole.USER, "Prior message"),
                AIMessage("1", AIRole.ASSISTANT, "Prior assistant response"),
                AIMessage("2", AIRole.USER, "Hi")
            )
        )
        val result = provider.generateResponse(request)

        assertTrue(result.isSuccess)
        assertEquals("Hello from OpenAI GPT-4o!", result.getOrNull()!!.message.content)
    }

    @Test
    fun openaiProvider_rejects_insecure_custom_endpoint() = runTest {
        val provider = OpenAIAIProvider(
            apiKey = "test-key",
            customEndpoint = "http://remote-server.com/api"
        )
        val request = AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))
        val result = provider.generateResponse(request)
        assertTrue(result.isError)
        assertTrue((result as com.vibespace.core.common.result.VibeResult.Error).message.contains("HTTPS"))
    }

    @Test
    fun openaiProvider_handles_http_errors_gracefully() = runTest {
        val errorJson = """
            {
                "error": {
                    "message": "Incorrect API key provided"
                }
            }
        """.trimIndent()

        val provider = OpenAIAIProvider(
            apiKey = "bad-key",
            httpTransport = { _, _, _, _ ->
                Pair(401, errorJson)
            }
        )

        val request = AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))
        val result = provider.generateResponse(request)

        assertTrue(result.isError)
        val errorResult = result as com.vibespace.core.common.result.VibeResult.Error
        assertTrue(errorResult.message.contains("Incorrect API key provided"))
    }

    @Test
    fun openaiProvider_streamsSseDeltasAsTheyArrive() = runTest {
        val server = createTestServer()
        server.createContext("/v1/chat/completions") { exchange ->
            val request = exchange.requestBody.bufferedReader().readText()
            assertTrue(request.contains("\"stream\":true"))
            val body = listOf(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n",
                "data: [DONE]\n\n"
            ).joinToString("").toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val provider = OpenAIAIProvider(
                apiKey = "test-key",
                customEndpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"
            )
            val chunks = provider.streamResponse(
                AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))
            ).toList()

            assertEquals(listOf("Hello", " world"), chunks.filterNot { it.isComplete }.map { it.delta })
            assertTrue(chunks.last().isComplete)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun openaiProvider_rejectsStreamThatEndsWithoutCompletionMarker() = runTest {
        val server = createTestServer()
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".toByteArray()) }
        }
        server.start()
        try {
            val provider = OpenAIAIProvider("test-key", customEndpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions")
            try {
                provider.streamResponse(AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))).toList()
                fail("A truncated SSE response must not report completion")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("completion marker"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun geminiProvider_streamsSseDeltasAndRequiresTerminalEvent() = runTest {
        val server = createTestServer()
        server.createContext("/stream") { exchange ->
            val events = listOf(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]}}]}\n\n",
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" Gemini\"}]},\"finishReason\":\"STOP\"}]}\n\n"
            ).joinToString("").toByteArray()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(events) }
        }
        server.start()
        try {
            val provider = GeminiAIProvider(
                apiKey = "test-key",
                streamEndpointOverride = "http://127.0.0.1:${server.address.port}/stream"
            )
            val chunks = provider.streamResponse(
                AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))
            ).toList()

            assertEquals(listOf("Hello", " Gemini"), chunks.filterNot { it.isComplete }.map { it.delta })
            assertTrue(chunks.last().isComplete)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun geminiProvider_reportsHttpStreamErrors() = runTest {
        val server = createTestServer()
        server.createContext("/stream") { exchange ->
            val body = "{\"error\":{\"message\":\"quota exhausted\"}}".toByteArray()
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val provider = GeminiAIProvider(
                apiKey = "test-key",
                streamEndpointOverride = "http://127.0.0.1:${server.address.port}/stream"
            )
            try {
                provider.streamResponse(AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))).toList()
                fail("HTTP errors must be surfaced")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("quota exhausted"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun geminiProvider_rejectsStreamMissingTheTerminalFinishReason() = runTest {
        val server = createTestServer()
        server.createContext("/stream") { exchange ->
            val body = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"partial\"}]}}]}\n\n".toByteArray()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val provider = GeminiAIProvider(
                apiKey = "test-key",
                streamEndpointOverride = "http://127.0.0.1:${server.address.port}/stream"
            )
            try {
                provider.streamResponse(AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))).toList()
                fail("A Gemini stream without finishReason must not report completion")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("complete response"))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun geminiProvider_cancelsTheEventStreamWhenTheCollectorStops() = runTest {
        val server = createTestServer()
        server.createContext("/stream") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            try {
                exchange.responseBody.use { output ->
                    output.write("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"first\"}]}}]}\n\n".toByteArray())
                    output.flush()
                    while (true) {
                        Thread.sleep(50)
                        output.write(": keep-alive\n\n".toByteArray())
                        output.flush()
                    }
                }
            } catch (_: Exception) {
                // The collector cancellation closes the HTTP connection.
            }
        }
        server.start()
        try {
            val provider = GeminiAIProvider(
                apiKey = "test-key",
                streamEndpointOverride = "http://127.0.0.1:${server.address.port}/stream"
            )
            val firstChunk = provider.streamResponse(
                AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))
            ).take(1).toList().single()

            assertEquals("first", firstChunk.delta)
        } finally {
            server.stop(0)
        }
    }

    private fun createTestServer(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = java.util.concurrent.Executors.newCachedThreadPool { task ->
            Thread(task, "vibespace-ai-test-http").apply { isDaemon = true }
        }
    }
}
