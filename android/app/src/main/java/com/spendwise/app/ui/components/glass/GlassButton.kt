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
 * Apple Liquid Glass Primary Gradient Button.
 * Used for main actions (Create Account, Add Transaction, Verify Code).
 */
@Composable
fun GlassGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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

    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            AppleIndigo,
            Color(0xFF6C5CE7),
            Color(0xFF7D5FFF)
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                spotColor = AppleIndigo.copy(alpha = 0.35f),
                ambientColor = AppleIndigo.copy(alpha = 0.15f),
                clip = false
            )
            .clip(shape)
            .background(gradientBrush, shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x80FFFFFF),
                        Color(0x1AFFFFFF)
                    )
                ),
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
                color = Color.White,
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
                    color = Color.White
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
 * Apple Liquid Glass Secondary Outlined/Translucent Button.
 * Used for secondary actions (Continue with Google, Cancel, Secondary Links).
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
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1f, label = "ButtonPressScale")

    val fill = if (isDark) GlassFillDark else GlassFillLight
    val borderBrush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush
    val textColor = if (isDark) TextPrimaryDark else TextPrimaryLight

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                spotColor = if (isDark) Color(0x26000000) else Color(0x0F0F172A),
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
 * Circular Glass Icon Button.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val fill = if (isDark) GlassFillDark else GlassFillLight
    val borderBrush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                spotColor = if (isDark) Color(0x33000000) else Color(0x140F172A),
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
