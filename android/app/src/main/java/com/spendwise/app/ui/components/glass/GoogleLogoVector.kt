package com.spendwise.app.ui.components.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Authentic 4-color Google "G" logo vector rendered cleanly via Compose Canvas.
 */
@Composable
fun GoogleLogoVector(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)
        val blue = Color(0xFF4285F4)

        // 1. Blue horizontal bar & right arc
        val bluePath = Path().apply {
            moveTo(w * 0.98f, h * 0.50f)
            lineTo(w * 0.52f, h * 0.50f)
            lineTo(w * 0.52f, h * 0.68f)
            lineTo(w * 0.81f, h * 0.68f)
            arcTo(
                rect = Rect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f),
                startAngleDegrees = 20f,
                sweepAngleDegrees = 40f,
                forceMoveTo = false
            )
            lineTo(w * 0.98f, h * 0.50f)
            close()
        }
        drawPath(bluePath, blue, style = Fill)

        // 2. Green bottom arc
        val greenPath = Path().apply {
            arcTo(
                rect = Rect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f),
                startAngleDegrees = 60f,
                sweepAngleDegrees = 90f,
                forceMoveTo = true
            )
            arcTo(
                rect = Rect(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f),
                startAngleDegrees = 150f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(greenPath, green, style = Fill)

        // 3. Yellow left arc
        val yellowPath = Path().apply {
            arcTo(
                rect = Rect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f),
                startAngleDegrees = 150f,
                sweepAngleDegrees = 90f,
                forceMoveTo = true
            )
            arcTo(
                rect = Rect(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f),
                startAngleDegrees = 240f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(yellowPath, yellow, style = Fill)

        // 4. Red top arc
        val redPath = Path().apply {
            arcTo(
                rect = Rect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f),
                startAngleDegrees = 240f,
                sweepAngleDegrees = 100f,
                forceMoveTo = true
            )
            arcTo(
                rect = Rect(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f),
                startAngleDegrees = 340f,
                sweepAngleDegrees = -100f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(redPath, red, style = Fill)
    }
}
