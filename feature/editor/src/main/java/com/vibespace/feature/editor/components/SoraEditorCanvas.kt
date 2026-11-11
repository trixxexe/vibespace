package com.vibespace.feature.editor.components

import android.graphics.Typeface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.sora.TextMateLanguageManager
import com.vibespace.core.editor.sora.VibeEditorColorScheme
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.language.model.LanguageDefinition
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class EditorBinding(
    var documentId: String,
    var languageId: String,
    var themeId: String,
    var editorLanguage: Language
) {
    var isUpdatingProgrammatically: Boolean = false
    var lastEmittedContent: String? = null
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
    val appContext = LocalContext.current.applicationContext
    var grammarReady by remember { mutableStateOf(TextMateLanguageManager.isReady()) }
    LaunchedEffect(appContext) {
        grammarReady = withContext(Dispatchers.IO) {
            TextMateLanguageManager.initialize(appContext)
            TextMateLanguageManager.isReady()
        }
    }

    var editorLanguage by remember(language.id, grammarReady) {
        mutableStateOf<Language>(EmptyLanguage())
    }
    LaunchedEffect(language.id, grammarReady) {
        editorLanguage = if (grammarReady) {
            withContext(Dispatchers.Default) {
                TextMateLanguageManager.createLanguageForDefinition(language)
            }
        } else {
            EmptyLanguage()
        }
    }

    LaunchedEffect(theme.id, grammarReady) {
        if (grammarReady) {
            withContext(Dispatchers.Default) {
                TextMateLanguageManager.loadThemeForDefinition(theme)
            }
        }
    }

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
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                colorScheme = VibeEditorColorScheme(theme)
                isLineNumberEnabled = showLineNumbers
                isWordwrap = wordWrap
                tabWidth = tabSize
                setTextSize(fontSizeSp)

                // Set syntax highlighting for active language
                setEditorLanguage(editorLanguage)

                // Initialize document content
                setText(content)
                restoreCursor(this, content, cursorPosition)
                val binding = EditorBinding(documentId, language.id, theme.id, editorLanguage).apply {
                    lastEmittedContent = content
                }
                tag = binding

                // Listen for content changes
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val b = tag as? EditorBinding
                    if (b?.isUpdatingProgrammatically != true) {
                        val currentText = text.toString()
                        b?.lastEmittedContent = currentText
                        onContentChange(currentText)
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

            val binding = editor.tag as? EditorBinding

            // Only update color scheme if theme actually changed to avoid invalidating token render cache on every frame
            if (binding?.themeId != theme.id) {
                editor.colorScheme = VibeEditorColorScheme(theme)
                binding?.themeId = theme.id
            }

            val isNewDocument = binding?.documentId != documentId
            val currentEditorText = editor.text.toString()
            val isExternalChange = !isNewDocument && currentEditorText != content && content != binding?.lastEmittedContent

            // Keep independent tab text and caret state when switching documents or on external changes.
            if (isNewDocument || isExternalChange) {
                val guard = binding ?: EditorBinding(documentId, language.id, theme.id, editorLanguage)
                editor.tag = guard
                guard.documentId = documentId
                guard.lastEmittedContent = content
                guard.isUpdatingProgrammatically = true
                try {
                    editor.setText(content)
                    restoreCursor(editor, content, cursorPosition)
                } finally {
                    guard.isUpdatingProgrammatically = false
                }
            }

            // Update language syntax if language changed
            if (binding?.languageId != language.id || binding?.editorLanguage !== editorLanguage) {
                editor.setEditorLanguage(editorLanguage)
                binding?.languageId = language.id
                binding?.editorLanguage = editorLanguage
            }
        },
        onRelease = { editor ->
            controller.unbind()
            editor.release()
        }
    )
}

private fun restoreCursor(editor: CodeEditor, content: String, cursorPosition: CursorPosition) {
    if (content.isEmpty()) {
        runCatching { editor.setSelection(0, 0) }
        return
    }
    val lines = content.split('\n')
    val line = (cursorPosition.line - 1).coerceIn(0, lines.lastIndex)
    val maxCol = lines.getOrElse(line) { "" }.length
    val column = (cursorPosition.column - 1).coerceIn(0, maxCol)
    runCatching { editor.setSelection(line, column) }
}
