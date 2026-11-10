package com.vibespace.core.language.lsp

import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.DefinitionLocation
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.language.model.HoverInfo
import kotlinx.coroutines.flow.Flow

/**
 * Language Server Protocol (LSP) client contract for Developer 5.
 * Defines standard language service interactions without hardcoding language-specific details.
 */
interface LanguageServerClient {
    val languageId: String
    val isRunning: Boolean

    suspend fun start(workspaceRootUri: String): VibeResult<Unit>
    suspend fun stop(): VibeResult<Unit>

    suspend fun didOpen(documentUri: String, text: String): VibeResult<Unit>
    suspend fun didChange(documentUri: String, newText: String, version: Int): VibeResult<Unit>
    suspend fun didClose(documentUri: String): VibeResult<Unit>

    fun observeDiagnostics(documentUri: String): Flow<List<Diagnostic>>

    suspend fun requestHover(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<HoverInfo?>

    suspend fun requestCompletion(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<List<CompletionItem>>

    suspend fun requestDefinition(
        documentUri: String,
        line: Int,
        column: Int
    ): VibeResult<List<DefinitionLocation>>

    suspend fun formatDocument(documentUri: String): VibeResult<String>
}
