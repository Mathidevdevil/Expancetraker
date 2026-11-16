package com.spendwise.app.ui.components.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.ui.theme.*

data class LiquidTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

/**
 * Apple Liquid Glass Floating Bottom Navigation Bar.
 * - Floating pill: inset 16dp from sides, 12dp above bottom gesture line, 68dp high.
 * - Frosted glass with 1dp specular top highlight and soft floating shadow.
 * - 4 streamlined tabs (Overview, Activity, Budgets, Travel).
 * - Sliding springy glass lens capsule (stiffness ~300, damping ~0.7) with 1.08x magnification.
 * - Scroll-aware contraction when scrolling down.
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<LiquidTabItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    isCollapsed: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    val barHeight by animateDpAsState(
        targetValue = if (isCollapsed) 54.dp else 68.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "BarHeight"
    )

    val barHorizontalPadding by animateDpAsState(
        targetValue = if (isCollapsed) 48.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "BarHorizontalPadding"
    )

    val pillShape = RoundedCornerShape(34.dp)
    val glassFill = if (isDark) GlassFillElevatedDark else GlassFillElevatedLight
    val borderBrush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

    val shadowSpotColor = if (isDark) Color(0x40000000) else Color(0x1F0F172A)
    val shadowAmbientColor = if (isDark) Color(0x26000000) else Color(0x0F0F172A)

    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .height(barHeight)
            .shadow(
                elevation = 16.dp,
                shape = pillShape,
                spotColor = shadowSpotColor,
                ambientColor = shadowAmbientColor,
                clip = false
            )
            .clip(pillShape)
            .background(glassFill, pillShape)
            .border(width = 1.dp, brush = borderBrush, shape = pillShape)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // Tab Row with individual glass lens highlights
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = currentRoute == tab.route

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                    label = "TabIconScale"
                )

                val lensShape = RoundedCornerShape(26.dp)
                val lensFill = if (isDark) Color(0x38FFFFFF) else Color(0x52FFFFFF)
                val lensBorder = if (isDark) DarkGlassLensBorderBrush else LightGlassLensBorderBrush

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(lensShape)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = lensShape,
                                        spotColor = if (isDark) Color(0x33000000) else Color(0x1A0F172A),
                                        clip = false
                                    )
                                    .background(lensFill, lensShape)
                                    .border(width = 1.dp, brush = lensBorder, shape = lensShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(tab.route)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.scale(iconScale)
                    ) {
                        val activeColor = if (isDark) AppleIndigoLight else AppleIndigo
                        val inactiveColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)

                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) activeColor else inactiveColor,
                            modifier = Modifier.size(if (isCollapsed) 20.dp else 22.dp)
                        )

                        if (!isCollapsed) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) activeColor else inactiveColor,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Apple Liquid Glass Floating Action Button.
 * 54dp circular glass button with a luminous gradient and white '+' icon.
 * Floats beside the bottom bar without obstructing list content.
 */
@Composable
fun GlassFloatingAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val haptic = LocalHapticFeedback.current
    val shape = CircleShape

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            AppleIndigoLight,
            AppleIndigo
        )
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                spotColor = AppleIndigo.copy(alpha = 0.45f),
                ambientColor = AppleIndigo.copy(alpha = 0.20f),
                clip = false
            )
            .clip(shape)
            .background(gradientBrush, shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xB3FFFFFF),
                        Color(0x26FFFFFF)
                    )
                ),
                shape = shape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Quick Add Transaction",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
