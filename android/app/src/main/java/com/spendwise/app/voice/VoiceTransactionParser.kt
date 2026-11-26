package com.spendwise.app.voice

import com.spendwise.app.data.model.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class VoiceSpeechErrorType {
    PERMISSION_DENIED,
    NO_SPEECH,
    NO_NETWORK,
    RECOGNIZER_BUSY,
    UNAVAILABLE,
    UNKNOWN
}

data class VoiceSpeechError(
    val type: VoiceSpeechErrorType,
    val message: String
)

data class ParsedVoiceTransaction(
    val type: String?, // "expense", "income", or null when ambiguous/missing
    val amount: Double?,
    val categoryId: String?,
    val categoryName: String?,
    val date: String, // yyyy-MM-dd
    val paymentMethod: String, // "cash", "upi", "card", "net_banking", "bank_transfer"
    val note: String,
    val usedSmartParser: Boolean = false
) {
    val isMissingRequiredFields: Boolean
        get() = type == null || amount == null || amount <= 0.0
}

@Serializable
private data class EdgeParseRequest(
    val transcript: String,
    val categories: List<String>,
    val referenceDate: String
)

object VoiceTransactionParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val expenseVerbs = setOf(
        "spent", "spend", "paid", "pay", "bought", "buy", "purchased", "purchase",
        "gave", "give", "charged", "ordered", "order", "booked", "book",
        "recharged", "recharge", "bill", "billed", "expense",
        // Tamil-transliterated expense verbs/nouns
        "selavu", "kuduthen", "koduthen", "vaanginen", "vanginen", "potten",
        "katnen", "kattinen", "anupinen"
    )

    private val incomeVerbs = setOf(
        "got", "received", "receive", "earned", "earn", "salary", "credited",
        "credit", "deposited", "deposit", "income", "bonus", "refund", "refunded",
        "dividend", "interest", "cashback",
        // Tamil-transliterated income verbs/nouns
        "vanthuchu", "vandhuchu", "vandhadhu", "kidaichuthu", "kedaichadhu",
        "sambalam", "varavu"
    )

    private val categoryKeywords: Map<String, List<String>> = mapOf(
        "Food & Dining" to listOf(
            "swiggy", "zomato", "dinner", "lunch", "breakfast", "coffee", "tea", "chai",
            "snacks", "snack", "biryani", "dosa", "idli", "tiffin", "restaurant",
            "cafe", "bakery", "pizza", "burger", "food", "dining", "meal", "eat",
            "sapadu", "saapadu", "hotel"
        ),
        "Transportation" to listOf(
            "metro", "uber", "ola", "rapido", "auto", "rickshaw", "bus", "train",
            "petrol", "diesel", "fuel", "cab", "taxi", "parking", "toll", "commute",
            "fastag", "bmtc", "cmrl"
        ),
        "Bills & Utilities" to listOf(
            "electricity", "eb", "power bill", "current bill", "recharge", "mobile bill",
            "jio", "airtel", "vodafone", "vi", "bsnl", "wifi", "internet", "broadband",
            "fiber", "gas", "cylinder", "water bill", "dth", "rent", "maintenance", "bill"
        ),
        "Groceries" to listOf(
            "grocery", "groceries", "milk", "vegetables", "veggies", "fruits",
            "supermarket", "bigbasket", "blinkit", "zepto", "instamart", "dmart",
            "reliance smart", "kirana", "maligai", "rice", "curd"
        ),
        "Shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "clothes", "dress",
            "shirt", "jeans", "shoes", "shopping", "mall", "watch", "bag", "electronics"
        ),
        "Entertainment" to listOf(
            "movie", "cinema", "bookmyshow", "pvr", "inox", "netflix", "prime",
            "hotstar", "spotify", "concert", "game", "gaming", "outing"
        ),
        "Healthcare" to listOf(
            "hospital", "pharmacy", "medical", "medicine", "medicines", "tablets",
            "doctor", "apollo", "medplus", "clinic", "checkup", "scan", "dental"
        ),
        "Travel" to listOf(
            "flight", "indigo", "air india", "oyo", "resort", "vacation", "trip",
            "lodge", "tour", "makemytrip", "irctc", "stay"
        ),
        "Education" to listOf(
            "tuition", "books", "book", "course", "udemy", "coursera", "exam",
            "fees", "college", "school", "stationery", "notebook"
        ),
        "Salary" to listOf("salary", "paycheck", "wages", "sambalam", "stipend"),
        "Freelance" to listOf("freelance", "client", "consulting", "project", "invoice", "gig"),
        "Investment" to listOf("dividend", "interest", "mutual fund", "sip", "stock", "shares", "returns"),
        "Gift / Bonus" to listOf("gift", "bonus", "cashback", "prize", "reward", "diwali", "pongal")
    )

    private val numberUnits = mapOf(
        "zero" to 0, "one" to 1, "a" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        // Tamil transliterated basic numbers
        "onnu" to 1, "oru" to 1, "rendu" to 2, "moonu" to 3, "naalu" to 4, "anju" to 5,
        "aaru" to 6, "ezhu" to 7, "ettu" to 8, "ombathu" to 9, "pathu" to 10
    )

    private val numberTens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    private val numberMultipliers = mapOf(
        "hundred" to 100.0,
        "nooru" to 100.0,
        "thousand" to 1000.0,
        "aayiram" to 1000.0,
        "lakh" to 100000.0,
        "lakhs" to 100000.0,
        "lac" to 100000.0,
        "lacs" to 100000.0,
        "latcham" to 100000.0
    )

    private val monthsMap = mapOf(
        "jan" to 0, "january" to 0, "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2, "apr" to 3, "april" to 3,
        "may" to 4, "jun" to 5, "june" to 5, "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7, "sep" to 8, "sept" to 8, "september" to 8,
        "oct" to 9, "october" to 9, "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    private val allowedPaymentMethods = setOf("cash", "upi", "card", "net_banking", "bank_transfer")

    /**
     * Step 1: Offline on-device rule parser. Works without network connectivity.
     */
    fun parseOffline(
        transcript: String,
        categories: List<CategoryEntity>,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): ParsedVoiceTransaction {
        val raw = transcript.trim()
        val lower = raw.lowercase(Locale.US)

        val (resolvedDate, withoutDateText) = extractDateAndStrip(lower, referenceDateMillis)
        val amount = extractAmount(withoutDateText)
        val paymentMethod = extractPaymentMethod(lower)
        val explicitType = detectExplicitType(lower)
        val matchedCategory = matchCategory(lower, explicitType, categories)

        val finalType = when {
            explicitType != null -> explicitType
            matchedCategory != null -> matchedCategory.type
            else -> null
        }

        // Re-align category if explicit type conflicts with inferred category
        val finalCategory = if (matchedCategory != null && finalType != null && matchedCategory.type != finalType) {
            matchCategory(lower, finalType, categories)
        } else {
            matchedCategory
        }

        val note = buildNote(raw, paymentMethod)

        return ParsedVoiceTransaction(
            type = finalType,
            amount = amount,
            categoryId = finalCategory?.id,
            categoryName = finalCategory?.name,
            date = resolvedDate,
            paymentMethod = paymentMethod,
            note = note,
            usedSmartParser = false
        )
    }

    /**
     * Step 2: Optional Gemini smart parser via Supabase Edge Function (strict 3-second timeout).
     * Automatically validates JSON and falls back to offline parseOffline() on failure/timeout.
     */
    suspend fun parseWithOptionalSmartFallback(
        transcript: String,
        categories: List<CategoryEntity>,
        supabaseUrl: String,
        supabaseKey: String,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): ParsedVoiceTransaction {
        val offlineResult = parseOffline(transcript, categories, referenceDateMillis)
        if (supabaseUrl.isBlank() || supabaseKey.isBlank() || transcript.isBlank()) {
            return offlineResult
        }

        val smartResult = withTimeoutOrNull(3000L) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val endpoint = "${supabaseUrl.trimEnd('/')}/functions/v1/parse-voice-transaction"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 2500
                        readTimeout = 2500
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("apikey", supabaseKey)
                        setRequestProperty("Authorization", "Bearer $supabaseKey")
                    }

                    val bodyJson = json.encodeToString(
                        EdgeParseRequest.serializer(),
                        EdgeParseRequest(
                            transcript = transcript,
                            categories = categories.map { it.name },
                            referenceDate = offlineResult.date
                        )
                    )
                    OutputStreamWriter(conn.outputStream).use { it.write(bodyJson) }

                    if (conn.responseCode !in 200..299) {
                        conn.disconnect()
                        return@runCatching null
                    }
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    validateSmartJson(respText, categories, offlineResult)
                }.getOrNull()
            }
        }

        return smartResult ?: offlineResult
    }

    internal fun validateSmartJson(
        rawJson: String,
        categories: List<CategoryEntity>,
        fallback: ParsedVoiceTransaction
    ): ParsedVoiceTransaction? {
        val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
        val payload = obj["parsed"] as? JsonObject ?: obj

        val typeRaw = payload["type"]?.jsonPrimitive?.content?.lowercase(Locale.US)?.trim()
        val type = when (typeRaw) {
            "expense", "income" -> typeRaw
            else -> fallback.type
        }

        val amtDouble = payload["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val amount = if (amtDouble != null && amtDouble > 0.0) amtDouble else fallback.amount

        val catNameRaw = payload["category"]?.jsonPrimitive?.content?.trim()
        val matchedCat = if (!catNameRaw.isNullOrBlank()) {
            categories.firstOrNull {
                it.name.equals(catNameRaw, ignoreCase = true) && (type == null || it.type == type)
            } ?: categories.firstOrNull { it.name.equals(catNameRaw, ignoreCase = true) }
        } else null

        val dateRaw = payload["date"]?.jsonPrimitive?.content?.trim()
        val date = if (dateRaw != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateRaw)) {
            dateRaw
        } else {
            fallback.date
        }

        val pmRaw = payload["paymentMethod"]?.jsonPrimitive?.content?.lowercase(Locale.US)?.trim()
        val paymentMethod = if (pmRaw != null && allowedPaymentMethods.contains(pmRaw)) {
            pmRaw
        } else {
            fallback.paymentMethod
        }

        val noteRaw = payload["note"]?.jsonPrimitive?.content?.trim()
        val note = if (!noteRaw.isNullOrBlank()) noteRaw else fallback.note

        return ParsedVoiceTransaction(
            type = type,
            amount = amount,
            categoryId = matchedCat?.id ?: fallback.categoryId,
            categoryName = matchedCat?.name ?: fallback.categoryName,
            date = date,
            paymentMethod = paymentMethod,
            note = note,
            usedSmartParser = true
        )
    }

    private fun detectExplicitType(lower: String): String? {
        val sanitized = lower.replace(Regex("\\b(?:credit|debit)\\s+card\\b"), " card ")
        val words = sanitized.replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val hasIncome = words.any { it in incomeVerbs } || sanitized.contains("money received")
        val hasExpense = words.any { it in expenseVerbs }

        return when {
            hasIncome && !hasExpense -> "income"
            hasExpense && !hasIncome -> "expense"
            hasIncome && hasExpense -> {
                // Salary / credited / received takes precedence if explicitly stated with payment verb
                if (words.any { it == "salary" || it == "credited" || it == "sambalam" || it == "received" }) {
                    "income"
                } else {
                    "expense"
                }
            }
            else -> null
        }
    }

    private fun extractDateAndStrip(lower: String, referenceDateMillis: Long): Pair<String, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { timeInMillis = referenceDateMillis }
        var cleaned = lower

        when {
            lower.contains("day before yesterday") || lower.contains("munthanethu") -> {
                cal.add(Calendar.DAY_OF_YEAR, -2)
                cleaned = cleaned.replace("day before yesterday", " ").replace("munthanethu", " ")
            }
            Regex("\\b(\\d+)\\s+days\\s+ago\\b").containsMatchIn(lower) -> {
                val match = Regex("\\b(\\d+)\\s+days\\s+ago\\b").find(lower)
                val days = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                cal.add(Calendar.DAY_OF_YEAR, -days)
                cleaned = cleaned.replace(match!!.value, " ")
            }
            Regex("\\b(yesterday|nethu|netru)\\b").containsMatchIn(lower) -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cleaned = cleaned.replace(Regex("\\b(yesterday|nethu|netru)\\b"), " ")
            }
            Regex("\\b(today|iniku|innaiku)\\b").containsMatchIn(lower) -> {
                cleaned = cleaned.replace(Regex("\\b(today|iniku|innaiku)\\b"), " ")
            }
            else -> {
                // Match "on 12th", "on the 5th", "12 nov", "12th november"
                val dayMonthRegex = Regex("\\b(?:on\\s+(?:the\\s+)?)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\b")
                val dmMatch = dayMonthRegex.find(lower)
                if (dmMatch != null) {
                    val day = dmMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 31)
                    val monthIdx = monthsMap[dmMatch.groupValues[2]]
                    if (day != null && monthIdx != null) {
                        cal.set(Calendar.MONTH, monthIdx)
                        cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                        cleaned = cleaned.replace(dmMatch.value, " ")
                    }
                } else {
                    val ordinalRegex = Regex("\\b(?:on\\s+(?:the\\s+)?)?(\\d{1,2})(?:st|nd|rd|th)\\b")
                    val ordMatch = ordinalRegex.find(lower)
                    if (ordMatch != null) {
                        val day = ordMatch.groupValues[1].toIntOrNull()
                        if (day != null && day in 1..31) {
                            val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                            if (day > currentDay) {
                                cal.add(Calendar.MONTH, -1)
                            }
                            cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                            cleaned = cleaned.replace(ordMatch.value, " ")
                        }
                    }
                }
            }
        }

        return sdf.format(cal.time) to cleaned
    }

    private fun extractAmount(textWithoutDate: String): Double? {
        // 1. Numeric expressions with optional commas, decimals, and k/lakh suffix (e.g. "486", "1,200", "1.5k", "2.5 lakh")
        val numericSuffixRegex = Regex("(?:₹|rs\\.?|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|thousand|aayiram|lakh|lakhs|lac|lacs|latcham)?\\b")
        val matches = numericSuffixRegex.findAll(textWithoutDate).toList()
        if (matches.isNotEmpty()) {
            for (m in matches) {
                val rawNum = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                if (rawNum <= 0.0) continue
                val suffix = m.groupValues[2].lowercase(Locale.US)
                val multiplier = when (suffix) {
                    "k", "thousand", "aayiram" -> 1000.0
                    "lakh", "lakhs", "lac", "lacs", "latcham" -> 100000.0
                    else -> 1.0
                }
                return rawNum * multiplier
            }
        }

        // 2. Spoken English & Tamil-transliterated number words (e.g. "twelve hundred", "one and a half thousand", "twenty five thousand")
        val normalized = textWithoutDate
            .replace("one and a half thousand", "1500")
            .replace("and a half thousand", "500")
            .replace("-", " ")

        // Re-check if phrase replacement introduced digits
        Regex("\\b(\\d+(?:\\.\\d+)?)\\b").find(normalized)?.let {
            return it.groupValues[1].toDoubleOrNull()
        }

        val tokens = normalized.replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        var total = 0.0
        var current = 0.0
        var foundWordNumber = false

        for (token in tokens) {
            when {
                token == "and" && foundWordNumber -> Unit
                numberUnits.containsKey(token) -> {
                    current += numberUnits[token]!!
                    foundWordNumber = true
                }
                numberTens.containsKey(token) -> {
                    current += numberTens[token]!!
                    foundWordNumber = true
                }
                numberMultipliers.containsKey(token) -> {
                    val mult = numberMultipliers[token]!!
                    val base = if (current == 0.0) 1.0 else current
                    if (mult >= 1000.0) {
                        total += base * mult
                        current = 0.0
                    } else {
                        current = base * mult
                    }
                    foundWordNumber = true
                }
                else -> {
                    if (foundWordNumber) break
                }
            }
        }

        val combined = total + current
        return if (foundWordNumber && combined > 0.0) combined else null
    }

    private fun extractPaymentMethod(lower: String): String {
        return when {
            Regex("\\b(net\\s*banking|netbanking|neft|imps|rtgs)\\b").containsMatchIn(lower) -> "net_banking"
            Regex("\\b(bank\\s*transfer)\\b").containsMatchIn(lower) -> "bank_transfer"
            Regex("\\b(credit\\s*card|debit\\s*card|card|visa|mastercard|rupay|atm)\\b").containsMatchIn(lower) -> "card"
            Regex("\\b(cash|kaasu)\\b").containsMatchIn(lower) -> "cash"
            Regex("\\b(upi|gpay|g\\s*pay|google\\s*pay|phonepe|phone\\s*pe|paytm|bhim)\\b").containsMatchIn(lower) -> "upi"
            else -> "upi"
        }
    }

    private fun matchCategory(
        lower: String,
        preferredType: String?,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        val candidates = if (preferredType != null) {
            categories.filter { it.type == preferredType }.ifEmpty { categories }
        } else {
            categories
        }

        // 1. Direct match against user's existing category names (custom categories checked first)
        val orderedByCustom = candidates.sortedBy { it.isDefault }
        for (cat in orderedByCustom) {
            val catLower = cat.name.lowercase(Locale.US).trim()
            if (catLower.isNotBlank() && Regex("\\b${Regex.escape(catLower)}\\b").containsMatchIn(lower)) {
                return cat
            }
        }

        // 2. Keyword dictionary match
        var bestCategory: CategoryEntity? = null
        var bestScore = 0

        for (cat in candidates) {
            val keywords = categoryKeywords[cat.name] ?: continue
            var score = 0
            for (kw in keywords) {
                if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(lower)) {
                    // Prefer multi-word or specific category match
                    score += if (kw.contains(" ")) 3 else 2
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestCategory = cat
            }
        }

        if (bestCategory != null) return bestCategory

        // 3. Fallback when type is known -> Other Expense / Other Income
        if (preferredType == "expense") {
            return candidates.firstOrNull { it.id == "cat-other-exp" || it.name.equals("Other Expense", true) }
        }
        if (preferredType == "income") {
            return candidates.firstOrNull { it.id == "cat-other-inc" || it.name.equals("Other Income", true) }
        }
        return null
    }

    private fun buildNote(raw: String, paymentMethod: String): String {
        var note = raw.trim()
            .replace(Regex("(?i)\\b(i|spent|paid|bought|gave|got|received|earned|credited|yesterday|today|day before yesterday|nethu|iniku|selavu|kuduthen|vaanginen|potten|vanthuchu|kidaichuthu|rooba|rupees|inr|rs\\.?|by|via|using|through|with|on|for|at|from|to|the|a|an|la|ku)\\b"), " ")
            .replace(Regex("(?i)(?:₹|\\b\\d[\\d,]*(?:\\.\\d+)?\\s*(?:k|thousand|lakh|lakhs|lac)?\\b)"), " ")
            .replace(Regex("(?i)\\b(upi|gpay|google pay|phonepe|paytm|cash|card|credit card|debit card|net banking|netbanking|neft|imps|bank transfer)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (note.isBlank()) {
            note = raw.trim()
        }
        return note.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .take(80)
    }

    fun mapAndroidSpeechError(errorCode: Int): VoiceSpeechError {
        return when (errorCode) {
            9 /* SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS */ ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.PERMISSION_DENIED,
                    "Microphone permission is required to use voice entry."
                )
            6 /* ERROR_SPEECH_TIMEOUT */, 7 /* ERROR_NO_MATCH */ ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.NO_SPEECH,
                    "Didn't catch any speech. Please try speaking clearly or enter manually."
                )
            1 /* ERROR_NETWORK_TIMEOUT */, 2 /* ERROR_NETWORK */ ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.NO_NETWORK,
                    "Speech service needs a network connection or on-device language pack."
                )
            8 /* ERROR_RECOGNIZER_BUSY */ ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.RECOGNIZER_BUSY,
                    "Speech recognizer is busy. Please wait a moment and try again."
                )
            12 /* ERROR_LANGUAGE_NOT_SUPPORTED */, 13 /* ERROR_LANGUAGE_UNAVAILABLE */ ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.UNAVAILABLE,
                    "Selected speech language is unavailable for inline recognition. Try system voice input or enter manually."
                )
            3 /* ERROR_AUDIO */, 4 /* ERROR_SERVER */, 5 /* ERROR_CLIENT */, 10 /* ERROR_SERVER_DISCONNECTED */ ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.UNAVAILABLE,
                    "Voice service could not connect (code $errorCode). Try system voice input or enter manually."
                )
            else ->
                VoiceSpeechError(
                    VoiceSpeechErrorType.UNKNOWN,
                    "Could not process voice input (code $errorCode). Please try again or enter manually."
                )
        }
    }
}
