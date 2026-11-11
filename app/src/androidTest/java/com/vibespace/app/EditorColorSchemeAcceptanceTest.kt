package com.vibespace.app

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vibespace.core.editor.sora.VibeEditorColorScheme
import com.vibespace.core.editor.theme.EditorThemeDefinition
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Sora's Android-backed color scheme on-device, including its superclass constructor. */
@RunWith(AndroidJUnit4::class)
class EditorColorSchemeAcceptanceTest {

    @Test
    fun defaultThemeIsAppliedAfterSuperclassInitialization() {
        val scheme = VibeEditorColorScheme()

        assertEquals(Color.parseColor(EditorThemeDefinition.VIBE_DARK.backgroundColor), scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND))
        assertEquals(Color.parseColor(EditorThemeDefinition.VIBE_DARK.keywordColor), scheme.getColor(EditorColorScheme.KEYWORD))
    }

    @Test
    fun customVsCodeThemeOverridesTheTemporaryDefaultTheme() {
        val customTheme = EditorThemeDefinition(
            id = "acceptance.custom",
            name = "Acceptance Custom",
            backgroundColor = "#123456",
            gutterColor = "#234567",
            keywordColor = "#345678",
            functionColor = "#456789"
        )

        val scheme = VibeEditorColorScheme(customTheme)

        assertEquals(Color.parseColor("#123456"), scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND))
        assertEquals(Color.parseColor("#234567"), scheme.getColor(EditorColorScheme.LINE_NUMBER_BACKGROUND))
        assertEquals(Color.parseColor("#345678"), scheme.getColor(EditorColorScheme.KEYWORD))
        assertEquals(Color.parseColor("#456789"), scheme.getColor(EditorColorScheme.FUNCTION_NAME))
    }

    @Test
    fun creatingSchemesForDifferentPresetsKeepsEachConfigurationIndependent() {
        val dracula = VibeEditorColorScheme(EditorThemeDefinition.DRACULA)
        val nord = VibeEditorColorScheme(EditorThemeDefinition.NORD)
        val invalidCustom = VibeEditorColorScheme(
            EditorThemeDefinition(id = "acceptance.invalid", name = "Invalid", backgroundColor = "not-a-color")
        )

        assertEquals(Color.parseColor(EditorThemeDefinition.DRACULA.backgroundColor), dracula.getColor(EditorColorScheme.WHOLE_BACKGROUND))
        assertEquals(Color.parseColor(EditorThemeDefinition.NORD.backgroundColor), nord.getColor(EditorColorScheme.WHOLE_BACKGROUND))
        assertEquals(Color.parseColor("#0F1115"), invalidCustom.getColor(EditorColorScheme.WHOLE_BACKGROUND))
    }
}
