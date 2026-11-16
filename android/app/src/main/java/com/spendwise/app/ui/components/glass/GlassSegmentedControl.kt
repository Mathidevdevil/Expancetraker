package com.spendwise.app.ui.components.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.ui.theme.*

/**
 * Apple Liquid Glass Segmented Pill Control.
 * Renders a frosted track with a sliding spring-physics lens capsule.
 */
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val haptic = LocalHapticFeedback.current
    val trackFill = if (isDark) Color(0x14FFFFFF) else Color(0x240F172A)
    val trackBorder = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

    val lensFill = if (isDark) AppleIndigoLight else AppleIndigo
    val lensBorder = if (isDark) DarkGlassLensBorderBrush else LightGlassLensBorderBrush

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackFill, RoundedCornerShape(16.dp))
            .border(width = 1.dp, brush = trackBorder, shape = RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val weight = 1f

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        spotColor = AppleIndigo.copy(alpha = 0.35f),
                                        ambientColor = Color.Transparent,
                                        clip = false
                                    )
                                    .background(lensFill, RoundedCornerShape(12.dp))
                                    .border(width = 1.dp, brush = lensBorder, shape = RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else if (isDark) TextMutedDark else TextMutedLight,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
