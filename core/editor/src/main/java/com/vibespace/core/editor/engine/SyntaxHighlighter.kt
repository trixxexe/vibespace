package com.vibespace.core.editor.engine

import com.vibespace.core.editor.model.TextSpan
import com.vibespace.core.language.model.LanguageDefinition

/**
 * Syntax highlighting contract designed for Developer 2 (Editor Engineer).
 */
interface SyntaxHighlighter {
    fun highlight(content: String, language: LanguageDefinition): List<TextSpan>
}

class RegexSyntaxHighlighter : SyntaxHighlighter {
    override fun highlight(content: String, language: LanguageDefinition): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()

        // Highlight line comments if configured
        val commentPrefix = language.lineCommentPrefix
        if (commentPrefix != null) {
            val lines = content.lines()
            var currentOffset = 0
            for (line in lines) {
                val commentIndex = line.indexOf(commentPrefix)
                if (commentIndex != -1) {
                    spans.add(
                        TextSpan(
                            startOffset = currentOffset + commentIndex,
                            endOffset = currentOffset + line.length,
                            styleKey = "comment"
                        )
                    )
                }
                currentOffset += line.length + 1
            }
        }

        return spans
    }
}
