package com.vibespace.feature.editor

import com.vibespace.feature.editor.components.SoraEditorController
import org.junit.Assert.assertNotNull
import org.junit.Test

class SoraEditorControllerTest {

    @Test
    fun controller_initializes_and_safely_handles_unbound_calls() {
        val controller = SoraEditorController()

        // Verify unattached calls do not throw NullPointerException
        controller.insertSymbol("(")
        controller.moveCursorLeft()
        controller.moveCursorRight()
        controller.undo()
        controller.redo()
        controller.unbind()

        assertNotNull(controller)
    }
}
