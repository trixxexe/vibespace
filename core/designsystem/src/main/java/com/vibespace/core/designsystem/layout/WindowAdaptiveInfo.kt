package com.vibespace.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthSizeClass {
    Compact, // Phones in portrait (< 600dp)
    Medium,  // Small tablets, phones in landscape (600dp - 840dp)
    Expanded // Large tablets, desktop/DeX (> 840dp)
}

data class VibeAdaptiveInfo(
    val widthSizeClass: WindowWidthSizeClass,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val isTabletOrExpanded: Boolean
)

@Composable
fun rememberVibeAdaptiveInfo(): VibeAdaptiveInfo {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp

    val widthSizeClass = when {
        widthDp < 600.dp -> WindowWidthSizeClass.Compact
        widthDp < 840.dp -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }

    return VibeAdaptiveInfo(
        widthSizeClass = widthSizeClass,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        isTabletOrExpanded = widthSizeClass != WindowWidthSizeClass.Compact
    )
}
