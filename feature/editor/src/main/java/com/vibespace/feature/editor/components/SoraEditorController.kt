package com.vibespace.feature.editor.components

import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.ref.WeakReference

/**
 * Controller to interact with the active Sora CodeEditor instance
 * from external Compose UI elements like the MobileSymbolBar and EditorToolbar.
 */
class SoraEditorController {

    private var editorRef: WeakReference<CodeEditor>? = null

    fun bind(editor: CodeEditor) {
        editorRef = WeakReference(editor)
    }

    fun unbind() {
        editorRef?.clear()
        editorRef = null
    }

    /**
     * Inserts text at the current cursor position or wraps selection.
     */
    fun insertSymbol(symbol: String) {
        val editor = editorRef?.get() ?: return
        val cursor = editor.cursor

        if (cursor.isSelected && isPairSymbol(symbol)) {
            val selectedText = cursor.selectedText
            val wrapped = wrapTextWithPair(symbol, selectedText)
            cursor.onCommitText(wrapped)
        } else {
            cursor.onCommitText(symbol)
        }
    }

    /**
     * Moves the cursor one position to the left.
     */
    fun moveCursorLeft() {
        val editor = editorRef?.get() ?: return
        try {
            editor.moveSelectionLeft()
        } catch (_: Throwable) {
            val line = editor.cursor.leftLine
            val col = (editor.cursor.leftColumn - 1).coerceAtLeast(0)
            editor.setSelection(line, col)
        }
    }

    /**
     * Moves the cursor one position to the right.
     */
    fun moveCursorRight() {
        val editor = editorRef?.get() ?: return
        try {
            editor.moveSelectionRight()
        } catch (_: Throwable) {
            val line = editor.cursor.leftLine
            val col = editor.cursor.leftColumn + 1
            editor.setSelection(line, col)
        }
    }

    /**
     * Performs undo in the editor text engine.
     */
    fun undo() {
        val editor = editorRef?.get() ?: return
        if (editor.canUndo()) {
            editor.undo()
        }
    }

    /**
     * Performs redo in the editor text engine.
     */
    fun redo() {
        val editor = editorRef?.get() ?: return
        if (editor.canRedo()) {
            editor.redo()
        }
    }

    private fun isPairSymbol(symbol: String): Boolean {
        return symbol in listOf("(", "{", "[", "\"", "'", "<")
    }

    private fun wrapTextWithPair(symbol: String, innerText: String): String {
        return when (symbol) {
            "(" -> "($innerText)"
            "{" -> "{$innerText}"
            "[" -> "[$innerText]"
            "\"" -> "\"$innerText\""
            "'" -> "'$innerText'"
            "<" -> "<$innerText>"
            else -> "$symbol$innerText"
        }
    }
}
