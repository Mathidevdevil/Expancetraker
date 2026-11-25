package com.spendwise.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.model.BudgetEntity
import com.spendwise.app.data.model.CategoryEntity
import com.spendwise.app.data.repository.FinanceRepository
import com.spendwise.app.ui.components.CategoryIconView
import com.spendwise.app.ui.components.glass.GlassCard
import com.spendwise.app.ui.components.glass.GlassDeleteConfirmDialog
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.components.glass.GlassRow
import com.spendwise.app.ui.components.glass.GlassSecondaryButton
import com.spendwise.app.ui.theme.*
import com.spendwise.app.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class BudgetItemUi(
    val budget: BudgetEntity,
    val category: CategoryEntity?,
    val spent: Double,
    val percent: Float,
    val isWarning: Boolean,
    val isExceeded: Boolean
)

data class BudgetsUiState(
    val items: List<BudgetItemUi> = emptyList(),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val monthName: String = "",
    val currencySymbol: String = "₹",
    val availableCategories: List<CategoryEntity> = emptyList()
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()
    private val currentMonth = calendar.get(Calendar.MONTH) + 1
    private val currentYear = calendar.get(Calendar.YEAR)

    val uiState: StateFlow<BudgetsUiState> = combine(
        repository.getBudgetsForMonth(currentMonth, currentYear),
        repository.allCategories,
        repository.transactionsWithDetails
    ) { budgets, categories, txs ->
        val catMap = categories.associateBy { it.id }

        // Compute expense in this month
        val spentMap = mutableMapOf<String, Double>()
        for (item in txs) {
            val t = item.transaction
            if (t.type == "expense" && t.categoryId != null) {
                val parts = t.date.split("-")
                if (parts.size == 3 && parts[1].toIntOrNull() == currentMonth && parts[0].toIntOrNull() == currentYear) {
                    spentMap[t.categoryId] = (spentMap[t.categoryId] ?: 0.0) + t.amount
                }
            }
        }

        val budgetItems = budgets.map { b ->
            val spent = spentMap[b.categoryId] ?: 0.0
            val percent = if (b.amount > 0.0) (((spent / b.amount) * 100.0).toFloat()).coerceAtLeast(0f) else 0f
            BudgetItemUi(
                budget = b,
                category = catMap[b.categoryId],
                spent = spent,
                percent = percent,
                isWarning = percent in 80f..99.99f,
                isExceeded = percent >= 100f
            )
        }

        val tBudget = budgets.sumOf { it.amount }
        val tSpent = budgetItems.sumOf { it.spent }
        val mName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""

        val budgetedCatIds = budgets.map { it.categoryId }.toSet()
        val availCats = categories.filter { it.type == "expense" && !budgetedCatIds.contains(it.id) }

        BudgetsUiState(
            items = budgetItems,
            totalBudget = tBudget,
            totalSpent = tSpent,
            monthName = mName,
            currencySymbol = repository.selectedCurrencySymbol,
            availableCategories = availCats
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsUiState())

    fun saveBudget(categoryId: String, amount: Double) {
        viewModelScope.launch {
            repository.setBudget(categoryId, currentMonth, currentYear, amount)
        }
    }

    fun updateBudget(budgetId: String, categoryId: String, amount: Double) {
        viewModelScope.launch {
            repository.setBudget(categoryId, currentMonth, currentYear, amount, existingId = budgetId)
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            repository.deleteBudget(budgetId)
        }
    }
}

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val state by viewModel.uiState.collectAsState()
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetItemUi?>(null) }
    var pendingDeleteBudget by remember { mutableStateOf<BudgetItemUi?>(null) }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Budgets",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Category limits & threshold alerts for ${state.monthName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                GlassSecondaryButton(
                    text = "Add Target",
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) AppleIndigoLight else AppleIndigo
                        )
                    },
                    isDark = isDark,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isAddDialogOpen = true
                    }
                )
            }
        }

        // Overall Monthly Health Card (Apple Liquid Glass)
        item {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    val overallPct = if (state.totalBudget > 0) ((state.totalSpent / state.totalBudget) * 100).toInt() else 0
                    val progressColor = if (overallPct >= 100) ExpenseRose else if (overallPct >= 80) WarningAmber else IncomeEmerald

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL MONTHLY BUDGET",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = if (isDark) AppleIndigoLight else AppleIndigo
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = progressColor.copy(alpha = if (isDark) 0.25f else 0.15f)
                        ) {
                            Text(
                                text = "$overallPct% Used",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = progressColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hero Budget Numbers
                    Text(
                        text = "${CurrencyFormatter.format(state.totalSpent, state.currencySymbol, false)} / ${CurrencyFormatter.format(state.totalBudget, state.currencySymbol, false)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Slim Progress Bar
                    val pct = if (state.totalBudget > 0) (state.totalSpent / state.totalBudget).toFloat().coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = if (isDark) Color(0x26FFFFFF) else Color(0x330F172A)
                    )
                }
            }
        }

        // Category Budgets Header
        item {
            Text(
                text = "CATEGORY TARGETS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = if (isDark) TextMutedDark else TextMutedLight,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        // Category Budgets List
        if (state.items.isEmpty()) {
            item {
                GlassRow(
                    isDark = isDark,
                    shape = RoundedCornerShape(20.dp),
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (isDark) AppleIndigoLight else AppleIndigo,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No budgets configured for this month",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                        }
                    }
                }
            }
        } else {
            items(state.items, key = { it.budget.id }) { item ->
                val statusColor = if (item.isExceeded) ExpenseRose else if (item.isWarning) WarningAmber else IncomeEmerald

                GlassRow(
                    isDark = isDark,
                    shape = RoundedCornerShape(22.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        editingBudget = item
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CategoryIconView(
                                    iconName = item.category?.icon ?: "Tag",
                                    colorHex = item.category?.color ?: "#5B4BE8",
                                    size = 40.dp
                                )
                                Column {
                                    Text(
                                        text = item.category?.name ?: "Category",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                    )
                                    Text(
                                        text = "Limit: ${CurrencyFormatter.format(item.budget.amount, state.currencySymbol, false)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) TextMutedDark else TextMutedLight
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (item.isExceeded) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ExpenseRose.copy(alpha = if (isDark) 0.25f else 0.15f)
                                    ) {
                                        Text(
                                            text = "Exceeded (100%+)",
                                            color = ExpenseRose,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else if (item.isWarning) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = WarningAmber.copy(alpha = if (isDark) 0.25f else 0.15f)
                                    ) {
                                        Text(
                                            text = "Warning (80%+)",
                                            color = WarningAmber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        editingBudget = item
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Target",
                                        tint = if (isDark) AppleIndigoLight else AppleIndigo,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        pendingDeleteBudget = item
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // Slim Progress Bar
                        LinearProgressIndicator(
                            progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = statusColor,
                            trackColor = if (isDark) Color(0x26FFFFFF) else Color(0x330F172A)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Spent: ${CurrencyFormatter.format(item.spent, state.currencySymbol, false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                            Text(
                                text = "${item.percent.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteBudget?.let { item ->
        val formattedBudget = CurrencyFormatter.format(item.budget.amount, state.currencySymbol, false)
        val categoryName = item.category?.name ?: "this category"
        GlassDeleteConfirmDialog(
            title = "Delete Budget?",
            message = "Are you sure you want to remove the monthly budget of $formattedBudget for \"$categoryName\"? This action cannot be undone.",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                viewModel.deleteBudget(item.budget.id)
                pendingDeleteBudget = null
            },
            onDismiss = {
                pendingDeleteBudget = null
            },
            isDark = isDark
        )
    }

    if (isAddDialogOpen) {
        AddBudgetDialog(
            availableCategories = state.availableCategories,
            currencySymbol = state.currencySymbol,
            isDark = isDark,
            onDismiss = { isAddDialogOpen = false },
            onSave = { catId, amt ->
                viewModel.saveBudget(catId, amt)
                isAddDialogOpen = false
            }
        )
    }

    editingBudget?.let { item ->
        EditBudgetDialog(
            item = item,
            currencySymbol = state.currencySymbol,
            isDark = isDark,
            onDismiss = { editingBudget = null },
            onSave = { newAmount ->
                viewModel.updateBudget(item.budget.id, item.budget.categoryId, newAmount)
                editingBudget = null
            }
        )
    }
}

@Composable
fun EditBudgetDialog(
    item: BudgetItemUi,
    currencySymbol: String,
    isDark: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onSave: (amount: Double) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val initialAmt = item.budget.amount
    var amountText by remember {
        mutableStateOf(if (initialAmt % 1.0 == 0.0) initialAmt.toLong().toString() else initialAmt.toString())
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x73FFFFFF),
        unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x52FFFFFF),
        focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
        unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x2E0F172A),
        focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
        unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(
            1.dp,
            if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush,
            RoundedCornerShape(26.dp)
        ),
        shape = RoundedCornerShape(26.dp),
        containerColor = if (isDark) SolidFallbackDark else SolidFallbackLight,
        title = {
            Text(
                "Edit Target",
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else TextPrimaryLight
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) GlassFillDark else GlassFillLight)
                        .border(1.dp, if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryIconView(
                        iconName = item.category?.icon ?: "Tag",
                        colorHex = item.category?.color ?: "#5B4BE8",
                        size = 38.dp
                    )
                    Column {
                        Text(
                            text = item.category?.name ?: "Category",
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) TextPrimaryDark else TextPrimaryLight
                        )
                        Text(
                            text = "Currently spent: ${CurrencyFormatter.format(item.spent, currencySymbol, false)}",
                            fontSize = 12.sp,
                            color = if (isDark) TextMutedDark else TextMutedLight
                        )
                    }
                }

                // Quick Increment Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500, 1000, 2000, 5000).forEach { inc ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) GlassFillDark else GlassFillLight)
                                .border(1.dp, if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush, RoundedCornerShape(10.dp))
                                .clickable {
                                    val cur = amountText.toDoubleOrNull() ?: 0.0
                                    amountText = (cur + inc).toLong().toString()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+$inc",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextPrimaryDark else TextPrimaryLight
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly Budget Limit ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            GlassGradientButton(
                text = "Update Target",
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        onSave(amt)
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }
        }
    )
}

@Composable
fun AddBudgetDialog(
    availableCategories: List<CategoryEntity>,
    currencySymbol: String,
    isDark: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onSave: (categoryId: String, amount: Double) -> Unit
) {
    var selectedCatId by remember { mutableStateOf(availableCategories.firstOrNull()?.id ?: "") }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(
            1.dp,
            if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush,
            RoundedCornerShape(26.dp)
        ),
        shape = RoundedCornerShape(26.dp),
        containerColor = if (isDark) SolidFallbackDark else SolidFallbackLight,
        title = {
            Text(
                "Set Monthly Target",
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else TextPrimaryLight
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (availableCategories.isEmpty()) {
                    Text(
                        "All expense categories already have budgets configured.",
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                } else {
                    Text(
                        "Select Category",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableCategories.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedCatId = cat.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedCatId == cat.id,
                                    onClick = { selectedCatId = cat.id },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = if (isDark) AppleIndigoLight else AppleIndigo
                                    )
                                )
                                Text(
                                    cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Budget Limit ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x73FFFFFF),
                            unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x52FFFFFF),
                            focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
                            unfocusedBorderColor = if (isDark) Color(0x1FFFFFFF) else Color(0x1F0F172A),
                            focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
                            unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            GlassGradientButton(
                text = "Save Target",
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedCatId.isNotEmpty()) {
                        onSave(selectedCatId, amt)
                    }
                },
                enabled = availableCategories.isNotEmpty() && (amountText.toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }
        }
    )
}
