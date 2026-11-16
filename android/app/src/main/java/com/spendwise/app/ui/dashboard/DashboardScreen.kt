package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.ui.components.CategoryIconView
import com.spendwise.app.ui.components.EditProfileDialog
import com.spendwise.app.ui.components.glass.GlassCard
import com.spendwise.app.ui.components.glass.GlassIconButton
import com.spendwise.app.ui.components.glass.GlassRow
import com.spendwise.app.ui.theme.*
import com.spendwise.app.util.CurrencyFormatter
import java.util.Calendar

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToSettings: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var isEditProfileDialogOpen by remember { mutableStateOf(false) }

    // Time-based greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    // Group transactions by friendly date
    val groupedTransactions = remember(state.recentTransactions) {
        state.recentTransactions.groupBy { CurrencyFormatter.formatFriendlyDate(it.transaction.date) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 120.dp), // 120dp ensures floating bottom bar never covers content
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header: Greeting, Brand & Quick Glass Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val firstName = state.userProfile.name.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "there"
                    Text(
                        text = "$greeting, $firstName 👋",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) TextMutedDark else TextMutedLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Spendwise",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )
                }

                // Profile Avatar & Glass Action Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Dark/Light Mode Glass Icon
                    GlassIconButton(
                        onClick = onToggleTheme,
                        size = 42.dp,
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = if (isDark) WarningAmber else AppleIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings Glass Icon
                    GlassIconButton(
                        onClick = onNavigateToSettings,
                        size = 42.dp,
                        isDark = isDark
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isDark) TextMutedDark else TextMutedLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Profile Avatar Glass Lens (Tappable to Edit Profile)
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) AppleIndigo.copy(alpha = 0.25f) else AppleIndigo.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isDark) AppleIndigoLight else AppleIndigo
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isEditProfileDialogOpen = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = state.userProfile.initials,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isDark) AppleIndigoLight else AppleIndigo
                            )
                        }
                    }
                }
            }
        }

        // 2. Apple Liquid Glass Hero Card: Total Balance & Trend Indicator
        item {
            GlassCard(
                isDark = isDark,
                isElevated = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "TOTAL NET BALANCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )

                    // Hero Large Balance with Tabular Digits & Indian grouping
                    Text(
                        text = CurrencyFormatter.format(state.totalBalance, state.currencySymbol),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )

                    // Monthly Trend Indicator Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isNetPositive = state.monthlyIncome >= state.monthlyExpense
                        val trendColor = if (isNetPositive) IncomeEmerald else ExpenseRose
                        val netChange = state.monthlyIncome - state.monthlyExpense

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = trendColor.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, trendColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isNetPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = trendColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${CurrencyFormatter.formatWithSign(netChange, state.currencySymbol)} this month",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = trendColor
                                )
                            }
                        }

                        Text(
                            text = "vs previous period",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) TextMutedDark else TextMutedLight
                        )
                    }
                }
            }
        }

        // 3. Compact Glass Stat Cards with Mini Sparklines (Income & Expense)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Inflow Glass Card
                GlassCard(
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Inflow",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(IncomeEmerald)
                            )
                        }

                        Text(
                            text = CurrencyFormatter.formatWithSign(state.monthlyIncome, state.currencySymbol),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = IncomeEmerald
                        )

                        // Mini Sparkline
                        MiniSparkline(
                            isPositive = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    }
                }

                // Outflow Glass Card
                GlassCard(
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Outflow",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ExpenseRose)
                            )
                        }

                        Text(
                            text = CurrencyFormatter.format(state.monthlyExpense, state.currencySymbol).let {
                                if (state.monthlyExpense > 0) "-$it" else it
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRose
                        )

                        // Mini Sparkline
                        MiniSparkline(
                            isPositive = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    }
                }
            }
        }

        // 4. Category Spending Donut Chart + Legend
        if (state.topCategories.isNotEmpty()) {
            item {
                GlassCard(
                    isDark = isDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Spending by Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) TextPrimaryDark else TextPrimaryLight
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Donut Chart Graphic
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DonutChart(
                                    categories = state.topCategories,
                                    modifier = Modifier.size(110.dp)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${state.topCategories.firstOrNull()?.percentage?.toInt() ?: 0}%",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                    Text(
                                        text = "Top Share",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) TextMutedDark else TextMutedLight
                                    )
                                }
                            }

                            // Category Legend List
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.topCategories.take(4).forEach { catSpend ->
                                    val catColor = parseCategoryColor(catSpend.category?.color)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(catColor)
                                            )
                                            Text(
                                                text = catSpend.category?.name ?: "Other",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${CurrencyFormatter.format(catSpend.amount, state.currencySymbol, false)} (${catSpend.percentage.toInt()}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) TextMutedDark else TextMutedLight
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Active Travel Plans Card
        if (state.activeTrips.isNotEmpty()) {
            item {
                val trip = state.activeTrips.first()
                GlassCard(
                    isDark = isDark,
                    onClick = onNavigateToTrips,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.FlightTakeoff,
                                    contentDescription = null,
                                    tint = TravelCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Upcoming Travel Plan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                            }
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) AppleIndigoLight else AppleIndigo
                            )
                        }

                        GlassRow(
                            isDark = isDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = trip.destination,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = TravelCyan.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = CurrencyFormatter.formatTripDateRange(trip.startDate, trip.endDate),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TravelCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Budget: ${CurrencyFormatter.format(trip.budget, state.currencySymbol, false)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) TextMutedDark else TextMutedLight
                                    )
                                    Text(
                                        text = "Target Spend Plan",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = TravelCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Recent Activity List Grouped by Friendly Date
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) AppleIndigoLight else AppleIndigo,
                    modifier = Modifier.clickable { onNavigateToTransactions() }
                )
            }
        }

        if (state.recentTransactions.isEmpty()) {
            item {
                GlassCard(
                    isDark = isDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDark) Color(0x1AFFFFFF) else Color(0x1F0F172A),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = if (isDark) TextMutedDark else TextMutedLight,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextPrimaryDark else TextPrimaryLight
                            )
                            Text(
                                text = "Tap the '+' button to log your first expense",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                        }
                    }
                }
            }
        } else {
            // Grouped by friendly date with 60fps GlassRow rendering
            groupedTransactions.forEach { (dateHeader, txs) ->
                item {
                    Text(
                        text = dateHeader.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (isDark) TextMutedDark else TextMutedLight,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                items(txs, key = { it.transaction.id }) { item ->
                    val isIncome = item.transaction.type == "income"
                    GlassRow(
                        isDark = isDark,
                        onClick = onNavigateToTransactions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CategoryIconView(
                                iconName = item.category?.icon ?: "Tag",
                                colorHex = item.category?.color ?: "#5B4BE8",
                                size = 42.dp
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.transaction.note?.takeIf { it.isNotBlank() } ?: (item.category?.name ?: "Transaction"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.category?.name ?: "General"} • ${item.transaction.paymentMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextMutedDark else TextMutedLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = if (isIncome) {
                                    CurrencyFormatter.formatWithSign(item.transaction.amount, state.currencySymbol)
                                } else {
                                    "-${CurrencyFormatter.format(item.transaction.amount, state.currencySymbol)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) IncomeEmerald else if (isDark) TextPrimaryDark else TextPrimaryLight
                            )
                        }
                    }
                }
            }
        }
    }

    if (isEditProfileDialogOpen) {
        EditProfileDialog(
            currentProfile = state.userProfile,
            onDismiss = { isEditProfileDialogOpen = false },
            onSave = { newName, newEmail ->
                viewModel.updateProfile(newName, newEmail)
            }
        )
    }
}

// ----------------------------------------------------
// Custom Visual Components: Mini Sparkline & Donut Chart
// ----------------------------------------------------

@Composable
fun MiniSparkline(
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) IncomeEmerald else ExpenseRose
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val points = if (isPositive) {
            listOf(0.7f, 0.65f, 0.75f, 0.5f, 0.45f, 0.55f, 0.25f, 0.15f)
        } else {
            listOf(0.2f, 0.35f, 0.25f, 0.55f, 0.45f, 0.65f, 0.6f, 0.8f)
        }

        val stepX = width / (points.size - 1)
        val path = Path().apply {
            moveTo(0f, points[0] * height)
            for (i in 1 until points.size) {
                val prevX = (i - 1) * stepX
                val prevY = points[i - 1] * height
                val curX = i * stepX
                val curY = points[i] * height
                val midX = (prevX + curX) / 2f
                cubicTo(midX, prevY, midX, curY, curX, curY)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun DonutChart(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 14.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f

        categories.forEach { item ->
            val sweepAngle = (item.percentage / 100f) * 360f
            val itemColor = parseCategoryColor(item.category?.color)
            drawArc(
                color = itemColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle.coerceAtLeast(2f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
            startAngle += sweepAngle
        }
    }
}

fun parseCategoryColor(colorHex: String?): Color {
    return try {
        if (!colorHex.isNullOrBlank()) {
            Color(android.graphics.Color.parseColor(colorHex))
        } else {
            AppleIndigo
        }
    } catch (_: Exception) {
        AppleIndigo
    }
}
