package com.vibespace.core.editor

import com.vibespace.core.common.dispatchers.TestDispatcherProvider
import com.vibespace.core.editor.engine.DefaultEditorEngine
import com.vibespace.core.language.model.LanguageDefinition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEditorEngineTest {

    private val engine = DefaultEditorEngine(dispatchers = TestDispatcherProvider())

    @Test
    fun openDocument_creates_document_and_sets_active() = runTest {
        val openResult = engine.openDocument(
            uri = "vibe://projects/demo/Main.kt",
            fileName = "Main.kt",
            content = "println(1)",
            language = LanguageDefinition.Kotlin
        )

        assertTrue(openResult.isSuccess)
        val doc = openResult.getOrNull()
        assertNotNull(doc)
        assertEquals("Main.kt", doc!!.fileName)
        assertFalse(doc.isDirty)
        assertEquals(doc, engine.activeDocument.value)
        assertEquals(1, engine.documents.value.size)
    }

    @Test
    fun updateDocument_marks_dirty_and_supports_undo_redo() = runTest {
        val doc = engine.openDocument(
            uri = "vibe://projects/demo/test.py",
            fileName = "test.py",
            content = "x = 1",
            language = LanguageDefinition.Python
        ).getOrNull()!!

        // Update content
        val updatedResult = engine.updateDocument(doc.documentId, "x = 2")
        assertTrue(updatedResult.isSuccess)
        val updated = updatedResult.getOrNull()!!
        assertTrue(updated.isDirty)
        assertEquals("x = 2", updated.content)

        // Undo
        val undoResult = engine.undo(doc.documentId)
        assertTrue(undoResult.isSuccess)
        assertEquals("x = 1", undoResult.getOrNull()!!.content)

        // Redo
        val redoResult = engine.redo(doc.documentId)
        assertTrue(redoResult.isSuccess)
        assertEquals("x = 2", redoResult.getOrNull()!!.content)
    }

    @Test
    fun closeDocument_removes_from_list_and_updates_active() = runTest {
        val doc1 = engine.openDocument("uri1", "1.kt", "1", LanguageDefinition.Kotlin).getOrNull()!!
        val doc2 = engine.openDocument("uri2", "2.kt", "2", LanguageDefinition.Kotlin).getOrNull()!!

        assertEquals(doc2, engine.activeDocument.value)

        engine.closeDocument(doc2.documentId)
        assertEquals(1, engine.documents.value.size)
        assertEquals(doc1.documentId, engine.activeDocument.value?.documentId)

        engine.closeDocument(doc1.documentId)
        assertTrue(engine.documents.value.isEmpty())
        assertNull(engine.activeDocument.value)
    }

    @Test
    fun find_and_replaceAll_works_accurately() = runTest {
        val doc = engine.openDocument("uri3", "3.txt", "foo bar foo", LanguageDefinition.PlainText).getOrNull()!!
        val matches = engine.find(doc.documentId, "foo")
        assertEquals(2, matches.size)

        val replaced = engine.replaceAll(doc.documentId, "foo", "baz").getOrNull()!!
        assertEquals("baz bar baz", replaced.content)
    }
}
