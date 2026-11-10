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
 * Real Google Gemini AI Provider supporting Gemini 2.0 Flash, Gemini 1.5 Flash, and Gemini 1.5 Pro.
 * Users bring their own API key (BYOK) obtained from Google AI Studio (aistudio.google.com).
 * Transports API keys securely via x-goog-api-key header and preserves conversational context.
 */
class GeminiAIProvider(
    var apiKey: String = "",
    var activeModel: String = "gemini-2.0-flash",
    private val httpTransport: ((url: String, method: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null
) : AIProvider {

    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val supportedModels: List<String> = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse> {
        if (!isConfigured) {
            return VibeResult.Error("No Gemini API key configured. Please enter your Google Gemini API key in Settings or AI Chat.")
        }

        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$activeModel:generateContent"
            val requestBody = buildRequestBody(request)

            val headers = mapOf(
                "Content-Type" to "application/json",
                "x-goog-api-key" to apiKey.trim()
            )

            val (statusCode, responseText) = executeHttp(endpoint, "POST", headers, requestBody)

            if (statusCode in 200..299) {
                val parsedText = extractCandidateText(responseText)
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
            VibeResult.Error("Failed to connect to Gemini API: ${e.message}", e)
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
        val systemInstructionText = "You are VibeSpace AI, an expert mobile coding companion. Provide clear, concise, production-ready code. When suggesting terminal commands, wrap them in ```bash ... ``` code blocks."

        return try {
            val root = JSONObject()
            val contents = JSONArray()

            val history = request.messages.takeLast(15)
            val lastUserMsg = history.lastOrNull { it.role == AIRole.USER }

            for (msg in history) {
                val role = if (msg.role == AIRole.ASSISTANT) "model" else "user"
                val msgObj = JSONObject()
                msgObj.put("role", role)

                val parts = JSONArray()
                val part = JSONObject()

                val contentBuilder = StringBuilder()
                // If this is the active user message, attach active code context
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

                part.put("text", contentBuilder.toString())
                parts.put(part)
                msgObj.put("parts", parts)
                contents.put(msgObj)
            }

            if (contents.length() == 0) {
                val msgObj = JSONObject()
                msgObj.put("role", "user")
                val parts = JSONArray()
                val part = JSONObject().apply { put("text", "Hello VibeSpace AI") }
                parts.put(part)
                msgObj.put("parts", parts)
                contents.put(msgObj)
            }

            root.put("contents", contents)

            val sysInstruction = JSONObject()
            val sysParts = JSONArray()
            val sysPart = JSONObject().apply { put("text", systemInstructionText) }
            sysParts.put(sysPart)
            sysInstruction.put("parts", sysParts)
            root.put("systemInstruction", sysInstruction)

            root.toString()
        } catch (t: Throwable) {
            val fallbackPrompt = request.messages.lastOrNull()?.content ?: "Hello"
            val escapedPrompt = fallbackPrompt.replace("\"", "\\\"").replace("\n", "\\n")
            """{"contents":[{"role":"user","parts":[{"text":"$escapedPrompt"}]}]}"""
        }
    }

    private fun extractCandidateText(jsonResponse: String): String {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val content = first.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "No response generated.")
                }
            }
            fallbackExtractText(jsonResponse)
        } catch (t: Throwable) {
            fallbackExtractText(jsonResponse)
        }
    }

    private fun fallbackExtractText(json: String): String {
        val match = Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)
        return match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: "No response from Gemini."
    }

    private fun extractErrorMessage(statusCode: Int, responseText: String): String {
        return try {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            val message = error?.optString("message")
            if (!message.isNullOrBlank()) {
                "Gemini API Error ($statusCode): $message"
            } else {
                "Gemini API returned HTTP $statusCode"
            }
        } catch (_: Throwable) {
            if (statusCode == 400) "Invalid request or API key."
            else if (statusCode == 403) "Gemini API key is invalid or quota exceeded."
            else "Gemini API request failed with HTTP $statusCode"
        }
    }
}
