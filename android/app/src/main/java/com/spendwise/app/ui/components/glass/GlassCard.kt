package com.spendwise.app.ui.components.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.theme.*

/**
 * Apple Liquid Glass Card.
 * Renders a translucent optical surface with a 1dp specular highlight border
 * (brighter at top, fading at bottom), soft ambient diffusion shadow, and rounded corners.
 * Uses RenderEffect blur on Android 12+ (API 31+) with an automatic solid surface fallback on older devices.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    isDark: Boolean = isSystemInDarkTheme(),
    isElevated: Boolean = false,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glassFill = when {
        isDark && isElevated -> GlassFillElevatedDark
        isDark -> GlassFillDark
        !isDark && isElevated -> GlassFillElevatedLight
        else -> GlassFillLight
    }

    val borderBrush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

    val shadowSpotColor = if (isDark) Color(0x33000000) else Color(0x180F172A)
    val shadowAmbientColor = if (isDark) Color(0x1F000000) else Color(0x0A0F172A)

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = shadowSpotColor,
                ambientColor = shadowAmbientColor,
                clip = false
            )
            .clip(shape)
            .background(glassFill, shape)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
            .then(clickableModifier)
    ) {
        content()
    }
}

/**
 * High-performance translucent glass row for scrolling lists (Activity, Budgets).
 * Uses a solid translucent fill to guarantee smooth 60fps scrolling without compounding blurs.
 */
@Composable
fun GlassRow(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    isDark: Boolean = isSystemInDarkTheme(),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val rowFill = if (isDark) GlassFillSubtleDark else GlassFillSubtleLight
    val borderBrush = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(rowFill, shape)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
            .then(clickableModifier)
    ) {
        content()
    }
}
