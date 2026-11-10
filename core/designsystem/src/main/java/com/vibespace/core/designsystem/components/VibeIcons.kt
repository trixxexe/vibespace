package com.vibespace.core.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bespoke VibeSpace vector icon family.
 * Built with consistent 24x24 optical sizing, 1.8dp developer stroke weight,
 * crisp corners, and authentic developer IDE identity.
 * Zero reliance on generic platform defaults or emojis.
 */
object VibeIcons {

    private fun strokeIcon(
        name: String,
        strokeWidth: Float = 1.8f,
        block: PathBuilder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = "VibeIcon.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }.build()
    }

    private fun fillIcon(
        name: String,
        block: PathBuilder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = "VibeIcon.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathBuilder = block
            )
        }.build()
    }

    // --- Core Navigation & Identity ---
    val Logo: ImageVector = strokeIcon("Logo", 2f) {
        moveTo(7f, 6f); lineTo(3f, 12f); lineTo(7f, 18f)
        moveTo(14f, 4f); lineTo(10f, 20f)
        moveTo(17f, 6f); lineTo(21f, 12f); lineTo(17f, 18f)
    }

    val Home: ImageVector = strokeIcon("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
        lineTo(19f, 10f); lineTo(19f, 21f); lineTo(5f, 21f); lineTo(5f, 10f); close()
        moveTo(9f, 21f); lineTo(9f, 14f); lineTo(15f, 14f); lineTo(15f, 21f)
    }

    val Editor: ImageVector = strokeIcon("Editor") {
        moveTo(8f, 7f); lineTo(3f, 12f); lineTo(8f, 17f)
        moveTo(16f, 7f); lineTo(21f, 12f); lineTo(16f, 17f)
        moveTo(14f, 5f); lineTo(10f, 19f)
    }

    val Explorer: ImageVector = strokeIcon("Explorer") {
        moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f)
        lineTo(21f, 19f); lineTo(3f, 19f); close()
    }

    val FolderOpen: ImageVector = strokeIcon("FolderOpen") {
        moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(20f, 8f); lineTo(20f, 11f)
        moveTo(2f, 20f); lineTo(5f, 11f); lineTo(22f, 11f); lineTo(19f, 20f); close()
    }

    val File: ImageVector = strokeIcon("File") {
        moveTo(4f, 4f); lineTo(14f, 4f); lineTo(20f, 10f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(14f, 4f); lineTo(14f, 10f); lineTo(20f, 10f)
        moveTo(8f, 14f); lineTo(16f, 14f)
        moveTo(8f, 17f); lineTo(13f, 17f)
    }

    val NewFolder: ImageVector = strokeIcon("NewFolder") {
        moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(12f, 11f); lineTo(12f, 17f)
        moveTo(9f, 14f); lineTo(15f, 14f)
    }

    val Terminal: ImageVector = strokeIcon("Terminal") {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f)
        moveTo(12f, 15f); lineTo(17f, 15f)
    }

    val TerminalAgent: ImageVector = strokeIcon("TerminalAgent") {
        moveTo(12f, 2f); lineTo(12f, 5f)
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        moveTo(8f, 10f); lineTo(10f, 10f)
        moveTo(14f, 10f); lineTo(16f, 10f)
        moveTo(9f, 15f); lineTo(15f, 15f)
        moveTo(2f, 11f); lineTo(4f, 11f)
        moveTo(20f, 11f); lineTo(22f, 11f)
    }

    val AI: ImageVector = fillIcon("AI") {
        moveTo(12f, 2f)
        cubicTo(12f, 7.5f, 16.5f, 12f, 22f, 12f)
        cubicTo(16.5f, 12f, 12f, 16.5f, 12f, 22f)
        cubicTo(12f, 16.5f, 7.5f, 12f, 2f, 12f)
        cubicTo(7.5f, 12f, 12f, 7.5f, 12f, 2f)
        close()
    }

    val Search: ImageVector = strokeIcon("Search") {
        moveTo(16f, 10.5f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5f, 10.5f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16f, 10.5f)
        close()
        moveTo(14.5f, 14.5f); lineTo(20f, 20f)
    }

    val Settings: ImageVector = strokeIcon("Settings") {
        moveTo(12f, 15f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 9f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 15f)
        close()
        moveTo(12f, 2f); lineTo(12f, 4f)
        moveTo(12f, 20f); lineTo(12f, 22f)
        moveTo(4f, 12f); lineTo(2f, 12f)
        moveTo(22f, 12f); lineTo(20f, 12f)
        moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
        moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
        moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
        moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
    }

    // --- Actions ---
    val Run: ImageVector = fillIcon("Run") {
        moveTo(7f, 5f); lineTo(19f, 12f); lineTo(7f, 19f); close()
    }

    val Save: ImageVector = strokeIcon("Save") {
        moveTo(4f, 4f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(7f, 4f); lineTo(7f, 9f); lineTo(15f, 9f); lineTo(15f, 4f)
        moveTo(7f, 14f); lineTo(17f, 14f); lineTo(17f, 20f); lineTo(7f, 20f); close()
    }

    val Undo: ImageVector = strokeIcon("Undo") {
        moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
        moveTo(4f, 9f); lineTo(14f, 9f)
        arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 15f)
        arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 21f)
        lineTo(11f, 21f)
    }

    val Redo: ImageVector = strokeIcon("Redo") {
        moveTo(15f, 14f); lineTo(20f, 9f); lineTo(15f, 4f)
        moveTo(20f, 9f); lineTo(10f, 9f)
        arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 15f)
        arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10f, 21f)
        lineTo(13f, 21f)
    }

    val Back: ImageVector = strokeIcon("Back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    val Add: ImageVector = strokeIcon("Add") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Delete: ImageVector = strokeIcon("Delete") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(10f, 11f); lineTo(10f, 17f)
        moveTo(14f, 11f); lineTo(14f, 17f)
        moveTo(5f, 7f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(19f, 7f)
        moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
    }

    val Rename: ImageVector = strokeIcon("Rename") {
        moveTo(17f, 3f); lineTo(21f, 7f); lineTo(7f, 21f); lineTo(3f, 21f); lineTo(3f, 17f); close()
        moveTo(14f, 6f); lineTo(18f, 10f)
    }

    val Refresh: ImageVector = strokeIcon("Refresh") {
        moveTo(20f, 4f); lineTo(20f, 9f); lineTo(15f, 9f)
        moveTo(4f, 20f); lineTo(4f, 15f); lineTo(9f, 15f)
        moveTo(20f, 9f)
        arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6.34f, 6.34f)
        moveTo(4f, 15f)
        arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.66f, 17.66f)
    }

    val More: ImageVector = fillIcon("More") {
        moveTo(12f, 6f); arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 3f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 6f); close()
        moveTo(12f, 13.5f); arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 10.5f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 13.5f); close()
        moveTo(12f, 21f); arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 18f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 21f); close()
    }

    val ArrowDown: ImageVector = strokeIcon("ArrowDown") {
        moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
    }

    val ArrowRight: ImageVector = strokeIcon("ArrowRight") {
        moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
    }

    val Send: ImageVector = strokeIcon("Send") {
        moveTo(22f, 2f); lineTo(11f, 13f)
        moveTo(22f, 2f); lineTo(15f, 22f); lineTo(11f, 13f); lineTo(2f, 9f); close()
    }

    val Close: ImageVector = strokeIcon("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Checkmark: ImageVector = strokeIcon("Checkmark", 2f) {
        moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f)
    }

    val Warning: ImageVector = strokeIcon("Warning") {
        moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close()
        moveTo(12f, 9f); lineTo(12f, 13f)
        moveTo(12f, 16.5f); lineTo(12f, 17f)
    }

    val Error: ImageVector = strokeIcon("Error") {
        moveTo(21f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
        close()
        moveTo(9f, 9f); lineTo(15f, 15f)
        moveTo(15f, 9f); lineTo(9f, 15f)
    }

    // --- Git & Source Control ---
    val SourceControl: ImageVector = strokeIcon("SourceControl") {
        moveTo(6f, 4f); arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 8f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 4f); close()
        moveTo(6f, 16f); arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 20f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 16f); close()
        moveTo(18f, 8f); arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 12f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 8f); close()
        moveTo(6f, 8f); lineTo(6f, 16f)
        moveTo(6f, 12f); arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 10f); lineTo(16f, 10f)
    }

    val Commit: ImageVector = strokeIcon("Commit") {
        moveTo(12f, 8f)
        arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 16f)
        arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 8f)
        close()
        moveTo(12f, 2f); lineTo(12f, 8f)
        moveTo(12f, 16f); lineTo(12f, 22f)
    }

    val Branch: ImageVector = strokeIcon("Branch") {
        moveTo(6f, 6f); lineTo(6f, 18f)
        moveTo(6f, 12f); lineTo(12f, 8f); lineTo(18f, 8f)
    }

    // --- Execution Runners & Package ---
    val RunnerInstant: ImageVector = fillIcon("RunnerInstant") {
        moveTo(13f, 2f); lineTo(5f, 13f); lineTo(11f, 13f)
        lineTo(9f, 22f); lineTo(19f, 10f); lineTo(13f, 10f); close()
    }

    val RunnerAlpine: ImageVector = strokeIcon("RunnerAlpine") {
        moveTo(2f, 18f); lineTo(8f, 6f); lineTo(13f, 15f); lineTo(16f, 10f); lineTo(22f, 18f); close()
        moveTo(7f, 13f); lineTo(10f, 13f)
    }

    val RunnerTermux: ImageVector = strokeIcon("RunnerTermux") {
        moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        moveTo(7f, 8f); lineTo(11f, 12f); lineTo(7f, 16f)
        moveTo(13f, 16f); lineTo(17f, 16f)
    }

    val Package: ImageVector = strokeIcon("Package") {
        moveTo(12f, 2f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(12f, 22f); lineTo(3f, 17f); lineTo(3f, 7f); close()
        moveTo(3f, 7f); lineTo(12f, 12f); lineTo(21f, 7f)
        moveTo(12f, 12f); lineTo(12f, 22f)
    }

    val Extension: ImageVector = strokeIcon("Extension") {
        moveTo(19f, 13f); lineTo(19f, 19f); lineTo(13f, 19f)
        moveTo(13f, 19f); arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11f, 19f)
        lineTo(5f, 19f); lineTo(5f, 13f)
        moveTo(5f, 13f); arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 11f)
        lineTo(5f, 5f); lineTo(11f, 5f)
        moveTo(11f, 5f); arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13f, 5f)
        lineTo(19f, 5f); lineTo(19f, 11f)
        moveTo(19f, 11f); arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19f, 13f)
    }

    val SplitHorizontal: ImageVector = strokeIcon("SplitHorizontal") {
        moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        moveTo(12f, 4f); lineTo(12f, 20f)
    }

    val SplitVertical: ImageVector = strokeIcon("SplitVertical") {
        moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        moveTo(3f, 12f); lineTo(21f, 12f)
    }

    val Snippet: ImageVector = strokeIcon("Snippet") {
        moveTo(13f, 2f); lineTo(7f, 12f); lineTo(12f, 12f)
        lineTo(9f, 22f); lineTo(17f, 10f); lineTo(12f, 10f); close()
    }

    val CommandPalette: ImageVector = strokeIcon("CommandPalette") {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(7f, 10f); lineTo(10f, 12f); lineTo(7f, 14f)
        moveTo(12f, 14f); lineTo(16f, 14f)
    }
}
