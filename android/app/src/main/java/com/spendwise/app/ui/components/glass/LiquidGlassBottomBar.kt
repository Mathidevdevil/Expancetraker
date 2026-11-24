package com.spendwise.app.ui.components.glass

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.ui.theme.*

data class LiquidTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

/**
 * Curved Notched Bar Shape.
 * Produces a smooth concave notch at the top center for the raised add action button.
 */
class NotchedBarShape(
    private val notchRadius: Dp = 30.dp,
    private val cornerRadius: Dp = 26.dp,
    private val notchMargin: Dp = 6.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val r = with(density) { cornerRadius.toPx() }
            val nr = with(density) { (notchRadius + notchMargin).toPx() }
            val cx = size.width / 2f
            val h = size.height
            val w = size.width

            // Top-left corner
            moveTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)

            // Line to left side of notch
            val notchLeft = cx - nr
            lineTo(notchLeft - nr * 0.15f, 0f)

            // Concave notch cutout
            cubicTo(
                notchLeft + nr * 0.2f, 0f,
                cx - nr * 0.6f, nr * 0.8f,
                cx, nr * 0.8f
            )
            val notchRight = cx + nr
            cubicTo(
                cx + nr * 0.6f, nr * 0.8f,
                notchRight - nr * 0.2f, 0f,
                notchRight + nr * 0.15f, 0f
            )

            // Line to top-right corner
            lineTo(w - r, 0f)
            quadraticBezierTo(w, 0f, w, r)

            // Right edge to bottom-right
            lineTo(w, h - r)
            quadraticBezierTo(w, h, w - r, h)

            // Bottom edge to bottom-left
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r)

            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Apple-style Liquid Glass Bottom Bar with a top-center curved notch
 * and a raised circular gold action button.
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<LiquidTabItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val haptic = LocalHapticFeedback.current
    val barShape = remember { NotchedBarShape(notchRadius = 30.dp, cornerRadius = 28.dp) }

    val baseFill = if (isDark) Plum900.copy(alpha = 0.88f) else Cream100.copy(alpha = 0.92f)
    val sheenBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Gold500.copy(alpha = 0.25f), Color(0x05FFFFFF))
        } else {
            listOf(Gold500.copy(alpha = 0.30f), Color(0x10FFFFFF))
        }
    )
    val borderBrush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

    val shadowSpotColor = if (isDark) Plum950.copy(alpha = 0.8f) else Taupe900.copy(alpha = 0.25f)
    val shadowAmbientColor = if (isDark) Color(0x33000000) else Taupe700.copy(alpha = 0.15f)

    val leftTabs = if (tabs.size >= 2) tabs.subList(0, 2) else tabs
    val rightTabs = if (tabs.size > 2) tabs.subList(2, tabs.size) else emptyList()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. Notched Glass Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = barShape,
                    spotColor = shadowSpotColor,
                    ambientColor = shadowAmbientColor,
                    clip = false
                )
                .clip(barShape)
                .background(baseFill, barShape)
                .background(sheenBrush, barShape)
                .border(width = 1.dp, brush = borderBrush, shape = barShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Tabs (Overview, Activity)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leftTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        BottomBarTabItem(
                            tab = tab,
                            isSelected = isSelected,
                            isDark = isDark,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(tab.route)
                                }
                            }
                        )
                    }
                }

                // Center Notch Void for the Raised Button (width 64dp)
                Spacer(modifier = Modifier.width(64.dp))

                // Right Tabs (Budgets, Travel)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rightTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        BottomBarTabItem(
                            tab = tab,
                            isSelected = isSelected,
                            isDark = isDark,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigate(tab.route)
                                }
                            }
                        )
                    }
                }
            }
        }

        // 2. Raised Round Gold FAB nestled inside the Notch
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-6).dp)
        ) {
            RaisedGoldAddButton(
                onClick = onAddClick
            )
        }
    }
}

@Composable
private fun BottomBarTabItem(
    tab: LiquidTabItem,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "TabIconScale"
    )

    val activeColor = if (isDark) Gold500 else Plum700
    val inactiveColor = if (isDark) Taupe300 else Taupe700

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 6.dp)
            .scale(iconScale)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = tab.title,
            fontFamily = SfPro,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Short Gold Underline Indicator for Active Tab
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Gold500)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

/**
 * Raised Circular Gold Button.
 * 54dp diameter, gold gradient with navy icon, sits elevated in the notch.
 */
@Composable
fun RaisedGoldAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val shape = CircleShape

    val goldGradient = Brush.verticalGradient(
        colors = listOf(
            Gold300,
            Gold500,
            Gold700
        )
    )

    Box(
        modifier = modifier
            .size(54.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                spotColor = Gold500.copy(alpha = 0.55f),
                ambientColor = Gold500.copy(alpha = 0.25f),
                clip = false
            )
            .clip(shape)
            .background(goldGradient, shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xE6FFFFFF),
                        Color(0x33FFFFFF)
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
            tint = Plum900,
            modifier = Modifier.size(26.dp)
        )
    }
}
