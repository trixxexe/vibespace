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
        diagnostics.addAll(validateBrackets(content, languageId))

        // 2. Language-specific syntax validation
        when (languageId.lowercase()) {
            "python" -> diagnostics.addAll(validatePython(content))
            "json" -> diagnostics.addAll(validateJson(content))
            "javascript", "typescript" -> diagnostics.addAll(validateJavaScript(content))
        }

        return diagnostics
    }

    private fun validateBrackets(content: String, languageId: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val stack = ArrayDeque<Triple<Char, Int, Int>>() // char, line, col
        val code = maskStringsAndComments(content, languageId)
        val lines = code.split("\n")

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            val lineNum = lineIdx + 1

            for (colIdx in line.indices) {
                val c = line[colIdx]
                val colNum = colIdx + 1

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
        val lines = maskStringsAndComments(content, "python").split("\n")
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
        if (content.isBlank()) return emptyList()
        return try {
            StrictJsonParser(content).parse()
            emptyList()
        } catch (invalid: Exception) {
            jsonDiagnostic(invalid.message ?: "Invalid JSON")
        }
    }

    /** Small RFC 8259 grammar parser; Android's JSONTokener accepts non-JSON extensions. */
    private class StrictJsonParser(private val input: String) {
        private var index = 0

        fun parse() {
            whitespace()
            value()
            whitespace()
            if (index != input.length) fail("Unexpected content after the JSON value")
        }

        private fun value() {
            whitespace()
            when (input.getOrNull(index)) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', in '0'..'9' -> numberValue()
                else -> fail("Expected a JSON value")
            }
        }

        private fun objectValue() {
            index++
            whitespace()
            if (consume('}')) return
            while (true) {
                whitespace()
                if (input.getOrNull(index) != '"') fail("Object keys must be double-quoted strings")
                stringValue()
                whitespace()
                expect(':')
                value()
                whitespace()
                if (consume('}')) return
                expect(',')
            }
        }

        private fun arrayValue() {
            index++
            whitespace()
            if (consume(']')) return
            while (true) {
                value()
                whitespace()
                if (consume(']')) return
                expect(',')
            }
        }

        private fun stringValue() {
            expect('"')
            while (index < input.length) {
                val character = input[index++]
                when {
                    character == '"' -> return
                    character.code < 0x20 -> fail("Unescaped control character in string")
                    character == '\\' -> {
                        val escape = input.getOrNull(index++) ?: fail("Incomplete string escape")
                        when (escape) {
                            '"', '\\', '/' , 'b', 'f', 'n', 'r', 't' -> Unit
                            'u' -> repeat(4) {
                                val hex = input.getOrNull(index++) ?: fail("Incomplete Unicode escape")
                                if (hex.digitToIntOrNull(16) == null) fail("Invalid Unicode escape")
                            }
                            else -> fail("Invalid string escape")
                        }
                    }
                }
            }
            fail("Unterminated string")
        }

        private fun numberValue() {
            val number = JSON_NUMBER.find(input, index) ?: fail("Invalid JSON number")
            if (number.range.first != index) fail("Invalid JSON number")
            index = number.range.last + 1
        }

        private fun literal(expected: String) {
            if (!input.startsWith(expected, index)) fail("Expected '$expected'")
            index += expected.length
        }

        private fun whitespace() {
            while (input.getOrNull(index)?.let { it in JSON_WHITESPACE } == true) index++
        }

        private fun expect(character: Char) {
            if (!consume(character)) fail("Expected '$character'")
        }

        private fun consume(character: Char): Boolean {
            if (input.getOrNull(index) != character) return false
            index++
            return true
        }

        private fun fail(message: String): Nothing {
            val prefix = input.take(index)
            val line = prefix.count { it == '\n' } + 1
            val column = prefix.length - (prefix.lastIndexOf('\n') + 1) + 1
            throw IllegalArgumentException("$message at line $line, column $column")
        }

        private companion object {
            val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
            val JSON_WHITESPACE = setOf(' ', '\t', '\r', '\n')
        }
    }

    /** Masks literals and comments while preserving every newline and source column. */
    private fun maskStringsAndComments(content: String, languageId: String): String {
        val python = languageId.equals("python", ignoreCase = true)
        val output = content.toCharArray()
        var index = 0
        var quote: Char? = null
        var tripleQuote = false
        var escaped = false
        while (index < content.length) {
            val current = content[index]
            if (quote != null) {
                if (current != '\n' && current != '\r') output[index] = ' '
                if (escaped) escaped = false
                else if (current == '\\') escaped = true
                else if (tripleQuote && index + 2 < content.length && content[index] == quote && content[index + 1] == quote && content[index + 2] == quote) {
                    output[index] = ' '; output[index + 1] = ' '; output[index + 2] = ' '
                    index += 2
                    quote = null
                    tripleQuote = false
                } else if (!tripleQuote && current == quote) quote = null
                index++
                continue
            }
            if (!python && current == '/' && index + 1 < content.length && content[index + 1] == '*') {
                output[index] = ' '; output[index + 1] = ' '
                index += 2
                while (index < content.length) {
                    if (content[index] != '\n' && content[index] != '\r') output[index] = ' '
                    if (content[index] == '*' && index + 1 < content.length && content[index + 1] == '/') {
                        output[index] = ' '; output[index + 1] = ' '; index += 2; break
                    }
                    index++
                }
                continue
            }
            val startsLineComment = (python && current == '#') ||
                (!python && current == '/' && index + 1 < content.length && content[index + 1] == '/')
            if (startsLineComment) {
                while (index < content.length && content[index] != '\n') output[index++] = ' '
                continue
            }
            if (current == '\'' || current == '"') {
                quote = current
                tripleQuote = python && index + 2 < content.length && content[index + 1] == current && content[index + 2] == current
                output[index] = ' '
                if (tripleQuote) { output[index + 1] = ' '; output[index + 2] = ' '; index += 2 }
            }
            index++
        }
        return String(output)
    }

    private fun jsonDiagnostic(message: String) = listOf(
        createDiagnostic(message, 1, 1, DiagnosticSeverity.ERROR, source = "json")
    )

    private fun validateJavaScript(content: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = maskStringsAndComments(content, "javascript").split("\n")

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
