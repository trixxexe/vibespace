package com.vibespace.core.language.lsp

import java.io.ByteArrayOutputStream

/**
 * Handles framing and deframing of Language Server Protocol (LSP) messages
 * using standard Content-Length headers over stdio streams.
 */
object LspFramer {

    private const val HEADER_SEPARATOR = "\r\n\r\n"
    private const val CONTENT_LENGTH_PREFIX = "Content-Length: "

    /**
     * Frames a JSON payload by prepending the Content-Length header.
     */
    fun frame(payload: String): ByteArray {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val header = "$CONTENT_LENGTH_PREFIX${payloadBytes.size}$HEADER_SEPARATOR"
        val headerBytes = header.toByteArray(Charsets.US_ASCII)

        val out = ByteArrayOutputStream(headerBytes.size + payloadBytes.size)
        out.write(headerBytes)
        out.write(payloadBytes)
        return out.toByteArray()
    }

    /**
     * Deframes a stream of raw string input and extracts complete JSON-RPC payloads.
     */
    class StreamDecoder {
        private var buffer = ""

        @Synchronized
        fun feed(chunk: String): List<String> {
            buffer += chunk
            val results = mutableListOf<String>()

            while (true) {
                val headerIndex = buffer.indexOf(HEADER_SEPARATOR)
                if (headerIndex == -1) break

                val headerPart = buffer.substring(0, headerIndex)
                val contentLength = parseContentLength(headerPart)
                if (contentLength == null) {
                    // Invalid header, discard up to separator
                    buffer = buffer.substring(headerIndex + HEADER_SEPARATOR.length)
                    continue
                }

                val bodyStartIndex = headerIndex + HEADER_SEPARATOR.length
                val bodyEndIndex = bodyStartIndex + contentLength

                if (buffer.length < bodyEndIndex) {
                    // Not enough bytes yet, wait for more chunks
                    break
                }

                val payload = buffer.substring(bodyStartIndex, bodyEndIndex)
                results.add(payload)
                buffer = buffer.substring(bodyEndIndex)
            }

            return results
        }

        private fun parseContentLength(header: String): Int? {
            val lines = header.split("\r\n")
            for (line in lines) {
                if (line.startsWith(CONTENT_LENGTH_PREFIX, ignoreCase = true)) {
                    val value = line.substring(CONTENT_LENGTH_PREFIX.length).trim()
                    return value.toIntOrNull()
                }
            }
            return null
        }
    }
}
