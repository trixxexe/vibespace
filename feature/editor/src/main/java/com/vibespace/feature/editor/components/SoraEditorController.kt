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
        val text = editor.text

        if (cursor.isSelected) {
            val startLine = cursor.leftLine
            val startCol = cursor.leftColumn
            val endLine = cursor.rightLine
            val endCol = cursor.rightColumn
            val selected = text.subSequence(cursor.left, cursor.right).toString()
            text.delete(startLine, startCol, endLine, endCol)
            val wrapped = if (isPairSymbol(symbol)) wrapTextWithPair(symbol, selected) else symbol
            text.insert(startLine, startCol, wrapped)
            editor.setSelection(startLine, startCol + wrapped.length)
        } else {
            val line = cursor.leftLine
            val col = cursor.leftColumn
            text.insert(line, col, symbol)
            editor.setSelection(line, col + symbol.length)
        }
    }

    /**
     * Moves the cursor one position to the left.
     */
    fun moveCursorLeft() {
        val editor = editorRef?.get() ?: return
        val cursor = editor.cursor
        val line = cursor.leftLine
        val col = cursor.leftColumn
        if (col > 0) {
            editor.setSelection(line, col - 1)
        } else if (line > 0) {
            val prevLineLen = editor.text.getColumnCount(line - 1)
            editor.setSelection(line - 1, prevLineLen)
        }
    }

    /**
     * Moves the cursor one position to the right.
     */
    fun moveCursorRight() {
        val editor = editorRef?.get() ?: return
        val cursor = editor.cursor
        val line = cursor.leftLine
        val col = cursor.leftColumn
        val lineLen = editor.text.getColumnCount(line)
        if (col < lineLen) {
            editor.setSelection(line, col + 1)
        } else if (line + 1 < editor.text.lineCount) {
            editor.setSelection(line + 1, 0)
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
