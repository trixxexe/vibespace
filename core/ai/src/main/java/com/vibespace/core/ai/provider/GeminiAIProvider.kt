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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Real Google Gemini AI Provider using the current Gemini generateContent API.
 * Users bring their own API key (BYOK) obtained from Google AI Studio (aistudio.google.com).
 * Transports API keys securely via x-goog-api-key header and preserves conversational context.
 */
class GeminiAIProvider(
    var apiKey: String = "",
    var activeModel: String = "gemini-2.5-flash",
    private val httpTransport: ((url: String, method: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    internal val streamEndpointOverride: String? = null,
    private val streamTransport: ((url: String, headers: Map<String, String>, body: String) -> Pair<Int, Flow<String>>)? = null
) : AIProvider {

    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val supportedModels: List<String> = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.0-flash",
        "gemini-1.5-pro",
        "gemini-1.5-flash"
    )
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    private fun resolveEffectiveModel(): String {
        val trimmed = activeModel.trim()
        if (httpTransport == null && streamTransport == null && streamEndpointOverride == null && trimmed.startsWith("gemini-3.")) {
            return "gemini-2.5-flash"
        }
        return trimmed
    }

    override suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse> {
        if (!isConfigured) {
            return VibeResult.Error("No Gemini API key configured. Please enter your Google Gemini API key in Settings or AI Chat.")
        }

        val effectiveModel = resolveEffectiveModel()
        if (!effectiveModel.matches(Regex("[A-Za-z0-9._-]+"))) {
            return VibeResult.Error("Invalid Gemini model identifier.")
        }

        return try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent"
            val requestBody = buildRequestBody(request)

            val headers = mapOf(
                "Content-Type" to "application/json",
                "x-goog-api-key" to apiKey.trim()
            )

            val (statusCode, responseText) = withContext(dispatchers.io) {
                executeHttp(endpoint, "POST", headers, requestBody)
            }

            if (statusCode in 200..299) {
                val parsedText = extractCandidateText(responseText)
                    ?: return VibeResult.Error("Gemini returned HTTP $statusCode without a usable assistant response.")
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
            VibeResult.Error("Failed to connect to Gemini API: ${e.message}", e)
        }
    }

    override fun streamResponse(request: AIRequest): Flow<AIResponseChunk> = flow {
        if (!isConfigured) throw IOException("No Gemini API key configured.")
        val effectiveModel = resolveEffectiveModel()
        if (!effectiveModel.matches(Regex("[A-Za-z0-9._-]+"))) throw IOException("Invalid Gemini model identifier.")
        val endpoint = streamEndpointOverride ?: "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:streamGenerateContent?alt=sse"
        val endpointUri = runCatching { java.net.URI(endpoint) }.getOrNull()
        val host = endpointUri?.host?.lowercase()
        val localHttp = endpointUri?.scheme.equals("http", ignoreCase = true) &&
            (host == "localhost" || host == "127.0.0.1" || host == "::1")
        if (endpointUri?.host.isNullOrBlank() || endpointUri?.userInfo != null ||
            (!endpointUri?.scheme.equals("https", ignoreCase = true) && !localHttp)
        ) throw IOException("Invalid Gemini stream endpoint.")
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-goog-api-key" to apiKey.trim()
        )
        val requestBody = buildRequestBody(request)
        streamTransport?.let { injectedTransport ->
            val (status, lines) = injectedTransport(endpoint, headers, requestBody)
            if (status !in 200..299) {
                throw IOException(extractErrorMessage(status, lines.toList().joinToString("\n")))
            }
            consumeSseLines(lines) { chunk -> emit(chunk) }
            return@flow
        }
        if (httpTransport != null) {
            // The injected request/response transport is intended for deterministic tests only;
            // the real app connection below consumes Gemini's event stream incrementally.
            when (val result = generateResponse(request)) {
                is VibeResult.Success -> {
                    emit(AIResponseChunk(delta = result.data.message.content, isComplete = true))
                }
                is VibeResult.Error -> throw IOException(result.message, result.throwable)
                else -> throw IOException("Gemini returned no response.")
            }
            return@flow
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = readLimited(connection.errorStream)
                throw IOException(extractErrorMessage(status, message))
            }
            val lines = flow {
                val coroutineContext = kotlinx.coroutines.currentCoroutineContext()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = try {
                            reader.readLine()
                        } catch (_: SocketTimeoutException) {
                            coroutineContext.ensureActive()
                            continue
                        } ?: break
                        coroutineContext.ensureActive()
                        emit(line)
                    }
                }
            }
            consumeSseLines(lines) { chunk -> emit(chunk) }
        } finally {
            connection.disconnect()
        }
    }.flowOn(dispatchers.io)

    private suspend fun consumeSseLines(lines: Flow<String>, emitChunk: suspend (AIResponseChunk) -> Unit) {
        var streamedBytes = 0
        var receivedContent = false
        var receivedTerminalEvent = false
        lines.collect { line ->
            if (!line.startsWith("data:")) return@collect
            val data = line.removePrefix("data:").trim()
            streamedBytes += data.length
            if (streamedBytes > MAX_RESPONSE_BYTES) throw IOException("Gemini stream exceeded the 4 MB limit.")
            if (data.isEmpty()) return@collect
            val payload = runCatching { JSONObject(data) }.getOrNull() ?: return@collect
            val candidate = payload.optJSONArray("candidates")?.optJSONObject(0)
            if (!candidate?.optString("finishReason").isNullOrBlank()) receivedTerminalEvent = true
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val delta = parts?.let { values ->
                (0 until values.length()).mapNotNull { values.optJSONObject(it)?.optString("text") }
                    .joinToString("")
            }.orEmpty()
            if (delta.isNotEmpty()) {
                receivedContent = true
                emitChunk(AIResponseChunk(delta = delta))
            }
        }
        if (!receivedContent || !receivedTerminalEvent) {
            throw IOException("Gemini stream ended before a complete response was received.")
        }
        emitChunk(AIResponseChunk(delta = "", isComplete = true))
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
            if (code in 300..399) throw IOException("Gemini redirected the request. Use its final HTTPS endpoint directly.")
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
                if (total > MAX_RESPONSE_BYTES) throw IOException("Gemini response exceeded the 4 MB limit.")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
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
            JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", fallbackPrompt)))))
                .toString()
        }
    }

    private fun extractCandidateText(jsonResponse: String): String? {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val content = first.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = (0 until parts.length())
                        .mapNotNull { index -> parts.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() } }
                        .joinToString("")
                    if (text.isNotBlank()) return text
                }
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

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
