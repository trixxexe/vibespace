package com.vibespace.core.language.diagnostics

import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.language.model.Diagnostic
import com.vibespace.core.language.model.DiagnosticPosition
import com.vibespace.core.language.model.DiagnosticRange
import com.vibespace.core.language.model.DiagnosticSeverity

/**
 * Real-time syntax and structural code validator.
 * Analyzes brackets, quotes, indentation, and language-specific syntax patterns.
 */
object SyntaxDiagnosticsValidator {

    fun validate(content: String, languageId: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        if (content.isEmpty()) return emptyList()

        // 1. Bracket and Parentheses matching
        diagnostics.addAll(validateBrackets(content))

        // 2. Language-specific syntax validation
        when (languageId.lowercase()) {
            "python" -> diagnostics.addAll(validatePython(content))
            "json" -> diagnostics.addAll(validateJson(content))
            "javascript", "typescript" -> diagnostics.addAll(validateJavaScript(content))
        }

        return diagnostics
    }

    private fun validateBrackets(content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val stack = ArrayDeque<Triple<Char, Int, Int>>() // char, line, col
        val lines = content.split("\n")

        var inSingleQuote = false
        var inDoubleQuote = false
        var inTripleQuote = false

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            val lineNum = lineIdx + 1

            for (colIdx in line.indices) {
                val c = line[colIdx]
                val colNum = colIdx + 1

                // Basic quote escaping check
                if (c == '"' && (colIdx == 0 || line[colIdx - 1] != '\\')) {
                    inDoubleQuote = !inDoubleQuote
                } else if (c == '\'' && (colIdx == 0 || line[colIdx - 1] != '\\')) {
                    inSingleQuote = !inSingleQuote
                }

                if (inSingleQuote || inDoubleQuote) continue

                when (c) {
                    '(', '[', '{' -> stack.add(Triple(c, lineNum, colNum))
                    ')' -> {
                        if (stack.isEmpty() || stack.last().first != '(') {
                            diagnostics.add(
                                createDiagnostic(
                                    message = "Unmatched closing parenthesis ')'",
                                    line = lineNum,
                                    column = colNum,
                                    severity = DiagnosticSeverity.ERROR
                                )
                            )
                        } else {
                            stack.removeLast()
                        }
                    }
                    ']' -> {
                        if (stack.isEmpty() || stack.last().first != '[') {
                            diagnostics.add(
                                createDiagnostic(
                                    message = "Unmatched closing bracket ']'",
                                    line = lineNum,
                                    column = colNum,
                                    severity = DiagnosticSeverity.ERROR
                                )
                            )
                        } else {
                            stack.removeLast()
                        }
                    }
                    '}' -> {
                        if (stack.isEmpty() || stack.last().first != '{') {
                            diagnostics.add(
                                createDiagnostic(
                                    message = "Unmatched closing brace '}'",
                                    line = lineNum,
                                    column = colNum,
                                    severity = DiagnosticSeverity.ERROR
                                )
                            )
                        } else {
                            stack.removeLast()
                        }
                    }
                }
            }
        }

        // Remaining unclosed openers
        while (stack.isNotEmpty()) {
            val unclosed = stack.removeLast()
            val expectedCloser = when (unclosed.first) {
                '(' -> "')'"
                '[' -> "']'"
                '{' -> "'}'"
                else -> "closer"
            }
            diagnostics.add(
                createDiagnostic(
                    message = "Unclosed opening delimiter '${unclosed.first}', expected $expectedCloser",
                    line = unclosed.second,
                    column = unclosed.third,
                    severity = DiagnosticSeverity.ERROR
                )
            )
        }

        return diagnostics
    }

    private fun validatePython(content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = content.split("\n")
        val colonKeywords = listOf("def", "class", "if", "elif", "else", "for", "while", "try", "except", "finally", "with")

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx].trim()
            val lineNum = lineIdx + 1

            if (line.isEmpty() || line.startsWith("#")) continue

            for (kw in colonKeywords) {
                if (line == kw || line.startsWith("$kw ") || line.startsWith("$kw(")) {
                    val cleanLine = line.substringBefore("#").trim()
                    if (!cleanLine.endsWith(":") && !cleanLine.endsWith("\\")) {
                        diagnostics.add(
                            createDiagnostic(
                                message = "SyntaxError: Expected ':' at end of '$kw' statement",
                                line = lineNum,
                                column = line.length,
                                severity = DiagnosticSeverity.ERROR,
                                source = "python"
                            )
                        )
                    }
                }
            }
        }
        return diagnostics
    }

    private fun validateJson(content: String): List<Diagnostic> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return listOf(
                createDiagnostic(
                    message = "JSON document must begin with '{' or '['",
                    line = 1,
                    column = 1,
                    severity = DiagnosticSeverity.ERROR,
                    source = "json"
                )
            )
        }

        return emptyList()
    }

    private fun validateJavaScript(content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = content.split("\n")

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx].trim()
            val lineNum = lineIdx + 1

            if (line.startsWith("const ") && !line.contains("=") && !line.endsWith(";")) {
                diagnostics.add(
                    createDiagnostic(
                        message = "Missing initializer in const declaration",
                        line = lineNum,
                        column = line.length,
                        severity = DiagnosticSeverity.ERROR,
                        source = "javascript"
                    )
                )
            }
        }
        return diagnostics
    }

    private fun createDiagnostic(
        message: String,
        line: Int,
        column: Int,
        severity: DiagnosticSeverity,
        source: String = "vibe-lint"
    ): Diagnostic {
        val pos = DiagnosticPosition(line, column)
        return Diagnostic(
            id = IdGenerator.shortId("diag"),
            message = message,
            severity = severity,
            range = DiagnosticRange(start = pos, end = DiagnosticPosition(line, column + 1)),
            source = source
        )
    }
}
