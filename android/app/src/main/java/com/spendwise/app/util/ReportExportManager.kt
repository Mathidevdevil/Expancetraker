package com.spendwise.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.spendwise.app.data.model.TransactionWithDetails
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

enum class ExportFilterMode {
    MONTHLY,
    YEARLY,
    DATE_RANGE,
    ALL
}

enum class ExportFormat {
    EXCEL, // XML Spreadsheet 2003 (.xls)
    CSV    // Comma-Separated Values (.csv with UTF-8 BOM)
}

object ReportExportManager {

    val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun filterTransactions(
        transactions: List<TransactionWithDetails>,
        mode: ExportFilterMode,
        year: Int,
        month: Int, // 1 to 12
        fromDate: String, // YYYY-MM-DD
        toDate: String    // YYYY-MM-DD
    ): List<TransactionWithDetails> {
        return transactions.filter { item ->
            val dateStr = item.transaction.date.trim()
            val parts = dateStr.split("-")
            val txYear = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val txMonth = parts.getOrNull(1)?.toIntOrNull() ?: 0

            when (mode) {
                ExportFilterMode.MONTHLY -> {
                    txYear == year && txMonth == month
                }
                ExportFilterMode.YEARLY -> {
                    txYear == year
                }
                ExportFilterMode.DATE_RANGE -> {
                    val cleanFrom = fromDate.trim()
                    val cleanTo = toDate.trim()
                    if (cleanFrom.isNotBlank() && cleanTo.isNotBlank()) {
                        dateStr in cleanFrom..cleanTo
                    } else if (cleanFrom.isNotBlank()) {
                        dateStr >= cleanFrom
                    } else if (cleanTo.isNotBlank()) {
                        dateStr <= cleanTo
                    } else {
                        true
                    }
                }
                ExportFilterMode.ALL -> true
            }
        }.sortedByDescending { it.transaction.date }
    }

    fun getReportTitle(
        mode: ExportFilterMode,
        year: Int,
        month: Int,
        fromDate: String,
        toDate: String
    ): String {
        return when (mode) {
            ExportFilterMode.MONTHLY -> {
                val monthName = MONTH_NAMES.getOrNull(month - 1) ?: "Month $month"
                "$monthName $year"
            }
            ExportFilterMode.YEARLY -> "Year $year"
            ExportFilterMode.DATE_RANGE -> "$fromDate to $toDate"
            ExportFilterMode.ALL -> "All Time"
        }
    }

    /**
     * Generates a Microsoft Excel XML Spreadsheet 2003 (.xls) file with 2 styled worksheets:
     * - "Financial Summary" (KPIs, Cashflow, Category Distribution)
     * - "Transactions Ledger" (Complete list of transactions)
     */
    fun generateExcelFile(
        context: Context,
        transactions: List<TransactionWithDetails>,
        periodTitle: String,
        currencySymbol: String
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safeTitle = periodTitle.replace(" ", "_").replace("/", "-")
        val file = File(reportsDir, "Spendwise_Report_${safeTitle}.xls")

        var totalIncome = 0.0
        var totalExpense = 0.0
        val catSpendMap = mutableMapOf<String, Double>()
        val catTypeMap = mutableMapOf<String, String>()

        for (item in transactions) {
            val t = item.transaction
            val catName = item.category?.name ?: "Uncategorized"
            if (t.type == "income") {
                totalIncome += t.amount
            } else {
                totalExpense += t.amount
                catSpendMap[catName] = (catSpendMap[catName] ?: 0.0) + t.amount
                catTypeMap[catName] = "Expense"
            }
        }

        val netSavings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).toInt() else 0
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <DocumentProperties xmlns="urn:schemas-microsoft-com:office:office">
  <Author>Spendwise Personal Finance</Author>
  <Created>$generatedAt</Created>
  <Company>Spendwise</Company>
 </DocumentProperties>
 <Styles>
  <Style ss:ID="Default" ss:Name="Normal">
   <Alignment ss:Vertical="Center"/>
   <Font ss:FontName="Segoe UI" x:Family="Swiss" ss:Size="10" ss:Color="#1E293B"/>
  </Style>
  <Style ss:ID="Title">
   <Font ss:FontName="Segoe UI" ss:Size="16" ss:Bold="1" ss:Color="#4F46E5"/>
   <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="Subtitle">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Color="#64748B" ss:Italic="1"/>
  </Style>
  <Style ss:ID="Header">
   <Font ss:FontName="Segoe UI" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>
   <Interior ss:Color="#4F46E5" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#312E81"/>
   </Borders>
  </Style>
  <Style ss:ID="SectionHeader">
   <Font ss:FontName="Segoe UI" ss:Size="12" ss:Bold="1" ss:Color="#1E293B"/>
   <Interior ss:Color="#EEF2FF" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="Bold">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1" ss:Color="#0F172A"/>
  </Style>
  <Style ss:ID="Income">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1" ss:Color="#10B981"/>
   <Alignment ss:Horizontal="Right"/>
  </Style>
  <Style ss:ID="Expense">
   <Font ss:FontName="Segoe UI" ss:Size="10" ss:Bold="1" ss:Color="#EF4444"/>
   <Alignment ss:Horizontal="Right"/>
  </Style>
  <Style ss:ID="NumberCell">
   <Alignment ss:Horizontal="Right"/>
  </Style>
  <Style ss:ID="CenterCell">
   <Alignment ss:Horizontal="Center"/>
  </Style>
 </Styles>
""")

            // -------------------------------------------------------------
            // WORKSHEET 1: FINANCIAL SUMMARY
            // -------------------------------------------------------------
            append(""" <Worksheet ss:Name="Financial Summary">
  <Table ss:DefaultRowHeight="20">
   <Column ss:Width="160"/>
   <Column ss:Width="140"/>
   <Column ss:Width="120"/>
   <Column ss:Width="120"/>

   <Row ss:Height="28">
    <Cell ss:StyleID="Title"><Data ss:Type="String">Spendwise Financial Statement</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Subtitle"><Data ss:Type="String">Period: ${escapeXml(periodTitle)} | Generated: $generatedAt</Data></Cell>
   </Row>
   <Row><Cell><Data ss:Type="String"></Data></Cell></Row>

   <!-- KPI Overview -->
   <Row ss:Height="22">
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Executive Summary</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Amount ($currencySymbol)</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Status</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Total Monthly Inflow (Income)</Data></Cell>
    <Cell ss:StyleID="Income"><Data ss:Type="Number">$totalIncome</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Credit</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Total Outflow (Expenses)</Data></Cell>
    <Cell ss:StyleID="Expense"><Data ss:Type="Number">$totalExpense</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Debit</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Net Savings Balance</Data></Cell>
    <Cell ss:StyleID="${if (netSavings >= 0) "Income" else "Expense"}"><Data ss:Type="Number">$netSavings</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${if (netSavings >= 0) "Surplus" else "Deficit"}</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Savings Rate</Data></Cell>
    <Cell ss:StyleID="NumberCell"><Data ss:Type="String">$savingsRate%</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${if (savingsRate >= 20) "Healthy" else "Needs Attention"}</Data></Cell>
   </Row>
   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">Total Transactions Count</Data></Cell>
    <Cell ss:StyleID="NumberCell"><Data ss:Type="Number">${transactions.size}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Verified</Data></Cell>
   </Row>

   <Row><Cell><Data ss:Type="String"></Data></Cell></Row>
   <Row><Cell><Data ss:Type="String"></Data></Cell></Row>

   <!-- Category Breakdown Table -->
   <Row ss:Height="22">
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Category Breakdown</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Type</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Spent ($currencySymbol)</Data></Cell>
    <Cell ss:StyleID="SectionHeader"><Data ss:Type="String">Share (%)</Data></Cell>
   </Row>
""")

            val sortedCategories = catSpendMap.entries.sortedByDescending { it.value }
            for (entry in sortedCategories) {
                val pct = if (totalExpense > 0) ((entry.value / totalExpense) * 100).toInt() else 0
                append("""   <Row>
    <Cell ss:StyleID="Bold"><Data ss:Type="String">${escapeXml(entry.key)}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">Expense</Data></Cell>
    <Cell ss:StyleID="Expense"><Data ss:Type="Number">${entry.value}</Data></Cell>
    <Cell ss:StyleID="NumberCell"><Data ss:Type="String">$pct%</Data></Cell>
   </Row>
""")
            }

            append("""  </Table>
 </Worksheet>
""")

            // -------------------------------------------------------------
            // WORKSHEET 2: TRANSACTIONS LEDGER
            // -------------------------------------------------------------
            append(""" <Worksheet ss:Name="Transactions Ledger">
  <Table ss:DefaultRowHeight="20">
   <Column ss:Width="90"/>
   <Column ss:Width="80"/>
   <Column ss:Width="140"/>
   <Column ss:Width="120"/>
   <Column ss:Width="100"/>
   <Column ss:Width="110"/>
   <Column ss:Width="220"/>

   <Row ss:Height="24">
    <Cell ss:StyleID="Header"><Data ss:Type="String">Date</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Type</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Category</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Trip / Event</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Amount ($currencySymbol)</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Payment Method</Data></Cell>
    <Cell ss:StyleID="Header"><Data ss:Type="String">Notes &amp; Details</Data></Cell>
   </Row>
""")

            for (item in transactions) {
                val t = item.transaction
                val catName = escapeXml(item.category?.name ?: "General")
                val tripName = escapeXml(item.trip?.destination ?: "-")
                val payMethod = escapeXml(t.paymentMethod.uppercase())
                val note = escapeXml(t.note ?: "")
                val typeStyle = if (t.type == "income") "Income" else "Expense"
                val typeLabel = if (t.type == "income") "INCOME" else "EXPENSE"

                append("""   <Row>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">${t.date}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">$typeLabel</Data></Cell>
    <Cell><Data ss:Type="String">$catName</Data></Cell>
    <Cell><Data ss:Type="String">$tripName</Data></Cell>
    <Cell ss:StyleID="$typeStyle"><Data ss:Type="Number">${t.amount}</Data></Cell>
    <Cell ss:StyleID="CenterCell"><Data ss:Type="String">$payMethod</Data></Cell>
    <Cell><Data ss:Type="String">$note</Data></Cell>
   </Row>
""")
            }

            append("""  </Table>
 </Worksheet>
</Workbook>
""")
        }

        FileOutputStream(file).use { out ->
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                writer.write(xml)
            }
        }

        return file
    }

    /**
     * Generates a CSV file with UTF-8 BOM (\uFEFF) for Microsoft Excel compatibility.
     */
    fun generateCsvFile(
        context: Context,
        transactions: List<TransactionWithDetails>,
        periodTitle: String,
        currencySymbol: String
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safeTitle = periodTitle.replace(" ", "_").replace("/", "-")
        val file = File(reportsDir, "Spendwise_Report_${safeTitle}.csv")

        var totalIncome = 0.0
        var totalExpense = 0.0
        for (item in transactions) {
            val t = item.transaction
            if (t.type == "income") totalIncome += t.amount else totalExpense += t.amount
        }
        val net = totalIncome - totalExpense

        FileOutputStream(file).use { out ->
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                // Write UTF-8 Byte Order Mark (BOM) so Excel opens UTF-8 properly
                writer.write("\uFEFF")

                // Header Metadata
                writer.write("Spendwise Financial Statement\n")
                writer.write("Report Period,${escapeCsv(periodTitle)}\n")
                writer.write("Currency,${escapeCsv(currencySymbol)}\n")
                writer.write("Total Inflow,${totalIncome}\n")
                writer.write("Total Outflow,${totalExpense}\n")
                writer.write("Net Balance,${net}\n")
                writer.write("Total Transactions,${transactions.size}\n\n")

                // Table Header
                writer.write("Date,Type,Category,Trip/Event,Amount ($currencySymbol),Payment Method,Notes\n")

                for (item in transactions) {
                    val t = item.transaction
                    val catName = escapeCsv(item.category?.name ?: "General")
                    val tripDest = escapeCsv(item.trip?.destination ?: "")
                    val note = escapeCsv(t.note ?: "")
                    val sign = if (t.type == "income") "+" else "-"
                    writer.write("${t.date},${t.type.uppercase()},$catName,$tripDest,$sign${t.amount},${t.paymentMethod.uppercase()},$note\n")
                }
            }
        }

        return file
    }

    fun shareExportedFile(context: Context, file: File, format: ExportFormat) {
        val mimeType = if (format == ExportFormat.EXCEL) {
            "application/vnd.ms-excel"
        } else {
            "text/csv"
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            putExtra(Intent.EXTRA_TEXT, "Here is your ${file.nameWithoutExtension} financial statement generated by Spendwise.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Download / Share Financial Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeCsv(text: String): String {
        val escaped = text.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
