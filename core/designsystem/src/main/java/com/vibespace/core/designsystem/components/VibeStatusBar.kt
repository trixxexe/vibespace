package com.vibespace.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun VibeStatusBar(
    line: Int = 1,
    column: Int = 1,
    language: String = "Plain Text",
    encoding: String = "UTF-8",
    indentation: String = "Spaces: 4",
    statusMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(VibeTheme.colors.gutterBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: status or branch
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusMessage ?: "VibeSpace Ready",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textSecondary
            )
        }

        // Right side: Ln, Col, Spaces, UTF-8, Language
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ln $line, Col $column",
                style = VibeCodeTypography.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                color = VibeTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = indentation,
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = encoding,
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textSecondary
            )
        }
    }
}
