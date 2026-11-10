package com.vibespace.core.language

import com.vibespace.core.language.autocomplete.AutocompleteEngine
import com.vibespace.core.language.diagnostics.SyntaxDiagnosticsValidator
import com.vibespace.core.language.model.CompletionItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxAndAutocompleteTest {

    @Test
    fun unclosed_parentheses_triggers_diagnostic() {
        val code = "def calculate(x, y:\n    return x + y"
        val diags = SyntaxDiagnosticsValidator.validate(code, "python")
        assertFalse(diags.isEmpty())
        assertTrue(diags.any { it.message.contains("Unclosed opening delimiter '('") })
    }

    @Test
    fun python_missing_colon_triggers_diagnostic() {
        val code = "def my_func()\n    pass"
        val diags = SyntaxDiagnosticsValidator.validate(code, "python")
        assertTrue(diags.any { it.message.contains("Expected ':'") })
    }

    @Test
    fun autocomplete_returns_matching_keywords_and_symbols() {
        val code = """
            user_count = 10
            def process_user(user_id):
                pass
        """.trimIndent()

        val suggestions = AutocompleteEngine.getSuggestions(code, "pro", "python")
        assertFalse(suggestions.isEmpty())
        assertTrue(suggestions.any { it.label == "process_user" && it.kind == CompletionItemKind.VARIABLE })
    }

    @Test
    fun autocomplete_returns_language_keywords() {
        val suggestions = AutocompleteEngine.getSuggestions("", "fun", "kotlin")
        assertTrue(suggestions.any { it.label == "fun" && it.kind == CompletionItemKind.KEYWORD })
    }
}
