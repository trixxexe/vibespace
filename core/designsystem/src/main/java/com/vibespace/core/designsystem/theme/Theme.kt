package com.vibespace.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VibeCyan,
    onPrimary = Color.Black,
    primaryContainer = VibeCyanDark,
    onPrimaryContainer = Color.White,
    secondary = VibeIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color.White,
    background = VibeDarkBackground,
    onBackground = VibeDarkTextPrimary,
    surface = VibeDarkSurface,
    onSurface = VibeDarkTextPrimary,
    surfaceVariant = VibeDarkSurfaceElevated,
    onSurfaceVariant = VibeDarkTextSecondary,
    outline = VibeDarkBorder,
    outlineVariant = VibeDarkBorderFocused,
    error = VibeRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = VibeCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF006064),
    secondary = VibeIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    background = VibeLightBackground,
    onBackground = VibeLightTextPrimary,
    surface = VibeLightSurface,
    onSurface = VibeLightTextPrimary,
    surfaceVariant = VibeLightSurfaceElevated,
    onSurfaceVariant = VibeLightTextSecondary,
    outline = VibeLightBorder,
    outlineVariant = VibeLightBorderFocused,
    error = VibeRed,
    onError = Color.White
)

private val DarkExtendedColors = VibeExtendedColors(
    editorBackground = VibeDarkEditorBackground,
    gutterBackground = VibeDarkGutterBackground,
    activeLineBackground = Color(0xFF1E2129),
    panelBackground = Color(0xFF13151A),
    borderSubtle = VibeDarkBorder,
    borderFocused = VibeDarkBorderFocused,
    textPrimary = VibeDarkTextPrimary,
    textSecondary = VibeDarkTextSecondary,
    textMuted = VibeDarkTextMuted,
    accentPrimary = VibeCyan,
    accentSecondary = VibeIndigoLight,
    statusSuccess = VibeGreen,
    statusWarning = VibeAmber,
    statusError = VibeRed,
    syntaxKeyword = SyntaxKeyword,
    syntaxFunction = SyntaxFunction,
    syntaxString = SyntaxString,
    syntaxComment = SyntaxComment
)

private val LightExtendedColors = VibeExtendedColors(
    editorBackground = VibeLightEditorBackground,
    gutterBackground = VibeLightGutterBackground,
    activeLineBackground = Color(0xFFF3F4F6),
    panelBackground = Color(0xFFFAFAFA),
    borderSubtle = VibeLightBorder,
    borderFocused = VibeLightBorderFocused,
    textPrimary = VibeLightTextPrimary,
    textSecondary = VibeLightTextSecondary,
    textMuted = VibeLightTextMuted,
    accentPrimary = VibeCyanDark,
    accentSecondary = VibeIndigo,
    statusSuccess = Color(0xFF059669),
    statusWarning = Color(0xFFD97706),
    statusError = VibeRed,
    syntaxKeyword = Color(0xFFCF222E),
    syntaxFunction = Color(0xFF8250DF),
    syntaxString = Color(0xFF0A3069),
    syntaxComment = Color(0xFF6E7781)
)

val LocalVibeColors = staticCompositionLocalOf { DarkExtendedColors }
val LocalVibeSpacing = staticCompositionLocalOf { VibeSpacing() }

object VibeTheme {
    val colors: VibeExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalVibeColors.current

    val spacing: VibeSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalVibeSpacing.current
}

@Composable
fun VibeSpaceTheme(
    darkTheme: Boolean = true, // Dark by default as required
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val spacing = VibeSpacing()

    CompositionLocalProvider(
        LocalVibeColors provides extendedColors,
        LocalVibeSpacing provides spacing
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VibeTypography,
            shapes = VibeShapes,
            content = content
        )
    }
}
