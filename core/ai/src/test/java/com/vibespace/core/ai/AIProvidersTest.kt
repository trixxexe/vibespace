package com.vibespace.core.ai

import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.ai.model.CodeContext
import com.vibespace.core.ai.provider.GeminiAIProvider
import com.vibespace.core.ai.provider.OpenAIAIProvider
import kotlinx.coroutines.test.runTest
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
    fun geminiProvider_with_fake_transport_parses_success_response() = runTest {
        val fakeJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": "Hello from Gemini 2.0 Flash!"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val provider = GeminiAIProvider(
            apiKey = "test-gemini-key",
            activeModel = "gemini-2.0-flash",
            httpTransport = { url, method, headers, body ->
                assertTrue(url.contains("gemini-2.0-flash"))
                assertTrue(url.contains("key=test-gemini-key"))
                assertEquals("POST", method)
                Pair(200, fakeJson)
            }
        )

        assertTrue(provider.isConfigured)
        val ctx = CodeContext("vibe://main.py", "main.py", "Python", fullContent = "print(1)")
        val request = AIRequest(
            messages = listOf(AIMessage("1", AIRole.USER, "Explain this code")),
            codeContext = ctx
        )

        val result = provider.generateResponse(request)
        assertTrue(result.isSuccess)
        assertEquals("Hello from Gemini 2.0 Flash!", result.getOrNull()!!.message.content)
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
                Pair(200, fakeJson)
            }
        )

        assertTrue(provider.isConfigured)
        val request = AIRequest(messages = listOf(AIMessage("1", AIRole.USER, "Hi")))
        val result = provider.generateResponse(request)

        assertTrue(result.isSuccess)
        assertEquals("Hello from OpenAI GPT-4o!", result.getOrNull()!!.message.content)
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
}
