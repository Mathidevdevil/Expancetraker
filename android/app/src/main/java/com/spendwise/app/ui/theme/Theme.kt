package com.spendwise.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RoyalPlumDarkColorScheme = darkColorScheme(
    primary = Gold500,
    onPrimary = Plum900,
    primaryContainer = Plum800,
    onPrimaryContainer = Cream50,
    secondary = IncomeDark,
    onSecondary = Plum900,
    secondaryContainer = Color(0x28FBF5EA),
    onSecondaryContainer = Cream50,
    tertiary = Plum300,
    onTertiary = Plum900,
    background = Plum950,
    onBackground = Cream50,
    surface = Plum900,
    onSurface = Cream50,
    surfaceVariant = Plum800,
    onSurfaceVariant = Taupe300,
    outline = Gold500.copy(alpha = 0.30f),
    outlineVariant = Gold500.copy(alpha = 0.50f),
    error = ExpenseDark,
    onError = Plum900
)

private val RoyalPlumLightColorScheme = lightColorScheme(
    primary = Plum700,
    onPrimary = Cream50,
    primaryContainer = Cream200,
    onPrimaryContainer = Plum900,
    secondary = IncomeLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xB3FFFFFF),
    onSecondaryContainer = Plum900,
    tertiary = GoldTextOnLight,
    onTertiary = Color.White,
    background = Cream100,
    onBackground = Plum900,
    surface = Cream50,
    onSurface = Plum900,
    surfaceVariant = Cream200,
    onSurfaceVariant = Taupe700,
    outline = Gold300.copy(alpha = 0.40f),
    outlineVariant = Gold300.copy(alpha = 0.70f),
    error = ExpenseLight,
    onError = Color.White
)

@Composable
fun SpendwiseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RoyalPlumDarkColorScheme else RoyalPlumLightColorScheme
    val spendwiseColors = if (darkTheme) DarkSpendwiseColors else LightSpendwiseColors

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

    CompositionLocalProvider(LocalSpendwiseColors provides spendwiseColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
