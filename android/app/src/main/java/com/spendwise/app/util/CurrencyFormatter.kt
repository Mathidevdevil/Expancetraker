package com.spendwise.app.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    /**
     * Formats amount according to Indian numbering system (e.g. 1,25,000.00)
     * and strictly puts negative sign before the currency symbol:
     * - Negative: -₹500.00 (Never ₹-500)
     * - Positive: ₹1,25,000.00
     */
    fun format(amount: Double, symbol: String = "₹", showDecimals: Boolean = true): String {
        val isNegative = amount < 0.0
        val absAmount = abs(amount)

        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = if (showDecimals) 2 else 0
            maximumFractionDigits = if (showDecimals) 2 else 0
        }
        val formattedNumber = formatter.format(absAmount)

        return if (isNegative) "-$symbol$formattedNumber" else "$symbol$formattedNumber"
    }

    /**
     * Formats with explicit sign (+ or -)
     * Positive: +₹4,500
     * Negative: -₹500
     */
    fun formatWithSign(amount: Double, symbol: String = "₹", showDecimals: Boolean = false): String {
        val isNegative = amount < 0.0
        val absAmount = abs(amount)

        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = if (showDecimals) 2 else 0
            maximumFractionDigits = if (showDecimals) 2 else 0
        }
        val formattedNumber = formatter.format(absAmount)

        return if (isNegative) "-$symbol$formattedNumber" else "+$symbol$formattedNumber"
    }

    /**
     * Formats a date string (YYYY-MM-DD) to friendly representation:
     * - "Today"
     * - "Yesterday"
     * - "15 Nov" or "15 Nov 2026"
     */
    fun formatFriendlyDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(dateStr) ?: return dateStr

            val today = Calendar.getInstance()
            val target = Calendar.getInstance().apply { time = date }

            if (today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            ) {
                return "Today"
            }

            today.add(Calendar.DAY_OF_YEAR, -1)
            if (today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            ) {
                return "Yesterday"
            }

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val pattern = if (target.get(Calendar.YEAR) == currentYear) "d MMM" else "d MMM yyyy"
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * Formats trip date range e.g. "20-25 Nov 2026"
     */
    fun formatTripDateRange(startDateStr: String, endDateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sDate = parser.parse(startDateStr)
            val eDate = parser.parse(endDateStr)
            if (sDate != null && eDate != null) {
                val sCal = Calendar.getInstance().apply { time = sDate }
                val eCal = Calendar.getInstance().apply { time = eDate }

                val sDay = sCal.get(Calendar.DAY_OF_MONTH)
                val eDay = eCal.get(Calendar.DAY_OF_MONTH)
                val sMonth = SimpleDateFormat("MMM", Locale.getDefault()).format(sDate)
                val eMonth = SimpleDateFormat("MMM", Locale.getDefault()).format(eDate)

                if (sMonth == eMonth && sCal.get(Calendar.YEAR) == eCal.get(Calendar.YEAR)) {
                    "$sDay-$eDay $sMonth"
                } else {
                    "$sDay $sMonth - $eDay $eMonth"
                }
            } else {
                "$startDateStr - $endDateStr"
            }
        } catch (e: Exception) {
            "$startDateStr - $endDateStr"
        }
    }
}
