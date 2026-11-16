package com.spendwise.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = AppleIndigoLight,
    onPrimary = Color.White,
    primaryContainer = Color(0x2BFFFFFF),
    onPrimaryContainer = AppleIndigoLight,
    secondary = IncomeEmeraldLuminous,
    onSecondary = Color.White,
    secondaryContainer = Color(0x1FFFFFFF),
    onSecondaryContainer = Color.White,
    tertiary = TravelCyan,
    onTertiary = Color.White,
    background = CanvasBaseDark,
    onBackground = TextPrimaryDark,
    surface = GlassFillDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0x21FFFFFF),
    onSurfaceVariant = TextMutedDark,
    outline = Color(0x1FFFFFFF),
    outlineVariant = Color(0x33FFFFFF),
    error = ExpenseRoseLuminous,
    onError = Color.White
)

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = AppleIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xCCEEF2FF),
    onPrimaryContainer = AppleIndigo,
    secondary = IncomeEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xB3FFFFFF),
    onSecondaryContainer = TextPrimaryLight,
    tertiary = TravelCyan,
    onTertiary = Color.White,
    background = CanvasBaseLight,
    onBackground = TextPrimaryLight,
    surface = GlassFillLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0x80F1F3F9),
    onSurfaceVariant = TextMutedLight,
    outline = Color(0x260F172A),
    outlineVariant = Color(0x400F172A),
    error = ExpenseRose,
    onError = Color.White
)

@Composable
fun SpendwiseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LiquidGlassDarkColorScheme else LiquidGlassLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Edge-to-edge transparent system bars for seamless aurora backdrop
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
