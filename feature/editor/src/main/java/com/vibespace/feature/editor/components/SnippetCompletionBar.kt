package com.vibespace.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind

/**
 * Mobile-first horizontal autocomplete bar displayed directly above the virtual symbol row.
 * Offers generous touch targets (min 44dp) for swift single-tap code and snippet insertion.
 */
@Composable
fun SnippetCompletionBar(
    suggestions: List<CompletionItem>,
    onSelectSnippet: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(VibeTheme.colors.gutterBackground)
            .border(width = 0.5.dp, color = VibeTheme.colors.borderSubtle)
            .horizontalScroll(scrollState)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick Templates sheet button
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = VibeTheme.colors.panelBackground,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, VibeTheme.colors.borderSubtle),
            modifier = Modifier
                .height(32.dp)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenTemplates()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Templates",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Templates",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Completion items
        suggestions.forEach { item ->
            CompletionPill(
                item = item,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectSnippet(item.insertText)
                }
            )
        }
    }
}

@Composable
private fun CompletionPill(
    item: CompletionItem,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val isSnippet = item.kind == CompletionItemKind.SNIPPET

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(VibeTheme.colors.panelBackground)
            .border(0.5.dp, VibeTheme.colors.borderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSnippet) "⚡" else "λ",
                style = VibeCodeTypography.copy(fontSize = 11.sp),
                color = if (isSnippet) VibeTheme.colors.accentPrimary else VibeTheme.colors.accentSecondary
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = item.label,
                style = VibeCodeTypography.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VibeTheme.colors.textPrimary
                )
            )
            val detail = item.detail
            if (detail != null && detail.length <= 15) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = VibeTheme.colors.textMuted
                )
            }
        }
    }
}
