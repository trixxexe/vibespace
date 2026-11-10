package com.vibespace.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vibespace.core.designsystem.theme.VibeShapes
import com.vibespace.core.designsystem.theme.VibeTheme

@Composable
fun VibeDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButtonText: String = "Confirm",
    onConfirm: () -> Unit,
    dismissButtonText: String? = "Cancel",
    onDismiss: (() -> Unit)? = onDismissRequest,
    isConfirmEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = VibeShapes.large,
            color = VibeTheme.colors.panelBackground,
            border = BorderStroke(1.dp, VibeTheme.colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = VibeTheme.colors.textPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                content()

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButtonText != null && onDismiss != null) {
                        VibeButton(
                            onClick = onDismiss,
                            style = VibeButtonStyle.Ghost
                        ) {
                            Text(text = dismissButtonText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    VibeButton(
                        onClick = onConfirm,
                        style = VibeButtonStyle.Primary,
                        enabled = isConfirmEnabled
                    ) {
                        Text(text = confirmButtonText)
                    }
                }
            }
        }
    }
}
