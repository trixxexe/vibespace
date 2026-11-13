package com.vibespace.feature.aichat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.ai.model.AIMessage
import com.vibespace.core.ai.model.AIRole
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeEmptyState
import com.vibespace.core.designsystem.components.VibeIcons
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

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty()) {
            val lastItem = uiState.messages.size - 1
            if (uiState.isGenerating) listState.scrollToItem(lastItem)
            else listState.animateScrollToItem(lastItem)
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
                imageVector = VibeIcons.AI,
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
                color = if (uiState.isConfigured) VibeTheme.colors.statusSuccess.copy(alpha = 0.15f) else VibeTheme.colors.statusWarning.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = if (uiState.isConfigured) uiState.activeModel else "BYOK Key Needed",
                    style = VibeCodeTypography.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isConfigured) VibeTheme.colors.statusSuccess else VibeTheme.colors.statusWarning
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
                        imageVector = VibeIcons.Close,
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
                        imageVector = VibeIcons.Settings,
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
                        imageVector = VibeIcons.Close,
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
                    imageVector = VibeIcons.Editor,
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
                var selectedProvider by remember { mutableStateOf("gemini") }
                val providerModels = remember {
                    mapOf(
                        "gemini" to listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro"),
                        "openai" to listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
                        "anthropic" to listOf("claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
                        "deepseek" to listOf("deepseek-chat", "deepseek-reasoner"),
                        "groq" to listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
                        "openrouter" to listOf("anthropic/claude-3.7-sonnet", "google/gemini-2.5-flash", "openai/gpt-4o-mini", "deepseek/deepseek-chat")
                    )
                }
                var selectedModel by remember(selectedProvider) {
                    mutableStateOf(providerModels[selectedProvider]?.firstOrNull() ?: "gemini-2.5-flash")
                }
                var isModelMenuExpanded by remember { mutableStateOf(false) }
                val isKeyEntered = keyInput.isNotBlank()

                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(VibeIcons.Key, contentDescription = null, tint = VibeTheme.colors.accentPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Your API Key (BYOK)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = VibeTheme.colors.textPrimary)
                    }
                    Text(
                        text = "Connect Gemini, OpenAI, Claude, DeepSeek, Groq, or OpenRouter. Keys are encrypted on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "gemini" to "Gemini",
                            "openai" to "OpenAI",
                            "anthropic" to "Claude",
                            "deepseek" to "DeepSeek",
                            "groq" to "Groq",
                            "openrouter" to "OpenRouter"
                        ).forEach { (id, label) ->
                            val isSelected = selectedProvider == id
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) VibeTheme.colors.accentPrimary.copy(alpha = 0.2f) else VibeTheme.colors.panelBackground,
                                border = BorderStroke(1.dp, if (isSelected) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle),
                                modifier = Modifier.clickable {
                                    selectedProvider = id
                                    selectedModel = providerModels[id]?.firstOrNull() ?: "gemini-2.5-flash"
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) VibeTheme.colors.accentPrimary else VibeTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste $selectedProvider API Key...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grey box under API key that lights up and transforms into a Model Dropdown Menu when key is entered
                    val availableModels = providerModels[selectedProvider].orEmpty()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isKeyEntered) {
                                VibeTheme.colors.accentPrimary.copy(alpha = 0.15f)
                            } else {
                                VibeTheme.colors.panelBackground.copy(alpha = 0.6f)
                            },
                            border = BorderStroke(
                                width = if (isKeyEntered) 1.5.dp else 1.dp,
                                color = if (isKeyEntered) VibeTheme.colors.accentPrimary else VibeTheme.colors.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isKeyEntered) {
                                    isModelMenuExpanded = true
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VibeIcons.AI,
                                        contentDescription = null,
                                        tint = if (isKeyEntered) VibeTheme.colors.accentPrimary else VibeTheme.colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isKeyEntered) "Model: $selectedModel" else "Enter API key above to select a model",
                                            style = if (isKeyEntered) {
                                                VibeCodeTypography.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                MaterialTheme.typography.bodySmall
                                            },
                                            color = if (isKeyEntered) VibeTheme.colors.textPrimary else VibeTheme.colors.textMuted
                                        )
                                        if (isKeyEntered) {
                                            Text(
                                                text = "Tap to choose from ${availableModels.size} available models",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VibeTheme.colors.accentPrimary
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (isModelMenuExpanded) VibeIcons.ArrowUp else VibeIcons.ArrowDown,
                                    contentDescription = "Select Model",
                                    tint = if (isKeyEntered) VibeTheme.colors.accentPrimary else VibeTheme.colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isModelMenuExpanded && isKeyEntered,
                            onDismissRequest = { isModelMenuExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(VibeTheme.colors.panelBackground)
                        ) {
                            availableModels.forEach { modelOption ->
                                val isCurrent = selectedModel == modelOption
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = modelOption,
                                                style = VibeCodeTypography.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = if (isCurrent) VibeTheme.colors.accentPrimary else VibeTheme.colors.textPrimary
                                            )
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = VibeIcons.Checkmark,
                                                    contentDescription = "Selected",
                                                    tint = VibeTheme.colors.accentPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedModel = modelOption
                                        isModelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (keyInput.isNotBlank()) {
                                viewModel.configureApiKey(keyInput.trim(), selectedProvider, selectedModel)
                            }
                        },
                        enabled = isKeyEntered,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VibeTheme.colors.accentPrimary),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Activate $selectedModel", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                        icon = VibeIcons.AI,
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
                itemsIndexed(uiState.messages, key = { index, msg -> "${msg.id}_$index" }) { _, msg ->
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
                    imageVector = VibeIcons.Send,
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
                                imageVector = VibeIcons.Run,
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
