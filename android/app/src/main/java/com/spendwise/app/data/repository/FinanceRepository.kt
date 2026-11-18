package com.spendwise.app.data.repository

import android.content.Context
import android.util.Log
import com.spendwise.app.data.local.AppDatabase
import com.spendwise.app.data.model.*
import com.spendwise.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
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

    var authUserId: String
        get() = prefs.getString("auth_user_id", "") ?: ""
        set(value) { prefs.edit().putString("auth_user_id", value).apply() }

    fun getEffectiveUserId(): String {
        val storedEmail = prefs.getString("user_email", "") ?: ""
        if (storedEmail.isNotBlank() && storedEmail != "user@spendwise.app") {
            return storedEmail
        }
        val storedId = authUserId
        if (storedId.isNotBlank()) return storedId
        if (supabaseProvider.isConfigured) {
            val liveUid = runCatching { supabaseProvider.getClient().auth.currentUserOrNull()?.id }.getOrNull()
            if (!liveUid.isNullOrBlank()) {
                authUserId = liveUid
                return liveUid
            }
        }
        return "user@spendwise.app"
    }

    fun logout() {
        isLoggedIn = false
        authUserId = ""
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

    /**
     * Restore all user data from Supabase cloud into local Room database.
     * Guarantees data is preserved even after uninstall / reinstall on same account.
     */
    suspend fun syncFromCloud() {
        if (!supabaseProvider.isConfigured) return
        withContext(Dispatchers.IO) {
            try {
                val client = supabaseProvider.getClient()
                val uid = getEffectiveUserId()
                if (uid.isBlank() || uid == "demo-user") return@withContext

                // 1. Transactions
                try {
                    val remoteTxs = client.from("transactions").select {
                        filter {
                            eq("user_id", uid)
                        }
                    }.decodeList<TransactionEntity>()
                    if (remoteTxs.isNotEmpty()) {
                        transactionDao.insertAll(remoteTxs)
                        Log.i("FinanceRepository", "Synced ${remoteTxs.size} transactions from Supabase")
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote transactions: ${e.message}")
                }

                // 2. Trips
                try {
                    val remoteTrips = client.from("trips").select {
                        filter {
                            eq("user_id", uid)
                        }
                    }.decodeList<TripEntity>()
                    if (remoteTrips.isNotEmpty()) {
                        tripDao.insertAll(remoteTrips)
                        Log.i("FinanceRepository", "Synced ${remoteTrips.size} trips from Supabase")
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote trips: ${e.message}")
                }

                // 3. Budgets
                try {
                    val remoteBudgets = client.from("budgets").select {
                        filter {
                            eq("user_id", uid)
                        }
                    }.decodeList<BudgetEntity>()
                    if (remoteBudgets.isNotEmpty()) {
                        budgetDao.insertAll(remoteBudgets)
                        Log.i("FinanceRepository", "Synced ${remoteBudgets.size} budgets from Supabase")
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote budgets: ${e.message}")
                }

                // 4. Categories (seed defaults & user custom)
                try {
                    val remoteCats = client.from("categories").select().decodeList<CategoryEntity>()
                    if (remoteCats.isNotEmpty()) {
                        categoryDao.insertAll(remoteCats)
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote categories: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("FinanceRepository", "Cloud sync exception: ${e.message}", e)
            }
        }
    }

    // Sub-5-second fast write to Room local DB + Background Cloud Upload
    suspend fun quickAddExpense(
        amount: Double,
        categoryId: String?,
        tripId: String?,
        paymentMethod: String,
        note: String?,
        date: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        type: String = "expense"
    ) {
        val currentUserId = getEffectiveUserId()
        val tx = TransactionEntity(
            userId = currentUserId,
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

        // Upload to Supabase if online
        if (supabaseProvider.isConfigured && currentUserId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("transactions").upsert(tx)
                    transactionDao.insertOrUpdate(tx.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Saved locally. Supabase upload queued/error: ${e.message}")
                }
            }
        }
    }

    suspend fun insertTransaction(transaction: TransactionEntity) {
        transactionDao.insertOrUpdate(transaction)
        if (supabaseProvider.isConfigured && transaction.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("transactions").upsert(transaction)
                    transactionDao.insertOrUpdate(transaction.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error uploading transaction to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteTransaction(id: String) {
        transactionDao.markForDeletion(id)
        if (supabaseProvider.isConfigured) {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("transactions").delete {
                        filter { eq("id", id) }
                    }
                    transactionDao.deletePermanently(id)
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error deleting transaction from Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun insertCategory(category: CategoryEntity) {
        categoryDao.insertOrUpdate(category)
        if (supabaseProvider.isConfigured && category.userId != null) {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("categories").upsert(category)
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error uploading category to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteCategory(id: String) {
        categoryDao.markForDeletion(id)
        if (supabaseProvider.isConfigured) {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("categories").delete {
                        filter { eq("id", id) }
                    }
                    categoryDao.deletePermanently(id)
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error deleting category from Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun setBudget(categoryId: String, month: Int, year: Int, amount: Double) {
        val currentUserId = getEffectiveUserId()
        val budget = BudgetEntity(
            userId = currentUserId,
            categoryId = categoryId,
            month = month,
            year = year,
            amount = amount,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            syncStatus = SyncStatus.PENDING_INSERT
        )
        budgetDao.insertOrUpdate(budget)

        if (supabaseProvider.isConfigured && currentUserId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("budgets").upsert(budget)
                    budgetDao.insertOrUpdate(budget.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error uploading budget to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteBudget(id: String) {
        budgetDao.markForDeletion(id)
        if (supabaseProvider.isConfigured) {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("budgets").delete {
                        filter { eq("id", id) }
                    }
                    budgetDao.deletePermanently(id)
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error deleting budget from Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun insertTrip(trip: TripEntity) {
        tripDao.insertOrUpdate(trip)
        if (supabaseProvider.isConfigured && trip.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("trips").upsert(trip)
                    tripDao.insertOrUpdate(trip.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error uploading trip to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteTrip(id: String) {
        tripDao.markForDeletion(id)
        if (supabaseProvider.isConfigured) {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("trips").delete {
                        filter { eq("id", id) }
                    }
                    tripDao.deletePermanently(id)
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error deleting trip from Supabase: ${e.message}")
                }
            }
        }
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
