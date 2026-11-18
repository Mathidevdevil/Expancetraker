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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFilterMode {
    MONTHLY,
    YEARLY,
    DATE_RANGE,
    ALL
}

enum class ExportFormat {
    EXCEL, // Native OpenXML (.xlsx)
    CSV    // Universal Comma-Separated Values (.csv with UTF-8 BOM)
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
     * Generates a true, native Microsoft Excel OpenXML (.xlsx) file.
     * Compatible with Microsoft Excel Mobile & Desktop, Google Sheets, LibreOffice, and WPS Office.
     */
    fun generateExcelFile(
        context: Context,
        transactions: List<TransactionWithDetails>,
        periodTitle: String,
        currencySymbol: String
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val safeTitle = periodTitle.replace(" ", "_").replace("/", "-")
        val file = File(reportsDir, "Spendwise_Report_${safeTitle}.xlsx")

        var totalIncome = 0.0
        var totalExpense = 0.0
        val catSpendMap = mutableMapOf<String, Double>()

        for (item in transactions) {
            val t = item.transaction
            val catName = item.category?.name ?: "Uncategorized"
            if (t.type == "income") {
                totalIncome += t.amount
            } else {
                totalExpense += t.amount
                catSpendMap[catName] = (catSpendMap[catName] ?: 0.0) + t.amount
            }
        }

        val netSavings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).toInt() else 0
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        // 1. Build Sheet 1: Transactions
        val sheet1Xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="14" customWidth="1"/>
    <col min="2" max="2" width="12" customWidth="1"/>
    <col min="3" max="3" width="22" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="10" customWidth="1"/>
    <col min="7" max="7" width="16" customWidth="1"/>
    <col min="8" max="8" width="32" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1">
      <c r="A1" t="inlineStr"><is><t>Date</t></is></c>
      <c r="B1" t="inlineStr"><is><t>Type</t></is></c>
      <c r="C1" t="inlineStr"><is><t>Category</t></is></c>
      <c r="D1" t="inlineStr"><is><t>Trip</t></is></c>
      <c r="E1" t="inlineStr"><is><t>Amount</t></is></c>
      <c r="F1" t="inlineStr"><is><t>Currency</t></is></c>
      <c r="G1" t="inlineStr"><is><t>Payment Method</t></is></c>
      <c r="H1" t="inlineStr"><is><t>Notes</t></is></c>
    </row>
""")
            transactions.forEachIndexed { idx, item ->
                val r = idx + 2
                val t = item.transaction
                val date = escapeXml(t.date)
                val type = if (t.type == "income") "Income" else "Expense"
                val cat = escapeXml(item.category?.name ?: "General")
                val trip = escapeXml(item.trip?.destination ?: "-")
                val payMethod = escapeXml(t.paymentMethod.uppercase())
                val note = escapeXml(t.note ?: "")
                val cur = escapeXml(currencySymbol)

                append("""    <row r="$r">
      <c r="A$r" t="inlineStr"><is><t>$date</t></is></c>
      <c r="B$r" t="inlineStr"><is><t>$type</t></is></c>
      <c r="C$r" t="inlineStr"><is><t>$cat</t></is></c>
      <c r="D$r" t="inlineStr"><is><t>$trip</t></is></c>
      <c r="E$r"><v>${t.amount}</v></c>
      <c r="F$r" t="inlineStr"><is><t>$cur</t></is></c>
      <c r="G$r" t="inlineStr"><is><t>$payMethod</t></is></c>
      <c r="H$r" t="inlineStr"><is><t>$note</t></is></c>
    </row>
""")
            }
            append("""  </sheetData>
</worksheet>""")
        }

        // 2. Build Sheet 2: Financial Summary
        val sheet2Xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="28" customWidth="1"/>
    <col min="2" max="2" width="20" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1">
      <c r="A1" t="inlineStr"><is><t>Spendwise Financial Statement</t></is></c>
    </row>
    <row r="2">
      <c r="A2" t="inlineStr"><is><t>Period: ${escapeXml(periodTitle)} | Generated: $generatedAt</t></is></c>
    </row>
    <row r="4">
      <c r="A4" t="inlineStr"><is><t>Executive Metric</t></is></c>
      <c r="B4" t="inlineStr"><is><t>Amount (${escapeXml(currencySymbol)})</t></is></c>
    </row>
    <row r="5">
      <c r="A5" t="inlineStr"><is><t>Total Income (Inflow)</t></is></c>
      <c r="B5"><v>$totalIncome</v></c>
    </row>
    <row r="6">
      <c r="A6" t="inlineStr"><is><t>Total Expense (Outflow)</t></is></c>
      <c r="B6"><v>$totalExpense</v></c>
    </row>
    <row r="7">
      <c r="A7" t="inlineStr"><is><t>Net Savings</t></is></c>
      <c r="B7"><v>$netSavings</v></c>
    </row>
    <row r="8">
      <c r="A8" t="inlineStr"><is><t>Savings Rate</t></is></c>
      <c r="B8" t="inlineStr"><is><t>$savingsRate%</t></is></c>
    </row>
    <row r="9">
      <c r="A9" t="inlineStr"><is><t>Total Transactions Count</t></is></c>
      <c r="B9"><v>${transactions.size}</v></c>
    </row>
    <row r="11">
      <c r="A11" t="inlineStr"><is><t>Category Breakdown</t></is></c>
      <c r="B11" t="inlineStr"><is><t>Total Spent (${escapeXml(currencySymbol)})</t></is></c>
      <c r="C11" t="inlineStr"><is><t>Share (%)</t></is></c>
    </row>
""")
            val sortedCategories = catSpendMap.entries.sortedByDescending { it.value }
            var startR = 12
            for (entry in sortedCategories) {
                val pct = if (totalExpense > 0) ((entry.value / totalExpense) * 100).toInt() else 0
                val cat = escapeXml(entry.key)
                append("""    <row r="$startR">
      <c r="A$startR" t="inlineStr"><is><t>$cat</t></is></c>
      <c r="B$startR"><v>${entry.value}</v></c>
      <c r="C$startR" t="inlineStr"><is><t>$pct%</t></is></c>
    </row>
""")
                startR++
            }
            append("""  </sheetData>
</worksheet>""")
        }

        // 3. Package as standard OpenXML .xlsx (Zip archive)
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val wbRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
</Relationships>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Transactions" sheetId="1" r:id="rId1"/>
    <sheet name="Summary" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""

        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                fun writeZipEntry(name: String, content: String) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }

                writeZipEntry("[Content_Types].xml", contentTypesXml)
                writeZipEntry("_rels/.rels", rootRelsXml)
                writeZipEntry("xl/_rels/workbook.xml.rels", wbRelsXml)
                writeZipEntry("xl/workbook.xml", workbookXml)
                writeZipEntry("xl/worksheets/sheet1.xml", sheet1Xml)
                writeZipEntry("xl/worksheets/sheet2.xml", sheet2Xml)
            }
        }

        return file
    }

    /**
     * Generates a clean, standard CSV file with UTF-8 BOM (\uFEFF).
     * Begins directly with clean table headers for immediate sorting and filtering.
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
                // Write UTF-8 Byte Order Mark (BOM) so Excel opens UTF-8 symbols natively
                writer.write("\uFEFF")

                // Line 1: Standard Table Header
                writer.write("Date,Type,Category,Trip,Amount,Currency,Payment Method,Notes\r\n")

                // Transaction rows
                for (item in transactions) {
                    val t = item.transaction
                    val date = t.date
                    val type = if (t.type == "income") "Income" else "Expense"
                    val cat = escapeCsv(item.category?.name ?: "General")
                    val trip = escapeCsv(item.trip?.destination ?: "-")
                    val amt = String.format(Locale.US, "%.2f", t.amount)
                    val cur = escapeCsv(currencySymbol)
                    val pay = escapeCsv(t.paymentMethod.uppercase())
                    val note = escapeCsv(t.note ?: "")
                    writer.write("$date,$type,$cat,$trip,$amt,$cur,$pay,$note\r\n")
                }

                // Summary block at the end
                writer.write("\r\n")
                writer.write("--- Financial Summary ---\r\n")
                writer.write("Report Period,${escapeCsv(periodTitle)}\r\n")
                writer.write("Total Inflow,${String.format(Locale.US, "%.2f", totalIncome)}\r\n")
                writer.write("Total Outflow,${String.format(Locale.US, "%.2f", totalExpense)}\r\n")
                writer.write("Net Balance,${String.format(Locale.US, "%.2f", net)}\r\n")
                writer.write("Total Transactions,${transactions.size}\r\n")
            }
        }

        return file
    }

    fun shareExportedFile(context: Context, file: File, format: ExportFormat) {
        val mimeType = if (format == ExportFormat.EXCEL) {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
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
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
