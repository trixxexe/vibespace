package com.vibespace.feature.home.onboarding

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.common.notification.VibeNotificationHelper
import com.vibespace.core.common.result.VibeResult
import com.vibespace.core.designsystem.components.VibeCard
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeCodeTypography
import com.vibespace.core.designsystem.theme.VibeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Visual theme mode selection options for onboarding.
 * Self-contained to preserve feature isolation.
 */
enum class OnboardingThemeMode {
    DARK,
    BLUE_VSC,
    LIGHT,
    SYSTEM
}

/**
 * Cinematic first-launch opening & A-to-Z interactive guide for VibeSpace.
 * Persistent across process restarts, resumable, and skippable at any time.
 */
@Composable
fun OnboardingScreen(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onOpenSafPicker: () -> Unit,
    onCreateNewProject: (String) -> Unit,
    onTrySampleProject: () -> Unit,
    onSelectTheme: (OnboardingThemeMode) -> Unit,
    currentTheme: OnboardingThemeMode,
    onConfigureAi: (suspend (provider: String, key: String, model: String) -> VibeResult<Unit>)? = null,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalSteps = 4
    val safeStep = currentStep.coerceIn(0, totalSteps - 1)

    // Never exit the app on Back: step back through the guide or return to Home
    BackHandler(enabled = true) {
        if (safeStep > 0) {
            onStepChange(safeStep - 1)
        } else {
            onSkip()
        }
    }

    if (errorMessage != null) {
        com.vibespace.core.designsystem.components.VibeDialog(
            title = "Setup Notice",
            onDismissRequest = onDismissError,
            confirmButtonText = "OK",
            onConfirm = onDismissError
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.statusError
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar: Step Progress & Skip Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = VibeIcons.Logo,
                    contentDescription = "VibeSpace",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VibeSpace Guide & Setup",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = VibeTheme.colors.textPrimary
                )
            }

            // Step Indicator Dots (clickable to jump between guide sections)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalSteps) {
                    val isActive = i == safeStep
                    val isPast = i < safeStep
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 12.dp else 8.dp)
                            .background(
                                color = when {
                                    isActive -> MaterialTheme.colorScheme.primary
                                    isPast -> VibeTheme.colors.accentSecondary
                                    else -> VibeTheme.colors.borderSubtle
                                },
                                shape = CircleShape
                            )
                            .clickable { onStepChange(i) }
                    )
                }
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Skip to IDE",
                    style = MaterialTheme.typography.labelMedium,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }

        // Main Content (snappy direct switching without laggy animations)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (safeStep) {
                0 -> StepWelcome(
                    onNext = { onStepChange(1) },
                    onJumpToGuide = { onStepChange(2) }
                )
                1 -> StepProjectChoice(
                    onOpenSafPicker = onOpenSafPicker,
                    onCreateNewProject = onCreateNewProject,
                    onTrySampleProject = onTrySampleProject,
                    onBack = { onStepChange(0) },
                    onNext = { onStepChange(2) }
                )
                2 -> StepIdeTour(
                    onBack = { onStepChange(1) },
                    onNext = { onStepChange(3) }
                )
                3 -> StepPreferencesAndFinish(
                    currentTheme = currentTheme,
                    onSelectTheme = onSelectTheme,
                    onConfigureAi = onConfigureAi,
                    onBack = { onStepChange(2) },
                    onFinish = onFinish
                )
            }
        }
    }
}

@Composable
private fun StepWelcome(
    onNext: () -> Unit,
    onJumpToGuide: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Cinematic Opening Hero Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    VibeTheme.colors.panelBackground,
                                    VibeTheme.colors.editorBackground
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "STARDEVS STUDIO PRESENTS",
                                    style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VibeTheme.colors.activeLineBackground
                            ) {
                                Text(
                                    text = "v2.4.0 • NATIVE IDE",
                                    style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = VibeTheme.colors.accentSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = VibeIcons.Logo,
                                        contentDescription = "VibeSpace Logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "VibeSpace",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = VibeTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Desktop-Grade Native Android Coding IDE",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Built from the ground up in Kotlin & Jetpack Compose for mobile touchscreens, foldables, tablets, and desktop mode. Edit with hardware-accelerated syntax highlighting, run Python 3 & Node.js in a bundled Alpine Linux PRoot container, clone Git repositories, and pair-program with multi-model AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // StarDevs Credits & Community Card
            VibeCard(contentPadding = 16.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEVELOPED BY STARDEVS",
                            style = VibeCodeTypography.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Official Release",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibeTheme.colors.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CreditBadgeRow(
                        role = "Lead Developer & Systems Architect",
                        name = "Ritam",
                        detail = "Core IDE Engine, Alpine Linux PRoot Runtime, Sora Editor & Architecture"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = VibeTheme.colors.borderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    CreditBadgeRow(
                        role = "Product Planning, QA, UX & Testing",
                        name = "Ankit",
                        detail = "Feature Roadmap, Mobile Ergonomics, Quality Assurance & Verification"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VibeTheme.colors.activeLineBackground,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "StarDevs Community Discord",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = VibeTheme.colors.textPrimary
                                )
                                Text(
                                    text = "discord.gg/stardevs",
                                    style = VibeCodeTypography.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("StarDevs Discord", "https://discord.gg/stardevs"))
                                        Toast.makeText(context, "Copied discord.gg/stardevs", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/stardevs")).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Toast.makeText(context, "Discord: https://discord.gg/stardevs", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Join", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FeaturePillar(
                icon = VibeIcons.Editor,
                title = "Snappy Native Code Editor + Autocomplete",
                description = "Multi-tab Sora editor with 11 VS Code themes (including Blue VS Code), instant live autocomplete, snippet bar, and zero-lag typing."
            )
            Spacer(modifier = Modifier.height(12.dp))
            FeaturePillar(
                icon = VibeIcons.Terminal,
                title = "Bundled Alpine Linux PRoot & Resizable Terminal",
                description = "Full Linux userspace with Python 3, Node.js, BusyBox, and apk package manager ready out of the box, plus a VS Code-style draggable split handle."
            )
            Spacer(modifier = Modifier.height(12.dp))
            FeaturePillar(
                icon = VibeIcons.SourceControl,
                title = "1-Click Git Clone, Diffs, Storage Import/Export & BYOK AI",
                description = "Paste any Git repo link to clone into your workspace, import/export files to device storage, and chat with Gemini 2.5, Claude, OpenAI, DeepSeek, Groq, or OpenRouter."
            )
        }

        Column(modifier = Modifier.padding(top = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onJumpToGuide,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("A-to-Z Feature Guide", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Start Setup", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun CreditBadgeRow(
    role: String,
    name: String,
    detail: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = role,
                style = MaterialTheme.typography.labelMedium,
                color = VibeTheme.colors.textSecondary
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Text(
                    text = name,
                    style = VibeCodeTypography.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = VibeTheme.colors.textMuted
        )
    }
}

@Composable
private fun StepProjectChoice(
    onOpenSafPicker: () -> Unit,
    onCreateNewProject: (String) -> Unit,
    onTrySampleProject: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectNameInput by remember { mutableStateOf("") }

    if (showCreateDialog) {
        com.vibespace.core.designsystem.components.VibeDialog(
            title = "New Project Name",
            onDismissRequest = { showCreateDialog = false },
            confirmButtonText = "Create",
            isConfirmEnabled = projectNameInput.isNotBlank(),
            onConfirm = {
                val name = projectNameInput.trim()
                showCreateDialog = false
                onCreateNewProject(name)
            }
        ) {
            Column {
                Text(
                    text = "Enter a name for your new workspace:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibeTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = projectNameInput,
                    onValueChange = { projectNameInput = it },
                    placeholder = { Text("e.g. MyPythonApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step 1: Choose or Create a Workspace",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pick how you want to store your code, or continue to the interactive A-to-Z tutorial first.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Sample Project
            WorkspaceOptionCard(
                icon = VibeIcons.Logo,
                badge = "Recommended for First Launch",
                title = "Explore Sample Project",
                description = "Creates a ready-to-run Python & Web workspace with sample scripts so you can test the editor, autocomplete, and Alpine terminal immediately.",
                onClick = onTrySampleProject
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Option 2: Open Device Folder via SAF
            WorkspaceOptionCard(
                icon = VibeIcons.FolderOpen,
                badge = "Storage Access Framework (Device Storage)",
                title = "Open Existing Device Folder",
                description = "Pick any directory on your phone or tablet via Android's Document Tree picker with persistent read/write access.",
                onClick = onOpenSafPicker
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Option 3: Create New Project
            WorkspaceOptionCard(
                icon = VibeIcons.NewFolder,
                badge = "Fast App-Private Workspace (Supports Alpine & JGit)",
                title = "Create Empty Workspace",
                description = "Initializes a dedicated POSIX-backed project directory that works directly with Alpine Linux PRoot and local Git repositories.",
                onClick = { showCreateDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            VibeCard(contentPadding = 12.dp) {
                Column {
                    Text(
                        text = "PRO TIP: GIT CLONE & STORAGE EXPORT",
                        style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• To clone a GitHub/GitLab repository: tap 'Clone Git Repo' on the Home screen or inside the Git Source Control drawer, paste the URL, and tap Clone.\n• To import or export individual files to phone storage: use the Import/Export buttons in the Explorer header or the Export icon in the Editor toolbar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textSecondary
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(top = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Next: A-to-Z Guide", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

private data class GuideModule(
    val letter: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val steps: List<String>,
    val proTip: String
)

@Composable
private fun StepIdeTour(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var expandedIndex by remember { mutableIntStateOf(0) }

    val modules = remember {
        listOf(
            GuideModule(
                letter = "A",
                title = "Explorer, Files & Device Storage Import/Export",
                subtitle = "Create files, import from phone storage, and export anywhere",
                icon = VibeIcons.Explorer,
                steps = listOf(
                    "1. Open the File Explorer: On phones, tap the Folder icon in the top bar (or swipe from the left edge). On tablets, click 'Files' on the left navigation rail.",
                    "2. Create Files & Folders: Tap the New File (+) or New Folder icon in the Explorer header, or tap the '...' menu next to any directory to create inside that folder.",
                    "3. Import from Device Storage: Tap the Import icon in the Explorer header to pick any file from your Android Downloads/Documents and copy it into your active workspace.",
                    "4. Export to Device Storage: Tap the '...' menu on any file and choose 'Export to Storage', or tap the Export icon in the Editor toolbar to save the active file anywhere on your device."
                ),
                proTip = "You will receive an Android system notification whenever a file export or project operation completes."
            ),
            GuideModule(
                letter = "B",
                title = "Code Editor, Live Autocomplete & Quick Symbols",
                subtitle = "Write code fast with completions, snippets, and touch symbols",
                icon = VibeIcons.Editor,
                steps = listOf(
                    "1. Multi-Tab Editing: Open multiple files at once and switch tabs at the top of the editor. Unsaved changes show a dot indicator.",
                    "2. Unified Autocomplete & Snippets Bar: As you type (2+ characters), live language keywords, built-in functions, and symbols from your file appear directly above the symbol row. Tap any suggestion to insert it cleanly without shifting the layout.",
                    "3. Quick Symbol Bar: Use the bottom touch bar for TAB, {, }, (, ), [, ], ;, =, \", ', and cursor arrows without switching keyboard pages.",
                    "4. Search & Replace: Tap the Search icon in the toolbar (or press Ctrl+F) to search across files with regex and replace matches."
                ),
                proTip = "Tap 'Templates' on the left side of the completion bar anytime to browse full boilerplate snippets (like main(), def, class, try/except, fetch)."
            ),
            GuideModule(
                letter = "C",
                title = "Bundled Alpine Linux PRoot & Resizable Terminal",
                subtitle = "Run Python, Node.js, shell commands, and install Linux packages",
                icon = VibeIcons.Terminal,
                steps = listOf(
                    "1. Run Active Code (Play Button): Open any .py, .js, or .sh file and tap the green Play button in the editor toolbar. VibeSpace saves the file, executes it in the terminal, and mirrors clean output to the OUTPUT tab.",
                    "2. Resize the Terminal Like VS Code: Drag the horizontal split handle at the top of the terminal panel up or down to give more room to your terminal or code.",
                    "3. Switch Execution Modes: Use the runner selector pill (Alpine / Instant / Termux) in the terminal header. 'Alpine' runs inside a full Linux rootfs with Python 3 & Node.js; 'Instant' runs fast built-in shell & scripts.",
                    "4. Install Extra Linux Packages: In the Alpine terminal, run 'apk add <package>' (e.g., 'apk add git curl nano') or 'pip install <pkg>' to install tools directly inside the container."
                ),
                proTip = "Use the TERMINAL, OUTPUT, PROBLEMS, and CONSOLE tabs to inspect live PTY shell sessions, clean program output, syntax diagnostics, and IDE logs."
            ),
            GuideModule(
                letter = "D",
                title = "Git Clone & Source Control Workflow",
                subtitle = "Clone GitHub repos by link, stage, diff, branch, commit & push",
                icon = VibeIcons.SourceControl,
                steps = listOf(
                    "1. Clone Any Git Repository: On the Home screen (or inside the Git tab of the Explorer drawer), tap 'Clone Git Repo', paste a HTTPS URL (e.g., https://github.com/user/repo.git) using the 'Paste' button, and tap Clone.",
                    "2. Review & Stage Changes: Open the Git panel to see Modified and Untracked files. Tap any file to inspect its color-coded unified diff (+ green additions, - red deletions), or tap '+' to stage it.",
                    "3. Commit with Author Identity: Enter a commit message and tap Commit. If you haven't set your Git author name and email yet, VibeSpace will prompt you once and save it automatically.",
                    "4. Branches & Remotes: Create or switch branches with the branch bar, and use Push / Pull with a Personal Access Token (PAT) for private repositories."
                ),
                proTip = "Cloning into a workspace automatically refreshes the Explorer file tree and sends a completion notification."
            ),
            GuideModule(
                letter = "E",
                title = "Multi-Model AI Coding Assistant (BYOK)",
                subtitle = "Gemini 2.5, Claude, OpenAI, DeepSeek, Groq & OpenRouter",
                icon = VibeIcons.AI,
                steps = listOf(
                    "1. Configure Your API Key: Tap the Sparkle (AI) icon in the top bar and tap 'Key' (or open Settings -> AI Coding Assistant). Select Gemini, OpenAI, Anthropic Claude, DeepSeek, Groq, or OpenRouter and paste your API key.",
                    "2. Automatic Code Context: When a file is open in the editor, the AI Assistant automatically attaches your active file name, language, and code context.",
                    "3. 1-Tap Terminal Execution: When the AI suggests a shell or package command in a bash block, tap 'Run in Terminal' on the code card to execute it immediately."
                ),
                proTip = "If a provider's SSE stream is interrupted by mobile network switching, VibeSpace automatically falls back to a standard request so your answer is never lost."
            ),
            GuideModule(
                letter = "F",
                title = "Themes (Blue VS Code), Command Palette & Back Navigation",
                subtitle = "Customize every detail of your mobile IDE",
                icon = VibeIcons.Settings,
                steps = listOf(
                    "1. Blue VS Code & 11 Color Themes: Open Settings to switch between Dark, Blue VS Code (Tomorrow Night Blue), Light, or System mode, plus 11 syntax themes (Monokai, Dracula, One Dark, Nord, Tokyo Night, Catppuccin, Solarized, and more).",
                    "2. Command Palette (Ctrl+P): Tap the Magnifying Glass in the top bar to fuzzy-search project files or trigger IDE actions in one tap.",
                    "3. Safe Back Navigation: Pressing the Android Back button will never accidentally close VibeSpace—it closes open overlays/drawers first, then returns from the Editor or Settings back to the Home screen."
                ),
                proTip = "You can reopen this A-to-Z Interactive Guide anytime from the Home screen ('Guide & Tour') or from Settings ('Interactive A-to-Z Guide')."
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step 2: Complete A-to-Z IDE Guide",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap each module below to learn exactly how to use every feature in VibeSpace from A to Z.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            modules.forEachIndexed { index, module ->
                val isExpanded = expandedIndex == index
                GuideModuleCard(
                    module = module,
                    isExpanded = isExpanded,
                    onClick = {
                        expandedIndex = if (isExpanded) -1 else index
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Next: Theme & AI", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun GuideModuleCard(
    module: GuideModule,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VibeTheme.colors.panelBackground,
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 0.5.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary else VibeTheme.colors.borderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else VibeTheme.colors.activeLineBackground,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = module.letter,
                            style = VibeCodeTypography.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                            color = if (isExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = VibeTheme.colors.textPrimary
                    )
                    Text(
                        text = module.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textSecondary
                    )
                }
                Icon(
                    imageVector = if (isExpanded) VibeIcons.ArrowDown else VibeIcons.ArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = VibeTheme.colors.borderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                module.steps.forEach { stepText ->
                    Text(
                        text = stepText,
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VibeTheme.colors.activeLineBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tip: ${module.proTip}",
                        style = VibeCodeTypography.copy(fontSize = 11.sp),
                        color = VibeTheme.colors.accentSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPreferencesAndFinish(
    currentTheme: OnboardingThemeMode,
    onSelectTheme: (OnboardingThemeMode) -> Unit,
    onConfigureAi: (suspend (provider: String, key: String, model: String) -> VibeResult<Unit>)?,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf("gemini") }
    val providerModels = remember {
        mapOf(
            "gemini" to listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro"),
            "openai" to listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
            "anthropic" to listOf("claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest"),
            "deepseek" to listOf("deepseek-chat", "deepseek-reasoner")
        )
    }
    var selectedModel by remember(selectedProvider) {
        mutableStateOf(providerModels[selectedProvider]?.firstOrNull() ?: "gemini-2.5-flash")
    }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var notificationsGranted by remember {
        mutableStateOf(VibeNotificationHelper.hasNotificationPermission(context))
    }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            VibeNotificationHelper.notify(
                context = context,
                title = "VibeSpace Notifications Enabled",
                message = "You will now receive alerts for Git clones, builds, and file exports."
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step 3: Theme, Notifications & AI",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose your IDE look (including Blue VS Code), enable build/clone notifications, and optionally connect an AI key.",
                style = MaterialTheme.typography.bodyMedium,
                color = VibeTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Theme Selection Row
            Text(
                text = "IDE COLOR THEME",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOptionPill(
                    title = "Dark",
                    isSelected = currentTheme == OnboardingThemeMode.DARK,
                    onClick = { onSelectTheme(OnboardingThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionPill(
                    title = "Blue VSC",
                    isSelected = currentTheme == OnboardingThemeMode.BLUE_VSC,
                    onClick = { onSelectTheme(OnboardingThemeMode.BLUE_VSC) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionPill(
                    title = "Light",
                    isSelected = currentTheme == OnboardingThemeMode.LIGHT,
                    onClick = { onSelectTheme(OnboardingThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionPill(
                    title = "System",
                    isSelected = currentTheme == OnboardingThemeMode.SYSTEM,
                    onClick = { onSelectTheme(OnboardingThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Notifications Permission Card
            Text(
                text = "IDE NOTIFICATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            VibeCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (notificationsGranted) "Notifications Active" else "Enable Build & Git Clone Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            color = VibeTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Get notified when Git repositories finish cloning or files are exported to storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VibeTheme.colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (notificationsGranted) {
                        OutlinedButton(
                            onClick = {
                                VibeNotificationHelper.notify(
                                    context = context,
                                    title = "VibeSpace Ready",
                                    message = "Notifications are working properly!"
                                )
                            }
                        ) {
                            Text("Test", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notificationsGranted = true
                                }
                            }
                        ) {
                            Text("Allow", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // AI Coding Assistant (Optional BYOK)
            Text(
                text = "AI CODING ASSISTANT (OPTIONAL BYOK)",
                style = MaterialTheme.typography.labelSmall,
                color = VibeTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            VibeCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = VibeIcons.AI,
                            contentDescription = null,
                            tint = VibeTheme.colors.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bring Your Own Key",
                            style = MaterialTheme.typography.titleSmall,
                            color = VibeTheme.colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect an API key now, or leave blank and configure anytime in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VibeTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "gemini" to "Gemini",
                            "openai" to "OpenAI",
                            "anthropic" to "Claude",
                            "deepseek" to "DeepSeek"
                        ).forEach { (id, label) ->
                            ThemeOptionPill(
                                title = label,
                                isSelected = selectedProvider == id,
                                onClick = { selectedProvider = id },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Enter $selectedProvider API Key (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Grey box under API key that lights up and transforms into a Model Dropdown Menu when key is entered
                    val isKeyEntered = apiKeyInput.isNotBlank()
                    val availableModels = providerModels[selectedProvider].orEmpty()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isKeyEntered) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                VibeTheme.colors.gutterBackground.copy(alpha = 0.65f)
                            },
                            border = BorderStroke(
                                width = if (isKeyEntered) 1.5.dp else 1.dp,
                                color = if (isKeyEntered) MaterialTheme.colorScheme.primary else VibeTheme.colors.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isKeyEntered) {
                                    isModelDropdownExpanded = true
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
                                        tint = if (isKeyEntered) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isKeyEntered) "Model: $selectedModel" else "Enter API Key above to unlock model selector",
                                            style = if (isKeyEntered) {
                                                VibeCodeTypography.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            } else {
                                                MaterialTheme.typography.bodySmall
                                            },
                                            color = if (isKeyEntered) VibeTheme.colors.textPrimary else VibeTheme.colors.textMuted
                                        )
                                        Text(
                                            text = if (isKeyEntered) {
                                                "Tap to select from ${availableModels.size} available models"
                                            } else {
                                                "Dropdown menu activates once an API key is provided"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isKeyEntered) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isModelDropdownExpanded) VibeIcons.ArrowUp else VibeIcons.ArrowDown,
                                    contentDescription = "Select Model",
                                    tint = if (isKeyEntered) MaterialTheme.colorScheme.primary else VibeTheme.colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isModelDropdownExpanded && isKeyEntered,
                            onDismissRequest = { isModelDropdownExpanded = false },
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
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else VibeTheme.colors.textPrimary
                                            )
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = VibeIcons.Checkmark,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedModel = modelOption
                                        isModelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (saveError != null) {
                        Text(
                            text = saveError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(0.35f)
                        .height(50.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (apiKeyInput.isBlank()) {
                                onFinish()
                                return@launch
                            }
                            isSaving = true
                            saveError = null
                            try {
                                val saveResult = onConfigureAi?.invoke(
                                    selectedProvider,
                                    apiKeyInput.trim(),
                                    selectedModel
                                ) ?: VibeResult.Error("AI settings are unavailable. Clear the key to continue without AI.")
                                when (saveResult) {
                                    is VibeResult.Success -> onFinish()
                                    is VibeResult.Error -> saveError = "Could not save the API key: ${saveResult.message}"
                                    else -> saveError = "Could not save the API key."
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                saveError = "Could not save the API key: ${failure.message ?: "unexpected error"}"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(0.65f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (isSaving) "Saving…" else "Finish Setup & Launch IDE",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePillar(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VibeTheme.colors.panelBackground,
            border = BorderStroke(0.5.dp, VibeTheme.colors.borderSubtle),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = VibeTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VibeTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun WorkspaceOptionCard(
    icon: ImageVector,
    badge: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    VibeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VibeTheme.colors.activeLineBackground,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VibeTheme.colors.textPrimary
                    )
                }
                Text(
                    text = badge,
                    style = VibeCodeTypography.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = VibeTheme.colors.accentSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VibeTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else VibeTheme.colors.panelBackground,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else VibeTheme.colors.borderSubtle
        ),
        modifier = modifier
            .height(42.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else VibeTheme.colors.textSecondary
            )
        }
    }
}
