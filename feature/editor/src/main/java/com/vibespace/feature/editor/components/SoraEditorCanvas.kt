package com.vibespace.feature.editor.components

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.sora.TextMateLanguageManager
import com.vibespace.core.editor.sora.VibeEditorColorScheme
import com.vibespace.core.editor.theme.EditorThemeDefinition
import com.vibespace.core.language.model.LanguageDefinition
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
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
    val currentOnContentChange by rememberUpdatedState(onContentChange)
    val currentOnCursorChange by rememberUpdatedState(onCursorChange)

    var grammarReady by remember { mutableStateOf(TextMateLanguageManager.isReady()) }
    LaunchedEffect(appContext) {
        if (!grammarReady) {
            grammarReady = withContext(Dispatchers.IO) {
                TextMateLanguageManager.initialize(appContext)
                TextMateLanguageManager.isReady()
            }
        }
    }

    LaunchedEffect(theme.id, grammarReady) {
        if (grammarReady) {
            withContext(Dispatchers.Default) {
                TextMateLanguageManager.loadThemeForDefinition(theme)
            }
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
                if (TextMateLanguageManager.isReady()) {
                    TextMateLanguageManager.loadThemeForDefinition(theme)
                }
                colorScheme = VibeEditorColorScheme(theme)
                isLineNumberEnabled = showLineNumbers
                isWordwrap = wordWrap
                tabWidth = tabSize
                setTextSize(fontSizeSp)

                // Set syntax highlighting for active language
                setEditorLanguage(editorLanguage)

                // Initialize document content
                val binding = EditorBinding(documentId, language.id, theme.id, editorLanguage).apply {
                    lastEmittedContent = content
                    isUpdatingProgrammatically = true
                }
                tag = binding
                try {
                    setText(content)
                    restoreCursor(this, cursorPosition)
                } finally {
                    binding.isUpdatingProgrammatically = false
                }

                // Listen for content changes
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val b = tag as? EditorBinding
                    if (b?.isUpdatingProgrammatically != true) {
                        val currentText = text.toString()
                        b?.lastEmittedContent = currentText
                        currentOnContentChange(currentText)
                    }
                }

                // Listen for cursor position updates
                subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                    if ((tag as? EditorBinding)?.isUpdatingProgrammatically != true) {
                        val line = cursor.leftLine + 1
                        val col = cursor.leftColumn + 1
                        val offset = cursor.left
                        currentOnCursorChange(CursorPosition(line = line, column = col, offset = offset))
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
                if (TextMateLanguageManager.isReady()) {
                    TextMateLanguageManager.loadThemeForDefinition(theme)
                }
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
                    restoreCursor(editor, cursorPosition)
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

private fun restoreCursor(editor: CodeEditor, cursorPosition: CursorPosition) {
    runCatching {
        val textObj = editor.text
        val lineCount = textObj.lineCount
        if (lineCount <= 0) {
            editor.setSelection(0, 0)
            return
        }
        val line = (cursorPosition.line - 1).coerceIn(0, lineCount - 1)
        val maxCol = textObj.getColumnCount(line).coerceAtLeast(0)
        val column = (cursorPosition.column - 1).coerceIn(0, maxCol)
        editor.setSelection(line, column)
    }
}
