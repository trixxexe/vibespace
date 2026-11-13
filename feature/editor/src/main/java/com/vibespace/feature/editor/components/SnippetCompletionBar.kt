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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind

/**
 * Unified, non-jumping mobile autocomplete & snippet bar displayed directly above the virtual symbol row.
 * Keeps a constant height so typing never causes layout shifts or viewport jumps.
 */
@Composable
fun SnippetCompletionBar(
    suggestions: List<CompletionItem>,
    onSelectSnippet: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    liveCompletions: List<CompletionItem> = emptyList(),
    onSelectLiveCompletion: ((CompletionItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty() && liveCompletions.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val distinctLive = remember(liveCompletions) {
        liveCompletions.distinctBy { "${it.kind}:${it.label}:${it.insertText}" }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
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
                    imageVector = VibeIcons.Snippet,
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

        // Live Context-Aware Autocomplete Suggestions First
        if (distinctLive.isNotEmpty() && onSelectLiveCompletion != null) {
            distinctLive.forEach { item ->
                LiveAutocompletePill(
                    item = item,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectLiveCompletion(item)
                    }
                )
            }
        }

        // Language Snippet items
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
private fun LiveAutocompletePill(
    item: CompletionItem,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val (badgeText, badgeColor) = when (item.kind) {
        CompletionItemKind.KEYWORD -> "K" to Color(0xFFBA68C8)
        CompletionItemKind.FUNCTION, CompletionItemKind.METHOD -> "F" to Color(0xFFFFB74D)
        CompletionItemKind.VARIABLE, CompletionItemKind.FIELD -> "V" to Color(0xFF81C784)
        CompletionItemKind.CLASS, CompletionItemKind.INTERFACE -> "C" to Color(0xFF4FC3F7)
        CompletionItemKind.SNIPPET -> "S" to Color(0xFFFF8A65)
        else -> "•" to VibeTheme.colors.accentPrimary
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(VibeTheme.colors.activeLineBackground)
            .border(1.dp, VibeTheme.colors.accentPrimary.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(badgeColor.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.label,
                style = VibeCodeTypography.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VibeTheme.colors.textPrimary
                )
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
            Icon(
                imageVector = if (isSnippet) VibeIcons.Snippet else VibeIcons.Editor,
                contentDescription = if (isSnippet) "Snippet" else "Symbol",
                tint = if (isSnippet) VibeTheme.colors.accentPrimary else VibeTheme.colors.accentSecondary,
                modifier = Modifier.size(12.dp)
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
