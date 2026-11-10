package com.vibespace.core.ai.provider

import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIResponse
import com.vibespace.core.ai.model.AIResponseChunk
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real OpenAI-compatible AI Provider supporting OpenAI (GPT-4o, GPT-4o-mini), OpenRouter, Groq,
 * Ollama, or any custom compatible proxy.
 * Validates endpoints before use and preserves conversational context.
 */
class OpenAIAIProvider(
    var apiKey: String = "",
    var activeModel: String = "gpt-4o-mini",
    var customEndpoint: String = "",
    private val httpTransport: ((url: String, method: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null
) : AIProvider {

    override val providerId: String = "openai"
    override val displayName: String = "OpenAI / Compatible"
    override val supportedModels: List<String> = listOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet", "deepseek-coder", "custom")
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse> {
        if (!isConfigured) {
            return VibeResult.Error("No OpenAI API key configured. Please enter your API key in Settings or AI Chat.")
        }

        val endpoint = customEndpoint.trim().ifBlank { "https://api.openai.com/v1/chat/completions" }
        if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://localhost") && !endpoint.startsWith("http://127.0.0.1")) {
            return VibeResult.Error("Invalid custom endpoint '$endpoint'. Endpoint must use HTTPS (or localhost for local proxies).")
        }

        return try {
            val requestBody = buildRequestBody(request)

            val headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${apiKey.trim()}"
            )

            val (statusCode, responseText) = executeHttp(endpoint, "POST", headers, requestBody)

            if (statusCode in 200..299) {
                val parsedText = extractChoiceContent(responseText)
                val responseMsg = AIMessage(
                    id = IdGenerator.shortId("msg"),
                    role = AIRole.ASSISTANT,
                    content = parsedText
                )
                VibeResult.Success(AIResponse(message = responseMsg))
            } else {
                val errorMsg = extractErrorMessage(statusCode, responseText)
                VibeResult.Error(errorMsg)
            }
        } catch (e: Throwable) {
            VibeResult.Error("Failed to connect to AI endpoint: ${e.message}", e)
        }
    }

    override fun streamResponse(request: AIRequest): Flow<AIResponseChunk> = flow {
        when (val result = generateResponse(request)) {
            is VibeResult.Success -> {
                emit(AIResponseChunk(delta = result.data.message.content, isComplete = true))
            }
            is VibeResult.Error -> {
                emit(AIResponseChunk(delta = "Error: ${result.message}", isComplete = true))
            }
            else -> Unit
        }
    }

    private fun executeHttp(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        body: String
    ): Pair<Int, String> {
        val custom = httpTransport
        if (custom != null) {
            return custom(urlString, method, headers, body)
        }

        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true

        conn.outputStream.use { os ->
            os.write(body.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val text = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        return Pair(code, text)
    }

    private fun buildRequestBody(request: AIRequest): String {
        return try {
            val root = JSONObject()
            root.put("model", activeModel)
            val messagesArray = JSONArray()

            val systemObj = JSONObject()
            systemObj.put("role", "system")
            systemObj.put("content", "You are VibeSpace AI, an expert mobile coding companion. Provide clear, concise, production-ready code. When suggesting terminal commands, wrap them in ```bash ... ``` code blocks.")
            messagesArray.put(systemObj)

            val history = request.messages.takeLast(15)
            val lastUserMsg = history.lastOrNull { it.role == AIRole.USER }

            for (msg in history) {
                val role = when (msg.role) {
                    AIRole.SYSTEM -> "system"
                    AIRole.ASSISTANT -> "assistant"
                    AIRole.USER -> "user"
                }
                val msgObj = JSONObject()
                msgObj.put("role", role)

                val contentBuilder = StringBuilder()
                if (msg == lastUserMsg && request.codeContext != null) {
                    val ctx = request.codeContext
                    contentBuilder.append("Active File: ${ctx.fileName} (${ctx.language})\n")
                    if (ctx.fullContent != null) {
                        contentBuilder.append("```${ctx.language.lowercase()}\n")
                        contentBuilder.append(ctx.fullContent)
                        contentBuilder.append("\n```\n\n")
                    }
                }
                contentBuilder.append(msg.content)
                msgObj.put("content", contentBuilder.toString())
                messagesArray.put(msgObj)
            }

            if (messagesArray.length() <= 1) {
                val userObj = JSONObject()
                userObj.put("role", "user")
                userObj.put("content", "Hello VibeSpace AI")
                messagesArray.put(userObj)
            }

            root.put("messages", messagesArray)
            root.toString()
        } catch (t: Throwable) {
            """{"model":"$activeModel","messages":[{"role":"user","content":"Code help"}]}"""
        }
    }

    private fun extractChoiceContent(jsonResponse: String): String {
        return try {
            val root = JSONObject(jsonResponse)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.getJSONObject(0)
                val message = first.optJSONObject("message")
                val content = message?.optString("content")
                if (!content.isNullOrBlank()) {
                    return content
                }
            }
            fallbackExtractContent(jsonResponse)
        } catch (t: Throwable) {
            fallbackExtractContent(jsonResponse)
        }
    }

    private fun fallbackExtractContent(json: String): String {
        val match = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)
        return match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: "No response from AI."
    }

    private fun extractErrorMessage(statusCode: Int, responseText: String): String {
        return try {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            val message = error?.optString("message")
            if (!message.isNullOrBlank()) {
                "OpenAI Error ($statusCode): $message"
            } else {
                "AI endpoint returned HTTP $statusCode"
            }
        } catch (_: Throwable) {
            if (statusCode == 401) "Invalid API key or unauthorized."
            else if (statusCode == 429) "Rate limit reached or quota exceeded."
            else "AI endpoint request failed with HTTP $statusCode"
        }
    }
}
