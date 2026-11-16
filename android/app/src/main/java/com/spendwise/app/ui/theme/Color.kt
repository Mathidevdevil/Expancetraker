package com.spendwise.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// Apple "Liquid Glass" Foundation & Canvases
// ==========================================

// Base Canvases beneath the drifting aurora
val CanvasBaseLight = Color(0xFFF4F4FA)          // Pale Lavender/Sky Base
val CanvasBaseDark = Color(0xFF080911)           // Obsidian Deep Navy Base

// Aurora Floating Gradient Orbs
val AuroraIndigo = Color(0xFF5B4BE8)            // Radiant Apple Indigo
val AuroraSky = Color(0xFF38BDF8)               // Luminous Sky Cyan
val AuroraMint = Color(0xFF34D399)              // Fresh Mint Green
val AuroraRose = Color(0xFFF43F5E)              // Ambient Coral Glow

// Glass Fill Layers (Translucent Optical Sheets)
val GlassFillLight = Color(0x9EFFFFFF)          // ~62% Translucent White
val GlassFillElevatedLight = Color(0xC4FFFFFF)  // ~77% Floating Lens / Dialogs
val GlassFillSubtleLight = Color(0x66FFFFFF)    // ~40% List items (60fps optimized)

val GlassFillDark = Color(0x18FFFFFF)           // ~9.5% Translucent White on Dark
val GlassFillElevatedDark = Color(0x2BFFFFFF)   // ~17% Floating Lens / Dialogs
val GlassFillSubtleDark = Color(0x0FFFFFFF)     // ~6% List items (60fps optimized)

// Fallback Solid Surfaces (for Android 11 & low-power GPUs)
val SolidFallbackLight = Color(0xFFEFF1F9)
val SolidFallbackDark = Color(0xFF121524)

// Specular Highlight Gradient Brushes (1dp Beveled Glass Edge)
val LightGlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xF0FFFFFF), // 94% crisp specular highlight at top edge
        Color(0x66FFFFFF), // 40% mid-reflection
        Color(0x1AFFFFFF)  // 10% bottom fade
    )
)

val DarkGlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x52FFFFFF), // 32% crisp specular highlight at top edge
        Color(0x21FFFFFF), // 13% mid-reflection
        Color(0x0AFFFFFF)  // 4% bottom fade
    )
)

val LightGlassLensBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0x80FFFFFF),
        Color(0x33FFFFFF)
    )
)

val DarkGlassLensBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x8AFFFFFF),
        Color(0x3DFFFFFF),
        Color(0x1AFFFFFF)
    )
)

// Brand Accent & Semantic Colors
val AppleIndigo = Color(0xFF5B4BE8)             // Primary Apple Indigo
val AppleIndigoLight = Color(0xFF7567F8)        // Radiant Violet in Dark Mode
val AppleIndigoDark = Color(0xFF4938D4)         // Deep Indigo

val IncomeEmerald = Color(0xFF10B981)           // Positive cash flow, credit
val IncomeEmeraldLuminous = Color(0xFF34D399)   // Dark mode emerald glow
val ExpenseRose = Color(0xFFF43F5E)             // Negative cash flow, coral debit
val ExpenseRoseLuminous = Color(0xFFFB7185)     // Dark mode rose glow
val WarningAmber = Color(0xFFF59E0B)            // 80% budget threshold alerts
val TravelCyan = Color(0xFF06B6D4)              // Travel badges & multi-currency

// Typography Colors
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextMutedDark = Color(0xFF94A3B8)
val TextTertiaryDark = Color(0xFF64748B)

val TextPrimaryLight = Color(0xFF0B0E17)
val TextMutedLight = Color(0xFF4B5563)
val TextTertiaryLight = Color(0xFF9CA3AF)

// Backwards-compatible aliases for existing screens
val DarkBase = CanvasBaseDark
val DarkSurface1 = SolidFallbackDark
val DarkSurface2 = Color(0xFF181B26)
val DarkSurface3 = Color(0xFF222638)
val DarkBorder = Color(0x1AFFFFFF)
val DarkBorderStrong = Color(0x33FFFFFF)

val LightBase = CanvasBaseLight
val LightSurface1 = SolidFallbackLight
val LightSurface2 = Color(0xFFF1F3F9)
val LightSurface3 = Color(0xFFE4E7F2)
val LightBorder = Color(0x140F172A)
val LightBorderStrong = Color(0x280F172A)

val IndigoPrimary = AppleIndigo
val IndigoPrimaryLight = AppleIndigoLight
val IndigoPrimaryDark = AppleIndigoDark
val RadiantViolet = AppleIndigo
val EmeraldNeon = IncomeEmerald
val EmeraldIncome = IncomeEmerald
val CoralRose = ExpenseRose
val RoseExpense = ExpenseRose
val AmberGlow = WarningAmber
val AmberWarning = WarningAmber
val SkyTravel = TravelCyan
val ObsidianBlack = CanvasBaseDark
val GlassSurface = DarkSurface1
val GlassSurfaceElevated = DarkSurface2
val GlassBorder = DarkBorder
