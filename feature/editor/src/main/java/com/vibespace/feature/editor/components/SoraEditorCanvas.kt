package com.vibespace.feature.editor.components

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.sora.TextMateLanguageManager
import com.vibespace.core.editor.sora.VibeEditorColorScheme
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.language.model.LanguageDefinition
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor

private class EditorBinding(val documentId: String, val languageId: String) {
    var isUpdatingProgrammatically: Boolean = false
}

/**
 * Jetpack Compose wrapper for Sora CodeEditor.
 * Provides high-performance native syntax highlighting, line numbers,
 * smooth touch gestures, and integration with VibeSpace's design system.
 */
@Composable
fun SoraEditorCanvas(
    content: String,
    documentId: String,
    cursorPosition: CursorPosition,
    language: LanguageDefinition,
    fontSizeSp: Float,
    onContentChange: (String) -> Unit,
    onCursorChange: (CursorPosition) -> Unit,
    tabSize: Int = 4,
    showLineNumbers: Boolean = true,
    wordWrap: Boolean = false,
    theme: EditorThemeDefinition = EditorThemeDefinition.VIBE_DARK,
    controller: SoraEditorController = remember { SoraEditorController() },
    modifier: Modifier = Modifier
) {
    DisposableEffect(controller) {
        onDispose {
            controller.unbind()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(VibeTheme.colors.editorBackground),
        factory = { context ->
            TextMateLanguageManager.initialize(context)

            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                colorScheme = VibeEditorColorScheme(theme)
                isLineNumberEnabled = showLineNumbers
                isWordwrap = wordWrap
                tabWidth = tabSize
                setTextSize(fontSizeSp)

                // Set TextMate syntax highlighting for active language
                val editorLang = TextMateLanguageManager.createLanguageForDefinition(language)
                setEditorLanguage(editorLang)

                // Initialize document content
                setText(content)
                restoreCursor(this, content, cursorPosition)
                tag = EditorBinding(documentId, language.id)

                // Listen for content changes
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    if ((tag as? EditorBinding)?.isUpdatingProgrammatically != true) {
                        onContentChange(text.toString())
                    }
                }

                // Listen for cursor position updates
                subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                    if ((tag as? EditorBinding)?.isUpdatingProgrammatically != true) {
                        val line = cursor.leftLine + 1
                        val col = cursor.leftColumn + 1
                        val offset = cursor.left
                        onCursorChange(CursorPosition(line = line, column = col, offset = offset))
                    }
                }

                controller.bind(this)
            }
        },
        update = { editor ->
            // Update font size if changed externally
            editor.setTextSize(fontSizeSp)

            if (editor.isLineNumberEnabled != showLineNumbers) {
                editor.isLineNumberEnabled = showLineNumbers
            }
            if (editor.isWordwrap != wordWrap) {
                editor.isWordwrap = wordWrap
            }
            if (editor.tabWidth != tabSize) {
                editor.tabWidth = tabSize
            }

            // Update color scheme if theme changed
            editor.colorScheme = VibeEditorColorScheme(theme)

            val binding = editor.tag as? EditorBinding

            // Keep independent tab text and caret state when switching documents.
            if (binding?.documentId != documentId || editor.text.toString() != content) {
                val guard = binding ?: EditorBinding(documentId, language.id)
                editor.tag = guard
                guard.isUpdatingProgrammatically = true
                try {
                    editor.setText(content)
                    restoreCursor(editor, content, cursorPosition)
                } finally {
                    guard.isUpdatingProgrammatically = false
                }
            }

            // Update language syntax if language changed
            if (binding?.languageId != language.id) {
                val editorLang = TextMateLanguageManager.createLanguageForDefinition(language)
                editor.setEditorLanguage(editorLang)
            }
            editor.tag = EditorBinding(documentId, language.id)
        },
        onRelease = { editor ->
            controller.unbind()
            editor.release()
        }
    )
}

private fun restoreCursor(editor: CodeEditor, content: String, cursorPosition: CursorPosition) {
    val lines = content.split('\n')
    val line = (cursorPosition.line - 1).coerceIn(0, lines.lastIndex)
    val column = (cursorPosition.column - 1).coerceIn(0, lines[line].length)
    editor.setSelection(line, column)
}
