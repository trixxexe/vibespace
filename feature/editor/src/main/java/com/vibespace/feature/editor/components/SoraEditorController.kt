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

        val sourceBefore = text.toString()
        if (cursor.isSelected) {
            val startOffset = cursor.left.coerceIn(0, sourceBefore.length)
            val startLine = cursor.leftLine
            val startCol = cursor.leftColumn
            val endLine = cursor.rightLine
            val endCol = cursor.rightColumn
            val selected = text.subSequence(cursor.left, cursor.right).toString()
            text.delete(startLine, startCol, endLine, endCol)
            val wrapped = if (isPairSymbol(symbol)) wrapTextWithPair(symbol, selected) else symbol
            text.insert(startLine, startCol, wrapped)
            val caret = positionAt(sourceBefore.substring(0, startOffset) + wrapped, startOffset + wrapped.length)
            editor.setSelection(caret.first, caret.second)
        } else {
            val startOffset = cursor.left.coerceIn(0, sourceBefore.length)
            val line = cursor.leftLine
            val col = cursor.leftColumn
            text.insert(line, col, symbol)
            val caretOffset = if (symbol.endsWith("()") && symbol.length > 2) {
                startOffset + symbol.length - 1
            } else {
                startOffset + symbol.length
            }
            val caret = positionAt(sourceBefore.substring(0, startOffset) + symbol, caretOffset)
            editor.setSelection(caret.first, caret.second)
        }
        (editor.tag as? EditorBinding)?.recordEmission(editor.text.toString())
        editor.requestFocus()
    }

    /** Replaces the identifier immediately before the caret with a selected completion. */
    fun replaceCurrentIdentifier(completion: String) {
        val editor = editorRef?.get() ?: return
        val text = editor.text
        val source = text.toString()
        val cursor = editor.cursor
        val startOffset: Int
        val endOffset: Int
        if (cursor.isSelected) {
            startOffset = cursor.left.coerceIn(0, source.length)
            endOffset = cursor.right.coerceIn(startOffset, source.length)
        } else {
            endOffset = cursor.left.coerceIn(0, source.length)
            var identifierStart = endOffset
            while (identifierStart > 0 && isIdentifierCharacter(source[identifierStart - 1])) identifierStart--
            startOffset = identifierStart
        }

        val start = positionAt(source, startOffset)
        val end = positionAt(source, endOffset)
        text.delete(start.first, start.second, end.first, end.second)
        text.insert(start.first, start.second, completion)
        val caretOffset = if (completion.endsWith("()") && completion.length > 2) {
            startOffset + completion.length - 1
        } else {
            startOffset + completion.length
        }
        val caret = positionAt(source.substring(0, startOffset) + completion, caretOffset)
        editor.setSelection(caret.first, caret.second)
        (editor.tag as? EditorBinding)?.recordEmission(editor.text.toString())
        editor.requestFocus()
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
            (editor.tag as? EditorBinding)?.recordEmission(editor.text.toString())
        }
    }

    /**
     * Performs redo in the editor text engine.
     */
    fun redo() {
        val editor = editorRef?.get() ?: return
        if (editor.canRedo()) {
            editor.redo()
            (editor.tag as? EditorBinding)?.recordEmission(editor.text.toString())
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

    private fun isIdentifierCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()

    private fun positionAt(content: String, offset: Int): Pair<Int, Int> {
        val safeOffset = offset.coerceIn(0, content.length)
        val prefix = content.substring(0, safeOffset)
        val line = prefix.count { it == '\n' }
        val column = prefix.length - (prefix.lastIndexOf('\n') + 1)
        return line to column
    }
}
