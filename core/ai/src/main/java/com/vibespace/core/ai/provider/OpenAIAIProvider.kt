package com.vibespace.core.ai.provider

import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRequest
import com.vibespace.core.ai.model.AIResponse
import com.vibespace.core.ai.model.AIResponseChunk
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Real OpenAI-compatible AI Provider supporting OpenAI (GPT-4o, GPT-4o-mini), OpenRouter, Groq,
 * Ollama, or any custom compatible proxy.
 * Validates endpoints before use and preserves conversational context.
 */
class OpenAIAIProvider(
    var apiKey: String = "",
    var activeModel: String = "gpt-4o-mini",
    var customEndpoint: String = "",
    private val httpTransport: ((url: String, method: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
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
        val endpointUri = runCatching { URI(endpoint) }.getOrNull()
        val host = endpointUri?.host?.lowercase()
        val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
        val secureEndpoint = endpointUri?.scheme.equals("https", ignoreCase = true)
        val localHttpEndpoint = endpointUri?.scheme.equals("http", ignoreCase = true) && localHost
        if (endpointUri == null || endpointUri.host.isNullOrBlank() || endpointUri.userInfo != null || (!secureEndpoint && !localHttpEndpoint)) {
            return VibeResult.Error("Invalid custom endpoint '$endpoint'. Endpoint must use HTTPS (or localhost for local proxies).")
        }

        return try {
            val requestBody = buildRequestBody(request)

            val headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${apiKey.trim()}"
            )

            val (statusCode, responseText) = withContext(dispatchers.io) {
                executeHttp(endpoint, "POST", headers, requestBody)
            }

            if (statusCode in 200..299) {
                val parsedText = extractChoiceContent(responseText)
                    ?: return VibeResult.Error("The AI endpoint returned HTTP $statusCode without a usable assistant response.")
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            VibeResult.Error("Failed to connect to AI endpoint: ${e.message}", e)
        }
    }

    override fun streamResponse(request: AIRequest): Flow<AIResponseChunk> = flow {
        if (!isConfigured) throw IOException("No OpenAI API key configured.")
        val endpoint = customEndpoint.trim().ifBlank { "https://api.openai.com/v1/chat/completions" }
        val endpointUri = runCatching { URI(endpoint) }.getOrNull()
        val host = endpointUri?.host?.lowercase()
        val localHttpEndpoint = endpointUri?.scheme.equals("http", ignoreCase = true) &&
            (host == "localhost" || host == "127.0.0.1" || host == "::1")
        if (endpointUri?.host.isNullOrBlank() || endpointUri?.userInfo != null ||
            (!endpointUri?.scheme.equals("https", ignoreCase = true) && !localHttpEndpoint)
        ) throw IOException("Invalid custom endpoint. Use HTTPS or a localhost HTTP proxy.")

        // Injected transports are request/response fakes, so they cannot expose a live byte stream.
        // Production connections below use the provider's actual SSE streaming endpoint.
        if (httpTransport != null) {
            when (val result = generateResponse(request)) {
                is VibeResult.Success -> {
                    emit(AIResponseChunk(delta = result.data.message.content, isComplete = true))
                }
                is VibeResult.Error -> throw IOException(result.message, result.throwable)
                else -> throw IOException("AI endpoint returned no response.")
            }
            return@flow
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }
        try {
            connection.outputStream.use { it.write(buildRequestBody(request, stream = true).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = readLimited(connection.errorStream)
                throw IOException(extractErrorMessage(status, message))
            }
            var receivedDone = false
            var streamedBytes = 0
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    streamedBytes += data.length
                    if (streamedBytes > MAX_RESPONSE_BYTES) throw IOException("AI stream exceeded the 4 MB limit.")
                    if (data == "[DONE]") {
                        receivedDone = true
                        emit(AIResponseChunk(delta = "", isComplete = true))
                        break
                    }
                    val delta = runCatching {
                        JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content").orEmpty()
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) emit(AIResponseChunk(delta = delta))
                }
            }
            if (!receivedDone) emit(AIResponseChunk(delta = "", isComplete = true))
        } finally {
            connection.disconnect()
        }
    }.flowOn(dispatchers.io)

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
        try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = method
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val text = readLimited(if (code in 200..299) conn.inputStream else conn.errorStream)
            if (code in 300..399) throw IOException("AI endpoint redirected the request. Use its final HTTPS URL directly.")
            return Pair(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream?): String {
        if (input == null) return ""
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_RESPONSE_BYTES) throw IOException("AI endpoint response exceeded the 4 MB limit.")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun buildRequestBody(request: AIRequest, stream: Boolean = false): String {
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
            root.put("stream", stream)
            root.toString()
        } catch (t: Throwable) {
            JSONObject().put("model", activeModel).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Code help"))).toString()
        }
    }

    private fun extractChoiceContent(jsonResponse: String): String? {
        return try {
            val root = JSONObject(jsonResponse)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.getJSONObject(0)
                val message = first.optJSONObject("message")
                val content = message?.optString("content")?.takeIf { it.isNotBlank() }
                if (content != null) return content
            }
            null
        } catch (_: Exception) {
            null
        }
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

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
