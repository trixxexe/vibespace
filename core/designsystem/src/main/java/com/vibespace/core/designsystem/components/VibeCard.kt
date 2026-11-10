package com.vibespace.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun VibeCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = VibeTheme.colors.panelBackground,
    borderColor: Color = VibeTheme.colors.borderSubtle,
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = VibeShapes.medium,
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
