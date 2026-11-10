package com.vibespace.core.language.treesitter

import com.vibespace.core.common.result.VibeResult

data class SyntaxNode(
    val type: String,
    val startByte: Int,
    val endByte: Int,
    val startRow: Int,
    val startColumn: Int,
    val endRow: Int,
    val endColumn: Int,
    val children: List<SyntaxNode> = emptyList()
)

data class TreeSitterParseResult(
    val languageId: String,
    val rootNode: SyntaxNode,
    val hasErrors: Boolean = false
)

/**
 * Tree-sitter AST parser contract for Developer 5.
 */
interface TreeSitterEngine {
    val supportedLanguages: Set<String>
    suspend fun parse(languageId: String, sourceCode: String): VibeResult<TreeSitterParseResult>
    suspend fun query(parseResult: TreeSitterParseResult, pattern: String): VibeResult<List<SyntaxNode>>
}
