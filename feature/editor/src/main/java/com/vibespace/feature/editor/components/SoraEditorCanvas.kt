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

/**
 * Jetpack Compose wrapper for Sora CodeEditor.
 * Provides high-performance native syntax highlighting, line numbers,
 * smooth touch gestures, and integration with VibeSpace's design system.
 */
@Composable
fun SoraEditorCanvas(
    content: String,
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

                // Listen for content changes
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val updated = text.toString()
                    onContentChange(updated)
                }

                // Listen for cursor position updates
                subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                    val line = cursor.leftLine + 1
                    val col = cursor.leftColumn + 1
                    val offset = cursor.left
                    onCursorChange(CursorPosition(line = line, column = col, offset = offset))
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

            // Sync content if updated externally
            if (editor.text.toString() != content) {
                editor.setText(content)
            }

            // Update language syntax if language changed
            val targetScope = TextMateLanguageManager.getScopeNameForLanguage(language)
            val currentLang = editor.editorLanguage
            if (targetScope != null) {
                val editorLang = TextMateLanguageManager.createLanguageForDefinition(language)
                editor.setEditorLanguage(editorLang)
            }
        },
        onRelease = { editor ->
            controller.unbind()
            editor.release()
        }
    )
}
