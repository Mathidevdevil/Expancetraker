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

private val FintechDarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = DarkSurface3,
    onPrimaryContainer = IndigoPrimaryLight,
    secondary = IncomeEmerald,
    onSecondary = Color.White,
    secondaryContainer = DarkSurface2,
    onSecondaryContainer = Color.White,
    tertiary = TravelCyan,
    onTertiary = Color.White,
    background = DarkBase,
    onBackground = TextPrimaryDark,
    surface = DarkSurface1,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = TextMutedDark,
    outline = DarkBorder,
    outlineVariant = DarkBorderStrong,
    error = ExpenseRose,
    onError = Color.White
)

private val FintechLightColorScheme = lightColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = LightSurface3,
    onPrimaryContainer = IndigoPrimaryDark,
    secondary = IncomeEmerald,
    onSecondary = Color.White,
    secondaryContainer = LightSurface2,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = TravelCyan,
    onTertiary = Color.White,
    background = LightBase,
    onBackground = TextPrimaryLight,
    surface = LightSurface1,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = TextMutedLight,
    outline = LightBorder,
    outlineVariant = LightBorderStrong,
    error = ExpenseRose,
    onError = Color.White
)

@Composable
fun SpendwiseTheme(
    darkTheme: Boolean = true, // Default to 2026 calm dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FintechDarkColorScheme else FintechLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
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
