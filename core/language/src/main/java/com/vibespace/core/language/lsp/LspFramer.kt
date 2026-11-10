package com.vibespace.core.language.lsp

import java.io.ByteArrayOutputStream

/**
 * Handles framing and deframing of Language Server Protocol (LSP) messages
 * using standard Content-Length headers over stdio streams.
 * Accurately measures UTF-8 payload byte counts and avoids character boundary corruptions.
 */
object LspFramer {

    private const val HEADER_SEPARATOR = "\r\n\r\n"
    private const val CONTENT_LENGTH_PREFIX = "Content-Length: "
    private val HEADER_SEP_BYTES = HEADER_SEPARATOR.toByteArray(Charsets.US_ASCII)

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
     * Deframes a stream of raw bytes or string input and extracts complete JSON-RPC payloads.
     */
    class StreamDecoder {
        private val buffer = ByteArrayOutputStream()

        @Synchronized
        fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<String> {
            buffer.write(bytes, offset, length)
            val currentBytes = buffer.toByteArray()
            val results = mutableListOf<String>()
            var searchStart = 0

            while (true) {
                val headerIndex = indexOf(currentBytes, HEADER_SEP_BYTES, searchStart)
                if (headerIndex == -1) break

                val headerStr = String(currentBytes, searchStart, headerIndex - searchStart, Charsets.US_ASCII)
                val contentLength = parseContentLength(headerStr)
                if (contentLength == null) {
                    searchStart = headerIndex + HEADER_SEP_BYTES.size
                    continue
                }

                val bodyStartIndex = headerIndex + HEADER_SEP_BYTES.size
                val bodyEndIndex = bodyStartIndex + contentLength
                if (currentBytes.size < bodyEndIndex) {
                    // Not enough bytes yet, wait for more chunks
                    break
                }

                val payload = String(currentBytes, bodyStartIndex, contentLength, Charsets.UTF_8)
                results.add(payload)
                searchStart = bodyEndIndex
            }

            if (searchStart > 0) {
                val remaining = currentBytes.copyOfRange(searchStart, currentBytes.size)
                buffer.reset()
                buffer.write(remaining)
            }

            return results
        }

        @Synchronized
        fun feed(chunk: String): List<String> {
            val bytes = chunk.toByteArray(Charsets.UTF_8)
            return feed(bytes, 0, bytes.size)
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

        private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
            if (fromIndex >= source.size || target.isEmpty()) return -1
            val max = source.size - target.size
            outer@ for (i in fromIndex..max) {
                for (j in target.indices) {
                    if (source[i + j] != target[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
