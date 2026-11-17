package com.spendwise.app.data.repository

import android.content.Context
import com.spendwise.app.data.local.AppDatabase
import com.spendwise.app.data.model.*
import com.spendwise.app.data.remote.SupabaseClientProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val name: String = "Spendwise User",
    val email: String = "user@spendwise.app",
    val initials: String = "SU"
)

@Singleton
class FinanceRepository @Inject constructor(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val supabaseProvider: SupabaseClientProvider
) {
    private val prefs = context.getSharedPreferences("spendwise_prefs", Context.MODE_PRIVATE)
    private val categoryDao = appDatabase.categoryDao()
    private val transactionDao = appDatabase.transactionDao()
    private val budgetDao = appDatabase.budgetDao()
    private val tripDao = appDatabase.tripDao()

    var selectedCurrency: String
        get() = prefs.getString("selected_currency", "INR") ?: "INR"
        set(value) { prefs.edit().putString("selected_currency", value).apply() }

    var selectedCurrencySymbol: String
        get() = prefs.getString("selected_currency_symbol", "₹") ?: "₹"
        set(value) { prefs.edit().putString("selected_currency_symbol", value).apply() }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", false)
        set(value) { prefs.edit().putBoolean("is_logged_in", value).apply() }

    fun logout() {
        isLoggedIn = false
    }

    suspend fun clearAllUserData() {
        transactionDao.clearAllTransactions()
        budgetDao.clearAllBudgets()
        tripDao.clearAllTrips()
    }

    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", "dark") ?: "dark"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        val validMode = when (mode) {
            "light", "system" -> mode
            else -> "dark"
        }
        prefs.edit().putString("theme_mode", validMode).apply()
        _themeMode.value = validMode
    }

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = prefs.getString("user_name", "Spendwise User") ?: "Spendwise User",
            email = prefs.getString("user_email", "user@spendwise.app") ?: "user@spendwise.app",
            initials = prefs.getString("user_initials", "SU") ?: "SU"
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    fun updateProfile(name: String, email: String, initials: String? = null) {
        val cleanName = name.trim().ifEmpty { "User" }
        val cleanEmail = email.trim().ifEmpty { "user@spendwise.app" }
        val derivedInitials = initials?.trim()?.takeIf { it.isNotBlank() } ?: cleanName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "SW" }

        prefs.edit()
            .putString("user_name", cleanName)
            .putString("user_email", cleanEmail)
            .putString("user_initials", derivedInitials)
            .apply()

        _userProfile.value = UserProfile(
            name = cleanName,
            email = cleanEmail,
            initials = derivedInitials
        )
    }

    // Transactions with joined category and trip
    val transactionsWithDetails: Flow<List<TransactionWithDetails>> = combine(
        transactionDao.getAllTransactions(),
        categoryDao.getAllCategories(),
        tripDao.getAllTrips()
    ) { txs, cats, trips ->
        val catMap = cats.associateBy { it.id }
        val tripMap = trips.associateBy { it.id }
        txs.map { tx ->
            TransactionWithDetails(
                transaction = tx,
                category = tx.categoryId?.let { catMap[it] },
                trip = tx.tripId?.let { tripMap[it] }
            )
        }
    }

    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allTrips: Flow<List<TripEntity>> = tripDao.getAllTrips()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()

    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(month, year)

    fun getTransactionsForTrip(tripId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForTrip(tripId)

    // Sub-5-second fast write to Room local DB
    suspend fun quickAddExpense(
        amount: Double,
        categoryId: String?,
        tripId: String?,
        paymentMethod: String,
        note: String?,
        date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        type: String = "expense"
    ) {
        val tx = TransactionEntity(
            userId = "demo-user",
            categoryId = categoryId,
            tripId = tripId,
            type = type,
            amount = amount,
            date = date,
            note = note,
            paymentMethod = paymentMethod,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            syncStatus = SyncStatus.PENDING_INSERT
        )
        transactionDao.insertOrUpdate(tx)
    }

    suspend fun insertTransaction(transaction: TransactionEntity) {
        transactionDao.insertOrUpdate(transaction)
    }

    suspend fun deleteTransaction(id: String) {
        transactionDao.markForDeletion(id)
    }

    suspend fun insertCategory(category: CategoryEntity) {
        categoryDao.insertOrUpdate(category)
    }

    suspend fun deleteCategory(id: String) {
        categoryDao.markForDeletion(id)
    }

    suspend fun setBudget(categoryId: String, month: Int, year: Int, amount: Double) {
        val budget = BudgetEntity(
            userId = "demo-user",
            categoryId = categoryId,
            month = month,
            year = year,
            amount = amount,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            syncStatus = SyncStatus.PENDING_INSERT
        )
        budgetDao.insertOrUpdate(budget)
    }

    suspend fun deleteBudget(id: String) {
        budgetDao.markForDeletion(id)
    }

    suspend fun insertTrip(trip: TripEntity) {
        tripDao.insertOrUpdate(trip)
    }

    suspend fun deleteTrip(id: String) {
        tripDao.markForDeletion(id)
    }

    fun generateCSVFile(context: Context, transactions: List<TransactionWithDetails>): java.io.File {
        val file = java.io.File(context.cacheDir, "spendwise_report_${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("Date,Type,Category,Trip,Amount,Payment Method,Note\n")
            for (item in transactions) {
                val t = item.transaction
                val catName = (item.category?.name ?: "").replace("\"", "\"\"")
                val tripDest = (item.trip?.destination ?: "").replace("\"", "\"\"")
                val cleanNote = (t.note ?: "").replace("\"", "\"\"")
                writer.write("${t.date},${t.type},\"$catName\",\"$tripDest\",${t.amount},${t.paymentMethod},\"$cleanNote\"\n")
            }
        }
        return file
    }
}
