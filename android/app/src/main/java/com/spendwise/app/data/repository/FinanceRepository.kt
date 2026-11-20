package com.spendwise.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.spendwise.app.data.local.AppDatabase
import com.spendwise.app.data.model.*
import com.spendwise.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                categoryDao.cleanLegacyCategories()
            } catch (e: Exception) {
                Log.w("FinanceRepository", "Error cleaning legacy categories: ${e.message}")
            }
            // Trigger automatic sync on launch
            syncPendingToCloud()
        }

        // Automatic Network Connectivity Callback:
        // Automatically syncs offline-created records to Supabase the instant device goes back online!
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i("FinanceRepository", "Device back online! Syncing pending offline records...")
                        CoroutineScope(Dispatchers.IO).launch {
                            syncPendingToCloud()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.w("FinanceRepository", "Failed to register network callback: ${e.message}")
        }
    }

    private suspend fun resolveCanonicalCategoryId(categoryId: String?): String? {
        if (categoryId == null) return null
        if (categoryId.startsWith("cat-")) return categoryId
        val cat = categoryDao.getCategoryById(categoryId) ?: return categoryId
        return when (cat.name.lowercase().trim()) {
            "food & dining" -> "cat-food"
            "transportation" -> "cat-trans"
            "shopping" -> "cat-shop"
            "entertainment" -> "cat-ent"
            "bills & utilities" -> "cat-bills"
            "healthcare" -> "cat-health"
            "travel" -> "cat-travel"
            "groceries" -> "cat-groc"
            "education" -> "cat-edu"
            "salary" -> "cat-sal"
            "investment" -> "cat-inv"
            "freelance" -> "cat-free"
            "gift / bonus" -> "cat-gift"
            "other income" -> "cat-other-inc"
            else -> "cat-other-exp"
        }
    }

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
        val storedId = authUserId
        if (storedId.isNotBlank()) return storedId
        val storedEmail = prefs.getString("user_email", "") ?: ""
        if (storedEmail.isNotBlank() && storedEmail != "user@spendwise.app") {
            return storedEmail
        }
        if (supabaseProvider.isConfigured) {
            val liveUid = runCatching { supabaseProvider.getClient().auth.currentUserOrNull()?.id }.getOrNull()
            if (!liveUid.isNullOrBlank()) {
                authUserId = liveUid
                return liveUid
            }
        }
        return "user@spendwise.app"
    }

    fun getAllUserIdentifiers(): List<String> {
        val list = mutableListOf<String>()
        val storedId = authUserId
        if (storedId.isNotBlank()) list.add(storedId)
        val storedEmail = prefs.getString("user_email", "") ?: ""
        if (storedEmail.isNotBlank() && storedEmail != "user@spendwise.app") list.add(storedEmail)
        val uid = getEffectiveUserId()
        if (uid.isNotBlank() && uid != "user@spendwise.app") list.add(uid)
        return list.distinct()
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
     * Complete Two-Way Sync:
     * 1. Pushes all pending offline transactions, categories, budgets, and trips to Supabase.
     * 2. Removes any items marked for deletion while offline from Supabase and Room.
     * 3. Pulls down any remote updates from Supabase into local Room.
     */
    suspend fun syncPendingToCloud() = withContext(Dispatchers.IO) {
        if (!supabaseProvider.isConfigured) return@withContext
        val currentUserId = getEffectiveUserId()
        if (currentUserId == "demo-user") return@withContext

        try {
            val client = supabaseProvider.getClient()

            // 1. Sync Pending Categories (so category IDs exist for transactions)
            try {
                val pendingCats = categoryDao.getPendingSyncCategories()
                for (cat in pendingCats) {
                    try {
                        val targetCat = if (cat.userId.isNullOrBlank() && currentUserId != "user@spendwise.app") {
                            cat.copy(userId = currentUserId)
                        } else cat
                        client.from("categories").upsert(targetCat)
                        categoryDao.updateSyncStatus(cat.id, SyncStatus.SYNCED)
                        Log.i("FinanceRepository", "Synced offline category ${cat.name} to cloud")
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed syncing pending category ${cat.id}: ${e.message}")
                    }
                }

                val pendingDeleteCats = categoryDao.getPendingDeleteCategories()
                for (cat in pendingDeleteCats) {
                    try {
                        client.from("categories").delete { filter { eq("id", cat.id) } }
                        categoryDao.deletePermanently(cat.id)
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed deleting remote category ${cat.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("FinanceRepository", "Error syncing pending categories: ${e.message}")
            }

            // 2. Sync Pending Trips
            try {
                val pendingTrips = tripDao.getPendingSyncTrips()
                for (trip in pendingTrips) {
                    try {
                        val targetTrip = if (trip.userId == "user@spendwise.app" && currentUserId != "user@spendwise.app") {
                            trip.copy(userId = currentUserId)
                        } else trip
                        client.from("trips").upsert(targetTrip)
                        tripDao.updateSyncStatus(trip.id, SyncStatus.SYNCED)
                        Log.i("FinanceRepository", "Synced offline trip ${trip.destination} to cloud")
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed syncing pending trip ${trip.id}: ${e.message}")
                    }
                }

                val pendingDeleteTrips = tripDao.getPendingDeleteTrips()
                for (trip in pendingDeleteTrips) {
                    try {
                        client.from("trips").delete { filter { eq("id", trip.id) } }
                        tripDao.deletePermanently(trip.id)
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed deleting remote trip ${trip.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("FinanceRepository", "Error syncing pending trips: ${e.message}")
            }

            // 3. Sync Pending Transactions (Expenses and Incomes added while offline)
            try {
                val pendingTxs = transactionDao.getPendingSyncTransactions()
                for (tx in pendingTxs) {
                    try {
                        val targetTx = if (tx.userId == "user@spendwise.app" && currentUserId != "user@spendwise.app") {
                            tx.copy(userId = currentUserId)
                        } else tx
                        client.from("transactions").upsert(targetTx)
                        transactionDao.updateSyncStatus(tx.id, SyncStatus.SYNCED)
                        Log.i("FinanceRepository", "Synced offline transaction ${tx.id} (${tx.type} ₹${tx.amount}) to cloud")
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed syncing pending transaction ${tx.id}: ${e.message}")
                    }
                }

                val pendingDeleteTxs = transactionDao.getPendingDeleteTransactions()
                for (tx in pendingDeleteTxs) {
                    try {
                        client.from("transactions").delete { filter { eq("id", tx.id) } }
                        transactionDao.deletePermanently(tx.id)
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed deleting remote transaction ${tx.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("FinanceRepository", "Error syncing pending transactions: ${e.message}")
            }

            // 4. Sync Pending Budgets
            try {
                val pendingBudgets = budgetDao.getPendingSyncBudgets()
                for (b in pendingBudgets) {
                    try {
                        val targetBudget = if (b.userId == "user@spendwise.app" && currentUserId != "user@spendwise.app") {
                            b.copy(userId = currentUserId)
                        } else b
                        client.from("budgets").upsert(targetBudget)
                        budgetDao.updateSyncStatus(b.id, SyncStatus.SYNCED)
                        Log.i("FinanceRepository", "Synced offline budget ${b.id} to cloud")
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed syncing pending budget ${b.id}: ${e.message}")
                    }
                }

                val pendingDeleteBudgets = budgetDao.getPendingDeleteBudgets()
                for (b in pendingDeleteBudgets) {
                    try {
                        client.from("budgets").delete { filter { eq("id", b.id) } }
                        budgetDao.deletePermanently(b.id)
                    } catch (e: Exception) {
                        Log.w("FinanceRepository", "Failed deleting remote budget ${b.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("FinanceRepository", "Error syncing pending budgets: ${e.message}")
            }

            // 5. Pull any remote updates from Supabase
            syncFromCloud()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Global sync error: ${e.message}", e)
        }
    }

    /**
     * Restore all user data from Supabase cloud into local Room database.
     * Guarantees data is preserved even after uninstall / reinstall on same account.
     */
    suspend fun syncFromCloud() {
        if (!supabaseProvider.isConfigured) return
        withContext(Dispatchers.IO) {
            try {
                val client = supabaseProvider.getClient()
                val userIds = getAllUserIdentifiers()
                if (userIds.isEmpty()) return@withContext

                // 1. Transactions (do not overwrite locally pending items)
                try {
                    val remoteTxs = client.from("transactions").select {
                        filter {
                            isIn("user_id", userIds)
                        }
                    }.decodeList<TransactionEntity>()
                    if (remoteTxs.isNotEmpty()) {
                        val pendingIds = transactionDao.getPendingSyncTransactions().map { it.id }.toSet()
                        val safeRemoteTxs = remoteTxs.filter { !pendingIds.contains(it.id) }
                        if (safeRemoteTxs.isNotEmpty()) {
                            transactionDao.insertAll(safeRemoteTxs)
                        }
                        Log.i("FinanceRepository", "Synced ${remoteTxs.size} transactions from Supabase")
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote transactions: ${e.message}")
                }

                // 2. Trips
                try {
                    val remoteTrips = client.from("trips").select {
                        filter {
                            isIn("user_id", userIds)
                        }
                    }.decodeList<TripEntity>()
                    if (remoteTrips.isNotEmpty()) {
                        val pendingTripIds = tripDao.getPendingSyncTrips().map { it.id }.toSet()
                        val safeTrips = remoteTrips.filter { !pendingTripIds.contains(it.id) }
                        if (safeTrips.isNotEmpty()) {
                            tripDao.insertAll(safeTrips)
                        }
                        Log.i("FinanceRepository", "Synced ${remoteTrips.size} trips from Supabase")
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote trips: ${e.message}")
                }

                // 3. Budgets
                try {
                    val remoteBudgets = client.from("budgets").select {
                        filter {
                            isIn("user_id", userIds)
                        }
                    }.decodeList<BudgetEntity>()
                    if (remoteBudgets.isNotEmpty()) {
                        val pendingBudgetIds = budgetDao.getPendingSyncBudgets().map { it.id }.toSet()
                        val safeBudgets = remoteBudgets.filter { !pendingBudgetIds.contains(it.id) }
                        if (safeBudgets.isNotEmpty()) {
                            budgetDao.insertAll(safeBudgets)
                        }
                        Log.i("FinanceRepository", "Synced ${remoteBudgets.size} budgets from Supabase")
                    }
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote budgets: ${e.message}")
                }

                // 4. Categories (seed defaults & user custom)
                try {
                    val remoteCats = client.from("categories").select().decodeList<CategoryEntity>()
                    if (remoteCats.isNotEmpty()) {
                        val pendingCatIds = categoryDao.getPendingSyncCategories().map { it.id }.toSet()
                        val safeCats = remoteCats.filter { !pendingCatIds.contains(it.id) }
                        if (safeCats.isNotEmpty()) {
                            categoryDao.insertAll(safeCats)
                        }
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
        val canonicalCatId = resolveCanonicalCategoryId(categoryId)
        val tx = TransactionEntity(
            userId = currentUserId,
            categoryId = canonicalCatId,
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
        val canonicalCatId = resolveCanonicalCategoryId(transaction.categoryId)
        val cleanTx = if (canonicalCatId != transaction.categoryId) transaction.copy(categoryId = canonicalCatId) else transaction
        transactionDao.insertOrUpdate(cleanTx)
        if (supabaseProvider.isConfigured && cleanTx.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("transactions").upsert(cleanTx)
                    transactionDao.insertOrUpdate(cleanTx.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error uploading transaction to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        val canonicalCatId = resolveCanonicalCategoryId(transaction.categoryId)
        val cleanTx = if (canonicalCatId != transaction.categoryId) transaction.copy(categoryId = canonicalCatId) else transaction
        val updated = cleanTx.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        transactionDao.insertOrUpdate(updated)
        if (supabaseProvider.isConfigured && updated.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("transactions").upsert(updated)
                    transactionDao.insertOrUpdate(updated.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error updating transaction in Supabase: ${e.message}")
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

    suspend fun setBudget(
        categoryId: String,
        month: Int,
        year: Int,
        amount: Double,
        existingId: String? = null
    ) {
        val currentUserId = getEffectiveUserId()
        val canonicalCatId = resolveCanonicalCategoryId(categoryId) ?: categoryId
        val budget = BudgetEntity(
            id = existingId ?: UUID.randomUUID().toString(),
            userId = currentUserId,
            categoryId = canonicalCatId,
            month = month,
            year = year,
            amount = amount,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            syncStatus = if (existingId != null) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_INSERT
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
