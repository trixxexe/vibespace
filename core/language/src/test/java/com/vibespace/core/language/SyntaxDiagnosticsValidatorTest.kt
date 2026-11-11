package com.vibespace.core.language

import com.vibespace.core.language.diagnostics.SyntaxDiagnosticsValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxDiagnosticsValidatorTest {
    @Test
    fun bracketValidationIgnoresStringAndCommentContents() {
        val python = """
            text = "([{}])"
            # ([)]
            value = 3
        """.trimIndent()

        assertTrue(SyntaxDiagnosticsValidator.validate(python, "python").isEmpty())
    }

    @Test
    fun bracketValidationFindsRealUnmatchedDelimiter() {
        val diagnostics = SyntaxDiagnosticsValidator.validate("value = (1]", "python")

        assertTrue(diagnostics.any { it.message.contains("Unmatched closing bracket") })
    }

    @Test
    fun jsonValidationAcceptsScalarsAndRejectsTrailingValues() {
        assertTrue(SyntaxDiagnosticsValidator.validate("42", "json").isEmpty())
        assertTrue(SyntaxDiagnosticsValidator.validate("{} false", "json").isNotEmpty())
        assertTrue(SyntaxDiagnosticsValidator.validate("{\"ok\": true}", "json").isEmpty())
    }
}
