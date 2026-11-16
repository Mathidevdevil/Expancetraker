package com.spendwise.app.ui.components.glass

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.spendwise.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Liquid Glass Aurora Background.
 * Renders a soft, organic canvas with drifting gradient chromatic blobs
 * (Apple Indigo, Sky Cyan, Mint Green) that move along gentle 25-second sinusoidal loops.
 * Respects Android "Reduce Motion" / disabled animator duration scale.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isReduceMotion = remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    // 25-second continuous looping time progress [0f .. 2 * PI]
    val infiniteTransition = rememberInfiniteTransition(label = "AuroraTransition")
    val animProgress by if (!isReduceMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "AuroraDrift"
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    val baseColor = if (isDark) CanvasBaseDark else CanvasBaseLight

    // Chromatic orb colors with balanced opacity for light vs dark modes
    val indigoColor = if (isDark) AuroraIndigo.copy(alpha = 0.22f) else AuroraIndigo.copy(alpha = 0.28f)
    val skyColor = if (isDark) AuroraSky.copy(alpha = 0.16f) else AuroraSky.copy(alpha = 0.22f)
    val mintColor = if (isDark) AuroraMint.copy(alpha = 0.14f) else AuroraMint.copy(alpha = 0.18f)
    val roseColor = if (isDark) AuroraRose.copy(alpha = 0.10f) else AuroraRose.copy(alpha = 0.14f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (width > 0 && height > 0) {
                // 1. Primary Indigo Orb (Top-Right / Center Drift)
                val orb1X = width * 0.72f + (width * 0.18f) * sin(animProgress.toDouble()).toFloat()
                val orb1Y = height * 0.22f + (height * 0.12f) * cos(animProgress.toDouble()).toFloat()
                val orb1Radius = width * 0.65f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(indigoColor, Color.Transparent),
                        center = Offset(orb1X, orb1Y),
                        radius = orb1Radius
                    ),
                    radius = orb1Radius,
                    center = Offset(orb1X, orb1Y)
                )

                // 2. Sky Cyan Orb (Top-Left / Middle-Left Drift)
                val orb2X = width * 0.25f + (width * 0.15f) * cos((animProgress * 0.9).toDouble()).toFloat()
                val orb2Y = height * 0.45f + (height * 0.16f) * sin((animProgress * 0.9).toDouble()).toFloat()
                val orb2Radius = width * 0.60f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(skyColor, Color.Transparent),
                        center = Offset(orb2X, orb2Y),
                        radius = orb2Radius
                    ),
                    radius = orb2Radius,
                    center = Offset(orb2X, orb2Y)
                )

                // 3. Mint Green Orb (Bottom-Right / Floating Ascend)
                val orb3X = width * 0.80f + (width * 0.16f) * sin((animProgress * 1.1).toDouble()).toFloat()
                val orb3Y = height * 0.78f + (height * 0.14f) * cos((animProgress * 1.1).toDouble()).toFloat()
                val orb3Radius = width * 0.58f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(mintColor, Color.Transparent),
                        center = Offset(orb3X, orb3Y),
                        radius = orb3Radius
                    ),
                    radius = orb3Radius,
                    center = Offset(orb3X, orb3Y)
                )

                // 4. Subtle Rose Orb (Bottom-Left Glow)
                val orb4X = width * 0.20f + (width * 0.12f) * cos((animProgress * 0.7).toDouble()).toFloat()
                val orb4Y = height * 0.88f + (height * 0.10f) * sin((animProgress * 0.7).toDouble()).toFloat()
                val orb4Radius = width * 0.50f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(roseColor, Color.Transparent),
                        center = Offset(orb4X, orb4Y),
                        radius = orb4Radius
                    ),
                    radius = orb4Radius,
                    center = Offset(orb4X, orb4Y)
                )
            }
        }

        // Screen content layer
        content()
    }
}
