package com.vibespace.feature.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeEmptyState
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun AIChatScreen(
    viewModel: AIChatViewModel,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI CODING ASSISTANT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            if (uiState.messages.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear chat",
                        tint = VibeTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = VibeTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Active Context Pill
        uiState.attachedCodeContext?.let { ctx ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VibeTheme.colors.panelBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Context: ${ctx.fileName} (${ctx.language})",
                    style = MaterialTheme.typography.labelSmall,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }

        // Chat Message List or Empty State
        if (uiState.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    VibeEmptyState(
                        icon = Icons.Default.AutoAwesome,
                        title = "How can I help you code today?",
                        subtitle = "Select an active file to attach context, or ask any programming question."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Suggested Prompts
                    SuggestedPromptChip("Explain active code") { viewModel.sendPrompt("Explain this code in detail.") }
                    Spacer(modifier = Modifier.height(8.dp))
                    SuggestedPromptChip("Find potential bugs") { viewModel.sendPrompt("Review this code for edge cases and bugs.") }
                    Spacer(modifier = Modifier.height(8.dp))
                    SuggestedPromptChip("Generate unit test") { viewModel.sendPrompt("Write complete unit tests for this code.") }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatMessageBubble(msg)
                }

                if (uiState.isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = VibeTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Input Prompt Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeTheme.colors.gutterBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.inputPrompt,
                onValueChange = { viewModel.onPromptChange(it) },
                placeholder = { Text("Ask VibeSpace AI...", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.sendPrompt() },
                enabled = uiState.inputPrompt.isNotBlank() && !uiState.isGenerating,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (uiState.inputPrompt.isNotBlank()) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: AIMessage) {
    val isUser = message.role == AIRole.USER
    val bubbleBg = if (isUser) MaterialTheme.colorScheme.primaryContainer else VibeTheme.colors.panelBackground
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else VibeTheme.colors.textPrimary
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleBg,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun SuggestedPromptChip(
    text: String,
    onClick: () -> Unit
) {
    VibeCard(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clickable { onClick() },
        contentPadding = 10.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
