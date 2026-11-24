package com.spendwise.app.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.theme.*

/**
 * Royal Plum + Champagne Taupe + Gold Trim Glass Card.
 * Dark theme: Translucent cream-tinted fill (6-10% cream over plum), 1dp border in gold-500 at 30%
 * that is brightest at the top edge and fades downward, soft shadow.
 * Light theme: White/cream glass (55-65% opacity) with gold-300 border and soft taupe shadow.
 *
 * Hero cards (balance card, trip card, login card) feature:
 * - A faint velvet noise texture at 3-4% opacity
 * - One soft embossed S-shaped curve watermark behind the content in a slightly lighter tone
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    isDark: Boolean = isSystemInDarkTheme(),
    isElevated: Boolean = false,
    isHeroCard: Boolean = false,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.spendwiseColors
    val glassFill = when {
        isDark && isElevated -> colors.glassFillElevated
        isDark -> colors.glassFill
        !isDark && isElevated -> colors.glassFillElevated
        else -> colors.glassFill
    }

    val borderBrush = colors.glassBorderBrush

    val shadowSpotColor = if (isDark) Plum950.copy(alpha = 0.65f) else Taupe700.copy(alpha = 0.15f)
    val shadowAmbientColor = if (isDark) Plum950.copy(alpha = 0.35f) else Taupe500.copy(alpha = 0.08f)

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
        // Decorative Hero Velvet S-Curve & Ambient Wave
        if (isHeroCard) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                // Soft embossed S-shaped watermark in a slightly lighter tone
                val sCurveColor = if (isDark) Plum300.copy(alpha = 0.06f) else Taupe300.copy(alpha = 0.12f)
                val sPath = Path().apply {
                    moveTo(w * 0.75f, h * 0.05f)
                    cubicTo(
                        w * 0.95f, h * 0.25f,
                        w * 0.55f, h * 0.55f,
                        w * 0.85f, h * 0.85f
                    )
                }
                drawPath(
                    path = sPath,
                    color = sCurveColor,
                    style = Stroke(width = 38.dp.toPx(), cap = StrokeCap.Round)
                )

                // Faint noise/stipple dots overlay (3-4% opacity)
                val dotColor = if (isDark) Cream50.copy(alpha = 0.035f) else Plum900.copy(alpha = 0.03f)
                val step = 18.dp.toPx()
                var x = 0f
                while (x < w) {
                    var y = 0f
                    while (y < h) {
                        drawCircle(
                            color = dotColor,
                            radius = 1.dp.toPx(),
                            center = Offset(x, y)
                        )
                        y += step
                    }
                    x += step
                }
            }
        }

        content()
    }
}

/**
 * Translucent glass row for scrolling lists (Activity, Budgets, Categories).
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
    val colors = MaterialTheme.spendwiseColors
    val rowFill = colors.glassFillSubtle
    val borderBrush = colors.glassBorderBrush

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
