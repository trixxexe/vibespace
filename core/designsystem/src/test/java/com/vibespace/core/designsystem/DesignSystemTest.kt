package com.vibespace.core.designsystem

import androidx.compose.ui.graphics.Color
import com.vibespace.core.designsystem.theme.VibeCyan
import com.vibespace.core.designsystem.theme.VibeDarkBackground
import com.vibespace.core.designsystem.components.SplitOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesignSystemTest {

    @Test
    fun colors_are_distinct_and_defined() {
        assertEquals(Color(0xFF00E5FF), VibeCyan)
        assertNotEquals(VibeDarkBackground, VibeCyan)
    }

    @Test
    fun splitOrientation_values_are_available() {
        assertEquals(2, SplitOrientation.values().size)
        assertEquals(SplitOrientation.HORIZONTAL, SplitOrientation.valueOf("HORIZONTAL"))
        assertEquals(SplitOrientation.VERTICAL, SplitOrientation.valueOf("VERTICAL"))
    }

    @Test
    fun vibeIcons_family_is_instantiated_and_valid() {
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Logo)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Home)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Editor)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Explorer)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.AI)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Terminal)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Search)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Settings)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Save)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Checkmark)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Warning)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.Error)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.SourceControl)
        org.junit.Assert.assertNotNull(com.vibespace.core.designsystem.components.VibeIcons.RunnerAlpine)
    }
}
