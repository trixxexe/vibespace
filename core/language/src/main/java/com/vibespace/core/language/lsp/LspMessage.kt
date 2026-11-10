package com.vibespace.core.language.lsp

/**
 * JSON-RPC 2.0 Error model for LSP communications.
 */
data class LspError(
    val code: Int,
    val message: String,
    val data: String? = null
)

/**
 * Base sealed class for JSON-RPC 2.0 messages exchanged via stdio.
 */
sealed class LspMessage {
    data class Request(
        val id: Long,
        val method: String,
        val paramsJson: String
    ) : LspMessage()

    data class Response(
        val id: Long,
        val resultJson: String?,
        val error: LspError? = null
    ) : LspMessage()

    data class Notification(
        val method: String,
        val paramsJson: String
    ) : LspMessage()
}
