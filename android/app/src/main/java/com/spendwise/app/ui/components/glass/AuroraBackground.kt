package com.spendwise.app.ui.components.glass

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
 * Royal Plum + Champagne Taupe + Gold Trim Aurora Canvas.
 * Dark theme: Vertical gradient Plum-950 (#1A0810) to #12060B, with a soft Plum-700 (#5A1A2E) glow
 * at top-left and a faint Gold-500 (#D4AF6A) glow at bottom-right.
 * Light theme: Cream-100 (#F3E6D3) ground with soft taupe and plum ambient glows.
 * Respects Android "Reduce Motion" system setting.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.spendwiseColors
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

    // 25-second slow organic looping progress
    val infiniteTransition = rememberInfiniteTransition(label = "AuroraPlumTransition")
    val animProgress by if (!isReduceMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "AuroraPlumDrift"
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackgroundBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (width > 0 && height > 0) {
                if (isDark) {
                    // Dark theme ambient glows:
                    // 1. Soft Plum-700 ambient glow at top-left
                    val plumOrbX = width * 0.15f + (width * 0.10f) * sin(animProgress.toDouble()).toFloat()
                    val plumOrbY = height * 0.15f + (height * 0.08f) * cos(animProgress.toDouble()).toFloat()
                    val plumRadius = width * 0.75f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Plum700.copy(alpha = 0.28f),
                                Plum900.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(plumOrbX, plumOrbY),
                            radius = plumRadius
                        ),
                        radius = plumRadius,
                        center = Offset(plumOrbX, plumOrbY)
                    )

                    // 2. Faint Gold-500 glow at bottom-right
                    val goldOrbX = width * 0.85f + (width * 0.08f) * cos((animProgress * 0.8).toDouble()).toFloat()
                    val goldOrbY = height * 0.82f + (height * 0.09f) * sin((animProgress * 0.8).toDouble()).toFloat()
                    val goldRadius = width * 0.65f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Gold500.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(goldOrbX, goldOrbY),
                            radius = goldRadius
                        ),
                        radius = goldRadius,
                        center = Offset(goldOrbX, goldOrbY)
                    )
                } else {
                    // Light theme: soft taupe and plum glows over cream-100
                    val taupeOrbX = width * 0.20f + (width * 0.10f) * sin(animProgress.toDouble()).toFloat()
                    val taupeOrbY = height * 0.20f + (height * 0.08f) * cos(animProgress.toDouble()).toFloat()
                    val taupeRadius = width * 0.70f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Taupe300.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(taupeOrbX, taupeOrbY),
                            radius = taupeRadius
                        ),
                        radius = taupeRadius,
                        center = Offset(taupeOrbX, taupeOrbY)
                    )

                    val plumLightOrbX = width * 0.80f + (width * 0.08f) * cos((animProgress * 0.8).toDouble()).toFloat()
                    val plumLightOrbY = height * 0.75f + (height * 0.09f) * sin((animProgress * 0.8).toDouble()).toFloat()
                    val plumLightRadius = width * 0.60f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Plum300.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(plumLightOrbX, plumLightOrbY),
                            radius = plumLightRadius
                        ),
                        radius = plumLightRadius,
                        center = Offset(plumLightOrbX, plumLightOrbY)
                    )
                }
            }
        }

        content()
    }
}
