package com.spendwise.app.ui.theme

import androidx.compose.ui.graphics.Color

// 2026 Calm Premium Fintech Dark Theme (Near-Black Base)
val DarkBase = Color(0xFF090A0F)           // Base near-black background
val DarkSurface1 = Color(0xFF11131C)       // Cards, bottom nav, dialogs
val DarkSurface2 = Color(0xFF181B26)       // Elevated inner cards, active pills
val DarkSurface3 = Color(0xFF222638)       // Inputs, keypad keys, chip containers
val DarkBorder = Color(0x12FFFFFF)         // 7% hairline white border
val DarkBorderStrong = Color(0x24FFFFFF)   // 14% white border

// 2026 Clean Crisp Light Theme
val LightBase = Color(0xFFF8F9FC)          // Soft neutral light canvas
val LightSurface1 = Color(0xFFFFFFFF)      // Pure white cards
val LightSurface2 = Color(0xFFF1F3F9)      // Secondary surfaces, active pills
val LightSurface3 = Color(0xFFE4E7F2)      // Inputs, chip containers
val LightBorder = Color(0x140F172A)        // 8% subtle dark hairline border
val LightBorderStrong = Color(0x280F172A)  // 16% dark border

// Brand Accent & Semantic Colors
val IndigoPrimary = Color(0xFF6366F1)      // Main violet/indigo accent
val IndigoPrimaryLight = Color(0xFF818CF8) // Text/highlight indigo in dark mode
val IndigoPrimaryDark = Color(0xFF4F46E5)  // Deep indigo for light mode

val IncomeEmerald = Color(0xFF10B981)      // Positive cash flow, savings
val ExpenseRose = Color(0xFFF43F5E)        // Outflows, warnings, negative
val WarningAmber = Color(0xFFF59E0B)       // 80% budget threshold alerts
val TravelCyan = Color(0xFF06B6D4)         // Travel badges & icons

// Typography Colors
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextMutedDark = Color(0xFF94A3B8)
val TextTertiaryDark = Color(0xFF64748B)

val TextPrimaryLight = Color(0xFF0F172A)
val TextMutedLight = Color(0xFF64748B)
val TextTertiaryLight = Color(0xFF94A3B8)

// Compatibility Aliases for existing screen imports
val RadiantViolet = IndigoPrimary
val EmeraldNeon = IncomeEmerald
val EmeraldIncome = IncomeEmerald
val CoralRose = ExpenseRose
val RoseExpense = ExpenseRose
val AmberGlow = WarningAmber
val AmberWarning = WarningAmber
val SkyTravel = TravelCyan
val ObsidianBlack = DarkBase
val GlassSurface = DarkSurface1
val GlassSurfaceElevated = DarkSurface2
val GlassBorder = DarkBorder
