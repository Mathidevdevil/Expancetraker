package com.spendwise.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.BuildConfig
import com.spendwise.app.data.remote.AppUpdateInfo
import com.spendwise.app.data.remote.UpdateDownloadState
import com.spendwise.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    updateInfo: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    isDark: Boolean,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = {
            if (!updateInfo.isMandatory && downloadState !is UpdateDownloadState.Downloading) {
                onDismiss()
            }
        },
        sheetState = modalBottomSheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = if (isDark) Plum900 else Cream50,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Gold500.copy(alpha = 0.3f) else Taupe500.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Plum700,
                                Gold500
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Update Available",
                fontFamily = SfPro,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = if (isDark) Cream50 else Plum900
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Spendwise v${updateInfo.versionName} is ready (Current: v${BuildConfig.VERSION_NAME})",
                fontFamily = SfPro,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = if (isDark) Taupe300 else Taupe700
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Changelog card
            if (!updateInfo.changelog.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isDark) Plum950 else Cream100,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Gold500.copy(alpha = 0.25f) else Taupe300
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "What's New:",
                            fontFamily = SfPro,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isDark) Gold500 else Plum700
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = updateInfo.changelog.trim(),
                            fontFamily = SfPro,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = if (isDark) Cream50 else Plum900
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Interactive state buttons & progress
            when (downloadState) {
                is UpdateDownloadState.Idle -> {
                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Gold500 else Plum700,
                            contentColor = if (isDark) Plum900 else Cream50
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update Now",
                            fontFamily = SfPro,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (!updateInfo.isMandatory) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                text = "Remind Me Later",
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isDark) Taupe300 else Taupe700
                            )
                        }
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    val percent = (downloadState.progress * 100).toInt().coerceIn(0, 100)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading update...",
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isDark) Cream50 else Plum900
                            )
                            Text(
                                text = "$percent%",
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Gold500
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Gold500,
                            trackColor = if (isDark) Taupe900 else Taupe100
                        )
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) IncomeDark else IncomeLight,
                                contentColor = if (isDark) Plum950 else Color.White
                            )
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Install Update",
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Play Protect Guidance Tip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0x1A6366F1) else Color(0x106366F1),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💡 Note: If Google Play Protect asks \"Harmful app blocked\", simply tap \"Install anyway\" to complete.",
                                    fontFamily = SfPro,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = if (isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA)
                                )
                            }
                        }
                    }
                }

                is UpdateDownloadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = downloadState.message,
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppleIndigo,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Retry Download", fontFamily = SfPro, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
