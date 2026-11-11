package com.vibespace.core.ai

import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.provider.GeminiAIProvider
import com.vibespace.core.ai.provider.OpenAIAIProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
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
}
