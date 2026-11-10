package com.vibespace.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.theme.VibeTheme

enum class SplitOrientation {
    HORIZONTAL, // Side-by-side (Left / Right)
    VERTICAL    // Stacked (Top / Bottom)
}

/**
 * Resizable split view for Tablets, Foldables, and Samsung DeX desktop mode.
 * Supports horizontal (side-by-side) and vertical (stacked) orientations,
 * interactive drag handle, and double-tap to reset to 50/50.
 */
@Composable
fun VibeSplitLayout(
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    orientation: SplitOrientation,
    modifier: Modifier = Modifier,
    firstContent: @Composable () -> Unit,
    secondContent: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        when (orientation) {
            SplitOrientation.HORIZONTAL -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Pane
                    Box(
                        modifier = Modifier
                            .weight(splitRatio.coerceIn(0.15f, 0.85f))
                            .fillMaxHeight()
                    ) {
                        firstContent()
                    }

                    // Draggable Vertical Divider Handle
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight()
                            .background(
                                if (isDragging) VibeTheme.colors.accentPrimary.copy(alpha = 0.25f)
                                else VibeTheme.colors.gutterBackground
                            )
                            .pointerInput(widthPx) {
                                detectTapGestures(
                                    onDoubleTap = { onSplitRatioChange(0.5f) }
                                )
                            }
                            .pointerInput(widthPx) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaRatio = dragAmount.x / widthPx
                                        val newRatio = (splitRatio + deltaRatio).coerceIn(0.15f, 0.85f)
                                        onSplitRatioChange(newRatio)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Visual grab pill indicator
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .background(
                                    if (isDragging) VibeTheme.colors.accentPrimary
                                    else VibeTheme.colors.borderSubtle,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }

                    // Right Pane
                    Box(
                        modifier = Modifier
                            .weight((1f - splitRatio).coerceIn(0.15f, 0.85f))
                            .fillMaxHeight()
                    ) {
                        secondContent()
                    }
                }
            }
            SplitOrientation.VERTICAL -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Pane
                    Box(
                        modifier = Modifier
                            .weight(splitRatio.coerceIn(0.15f, 0.85f))
                            .fillMaxWidth()
                    ) {
                        firstContent()
                    }

                    // Draggable Horizontal Divider Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(
                                if (isDragging) VibeTheme.colors.accentPrimary.copy(alpha = 0.25f)
                                else VibeTheme.colors.gutterBackground
                            )
                            .pointerInput(heightPx) {
                                detectTapGestures(
                                    onDoubleTap = { onSplitRatioChange(0.55f) }
                                )
                            }
                            .pointerInput(heightPx) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaRatio = dragAmount.y / heightPx
                                        val newRatio = (splitRatio + deltaRatio).coerceIn(0.15f, 0.85f)
                                        onSplitRatioChange(newRatio)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Visual grab pill indicator
                        Box(
                            modifier = Modifier
                                .height(3.dp)
                                .width(36.dp)
                                .background(
                                    if (isDragging) VibeTheme.colors.accentPrimary
                                    else VibeTheme.colors.borderSubtle,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }

                    // Bottom Pane
                    Box(
                        modifier = Modifier
                            .weight((1f - splitRatio).coerceIn(0.15f, 0.85f))
                            .fillMaxWidth()
                    ) {
                        secondContent()
                    }
                }
            }
        }
    }
}
