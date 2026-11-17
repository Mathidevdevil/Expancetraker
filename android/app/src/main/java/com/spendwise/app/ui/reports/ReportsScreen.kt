package com.spendwise.app.ui.reports

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.model.TransactionWithDetails
import com.spendwise.app.data.repository.FinanceRepository
import com.spendwise.app.ui.components.CategoryIconView
import com.spendwise.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class MonthlyBarData(
    val monthLabel: String,
    val income: Double,
    val expense: Double
)

data class ReportsUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val savingsRate: Float = 0f,
    val monthlyBars: List<MonthlyBarData> = emptyList(),
    val rawTransactions: List<TransactionWithDetails> = emptyList(),
    val currencySymbol: String = "₹"
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    val uiState: StateFlow<ReportsUiState> = repository.transactionsWithDetails.map { txs ->
        var income = 0.0
        var expense = 0.0

        // Last 6 months buckets
        val monthMap = LinkedHashMap<String, Pair<Double, Double>>()
        val sdf = SimpleDateFormat("MMM", Locale.getDefault())
        val cal = Calendar.getInstance()

        for (i in 5 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -i)
            val label = sdf.format(c.time)
            monthMap[label] = Pair(0.0, 0.0)
        }

        for (item in txs) {
            val t = item.transaction
            if (t.type == "income") {
                income += t.amount
            } else {
                expense += t.amount
            }

            // Bucket by month
            val tSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            runCatching {
                val date = tSdf.parse(t.date)
                if (date != null) {
                    val label = sdf.format(date)
                    if (monthMap.containsKey(label)) {
                        val current = monthMap[label]!!
                        monthMap[label] = if (t.type == "income") {
                            Pair(current.first + t.amount, current.second)
                        } else {
                            Pair(current.first, current.second + t.amount)
                        }
                    }
                }
            }
        }

        val bars = monthMap.map { (label, pair) ->
            MonthlyBarData(monthLabel = label, income = pair.first, expense = pair.second)
        }

        val rate = if (income > 0) (((income - expense) / income) * 100).toFloat() else 0f

        ReportsUiState(
            totalIncome = income,
            totalExpense = expense,
            savingsRate = rate,
            monthlyBars = bars,
            rawTransactions = txs,
            currencySymbol = repository.selectedCurrencySymbol
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    fun exportCSV(context: Context) {
        val file = repository.generateCSVFile(context, uiState.value.rawTransactions)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Spendwise CSV Report"))
    }
}

@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp),
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
                        text = "Reports & Analytics",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cash flow comparison & CSV statement export",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))

                FilledTonalButton(
                    onClick = { viewModel.exportCSV(context) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }
            }
        }

        // Summary Metric Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = EmeraldIncome.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Income", style = MaterialTheme.typography.labelSmall, color = EmeraldIncome)
                        Text(
                            "+${state.currencySymbol}${state.totalIncome.toInt()}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldIncome
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RoseExpense.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Expense", style = MaterialTheme.typography.labelSmall, color = RoseExpense)
                        Text(
                            "-${state.currencySymbol}${state.totalExpense.toInt()}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = RoseExpense
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = IndigoPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Savings Rate", style = MaterialTheme.typography.labelSmall, color = IndigoPrimary)
                        Text(
                            "${state.savingsRate.toInt()}%",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = IndigoPrimary
                        )
                    }
                }
            }
        }

        // Canvas Cashflow Chart
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Monthly Trends (Last 6 Months)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(EmeraldIncome))
                                Text("Income", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(RoseExpense))
                                Text("Expense", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Native Compose Canvas Bar Chart
                    val maxVal = remember(state.monthlyBars) {
                        state.monthlyBars.maxOfOrNull { kotlin.math.max(it.income, it.expense) }?.coerceAtLeast(100.0) ?: 100.0
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(top = 10.dp, bottom = 4.dp)
                    ) {
                        val barGroupWidth = size.width / (state.monthlyBars.size.coerceAtLeast(1))
                        val singleBarWidth = barGroupWidth * 0.28f

                        state.monthlyBars.forEachIndexed { index, bar ->
                            val xOffset = index * barGroupWidth + (barGroupWidth * 0.2f)
                            val incomeHeight = (bar.income / maxVal).toFloat() * size.height * 0.85f
                            val expenseHeight = (bar.expense / maxVal).toFloat() * size.height * 0.85f

                            // Draw Income Bar
                            drawRoundRect(
                                color = EmeraldIncome,
                                topLeft = Offset(xOffset, size.height - incomeHeight),
                                size = Size(singleBarWidth, incomeHeight.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(6f, 6f)
                            )

                            // Draw Expense Bar
                            drawRoundRect(
                                color = RoseExpense,
                                topLeft = Offset(xOffset + singleBarWidth + 4f, size.height - expenseHeight),
                                size = Size(singleBarWidth, expenseHeight.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }

                    // Month Labels Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        state.monthlyBars.forEach { b ->
                            Text(
                                text = b.monthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
