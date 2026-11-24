package com.spendwise.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =========================================================================
// Spendwise 2026: Royal Plum + Champagne Taupe + Gold Trim Design System
// =========================================================================

// --- 1. Royal Plum (Primary Brand Family) ---
val Plum950 = Color(0xFF1A0810) // Deepest dark ground, gradient start
val Plum900 = Color(0xFF2A0F1A) // Glass card fills, dark bottom bar, text on gold
val Plum800 = Color(0xFF3E1426) // Elevated glass surfaces
val Plum700 = Color(0xFF5A1A2E) // Core brand plum, light primary button, ambient glow
val Plum600 = Color(0xFF7A2A3F) // Mid plum, interactive hover/press
val Plum500 = Color(0xFF9C4259) // Category chart #1, accent borders
val Plum300 = Color(0xFFC98A9A) // Velvet highlight, subtle card borders

// --- 2. Champagne Taupe (Neutral Family) ---
val Taupe900 = Color(0xFF3F372E) // Deep neutral boundary
val Taupe700 = Color(0xFF6B5F52) // Light secondary text (4.98:1 contrast on cream-100)
val Taupe500 = Color(0xFF8F8272) // Core neutral, disabled text, unfocused input border
val Taupe300 = Color(0xFFB9AD9A) // Dark secondary text (8.02:1 contrast on plum-900), chart #3
val Taupe100 = Color(0xFFDDD3C3) // Light card borders, soft dividers

// --- 3. Gold Trim (Accent Family) ---
val Gold300 = Color(0xFFE8CC94) // Light-mode card borders, specular glass highlights
val Gold500 = Color(0xFFD4AF6A) // Core accent, dark primary buttons, active tabs, focus glow
val Gold700 = Color(0xFFA67C36) // Pressed states, high-contrast gold boundaries
val GoldTextOnLight = Color(0xFF7A5A14) // High-contrast links & accents on light ground (5.16:1)

// --- 4. Cream (Light Ground Family) ---
val Cream50 = Color(0xFFFBF5EA) // Dark primary text (16.24:1 contrast on plum-900), button labels
val Cream100 = Color(0xFFF3E6D3) // Light page background ground
val Cream200 = Color(0xFFE9D8BF) // Light surface elevated, input backgrounds

// --- 5. Semantic Colors ---
val IncomeDark = Color(0xFF7FD1A8) // Positive cash flow (Dark mode)
val IncomeLight = Color(0xFF1E8A5E) // Positive cash flow (Light mode)

val ExpenseDark = Color(0xFFF1887A) // Negative cash flow (Dark mode)
val ExpenseLight = Color(0xFFB8372C) // Negative cash flow (Light mode)

val WarningDark = Color(0xFFF2A65A) // 80% budget alert (Dark mode)
val WarningLight = Color(0xFFB7650F) // 80% budget alert (Light mode)

// --- 6. Chart Category Palette (Strict Order) ---
val ChartCategory1 = Plum500             // #9C4259
val ChartCategory2 = Gold500             // #D4AF6A
val ChartCategory3 = Taupe300            // #B9AD9A
val ChartCategory4 = Color(0xFFD98BA0)   // Rose
val ChartCategory5 = Color(0xFF8FB39A)   // Sage
val ChartCategory6 = Color(0xFF8A6A52)   // Mocha

val SpendwiseChartColors = listOf(
    ChartCategory1,
    ChartCategory2,
    ChartCategory3,
    ChartCategory4,
    ChartCategory5,
    ChartCategory6
)

// --- 7. Glass Surfaces & Specular Border Brushes ---
// Dark mode: translucent cream-tinted fill (6-10% white/cream over plum), 1dp gold-500 border at 30% brightest at top edge
val DarkGlassFill = Color(0x18FBF5EA)          // ~9.5% Cream-50 over Plum
val DarkGlassFillElevated = Color(0x28FBF5EA)  // ~16% Cream-50 for Sheets / Dialogs
val DarkGlassFillSubtle = Color(0x0FFBF5EA)    // ~6% for smooth list rows

// Light mode: white at 55-65% opacity with gold-300 border
val LightGlassFill = Color(0x9EFFFFFF)         // ~62% Translucent White
val LightGlassFillElevated = Color(0xC4FFFFFF) // ~77% Floating Lens / Dialogs
val LightGlassFillSubtle = Color(0x66FFFFFF)   // ~40% List items

val DarkGlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Gold500.copy(alpha = 0.45f), // Bright specular top edge
        Gold500.copy(alpha = 0.25f), // Mid fade
        Gold500.copy(alpha = 0.08f)  // Faded downward boundary
    )
)

val LightGlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Gold300.copy(alpha = 0.70f), // Soft gold top edge
        Gold300.copy(alpha = 0.40f), // Mid reflection
        Gold300.copy(alpha = 0.20f)  // Bottom edge
    )
)

val DarkGlassLensBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Gold500.copy(alpha = 0.60f),
        Gold500.copy(alpha = 0.35f),
        Gold500.copy(alpha = 0.15f)
    )
)

val LightGlassLensBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Gold300.copy(alpha = 0.85f),
        Gold300.copy(alpha = 0.50f),
        Gold300.copy(alpha = 0.30f)
    )
)

// Background Gradients
val DarkBackgroundStart = Plum950
val DarkBackgroundEnd = Color(0xFF12060B)
val LightBackgroundStart = Cream100
val LightBackgroundEnd = Cream50

val DarkPageBackgroundBrush = Brush.verticalGradient(
    colors = listOf(DarkBackgroundStart, DarkBackgroundEnd)
)

val LightPageBackgroundBrush = Brush.verticalGradient(
    colors = listOf(LightBackgroundStart, LightBackgroundEnd)
)

// --- 8. Unified SpendwiseColors Token Object ---
@Immutable
data class SpendwiseColors(
    val isDark: Boolean,
    // Backgrounds
    val pageBackgroundStart: Color,
    val pageBackgroundEnd: Color,
    val pageBackgroundBrush: Brush,
    val glowPrimary: Color,
    val glowSecondary: Color,
    // Glass & Surfaces
    val glassFill: Color,
    val glassFillElevated: Color,
    val glassFillSubtle: Color,
    val glassBorderBrush: Brush,
    val glassLensBorderBrush: Brush,
    val solidSurface: Color,
    val solidSurfaceElevated: Color,
    // Typography
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    // Brand & Buttons
    val primaryBrand: Color,
    val primaryButtonBg: Color,
    val primaryButtonText: Color,
    val secondaryButtonOutline: Color,
    val accentGold: Color,
    val accentGoldText: Color,
    // Input Fields
    val inputBg: Color,
    val inputBorder: Color,
    val inputBorderFocused: Color,
    val inputPlaceholder: Color,
    // Bottom Bar
    val bottomBarGlass: Color,
    val bottomBarBorderBrush: Brush,
    val bottomBarActive: Color,
    val bottomBarInactive: Color,
    val bottomBarAddButton: Color,
    val bottomBarAddIcon: Color,
    // Semantics
    val income: Color,
    val expense: Color,
    val warning: Color,
    // Category Palette
    val categoryColors: List<Color>
)

val DarkSpendwiseColors = SpendwiseColors(
    isDark = true,
    pageBackgroundStart = DarkBackgroundStart,
    pageBackgroundEnd = DarkBackgroundEnd,
    pageBackgroundBrush = DarkPageBackgroundBrush,
    glowPrimary = Plum700,
    glowSecondary = Gold500,
    glassFill = DarkGlassFill,
    glassFillElevated = DarkGlassFillElevated,
    glassFillSubtle = DarkGlassFillSubtle,
    glassBorderBrush = DarkGlassBorderBrush,
    glassLensBorderBrush = DarkGlassLensBorderBrush,
    solidSurface = Plum900,
    solidSurfaceElevated = Plum800,
    textPrimary = Cream50,
    textSecondary = Taupe300,
    textDisabled = Taupe500,
    primaryBrand = Plum700,
    primaryButtonBg = Gold500,
    primaryButtonText = Plum900,
    secondaryButtonOutline = Gold500,
    accentGold = Gold500,
    accentGoldText = Gold500,
    inputBg = Color(0x18FBF5EA),
    inputBorder = Taupe500,
    inputBorderFocused = Gold500,
    inputPlaceholder = Taupe500,
    bottomBarGlass = Plum900.copy(alpha = 0.88f),
    bottomBarBorderBrush = DarkGlassBorderBrush,
    bottomBarActive = Gold500,
    bottomBarInactive = Taupe300,
    bottomBarAddButton = Gold500,
    bottomBarAddIcon = Plum900,
    income = IncomeDark,
    expense = ExpenseDark,
    warning = WarningDark,
    categoryColors = SpendwiseChartColors
)

val LightSpendwiseColors = SpendwiseColors(
    isDark = false,
    pageBackgroundStart = LightBackgroundStart,
    pageBackgroundEnd = LightBackgroundEnd,
    pageBackgroundBrush = LightPageBackgroundBrush,
    glowPrimary = Taupe300,
    glowSecondary = Plum300,
    glassFill = LightGlassFill,
    glassFillElevated = LightGlassFillElevated,
    glassFillSubtle = LightGlassFillSubtle,
    glassBorderBrush = LightGlassBorderBrush,
    glassLensBorderBrush = LightGlassLensBorderBrush,
    solidSurface = Cream50,
    solidSurfaceElevated = Cream200,
    textPrimary = Plum900,
    textSecondary = Taupe700,
    textDisabled = Taupe500,
    primaryBrand = Plum700,
    primaryButtonBg = Plum700,
    primaryButtonText = Cream50,
    secondaryButtonOutline = Plum700,
    accentGold = Gold300,
    accentGoldText = GoldTextOnLight,
    inputBg = Color(0x80FFFFFF),
    inputBorder = Taupe300,
    inputBorderFocused = GoldTextOnLight,
    inputPlaceholder = Taupe700,
    bottomBarGlass = Cream100.copy(alpha = 0.90f),
    bottomBarBorderBrush = LightGlassBorderBrush,
    bottomBarActive = Plum700,
    bottomBarInactive = Taupe700,
    bottomBarAddButton = Plum700,
    bottomBarAddIcon = Cream50,
    income = IncomeLight,
    expense = ExpenseLight,
    warning = WarningLight,
    categoryColors = SpendwiseChartColors
)

val LocalSpendwiseColors = staticCompositionLocalOf { DarkSpendwiseColors }

// Direct convenience accessor from MaterialTheme
val androidx.compose.material3.MaterialTheme.spendwiseColors: SpendwiseColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSpendwiseColors.current

// --- 9. Backward-Compatibility Aliases for Smooth Migration ---
val CanvasBaseLight = LightBackgroundStart
val CanvasBaseDark = DarkBackgroundStart

val AuroraIndigo = Plum700
val AuroraSky = Plum500
val AuroraMint = Gold500
val AuroraRose = Plum300

val GlassFillLight = LightGlassFill
val GlassFillElevatedLight = LightGlassFillElevated
val GlassFillSubtleLight = LightGlassFillSubtle

val GlassFillDark = DarkGlassFill
val GlassFillElevatedDark = DarkGlassFillElevated
val GlassFillSubtleDark = DarkGlassFillSubtle

val SolidFallbackLight = Cream50
val SolidFallbackDark = Plum900

val AppleIndigo = Plum700
val AppleIndigoLight = Gold500
val AppleIndigoDark = Plum900

val IncomeEmerald = IncomeLight
val IncomeEmeraldLuminous = IncomeDark
val ExpenseRose = ExpenseLight
val ExpenseRoseLuminous = ExpenseDark
val WarningAmber = WarningDark
val TravelCyan = Gold500

val TextPrimaryDark = Cream50
val TextMutedDark = Taupe300
val TextTertiaryDark = Taupe500

val TextPrimaryLight = Plum900
val TextMutedLight = Taupe700
val TextTertiaryLight = Taupe500

val DarkBase = DarkBackgroundStart
val DarkSurface1 = Plum900
val DarkSurface2 = Plum800
val DarkSurface3 = Plum700
val DarkBorder = Gold500.copy(alpha = 0.30f)
val DarkBorderStrong = Gold500.copy(alpha = 0.50f)

val LightBase = LightBackgroundStart
val LightSurface1 = Cream50
val LightSurface2 = Cream100
val LightSurface3 = Cream200
val LightBorder = Gold300.copy(alpha = 0.40f)
val LightBorderStrong = Gold300.copy(alpha = 0.70f)

val IndigoPrimary = Plum700
val IndigoPrimaryLight = Gold500
val IndigoPrimaryDark = Plum900
val RadiantViolet = Plum600
val EmeraldNeon = IncomeDark
val EmeraldIncome = IncomeDark
val CoralRose = ExpenseDark
val RoseExpense = ExpenseDark
val AmberGlow = WarningDark
val AmberWarning = WarningDark
val SkyTravel = Gold500
val ObsidianBlack = DarkBackgroundEnd
val GlassSurface = DarkSurface1
val GlassSurfaceElevated = DarkSurface2
val GlassBorder = DarkBorder

val BrandNavy = Plum950
val BrandNavyGradientStart = Plum950
val BrandNavyGradientMid = Plum900
val BrandNavyGradientEnd = DarkBackgroundEnd
val BrandGold = Gold500
val BrandGoldHover = Gold300
val BrandGoldDark = Gold700
val BrandCardLight = Cream50
val BrandCardDark = Plum900
val BrandCardBorderDark = Gold500.copy(alpha = 0.30f)
val BrandInputBgLight = Color(0x80FFFFFF)
val BrandInputBgDark = Color(0x18FBF5EA)
val BrandInputBorderLight = Taupe300
val BrandInputBorderDark = Taupe500
val BrandTextNavy = Plum900
val BrandTextMutedLight = Taupe700
val BrandTextMutedDark = Taupe300
