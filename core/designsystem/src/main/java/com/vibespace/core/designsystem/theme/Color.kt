package com.vibespace.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Primary Brand Colors - Deep modern cyber palette
val VibeCyan = Color(0xFF00E5FF)
val VibeCyanDark = Color(0xFF0097A7)
val VibeIndigo = Color(0xFF6366F1)
val VibeIndigoLight = Color(0xFF818CF8)
val VibeAmber = Color(0xFFF59E0B)
val VibeGreen = Color(0xFF10B981)
val VibeRed = Color(0xFFEF4444)

// Dark Theme Surfaces - Deep Charcoal Palette
val VibeDarkBackground = Color(0xFF0F1115)
val VibeDarkSurface = Color(0xFF16181D)
val VibeDarkSurfaceElevated = Color(0xFF1E2128)
val VibeDarkEditorBackground = Color(0xFF12141A)
val VibeDarkGutterBackground = Color(0xFF0D0F13)
val VibeDarkBorder = Color(0xFF272A34)
val VibeDarkBorderFocused = Color(0xFF3E4352)
val VibeDarkTextPrimary = Color(0xFFF3F4F6)
val VibeDarkTextSecondary = Color(0xFF9CA3AF)
val VibeDarkTextMuted = Color(0xFF6B7280)

// Light Theme Surfaces
val VibeLightBackground = Color(0xFFF8F9FA)
val VibeLightSurface = Color(0xFFFFFFFF)
val VibeLightSurfaceElevated = Color(0xFFF1F3F5)
val VibeLightEditorBackground = Color(0xFFFFFFFF)
val VibeLightGutterBackground = Color(0xFFF1F3F5)
val VibeLightBorder = Color(0xFFE5E7EB)
val VibeLightBorderFocused = Color(0xFF9CA3AF)
val VibeLightTextPrimary = Color(0xFF111827)
val VibeLightTextSecondary = Color(0xFF4B5563)
val VibeLightTextMuted = Color(0xFF9CA3AF)

// Syntax Token Highlights
val SyntaxKeyword = Color(0xFFFF7B72)
val SyntaxFunction = Color(0xFFD2A8FF)
val SyntaxString = Color(0xFFA5D6FF)
val SyntaxComment = Color(0xFF8B949E)
val SyntaxType = Color(0xFFFFA657)
val SyntaxNumber = Color(0xFF79C0FF)

@Immutable
data class VibeExtendedColors(
    val editorBackground: Color,
    val gutterBackground: Color,
    val activeLineBackground: Color,
    val panelBackground: Color,
    val borderSubtle: Color,
    val borderFocused: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val syntaxKeyword: Color,
    val syntaxFunction: Color,
    val syntaxString: Color,
    val syntaxComment: Color
)
