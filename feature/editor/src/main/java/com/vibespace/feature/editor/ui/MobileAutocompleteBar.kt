package com.vibespace.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.theme.VibeTheme
import com.vibespace.core.language.model.CompletionItem
import com.vibespace.core.language.model.CompletionItemKind

@Composable
fun MobileAutocompleteBar(
    completions: List<CompletionItem>,
    onSelectCompletion: (CompletionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (completions.isEmpty()) return

    Surface(
        color = VibeTheme.colors.gutterBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(completions, key = { it.label }) { item ->
                AutocompleteChip(
                    item = item,
                    onClick = { onSelectCompletion(item) }
                )
            }
        }
    }
}

@Composable
private fun AutocompleteChip(
    item: CompletionItem,
    onClick: () -> Unit
) {
    val (badgeText, badgeColor) = when (item.kind) {
        CompletionItemKind.KEYWORD -> "K" to Color(0xFFBA68C8)
        CompletionItemKind.FUNCTION, CompletionItemKind.METHOD -> "F" to Color(0xFFFFB74D)
        CompletionItemKind.VARIABLE, CompletionItemKind.FIELD -> "V" to Color(0xFF81C784)
        CompletionItemKind.CLASS, CompletionItemKind.INTERFACE -> "C" to Color(0xFF4FC3F7)
        CompletionItemKind.SNIPPET -> "S" to Color(0xFFFF8A65)
        else -> "•" to VibeTheme.colors.textMuted
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VibeTheme.colors.activeLineBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
        modifier = Modifier
            .height(34.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kind badge
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp)),
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
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = VibeTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
