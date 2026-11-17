package com.spendwise.app.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spendwise 56dp Brand Wallet & Sparkle Logo Icon
 * Matches web/public/spendwise-icon.svg with deep navy gradient and champagne gold accents.
 */
@Composable
fun SpendwiseLogoVector(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val scale = w / 56f

        // Brand gradients
        val bgBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF181C38), Color(0xFF0D0F1F)),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val goldBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFFCE59F), Color(0xFFE7B94F), Color(0xFFC59328)),
            start = Offset(w * 0.25f, h * 0.25f),
            end = Offset(w * 0.75f, h * 0.75f)
        )
        val goldBorder = Color(0xFFE7B94F).copy(alpha = 0.35f)

        // 1. Base Rounded Container
        val cornerRadius = CornerRadius(16f * scale, 16f * scale)
        val inset = 1f * scale
        val rectSize = Size(w - (2 * inset), h - (2 * inset))
        drawRoundRect(
            brush = bgBrush,
            topLeft = Offset(inset, inset),
            size = rectSize,
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = goldBorder,
            topLeft = Offset(inset, inset),
            size = rectSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.5f * scale)
        )

        // 2. Wallet Body Rectangle
        val walletLeft = 15f * scale
        val walletTop = 19f * scale
        val walletWidth = 26f * scale
        val walletHeight = 19f * scale
        val walletRadius = CornerRadius(4f * scale, 4f * scale)
        drawRoundRect(
            brush = goldBrush,
            topLeft = Offset(walletLeft, walletTop),
            size = Size(walletWidth, walletHeight),
            cornerRadius = walletRadius,
            style = Stroke(width = 2.5f * scale)
        )

        // 3. Dividing Line
        drawLine(
            brush = goldBrush,
            start = Offset(15f * scale, 24f * scale),
            end = Offset(41f * scale, 24f * scale),
            strokeWidth = 2f * scale,
            cap = StrokeCap.Round
        )

        // 4. Wallet Clasp / Flap
        val flapPath = Path().apply {
            val fLeft = 32f * scale
            val fTop = 26f * scale
            val fRight = 41f * scale
            val fBottom = 33f * scale
            val r = 2f * scale

            moveTo(fLeft, fTop)
            lineTo(fRight - r, fTop)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(fRight - (2 * r), fTop, fRight, fTop + (2 * r)),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(fRight, fBottom - r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(fRight - (2 * r), fBottom - (2 * r), fRight, fBottom),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(fLeft, fBottom)
            close()
        }
        drawPath(flapPath, Color(0xFF14172B), style = Fill)
        drawPath(flapPath, brush = goldBrush, style = Stroke(width = 2f * scale))

        // Clasp gold dot
        drawCircle(
            brush = goldBrush,
            radius = 1.5f * scale,
            center = Offset(36f * scale, 29.5f * scale)
        )

        // 5. Rising Sparkle
        val sparkle = Path().apply {
            moveTo(22f * scale, 15f * scale)
            lineTo(23.2f * scale, 17.5f * scale)
            lineTo(25.7f * scale, 18.7f * scale)
            lineTo(23.2f * scale, 19.9f * scale)
            lineTo(22f * scale, 22.4f * scale)
            lineTo(20.8f * scale, 19.9f * scale)
            lineTo(18.3f * scale, 18.7f * scale)
            lineTo(20.8f * scale, 17.5f * scale)
            close()
        }
        drawPath(sparkle, brush = goldBrush, style = Fill)
    }
}
