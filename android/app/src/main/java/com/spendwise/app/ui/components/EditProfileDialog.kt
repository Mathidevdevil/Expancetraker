package com.spendwise.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.data.repository.UserProfile
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.theme.*

@Composable
fun EditProfileDialog(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String) -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    var name by remember { mutableStateOf(currentProfile.name) }
    var email by remember { mutableStateOf(currentProfile.email) }
    val haptic = LocalHapticFeedback.current

    val previewInitials = remember(name) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) "SW"
        else {
            trimmed.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .map { it.first().uppercaseChar() }
                .joinToString("")
                .ifEmpty { "SW" }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = if (isDark) Color(0x1F22283A) else Color(0xFFF1F3F9),
        unfocusedContainerColor = if (isDark) Color(0x1422283A) else Color(0xFFF8F9FD),
        focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
        unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x260F172A),
        focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
        unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
        focusedLabelColor = if (isDark) AppleIndigoLight else AppleIndigo,
        unfocusedLabelColor = if (isDark) TextMutedDark else TextMutedLight
    )

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
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Avatar Preview
                Surface(
                    shape = CircleShape,
                    color = if (isDark) AppleIndigo.copy(alpha = 0.25f) else AppleIndigo.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (isDark) AppleIndigoLight else AppleIndigo),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = previewInitials,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDark) AppleIndigoLight else AppleIndigo
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Edit Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Update your account display information",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    placeholder = { Text("e.g. Alex Morgan") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isDark) AppleIndigoLight else AppleIndigo
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("e.g. alex@spendwise.app") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = if (isDark) AppleIndigoLight else AppleIndigo
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            GlassGradientButton(
                text = "Save Profile",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val finalName = name.trim().ifEmpty { "User" }
                    val finalEmail = email.trim().ifEmpty { "user@spendwise.app" }
                    onSave(finalName, finalEmail)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Cancel",
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }
        }
    )
}
