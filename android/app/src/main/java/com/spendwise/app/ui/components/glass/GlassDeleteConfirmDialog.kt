package com.spendwise.app.ui.components.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.ui.theme.*

@Composable
fun GlassDeleteConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    cancelText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(
            width = 1.dp,
            brush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush,
            shape = RoundedCornerShape(26.dp)
        ),
        shape = RoundedCornerShape(26.dp),
        containerColor = if (isDark) SolidFallbackDark else Color.White,
        tonalElevation = 8.dp,
        icon = {
            Surface(
                shape = CircleShape,
                color = ExpenseRose.copy(alpha = if (isDark) 0.22f else 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ExpenseRose.copy(alpha = 0.5f)),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Icon",
                        tint = ExpenseRose,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) TextMutedDark else TextMutedLight,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExpenseRose,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    text = confirmText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color(0x33FFFFFF) else Color(0x260F172A)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDark) TextPrimaryDark else TextPrimaryLight
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = cancelText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    )
}
