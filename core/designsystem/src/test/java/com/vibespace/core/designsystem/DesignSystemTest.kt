package com.vibespace.core.designsystem

import androidx.compose.ui.graphics.Color
import com.vibespace.core.designsystem.theme.VibeCyan
import com.vibespace.core.designsystem.theme.VibeDarkBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesignSystemTest {

    @Test
    fun colors_are_distinct_and_defined() {
        assertEquals(Color(0xFF00E5FF), VibeCyan)
        assertNotEquals(VibeDarkBackground, VibeCyan)
    }
}
