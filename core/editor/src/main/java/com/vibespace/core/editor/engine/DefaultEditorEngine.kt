package com.vibespace.core.editor.engine

import com.vibespace.core.common.dispatchers.DefaultDispatcherProvider
import com.vibespace.core.common.dispatchers.DispatcherProvider
import com.vibespace.core.common.id.IdGenerator
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.editor.model.CursorPosition
import com.vibespace.core.editor.model.EditorDocument
import com.vibespace.core.language.model.LanguageDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultEditorEngine(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : EditorEngine {

    private val stateMutex = Mutex()

    private val _documents = MutableStateFlow<List<EditorDocument>>(emptyList())
    override val documents: StateFlow<List<EditorDocument>> = _documents.asStateFlow()

    private val _activeDocument = MutableStateFlow<EditorDocument?>(null)
    override val activeDocument: StateFlow<EditorDocument?> = _activeDocument.asStateFlow()

    // Undo / Redo stacks per documentId
    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val maxHistorySize = 50

    override suspend fun openDocument(
        uri: String,
        fileName: String,
        content: String,
        language: LanguageDefinition
    ): VibeResult<EditorDocument> = withContext(dispatchers.default) {
        stateMutex.withLock {
            // If document with this URI is already open, activate it
            val existing = _documents.value.firstOrNull { it.uri == uri }
            if (existing != null) {
                _activeDocument.value = existing
                return@withLock VibeResult.Success(existing)
            }

            val docId = IdGenerator.shortId("doc")
            val newDoc = EditorDocument(
                documentId = docId,
                uri = uri,
                fileName = fileName,
                content = content,
                language = language,
                isDirty = false
            )

            undoStacks[docId] = ArrayDeque()
            redoStacks[docId] = ArrayDeque()

            _documents.value = _documents.value + newDoc
            _activeDocument.value = newDoc
            VibeResult.Success(newDoc)
        }
    }

    override suspend fun closeDocument(documentId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val currentList = _documents.value
            val index = currentList.indexOfFirst { it.documentId == documentId }
            if (index == -1) {
                return@withLock VibeResult.Error("Document not found: $documentId")
            }

            undoStacks.remove(documentId)
            redoStacks.remove(documentId)

            val updated = currentList.filterNot { it.documentId == documentId }
            _documents.value = updated

            if (_activeDocument.value?.documentId == documentId) {
                _activeDocument.value = when {
                    updated.isEmpty() -> null
                    index < updated.size -> updated[index]
                    else -> updated.lastOrNull()
                }
            }

            VibeResult.Success(Unit)
        }
    }

    override suspend fun setActiveDocument(documentId: String): VibeResult<Unit> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val target = _documents.value.firstOrNull { it.documentId == documentId }
                ?: return@withLock VibeResult.Error("Document not found: $documentId")

            _activeDocument.value = target
            VibeResult.Success(Unit)
        }
    }

    override suspend fun updateDocument(
        documentId: String,
        newContent: String
    ): VibeResult<EditorDocument> = withContext(dispatchers.default) {
        stateMutex.withLock {
            updateDocumentLocked(documentId, newContent)
        }
    }

    private fun updateDocumentLocked(
        documentId: String,
        newContent: String
    ): VibeResult<EditorDocument> {
        val current = getDocument(documentId)
            ?: return VibeResult.Error("Document not found: $documentId")

        if (current.content == newContent) {
            return VibeResult.Success(current)
        }

        // Push current content to undo stack
        val undoStack = undoStacks.getOrPut(documentId) { ArrayDeque() }
        undoStack.addLast(current.content)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStacks[documentId]?.clear()

        val updated = current.copy(
            content = newContent,
            isDirty = true,
            version = current.version + 1,
            lineCount = newContent.lines().size.coerceAtLeast(1)
        )

        updateDocumentInList(updated)
        return VibeResult.Success(updated)
    }

    override suspend fun updateCursorPosition(
        documentId: String,
        cursorPosition: CursorPosition
    ): VibeResult<Unit> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val current = getDocument(documentId) ?: return@withLock VibeResult.Error("Document not found: $documentId")
            val updated = current.copy(cursorPosition = cursorPosition)
            updateDocumentInList(updated)
            VibeResult.Success(Unit)
        }
    }

    override suspend fun markSaved(documentId: String, contentAtSave: String?): VibeResult<Unit> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val current = getDocument(documentId) ?: return@withLock VibeResult.Error("Document not found: $documentId")
            if (contentAtSave != null && current.content != contentAtSave) {
                return@withLock VibeResult.Success(Unit)
            }
            val updated = current.copy(isDirty = false)
            updateDocumentInList(updated)
            VibeResult.Success(Unit)
        }
    }

    override fun getDocument(documentId: String): EditorDocument? {
        return _documents.value.firstOrNull { it.documentId == documentId }
    }

    override suspend fun undo(documentId: String): VibeResult<EditorDocument> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val current = getDocument(documentId) ?: return@withLock VibeResult.Error("Document not found: $documentId")
            val undoStack = undoStacks[documentId]
            if (undoStack.isNullOrEmpty()) {
                return@withLock VibeResult.Success(current)
            }

            val previousContent = undoStack.removeLast()
            redoStacks.getOrPut(documentId) { ArrayDeque() }.addLast(current.content)

            val updated = current.copy(
                content = previousContent,
                isDirty = true,
                version = current.version + 1,
                lineCount = previousContent.lines().size.coerceAtLeast(1)
            )

            updateDocumentInList(updated)
            VibeResult.Success(updated)
        }
    }

    override suspend fun redo(documentId: String): VibeResult<EditorDocument> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val current = getDocument(documentId) ?: return@withLock VibeResult.Error("Document not found: $documentId")
            val redoStack = redoStacks[documentId]
            if (redoStack.isNullOrEmpty()) {
                return@withLock VibeResult.Success(current)
            }

            val nextContent = redoStack.removeLast()
            undoStacks.getOrPut(documentId) { ArrayDeque() }.addLast(current.content)

            val updated = current.copy(
                content = nextContent,
                isDirty = true,
                version = current.version + 1,
                lineCount = nextContent.lines().size.coerceAtLeast(1)
            )

            updateDocumentInList(updated)
            VibeResult.Success(updated)
        }
    }

    override fun find(documentId: String, query: String, matchCase: Boolean): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val doc = getDocument(documentId) ?: return emptyList()
        val text = doc.content
        val results = mutableListOf<IntRange>()

        var startIndex = 0
        while (startIndex < text.length) {
            val index = text.indexOf(query, startIndex, ignoreCase = !matchCase)
            if (index == -1) break
            results.add(index until (index + query.length))
            startIndex = index + query.length
        }
        return results
    }

    override suspend fun replace(
        documentId: String,
        range: IntRange,
        replacement: String
    ): VibeResult<EditorDocument> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val doc = getDocument(documentId) ?: return@withLock VibeResult.Error("Document not found: $documentId")
            val text = doc.content
            if (range.first < 0 || range.last >= text.length) {
                return@withLock VibeResult.Error("Range out of bounds: $range")
            }

            val newContent = text.substring(0, range.first) + replacement + text.substring(range.last + 1)
            updateDocumentLocked(documentId, newContent)
        }
    }

    override suspend fun replaceAll(
        documentId: String,
        query: String,
        replacement: String,
        matchCase: Boolean
    ): VibeResult<EditorDocument> = withContext(dispatchers.default) {
        stateMutex.withLock {
            val doc = getDocument(documentId) ?: return@withLock VibeResult.Error("Document not found: $documentId")
            if (query.isEmpty()) return@withLock VibeResult.Success(doc)

            val newContent = doc.content.replace(query, replacement, ignoreCase = !matchCase)
            updateDocumentLocked(documentId, newContent)
        }
    }

    private fun updateDocumentInList(updated: EditorDocument) {
        _documents.value = _documents.value.map {
            if (it.documentId == updated.documentId) updated else it
        }
        if (_activeDocument.value?.documentId == updated.documentId) {
            _activeDocument.value = updated
        }
    }
}
