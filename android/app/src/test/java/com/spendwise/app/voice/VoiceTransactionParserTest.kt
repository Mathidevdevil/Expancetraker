package com.spendwise.app.voice

import com.spendwise.app.data.model.CategoryEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class VoiceTransactionParserTest {

    private val defaultCategories = listOf(
        CategoryEntity(id = "cat-food", name = "Food & Dining", type = "expense", icon = "Utensils", color = "#EF4444", isDefault = true),
        CategoryEntity(id = "cat-trans", name = "Transportation", type = "expense", icon = "Car", color = "#F59E0B", isDefault = true),
        CategoryEntity(id = "cat-shop", name = "Shopping", type = "expense", icon = "ShoppingBag", color = "#EC4899", isDefault = true),
        CategoryEntity(id = "cat-ent", name = "Entertainment", type = "expense", icon = "Film", color = "#8B5CF6", isDefault = true),
        CategoryEntity(id = "cat-bills", name = "Bills & Utilities", type = "expense", icon = "Zap", color = "#3B82F6", isDefault = true),
        CategoryEntity(id = "cat-health", name = "Healthcare", type = "expense", icon = "HeartPulse", color = "#10B981", isDefault = true),
        CategoryEntity(id = "cat-travel", name = "Travel", type = "expense", icon = "Plane", color = "#06B6D4", isDefault = true),
        CategoryEntity(id = "cat-groc", name = "Groceries", type = "expense", icon = "ShoppingCart", color = "#84CC16", isDefault = true),
        CategoryEntity(id = "cat-edu", name = "Education", type = "expense", icon = "GraduationCap", color = "#6366F1", isDefault = true),
        CategoryEntity(id = "cat-other-exp", name = "Other Expense", type = "expense", icon = "MoreHorizontal", color = "#64748B", isDefault = true),
        CategoryEntity(id = "cat-sal", name = "Salary", type = "income", icon = "Briefcase", color = "#10B981", isDefault = true),
        CategoryEntity(id = "cat-inv", name = "Investment", type = "income", icon = "TrendingUp", color = "#059669", isDefault = true),
        CategoryEntity(id = "cat-free", name = "Freelance", type = "income", icon = "Laptop", color = "#34D399", isDefault = true),
        CategoryEntity(id = "cat-gift", name = "Gift / Bonus", type = "income", icon = "Gift", color = "#2DD4BF", isDefault = true),
        CategoryEntity(id = "cat-other-inc", name = "Other Income", type = "income", icon = "PlusCircle", color = "#14B8A6", isDefault = true),
        CategoryEntity(id = "custom-coffee", userId = "u1", name = "Specialty Coffee", type = "expense", icon = "Coffee", color = "#A67C36", isDefault = false)
    )

    // Fixed reference date: 2026-11-26
    private val refMillis: Long = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        set(2026, Calendar.NOVEMBER, 26, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun parsesTwentySixVoiceSentencesAccurately() {
        data class Case(
            val sentence: String,
            val expectedType: String?,
            val expectedAmount: Double?,
            val expectedCategory: String?,
            val expectedDate: String,
            val expectedPayment: String
        )

        val cases = listOf(
            // Required prompt examples (1-5)
            Case("spent 486 on swiggy dinner yesterday by UPI", "expense", 486.0, "Food & Dining", "2026-11-25", "upi"),
            Case("got 12000 freelance payment", "income", 12000.0, "Freelance", "2026-11-26", "upi"),
            Case("paid 1840 electricity bill", "expense", 1840.0, "Bills & Utilities", "2026-11-26", "upi"),
            Case("metro recharge 300", "expense", 300.0, "Transportation", "2026-11-26", "upi"),
            Case("salary credited 65000", "income", 65000.0, "Salary", "2026-11-26", "upi"),

            // Amounts: commas, words, decimals, k-shorthand (6-11)
            Case("spent 1,200 on amazon shopping via card", "expense", 1200.0, "Shopping", "2026-11-26", "card"),
            Case("paid twelve hundred for wifi internet by net banking", "expense", 1200.0, "Bills & Utilities", "2026-11-26", "net_banking"),
            Case("bought groceries for 1.5k today by gpay", "expense", 1500.0, "Groceries", "2026-11-26", "upi"),
            Case("earned twenty five thousand freelance project", "income", 25000.0, "Freelance", "2026-11-26", "upi"),
            Case("received dividend 3450.50 bank transfer", "income", 3450.50, "Investment", "2026-11-26", "bank_transfer"),
            Case("gave five hundred cash for auto ride", "expense", 500.0, "Transportation", "2026-11-26", "cash"),

            // Dates: day before yesterday, on 12th, explicit month (12-16)
            Case("spent 650 on zomato lunch day before yesterday", "expense", 650.0, "Food & Dining", "2026-11-24", "upi"),
            Case("paid 920 medicine at apollo on 12th", "expense", 920.0, "Healthcare", "2026-11-12", "upi"),
            Case("booked movie tickets 540 on 15th nov by paytm", "expense", 540.0, "Entertainment", "2026-11-15", "upi"),
            Case("spent 2200 on petrol 2 days ago via credit card", "expense", 2200.0, "Transportation", "2026-11-24", "card"),
            Case("got 5000 diwali bonus yesterday", "income", 5000.0, "Gift / Bonus", "2026-11-25", "upi"),

            // Additional English & custom category cases (17-20)
            Case("bought flight ticket 4800 for trip via debit card", "expense", 4800.0, "Travel", "2026-11-26", "card"),
            Case("paid 1499 college books by phonepe", "expense", 1499.0, "Education", "2026-11-26", "upi"),
            Case("spent 280 on specialty coffee today", "expense", 280.0, "Specialty Coffee", "2026-11-26", "upi"),
            Case("received 2000 cashback gift", "income", 2000.0, "Gift / Bonus", "2026-11-26", "upi"),

            // Tamil-transliterated English sentences (21-26)
            Case("swiggy dinner 450 rooba nethu gpay la potten", "expense", 450.0, "Food & Dining", "2026-11-25", "upi"),
            Case("auto ku 120 cash kuduthen iniku", "expense", 120.0, "Transportation", "2026-11-26", "cash"),
            Case("sambalam 55000 vanthuchu bank transfer", "income", 55000.0, "Salary", "2026-11-26", "bank_transfer"),
            Case("eb bill 1840 potten netbanking la", "expense", 1840.0, "Bills & Utilities", "2026-11-26", "net_banking"),
            Case("maligai groceries 850 rooba vaanginen", "expense", 850.0, "Groceries", "2026-11-26", "upi"),
            Case("freelance client kitta 15000 kidaichuthu", "income", 15000.0, "Freelance", "2026-11-26", "upi")
        )

        assertTrue("Expected at least 25 sentences", cases.size >= 25)

        cases.forEach { tc ->
            val parsed = VoiceTransactionParser.parseOffline(tc.sentence, defaultCategories, refMillis)
            assertEquals("Type mismatch for '${tc.sentence}'", tc.expectedType, parsed.type)
            assertEquals("Amount mismatch for '${tc.sentence}'", tc.expectedAmount!!, parsed.amount!!, 0.01)
            assertEquals("Category mismatch for '${tc.sentence}'", tc.expectedCategory, parsed.categoryName)
            assertEquals("Date mismatch for '${tc.sentence}'", tc.expectedDate, parsed.date)
            assertEquals("Payment mismatch for '${tc.sentence}'", tc.expectedPayment, parsed.paymentMethod)
            assertFalse("Should not be missing required fields for '${tc.sentence}'", parsed.isMissingRequiredFields)
        }
    }

    @Test
    fun missingAmountOrAmbiguousTypeRequiresUserCompletion() {
        val noAmount = VoiceTransactionParser.parseOffline("paid for swiggy dinner yesterday", defaultCategories, refMillis)
        assertEquals("expense", noAmount.type)
        assertNull(noAmount.amount)
        assertTrue(noAmount.isMissingRequiredFields)

        val noType = VoiceTransactionParser.parseOffline("500 something miscellaneous", defaultCategories, refMillis)
        assertNull(noType.type)
        assertEquals(500.0, noType.amount!!, 0.01)
        assertTrue(noType.isMissingRequiredFields)
    }

    @Test
    fun mapsSpeechPermissionDeniedAndNoSpeechErrorsProperly() {
        val permDenied = VoiceTransactionParser.mapAndroidSpeechError(9)
        assertEquals(VoiceSpeechErrorType.PERMISSION_DENIED, permDenied.type)
        assertTrue(permDenied.message.contains("permission", ignoreCase = true))

        val noSpeechMatch = VoiceTransactionParser.mapAndroidSpeechError(7)
        assertEquals(VoiceSpeechErrorType.NO_SPEECH, noSpeechMatch.type)

        val speechTimeout = VoiceTransactionParser.mapAndroidSpeechError(6)
        assertEquals(VoiceSpeechErrorType.NO_SPEECH, speechTimeout.type)

        val busy = VoiceTransactionParser.mapAndroidSpeechError(8)
        assertEquals(VoiceSpeechErrorType.RECOGNIZER_BUSY, busy.type)

        val net = VoiceTransactionParser.mapAndroidSpeechError(2)
        assertEquals(VoiceSpeechErrorType.NO_NETWORK, net.type)

        val langUnavailable = VoiceTransactionParser.mapAndroidSpeechError(12)
        assertEquals(VoiceSpeechErrorType.UNAVAILABLE, langUnavailable.type)

        val clientErr = VoiceTransactionParser.mapAndroidSpeechError(5)
        assertEquals(VoiceSpeechErrorType.UNAVAILABLE, clientErr.type)
    }
}
