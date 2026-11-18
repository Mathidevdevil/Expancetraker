package com.spendwise.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.data.model.TransactionWithDetails
import com.spendwise.app.ui.theme.AppleIndigo
import com.spendwise.app.ui.theme.AppleIndigoLight
import com.spendwise.app.ui.theme.SfPro
import com.spendwise.app.util.ExportFilterMode
import com.spendwise.app.util.ExportFormat
import com.spendwise.app.util.ReportExportManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    transactions: List<TransactionWithDetails>,
    currencySymbol: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentCal = remember { Calendar.getInstance() }
    val currentYear = remember { currentCal.get(Calendar.YEAR) }
    val currentMonth = remember { currentCal.get(Calendar.MONTH) + 1 }

    var selectedMode by remember { mutableStateOf(ExportFilterMode.MONTHLY) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.EXCEL) }
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    val defaultFromDate = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
    }
    val defaultToDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    var fromDateText by remember { mutableStateOf(defaultFromDate) }
    var toDateText by remember { mutableStateOf(defaultToDate) }

    // Dynamic filtered transaction list
    val filteredTransactions = remember(
        transactions, selectedMode, selectedYear, selectedMonth, fromDateText, toDateText
    ) {
        ReportExportManager.filterTransactions(
            transactions = transactions,
            mode = selectedMode,
            year = selectedYear,
            month = selectedMonth,
            fromDate = fromDateText,
            toDate = toDateText
        )
    }

    val reportTitle = remember(
        selectedMode, selectedYear, selectedMonth, fromDateText, toDateText
    ) {
        ReportExportManager.getReportTitle(
            mode = selectedMode,
            year = selectedYear,
            month = selectedMonth,
            fromDate = fromDateText,
            toDate = toDateText
        )
    }

    var totalIncome = 0.0
    var totalExpense = 0.0
    for (item in filteredTransactions) {
        if (item.transaction.type == "income") {
            totalIncome += item.transaction.amount
        } else {
            totalExpense += item.transaction.amount
        }
    }
    val netBalance = totalIncome - totalExpense

    val availableYears = remember {
        (currentYear downTo currentYear - 4).toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = if (isDark) Color(0xFF131520) else Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon badge (Excel Green / Indigo gradient)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF107C41), // Microsoft Excel Green
                                Color(0xFF1F8A70),
                                Color(0xFF6366F1)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Download Financial Report",
                fontFamily = SfPro,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )

            Text(
                text = "Export Monthly, Yearly, or Date-wise statements",
                fontFamily = SfPro,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Report Range Mode Tabs
            Text(
                text = "REPORT TIMEFRAME",
                fontFamily = SfPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = if (isDark) AppleIndigoLight else AppleIndigo,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1C1F2E) else Color(0xFFF1F5F9))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExportFilterMode.values().forEach { mode ->
                    val isSelected = selectedMode == mode
                    val label = when (mode) {
                        ExportFilterMode.MONTHLY -> "Monthly"
                        ExportFilterMode.YEARLY -> "Yearly"
                        ExportFilterMode.DATE_RANGE -> "Date Range"
                        ExportFilterMode.ALL -> "All Time"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    if (isDark) AppleIndigo else Color.White
                                } else Color.Transparent
                            )
                            .clickable { selectedMode = mode }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = SfPro,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isSelected) {
                                if (isDark) Color.White else AppleIndigo
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Contextual Controls for Timeframe
            when (selectedMode) {
                ExportFilterMode.MONTHLY -> {
                    // Month Selector Chips (Horizontal Scroll)
                    Text(
                        text = "SELECT MONTH",
                        fontFamily = SfPro,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportExportManager.MONTH_NAMES.forEachIndexed { index, name ->
                            val monthNum = index + 1
                            val isSelected = selectedMonth == monthNum
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AppleIndigo else if (isDark) Color(0xFF1E2235) else Color(0xFFE2E8F0),
                                modifier = Modifier.clickable { selectedMonth = monthNum }
                            ) {
                                Text(
                                    text = name.take(3),
                                    fontFamily = SfPro,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Year Selector Chips
                    Text(
                        text = "SELECT YEAR",
                        fontFamily = SfPro,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableYears.forEach { yr ->
                            val isSelected = selectedYear == yr
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AppleIndigo else if (isDark) Color(0xFF1E2235) else Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedYear = yr }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$yr",
                                        fontFamily = SfPro,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }
                }

                ExportFilterMode.YEARLY -> {
                    Text(
                        text = "SELECT YEAR",
                        fontFamily = SfPro,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableYears.forEach { yr ->
                            val isSelected = selectedYear == yr
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AppleIndigo else if (isDark) Color(0xFF1E2235) else Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedYear = yr }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$yr",
                                        fontFamily = SfPro,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }
                }

                ExportFilterMode.DATE_RANGE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = fromDateText,
                            onValueChange = { fromDateText = it },
                            label = { Text("Start Date", fontFamily = SfPro, fontSize = 12.sp) },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = toDateText,
                            onValueChange = { toDateText = it },
                            label = { Text("End Date", fontFamily = SfPro, fontSize = 12.sp) },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                ExportFilterMode.ALL -> {
                    // No extra parameters needed
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. File Format Picker (Excel vs CSV)
            Text(
                text = "CHOOSE FILE FORMAT",
                fontFamily = SfPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = if (isDark) AppleIndigoLight else AppleIndigo,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Excel Option
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedFormat == ExportFormat.EXCEL) {
                        Color(0xFF107C41).copy(alpha = if (isDark) 0.25f else 0.12f)
                    } else if (isDark) Color(0xFF181B28) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedFormat == ExportFormat.EXCEL) Color(0xFF107C41) else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFormat = ExportFormat.EXCEL }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF107C41)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Excel Sheet",
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            Text(
                                text = ".xls (Formatted)",
                                fontFamily = SfPro,
                                fontSize = 11.sp,
                                color = Color(0xFF107C41),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // CSV Option
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedFormat == ExportFormat.CSV) {
                        AppleIndigo.copy(alpha = if (isDark) 0.25f else 0.12f)
                    } else if (isDark) Color(0xFF181B28) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (selectedFormat == ExportFormat.CSV) AppleIndigo else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFormat = ExportFormat.CSV }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CSV Sheet",
                                fontFamily = SfPro,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            Text(
                                text = ".csv (Universal)",
                                fontFamily = SfPro,
                                fontSize = 11.sp,
                                color = if (isDark) AppleIndigoLight else AppleIndigo,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Live Report Summary Preview Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF1A1D2B) else Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reportTitle,
                            fontFamily = SfPro,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                        Text(
                            text = "${filteredTransactions.size} transactions",
                            fontFamily = SfPro,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (filteredTransactions.isNotEmpty()) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Inflow", fontSize = 11.sp, color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B))
                            Text("+$currencySymbol${totalIncome.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF10B981))
                        }
                        Column {
                            Text("Outflow", fontSize = 11.sp, color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B))
                            Text("-$currencySymbol${totalExpense.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEF4444))
                        }
                        Column {
                            Text("Net Balance", fontSize = 11.sp, color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B))
                            Text(
                                "${if (netBalance >= 0) "+" else ""}$currencySymbol${netBalance.toInt()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (netBalance >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Download & Share Button
            Button(
                onClick = {
                    if (filteredTransactions.isEmpty()) {
                        Toast.makeText(context, "No transactions found for $reportTitle", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    try {
                        val file = if (selectedFormat == ExportFormat.EXCEL) {
                            ReportExportManager.generateExcelFile(
                                context = context,
                                transactions = filteredTransactions,
                                periodTitle = reportTitle,
                                currencySymbol = currencySymbol
                            )
                        } else {
                            ReportExportManager.generateCsvFile(
                                context = context,
                                transactions = filteredTransactions,
                                periodTitle = reportTitle,
                                currencySymbol = currencySymbol
                            )
                        }

                        ReportExportManager.shareExportedFile(
                            context = context,
                            file = file,
                            format = selectedFormat
                        )
                        Toast.makeText(context, "Exported $reportTitle successfully!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedFormat == ExportFormat.EXCEL) Color(0xFF107C41) else AppleIndigo,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedFormat == ExportFormat.EXCEL) "Download Excel (.xls)" else "Download CSV (.csv)",
                    fontFamily = SfPro,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = SfPro,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
                )
            }
        }
    }
}
