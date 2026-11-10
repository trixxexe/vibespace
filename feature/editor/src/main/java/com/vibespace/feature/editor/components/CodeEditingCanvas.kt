package com.vibespace.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.editor.model.CursorPosition

@Composable
fun CodeEditingCanvas(
    content: String,
    onContentChange: (String) -> Unit,
    onCursorChange: (CursorPosition) -> Unit,
    fontSizeSp: Float = 13f,
    modifier: Modifier = Modifier
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val lines = content.lines()
    val lineCount = lines.size.coerceAtLeast(1)
    val gutterWidth = (lineCount.toString().length * 10 + 24).dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(VibeTheme.colors.editorBackground)
            .verticalScroll(verticalScroll)
    ) {
        // Line Numbers Gutter
        Box(
            modifier = Modifier
                .width(gutterWidth)
                .fillMaxHeight()
                .background(VibeTheme.colors.gutterBackground)
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            val gutterText = (1..lineCount).joinToString("\n")
            Text(
                text = gutterText,
                style = VibeCodeTypography.copy(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp,
                    color = VibeTheme.colors.textMuted,
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.width(gutterWidth - 12.dp)
            )
        }

        // Editable Code Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScroll)
                .padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = { newContent ->
                    onContentChange(newContent)
                },
                textStyle = VibeCodeTypography.copy(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp,
                    color = VibeTheme.colors.textPrimary
                ),
                cursorBrush = SolidColor(VibeTheme.colors.accentPrimary),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
