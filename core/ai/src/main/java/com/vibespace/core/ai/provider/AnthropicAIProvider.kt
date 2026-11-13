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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

/**
 * Native Anthropic Claude Messages API provider (`/v1/messages`).
 * Supports Claude 3.7 Sonnet, Claude 3.5 Sonnet, Claude 3.5 Haiku, and Claude 3 Opus,
 * and transparently delegates to OpenAI-compatible format when routed via OpenRouter.
 */
class AnthropicAIProvider(
    var apiKey: String = "",
    var activeModel: String = "claude-3-7-sonnet-latest",
    var customEndpoint: String = "https://api.anthropic.com/v1/messages",
    override val providerId: String = "anthropic",
    override val displayName: String = "Anthropic (Claude)",
    override val supportedModels: List<String> = listOf(
        "claude-3-7-sonnet-latest",
        "claude-3-5-sonnet-latest",
        "claude-3-5-haiku-latest",
        "claude-3-opus-latest"
    ),
    private val httpTransport: ((url: String, method: String, headers: Map<String, String>, body: String) -> Pair<Int, String>)? = null,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val streamTransport: ((url: String, headers: Map<String, String>, body: String) -> Pair<Int, Flow<String>>)? = null
) : AIProvider {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    private fun resolveModelName(model: String): String {
        return when (model.trim()) {
            "claude-3-7-sonnet" -> "claude-3-7-sonnet-latest"
            "claude-3-5-sonnet" -> "claude-3-5-sonnet-latest"
            "claude-3-5-haiku" -> "claude-3-5-haiku-latest"
            "" -> "claude-3-7-sonnet-latest"
            else -> model.trim()
        }
    }

    private fun isOpenRouterEndpoint(endpoint: String): Boolean {
        return endpoint.contains("openrouter.ai", ignoreCase = true) ||
            endpoint.endsWith("/chat/completions", ignoreCase = true)
    }

    override suspend fun generateResponse(request: AIRequest): VibeResult<AIResponse> {
        if (!isConfigured) {
            return VibeResult.Error("No Anthropic API key configured. Please enter your Claude API key in Settings or AI Chat.")
        }

        val endpoint = customEndpoint.trim().ifBlank { "https://api.anthropic.com/v1/messages" }
        if (isOpenRouterEndpoint(endpoint)) {
            val delegate = OpenAIAIProvider(
                apiKey = apiKey,
                activeModel = activeModel,
                customEndpoint = endpoint,
                providerId = providerId,
                displayName = displayName,
                supportedModels = supportedModels,
                httpTransport = httpTransport,
                dispatchers = dispatchers,
                streamTransport = streamTransport
            )
            return delegate.generateResponse(request)
        }

        val endpointUri = runCatching { URI(endpoint) }.getOrNull()
        val host = endpointUri?.host?.lowercase()
        val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
        val secureEndpoint = endpointUri?.scheme.equals("https", ignoreCase = true)
        val localHttpEndpoint = endpointUri?.scheme.equals("http", ignoreCase = true) && localHost
        if (endpointUri == null || endpointUri.host.isNullOrBlank() || endpointUri.userInfo != null || (!secureEndpoint && !localHttpEndpoint)) {
            return VibeResult.Error("Invalid Anthropic endpoint '$endpoint'. Endpoint must use HTTPS.")
        }

        return try {
            val requestBody = buildAnthropicPayload(request, stream = false)
            val headers = mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to apiKey.trim(),
                "anthropic-version" to "2023-06-01"
            )

            val (statusCode, responseText) = withContext(dispatchers.io) {
                executeHttp(endpoint, "POST", headers, requestBody)
            }

            if (statusCode in 200..299) {
                val parsedText = extractAnthropicContent(responseText)
                    ?: return VibeResult.Error("Anthropic returned HTTP $statusCode without a usable assistant response.")
                val responseMsg = AIMessage(
                    id = IdGenerator.shortId("msg"),
                    role = AIRole.ASSISTANT,
                    content = parsedText
                )
                VibeResult.Success(AIResponse(message = responseMsg))
            } else {
                VibeResult.Error(extractErrorMessage(statusCode, responseText))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            VibeResult.Error("Failed to connect to Anthropic API: ${e.message}", e)
        }
    }

    override fun streamResponse(request: AIRequest): Flow<AIResponseChunk> = flow {
        if (!isConfigured) throw IOException("No Anthropic API key configured.")
        val endpoint = customEndpoint.trim().ifBlank { "https://api.anthropic.com/v1/messages" }

        if (isOpenRouterEndpoint(endpoint)) {
            val delegate = OpenAIAIProvider(
                apiKey = apiKey,
                activeModel = activeModel,
                customEndpoint = endpoint,
                providerId = providerId,
                displayName = displayName,
                supportedModels = supportedModels,
                httpTransport = httpTransport,
                dispatchers = dispatchers,
                streamTransport = streamTransport
            )
            delegate.streamResponse(request).collect { emit(it) }
            return@flow
        }

        val endpointUri = runCatching { URI(endpoint) }.getOrNull()
        val host = endpointUri?.host?.lowercase()
        val localHttpEndpoint = endpointUri?.scheme.equals("http", ignoreCase = true) &&
            (host == "localhost" || host == "127.0.0.1" || host == "::1")
        if (endpointUri?.host.isNullOrBlank() || endpointUri?.userInfo != null ||
            (!endpointUri?.scheme.equals("https", ignoreCase = true) && !localHttpEndpoint)
        ) throw IOException("Invalid Anthropic endpoint. Use HTTPS.")

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "x-api-key" to apiKey.trim(),
            "anthropic-version" to "2023-06-01"
        )
        val requestBody = buildAnthropicPayload(request, stream = true)

        streamTransport?.let { injectedTransport ->
            val (status, lines) = injectedTransport(endpoint, headers, requestBody)
            if (status !in 200..299) {
                throw IOException(extractErrorMessage(status, lines.toList().joinToString("\n")))
            }
            consumeSseLines(lines) { chunk -> emit(chunk) }
            return@flow
        }

        if (httpTransport != null) {
            when (val result = generateResponse(request)) {
                is VibeResult.Success -> {
                    emit(AIResponseChunk(delta = result.data.message.content, isComplete = true))
                }
                is VibeResult.Error -> throw IOException(result.message, result.throwable)
                else -> throw IOException("Anthropic returned no response.")
            }
            return@flow
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 20_000
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
        lines.collect { line ->
            if (!line.startsWith("data:")) return@collect
            val data = line.removePrefix("data:").trim()
            streamedBytes += data.length
            if (streamedBytes > MAX_RESPONSE_BYTES) throw IOException("Anthropic stream exceeded the 4 MB limit.")
            if (data.isEmpty() || data == "[DONE]") return@collect
            val obj = runCatching { JSONObject(data) }.getOrNull() ?: return@collect
            when (obj.optString("type")) {
                "content_block_delta" -> {
                    val text = obj.optJSONObject("delta")?.optString("text").orEmpty()
                    if (text.isNotEmpty()) {
                        receivedContent = true
                        emitChunk(AIResponseChunk(delta = text))
                    }
                }
                "content_block_start" -> {
                    val text = obj.optJSONObject("content_block")?.optString("text").orEmpty()
                    if (text.isNotEmpty()) {
                        receivedContent = true
                        emitChunk(AIResponseChunk(delta = text))
                    }
                }
            }
        }
        if (!receivedContent) {
            throw IOException("Anthropic stream ended before any response content was received.")
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
            conn.connectTimeout = 20_000
            conn.readTimeout = 45_000
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val text = readLimited(if (code in 200..299) conn.inputStream else conn.errorStream)
            return code to text
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
                if (total > MAX_RESPONSE_BYTES) throw IOException("Anthropic response exceeded the 4 MB limit.")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun buildAnthropicPayload(request: AIRequest, stream: Boolean): String {
        val root = JSONObject()
        root.put("model", resolveModelName(activeModel))
        root.put("max_tokens", 4096)
        root.put("stream", stream)
        root.put(
            "system",
            "You are VibeSpace AI, an expert mobile coding companion. Provide clear, concise, production-ready code. When suggesting terminal commands, wrap them in ```bash ... ``` code blocks."
        )

        val messagesArray = JSONArray()
        val history = request.messages.takeLast(15)
        val lastUserMsg = history.lastOrNull { it.role == AIRole.USER }

        for (msg in history) {
            if (msg.role == AIRole.SYSTEM) continue
            val role = if (msg.role == AIRole.ASSISTANT) "assistant" else "user"
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
            contentBuilder.append(msg.content.ifBlank { "..." })
            messagesArray.put(
                JSONObject().apply {
                    put("role", role)
                    put("content", contentBuilder.toString())
                }
            )
        }

        if (messagesArray.length() == 0) {
            messagesArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", "Hello VibeSpace AI")
                }
            )
        }

        root.put("messages", messagesArray)
        return root.toString()
    }

    private fun extractAnthropicContent(jsonResponse: String): String? {
        return try {
            val root = JSONObject(jsonResponse)
            val content = root.optJSONArray("content") ?: return null
            val text = (0 until content.length())
                .mapNotNull { index ->
                    val block = content.optJSONObject(index)
                    if (block?.optString("type") == "text") block.optString("text") else null
                }
                .joinToString("")
            text.takeIf { it.isNotBlank() }
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
                "Anthropic Error ($statusCode): $message"
            } else {
                "Anthropic endpoint returned HTTP $statusCode"
            }
        } catch (_: Throwable) {
            if (statusCode == 401) "Invalid Anthropic API key."
            else if (statusCode == 429) "Anthropic rate limit or quota exceeded."
            else "Anthropic API request failed with HTTP $statusCode"
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    }
}
