package com.vibespace.feature.aichat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeEmptyState
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun AIChatScreen(
    viewModel: AIChatViewModel,
    onClose: (() -> Unit)? = null,
    onRunInTerminal: ((String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
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

            // Status chip: BYOK
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (uiState.isConfigured) VibeTheme.colors.accentSuccess.copy(alpha = 0.15f) else VibeTheme.colors.accentWarning.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = if (uiState.isConfigured) uiState.activeModel else "BYOK Key Needed",
                    style = VibeCodeTypography.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isConfigured) VibeTheme.colors.accentSuccess else VibeTheme.colors.accentWarning
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

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

            if (onOpenSettings != null) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "AI Settings",
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

        // Quick Key Configuration Banner if unconfigured
        if (!uiState.isConfigured) {
            Surface(
                color = VibeTheme.colors.gutterBackground,
                border = BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                var keyInput by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = VibeTheme.colors.accentPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Your API Key (BYOK)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                    }
                    Text(
                        text = "Enter your Google Gemini API key to activate intelligent code analysis, debugging, and terminal pairing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("Paste API Key (AIzaSy...)", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    viewModel.configureApiKey(keyInput)
                                }
                            },
                            enabled = keyInput.isNotBlank(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VibeTheme.colors.accentPrimary),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Activate", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                    ChatMessageBubble(msg, onRunInTerminal = onRunInTerminal)
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
                modifier = Modifier.size(44.dp)
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
private fun ChatMessageBubble(
    message: AIMessage,
    onRunInTerminal: ((String) -> Unit)? = null
) {
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
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                } else {
                    val segments = parseMessageSegments(message.content)
                    segments.forEach { segment ->
                        if (segment.isCode) {
                            CodeBlockItem(
                                code = segment.text,
                                language = segment.language,
                                onRunInTerminal = onRunInTerminal
                            )
                        } else {
                            Text(
                                text = segment.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockItem(
    code: String,
    language: String,
    onRunInTerminal: ((String) -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VibeTheme.colors.gutterBackground,
        border = BorderStroke(0.5.dp, VibeTheme.colors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VibeTheme.colors.activeLineBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = VibeTheme.colors.textMuted
                )

                if (onRunInTerminal != null) {
                    Surface(
                        onClick = { onRunInTerminal(code.trim()) },
                        shape = RoundedCornerShape(4.dp),
                        color = VibeTheme.colors.accentPrimary.copy(alpha = 0.2f),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run in Terminal",
                                tint = VibeTheme.colors.accentPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Run in Terminal",
                                style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = VibeTheme.colors.accentPrimary
                            )
                        }
                    }
                }
            }

            Text(
                text = code,
                style = VibeCodeTypography.copy(fontSize = 11.sp),
                color = VibeTheme.colors.textPrimary,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

data class MessageSegment(
    val text: String,
    val isCode: Boolean,
    val language: String = ""
)

fun parseMessageSegments(content: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\n([\\s\\S]*?)```")
    var lastIndex = 0

    for (match in codeBlockRegex.findAll(content)) {
        if (match.range.first > lastIndex) {
            val textBefore = content.substring(lastIndex, match.range.first)
            if (textBefore.isNotBlank()) {
                segments.add(MessageSegment(textBefore, false))
            }
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        segments.add(MessageSegment(code, true, lang))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex)
        if (remaining.isNotBlank()) {
            segments.add(MessageSegment(remaining, false))
        }
    }

    if (segments.isEmpty() && content.isNotBlank()) {
        segments.add(MessageSegment(content, false))
    }

    return segments
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
