package com.spendwise.app.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.theme.*

/**
 * Royal Plum + Champagne Taupe + Gold Trim Primary Button.
 * Dark theme: Gold-500 fill with Plum-900 bold text.
 * Light theme: Plum-700 fill with Cream-50 bold text.
 */
@Composable
fun GlassGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    isLoading: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1f, label = "ButtonPressScale")

    val gradientBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Gold300,
                Gold500,
                Gold700
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Plum600,
                Plum700,
                Plum800
            )
        )
    }

    val textColor = if (isDark) Plum900 else Cream50
    val shadowColor = if (isDark) Gold500.copy(alpha = 0.40f) else Plum700.copy(alpha = 0.25f)
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0x99FFFFFF),
                Gold500.copy(alpha = 0.40f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Plum300.copy(alpha = 0.50f),
                Plum700.copy(alpha = 0.25f)
            )
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                spotColor = shadowColor,
                ambientColor = shadowColor.copy(alpha = 0.20f),
                clip = false
            )
            .clip(shape)
            .background(gradientBrush, shape)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(vertical = 14.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = textColor,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * Secondary Outlined Glass Button with Gold Trim outline.
 */
@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    shape: Shape = RoundedCornerShape(18.dp),
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.spendwiseColors
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1f, label = "ButtonPressScale")

    val fill = colors.glassFill
    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Gold500.copy(alpha = 0.65f),
                Gold500.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Gold300.copy(alpha = 0.85f),
                Taupe300.copy(alpha = 0.40f)
            )
        )
    }
    val textColor = colors.textPrimary

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                spotColor = if (isDark) Plum950.copy(alpha = 0.5f) else Taupe700.copy(alpha = 0.1f),
                ambientColor = Color.Transparent,
                clip = false
            )
            .clip(shape)
            .background(fill, shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(vertical = 14.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

/**
 * Circular Glass Icon Button with Gold rim.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.spendwiseColors
    val haptic = LocalHapticFeedback.current
    val fill = colors.glassFill
    val borderBrush = colors.glassBorderBrush

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                spotColor = if (isDark) Plum950.copy(alpha = 0.5f) else Taupe700.copy(alpha = 0.1f),
                ambientColor = Color.Transparent,
                clip = false
            )
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(width = 1.dp, brush = borderBrush, shape = CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
