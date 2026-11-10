package com.vibespace.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme

/**
 * High-precision, touch-ergonomic virtual accessory bar anchored above the soft keyboard.
 * Eliminates mobile misclicks by providing dedicated, generous tap targets for coding symbols.
 */
@Composable
fun MobileSymbolBar(
    onInsertSymbol: (String) -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val primarySymbols = listOf(
        "TAB" to "    ",
        "{" to "{",
        "}" to "}",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        "<" to "<",
        ">" to ">",
        "\"" to "\"",
        "'" to "'",
        ":" to ":",
        ";" to ";",
        "=" to "=",
        "." to ".",
        "," to ",",
        "_" to "_",
        "+" to "+",
        "-" to "-",
        "*" to "*",
        "/" to "/",
        "\\" to "\\",
        "|" to "|",
        "&" to "&",
        "!" to "!",
        "?" to "?",
        "#" to "#",
        "$" to "$"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(VibeTheme.colors.panelBackground)
            .border(width = 0.5.dp, color = VibeTheme.colors.borderSubtle)
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo / Redo controls
        SymbolButton(
            label = "↶",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onUndo()
            }
        )
        SymbolButton(
            label = "↷",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRedo()
            }
        )

        // Arrow navigation
        SymbolButton(
            label = "◀",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMoveCursorLeft()
            }
        )
        SymbolButton(
            label = "▶",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMoveCursorRight()
            }
        )

        // Code symbols
        primarySymbols.forEach { (display, insertText) ->
            SymbolButton(
                label = display,
                isSpecial = display == "TAB",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onInsertSymbol(insertText)
                }
            )
        }
    }
}

@Composable
private fun SymbolButton(
    label: String,
    isSpecial: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .height(38.dp)
            .widthIn(min = if (isSpecial) 48.dp else 36.dp)
            .clip(shape)
            .background(if (isSpecial) VibeTheme.colors.activeLineBackground else VibeTheme.colors.panelBackground)
            .border(width = 0.5.dp, color = VibeTheme.colors.borderSubtle, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = VibeCodeTypography.copy(
                fontSize = if (isSpecial) 12.sp else 15.sp,
                fontWeight = if (isSpecial) FontWeight.Bold else FontWeight.Medium,
                color = if (isSpecial) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary
            )
        )
    }
}
