package com.vibespace.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

enum class VibeButtonStyle {
    Primary,
    Secondary,
    Outline,
    Ghost,
    Danger
}

@Composable
fun VibeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VibeButtonStyle = VibeButtonStyle.Primary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val shape = VibeShapes.small
    val minTouchModifier = modifier.defaultMinSize(minHeight = 44.dp)

    when (style) {
        VibeButtonStyle.Primary -> {
            Button(
                onClick = onClick,
                modifier = minTouchModifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                ),
                contentPadding = contentPadding,
                content = content
            )
        }
        VibeButtonStyle.Secondary -> {
            Button(
                onClick = onClick,
                modifier = minTouchModifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = VibeTheme.colors.textPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    disabledContentColor = VibeTheme.colors.textMuted
                ),
                contentPadding = contentPadding,
                content = content
            )
        }
        VibeButtonStyle.Outline -> {
            OutlinedButton(
                onClick = onClick,
                modifier = minTouchModifier,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VibeTheme.colors.textPrimary
                ),
                contentPadding = contentPadding,
                content = content
            )
        }
        VibeButtonStyle.Ghost -> {
            TextButton(
                onClick = onClick,
                modifier = minTouchModifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = VibeTheme.colors.textPrimary
                ),
                contentPadding = contentPadding,
                content = content
            )
        }
        VibeButtonStyle.Danger -> {
            Button(
                onClick = onClick,
                modifier = minTouchModifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                contentPadding = contentPadding,
                content = content
            )
        }
    }
}
