package com.vibespace.feature.editor.components

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
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
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EditorBinding(
    var documentId: String,
    var languageId: String,
    var themeId: String,
    var editorLanguage: Language,
    var externalContentVersion: Long = 0L
) {
    var isUpdatingProgrammatically: Boolean = false
    var lastEmittedContent: String? = null
    private val recentEmissions = ArrayDeque<String>()

    fun recordEmission(text: String) {
        lastEmittedContent = text
        if (recentEmissions.lastOrNull() != text) {
            recentEmissions.addLast(text)
            if (recentEmissions.size > 64) {
                recentEmissions.removeFirst()
            }
        }
    }

    fun isRecentEmission(text: String): Boolean {
        return text == lastEmittedContent || recentEmissions.contains(text)
    }

    fun clearEmissions() {
        recentEmissions.clear()
    }
}

/**
 * Jetpack Compose wrapper for Sora CodeEditor.
 * Provides high-performance native syntax highlighting, line numbers,
 * snappy touch handling, and integration with VibeSpace's design system.
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
    externalContentVersion: Long = 0L,
    controller: SoraEditorController = remember { SoraEditorController() },
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val currentOnContentChange by rememberUpdatedState(onContentChange)
    val currentOnCursorChange by rememberUpdatedState(onCursorChange)

    var grammarReady by remember {
        if (!TextMateLanguageManager.isReady()) {
            runCatching { TextMateLanguageManager.initialize(appContext) }
        }
        mutableStateOf(TextMateLanguageManager.isReady())
    }
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
        mutableStateOf<Language>(
            if (grammarReady) {
                runCatching {
                    TextMateLanguageManager.loadThemeForDefinition(theme)
                    TextMateLanguageManager.createLanguageForDefinition(language)
                }.getOrElse { EmptyLanguage() }
            } else {
                EmptyLanguage()
            }
        )
    }
    LaunchedEffect(language.id, grammarReady) {
        if (grammarReady && editorLanguage is EmptyLanguage) {
            editorLanguage = withContext(Dispatchers.Default) {
                TextMateLanguageManager.loadThemeForDefinition(theme)
                TextMateLanguageManager.createLanguageForDefinition(language)
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
                isFocusable = true
                isFocusableInTouchMode = true
                isEnabled = true
                isEditable = true
                typefaceText = Typeface.MONOSPACE
                isCursorAnimationEnabled = false
                if (!TextMateLanguageManager.isReady()) {
                    runCatching { TextMateLanguageManager.initialize(context.applicationContext) }
                }
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
                val binding = EditorBinding(
                    documentId = documentId,
                    languageId = language.id,
                    themeId = theme.id,
                    editorLanguage = editorLanguage,
                    externalContentVersion = externalContentVersion
                ).apply {
                    recordEmission(content)
                    isUpdatingProgrammatically = true
                }
                tag = binding
                try {
                    setText(content)
                    restoreCursor(this, cursorPosition)
                } finally {
                    binding.isUpdatingProgrammatically = false
                }

                // Ensure tapping anywhere in the editor focuses the view and opens the soft keyboard
                subscribeEvent(ClickEvent::class.java) { _, _ ->
                    if (!hasFocus()) {
                        requestFocusFromTouch()
                        requestFocus()
                    }
                    showSoftInput()
                    post {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }

                // Listen for content changes
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    val b = tag as? EditorBinding
                    if (b?.isUpdatingProgrammatically != true) {
                        val currentText = text.toString()
                        b?.recordEmission(currentText)
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
                post {
                    requestFocus()
                }
            }
        },
        update = { editor ->
            editor.isEnabled = true
            editor.isEditable = true
            editor.isCursorAnimationEnabled = false
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
            val isExplicitExternalVersionChange = binding?.externalContentVersion != externalContentVersion
            val isUnfocusedExternalChange = !isNewDocument &&
                currentEditorText != content &&
                binding?.isRecentEmission(content) != true &&
                !editor.hasFocus()

            // Only replace editor buffer on document switch or explicit external edits (Undo/Redo/Format/Replace),
            // never from asynchronous echoes of the user's own typing!
            if (isNewDocument || isExplicitExternalVersionChange || isUnfocusedExternalChange) {
                val guard = binding ?: EditorBinding(
                    documentId = documentId,
                    languageId = language.id,
                    themeId = theme.id,
                    editorLanguage = editorLanguage,
                    externalContentVersion = externalContentVersion
                )
                editor.tag = guard
                guard.documentId = documentId
                guard.externalContentVersion = externalContentVersion
                guard.clearEmissions()
                guard.recordEmission(content)
                guard.isUpdatingProgrammatically = true
                try {
                    if (currentEditorText != content || isNewDocument) {
                        editor.setText(content)
                    }
                    restoreCursor(editor, cursorPosition)
                } finally {
                    guard.isUpdatingProgrammatically = false
                }
                if (isNewDocument) {
                    editor.post {
                        editor.requestFocus()
                    }
                }
            }

            // Update language syntax if language changed
            if (binding?.languageId != language.id || binding?.editorLanguage !== editorLanguage) {
                if (TextMateLanguageManager.isReady()) {
                    TextMateLanguageManager.loadThemeForDefinition(theme)
                }
                editor.colorScheme = VibeEditorColorScheme(theme)
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
