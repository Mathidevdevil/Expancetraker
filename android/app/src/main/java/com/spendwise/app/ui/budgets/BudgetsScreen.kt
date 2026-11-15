package com.spendwise.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
            availableCategories = availCats
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsUiState())

    fun saveBudget(categoryId: String, amount: Double) {
        viewModelScope.launch {
            repository.setBudget(categoryId, currentMonth, currentYear, amount)
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
    viewModel: BudgetsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var isAddDialogOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Budgets",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Category limits & threshold alerts for ${state.monthName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isAddDialogOpen = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Budget", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

            // Overall Monthly Health Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = progressColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$overallPct% Used",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = progressColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Hero Budget Numbers
                        Text(
                            text = "${CurrencyFormatter.format(state.totalSpent, state.currencySymbol, false)} / ${CurrencyFormatter.format(state.totalBudget, state.currencySymbol, false)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Slim Animated Progress Bar
                        val pct = if (state.totalBudget > 0) (state.totalSpent / state.totalBudget).toFloat().coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // Category Budgets
            if (state.items.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                                Text(
                                    text = "No budgets configured for this month",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(state.items, key = { it.budget.id }) { item ->
                    val statusColor = if (item.isExceeded) ExpenseRose else if (item.isWarning) WarningAmber else IncomeEmerald

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        colorHex = item.category?.color ?: "#6366F1",
                                        size = 38.dp
                                    )
                                    Column {
                                        Text(
                                            text = item.category?.name ?: "Category",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "Limit: ${CurrencyFormatter.format(item.budget.amount, state.currencySymbol, false)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            color = ExpenseRose.copy(alpha = 0.15f)
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
                                            color = WarningAmber.copy(alpha = 0.15f)
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
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.deleteBudget(item.budget.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
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
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Spent: ${CurrencyFormatter.format(item.spent, state.currencySymbol, false)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (isAddDialogOpen) {
        AddBudgetDialog(
            availableCategories = state.availableCategories,
            currencySymbol = state.currencySymbol,
            onDismiss = { isAddDialogOpen = false },
            onSave = { catId, amt ->
                viewModel.saveBudget(catId, amt)
                isAddDialogOpen = false
            }
        )
    }
}

@Composable
fun AddBudgetDialog(
    availableCategories: List<CategoryEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (categoryId: String, amount: Double) -> Unit
) {
    var selectedCatId by remember { mutableStateOf(availableCategories.firstOrNull()?.id ?: "") }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (availableCategories.isEmpty()) {
                    Text("All expense categories already have budgets configured.")
                } else {
                    Text("Select Category", style = MaterialTheme.typography.labelSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    onClick = { selectedCatId = cat.id }
                                )
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Budget Limit ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedCatId.isNotEmpty()) {
                        onSave(selectedCatId, amt)
                    }
                },
                enabled = availableCategories.isNotEmpty() && (amountText.toDoubleOrNull() ?: 0.0) > 0,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Target")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
