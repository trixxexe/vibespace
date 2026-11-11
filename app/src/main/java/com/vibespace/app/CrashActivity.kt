package com.vibespace.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibespace.core.designsystem.components.VibeIcons
import com.vibespace.core.designsystem.theme.VibeSpaceTheme
import java.io.File

/**
 * Dedicated Crash Recovery Activity.
 * Intercepts uncaught exceptions and displays a clean error recovery UI with
 * clipboard copy, cache wiping, and 1-tap app restart, preventing OS crash dialogs.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}

        val errorType = intent.getStringExtra("error_type") ?: "Crash"
        val errorMessage = intent.getStringExtra("error_message") ?: "An unexpected error occurred"
        val stackTrace = intent.getStringExtra("stack_trace") ?: "No stack trace available"

        setContent {
            VibeSpaceTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = Color(0xFF0F1115)
                ) {
                    CrashScreen(
                        errorType = errorType,
                        errorMessage = errorMessage,
                        stackTrace = stackTrace,
                        onCopy = {
                            try {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("VibeSpace Crash Log", "$errorType: $errorMessage\n\n$stackTrace")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {}
                        },
                        onClearCache = {
                            try {
                                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                                val crashFile = File(filesDir, "crash_log.txt")
                                if (crashFile.exists()) {
                                    crashFile.delete()
                                }
                                Toast.makeText(this, "Temporary cache cleared; preferences kept", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {}
                            restartApp()
                        },
                        onRestart = {
                            restartApp()
                        }
                    )
                }
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun CrashScreen(
    errorType: String,
    errorMessage: String,
    stackTrace: String,
    onCopy: () -> Unit,
    onClearCache: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = VibeIcons.Warning,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "VibeSpace Recovery",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "An unexpected error occurred during execution",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        // Error message card
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2129)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = errorType,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFF87171)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE5E7EB)
                )
            }
        }

        // Stack trace view
        Text(
            text = "STACK TRACE",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B7280)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xFF13151A), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stackTrace,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = Color(0xFFD1D5DB)
            )
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCopy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("Copy Error", color = Color.Black)
            }

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("Restart App", color = Color.White)
            }
        }

        OutlinedButton(
            onClick = onClearCache,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text("Clear Temporary Cache & Restart", color = Color(0xFFEF4444))
        }
    }
}
