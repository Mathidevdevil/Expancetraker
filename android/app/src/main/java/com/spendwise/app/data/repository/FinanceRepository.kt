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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    private fun nowIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun todayIsoDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private val canonicalCategoryAliases = mapOf(
        "food & dining" to "cat-food",
        "transportation" to "cat-trans",
        "shopping" to "cat-shop",
        "entertainment" to "cat-ent",
        "bills & utilities" to "cat-bills",
        "healthcare" to "cat-health",
        "travel" to "cat-travel",
        "groceries" to "cat-groc",
        "education" to "cat-edu",
        "other expense" to "cat-other-exp",
        "salary" to "cat-sal",
        "investment" to "cat-inv",
        "freelance" to "cat-free",
        "gift / bonus" to "cat-gift",
        "other income" to "cat-other-inc"
    )

    private suspend fun resolveCanonicalCategoryId(categoryId: String?): String? {
        if (categoryId == null) return null
        if (categoryId.startsWith("cat-")) return categoryId
        val cat = categoryDao.getCategoryById(categoryId) ?: return categoryId
        val mapped = canonicalCategoryAliases[cat.name.lowercase().trim()]
        // Only remap if it's a legacy/default duplicate of a system category; preserve all user custom category IDs!
        return if (mapped != null && (cat.isDefault || cat.userId == null)) {
            mapped
        } else {
            cat.id
        }
    }

    private suspend fun ensureDefaultCategoriesSeeded() {
        val defaults = listOf(
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
            CategoryEntity(id = "cat-other-inc", name = "Other Income", type = "income", icon = "PlusCircle", color = "#14B8A6", isDefault = true)
        )
        categoryDao.insertAll(defaults)
    }

    private fun deduplicateCategories(list: List<CategoryEntity>): List<CategoryEntity> {
        val grouped = LinkedHashMap<String, CategoryEntity>()
        for (cat in list) {
            val key = "${cat.name.trim().lowercase()}|${cat.type.trim().lowercase()}"
            val existing = grouped[key]
            if (existing == null) {
                grouped[key] = cat
            } else {
                // Always prefer canonical global slug ID ('cat-*') over copied UUID defaults
                val existingCanonical = existing.id.startsWith("cat-")
                val candidateCanonical = cat.id.startsWith("cat-")
                if (!existingCanonical && candidateCanonical) {
                    grouped[key] = cat
                } else if (existingCanonical == candidateCanonical && existing.isDefault && !cat.isDefault) {
                    grouped[key] = cat
                }
            }
        }
        return grouped.values.toList()
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

    private val _voiceLanguage = MutableStateFlow(
        prefs.getString("voice_language", "en-IN") ?: "en-IN"
    )
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    fun setVoiceLanguage(languageTag: String) {
        val valid = if (languageTag == "ta-IN") "ta-IN" else "en-IN"
        prefs.edit().putString("voice_language", valid).apply()
        _voiceLanguage.value = valid
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
        val dedupedCats = deduplicateCategories(cats)
        val catMap = cats.associateBy { it.id }
        val canonicalMap = dedupedCats.associateBy { it.id }
        val tripMap = trips.associateBy { it.id }
        txs.map { tx ->
            val rawCat = tx.categoryId?.let { catMap[it] }
            val resolvedCat = when {
                rawCat != null && (!rawCat.isDefault || rawCat.id.startsWith("cat-")) -> rawCat
                rawCat != null -> canonicalCategoryAliases[rawCat.name.trim().lowercase()]?.let { canonicalMap[it] } ?: rawCat
                else -> tx.categoryId?.let { canonicalMap[it] }
            }
            TransactionWithDetails(
                transaction = tx,
                category = resolvedCat,
                trip = tx.tripId?.let { tripMap[it] }
            )
        }
    }

    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories().map { deduplicateCategories(it) }
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
                        val targetCat = cat.copy(
                            userId = if (cat.isDefault) null else (cat.userId?.takeIf { it.isNotBlank() } ?: currentUserId),
                            createdAt = cat.createdAt.ifBlank { nowIsoUtc() }
                        )
                        categoryDao.insertOrUpdate(targetCat)
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
                        val targetTrip = trip.copy(
                            userId = if ((trip.userId == "user@spendwise.app" || trip.userId == "default-user" || trip.userId.isBlank()) && currentUserId != "user@spendwise.app") {
                                currentUserId
                            } else trip.userId,
                            createdAt = trip.createdAt.ifBlank { nowIsoUtc() }
                        )
                        tripDao.insertOrUpdate(targetTrip)
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
                        val resolvedCatId = resolveCanonicalCategoryId(tx.categoryId)
                        val targetTx = tx.copy(
                            userId = if (tx.userId == "user@spendwise.app" && currentUserId != "user@spendwise.app") {
                                currentUserId
                            } else tx.userId,
                            categoryId = resolvedCatId,
                            createdAt = tx.createdAt.ifBlank { nowIsoUtc() }
                        )
                        ensureParentsExistInCloud(targetTx.categoryId, targetTx.tripId, currentUserId)
                        transactionDao.insertOrUpdate(targetTx)
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
                        val targetBudget = b.copy(
                            userId = if (b.userId == "user@spendwise.app" && currentUserId != "user@spendwise.app") {
                                currentUserId
                            } else b.userId,
                            categoryId = resolveCanonicalCategoryId(b.categoryId) ?: b.categoryId,
                            createdAt = b.createdAt.ifBlank { nowIsoUtc() }
                        )
                        ensureParentsExistInCloud(targetBudget.categoryId, null, currentUserId)
                        budgetDao.insertOrUpdate(targetBudget)
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

                // 4. Categories (seed canonical defaults + only current user's custom categories)
                try {
                    ensureDefaultCategoriesSeeded()
                    val remoteCats = client.from("categories").select {
                        filter {
                            or {
                                exact("user_id", null)
                                isIn("user_id", userIds)
                            }
                        }
                    }.decodeList<CategoryEntity>()

                    if (remoteCats.isNotEmpty()) {
                        val pendingCatIds = categoryDao.getPendingSyncCategories().map { it.id }.toSet()
                        val filteredCats = remoteCats.filter { cat ->
                            val belongsToUser = cat.userId == null || userIds.contains(cat.userId)
                            val isClonedDefault = cat.isDefault && !cat.id.startsWith("cat-")
                            if (isClonedDefault) {
                                // Migrate any local references from legacy cloned default UUIDs to canonical cat-* slug
                                canonicalCategoryAliases[cat.name.trim().lowercase()]?.let { canonicalId ->
                                    transactionDao.migrateCategoryIds(cat.id, canonicalId)
                                    categoryDao.migrateBudgetCategoryIds(cat.id, canonicalId)
                                }
                            }
                            belongsToUser && !isClonedDefault && !pendingCatIds.contains(cat.id)
                        }
                        if (filteredCats.isNotEmpty()) {
                            categoryDao.insertAll(filteredCats)
                        }
                    }
                    categoryDao.cleanLegacyCategories()
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error fetching remote categories: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("FinanceRepository", "Cloud sync exception: ${e.message}", e)
            }
        }
    }

    private suspend fun ensureParentsExistInCloud(categoryId: String?, tripId: String?, currentUserId: String) {
        if (!supabaseProvider.isConfigured || currentUserId == "demo-user") return
        val client = supabaseProvider.getClient()
        if (!categoryId.isNullOrBlank() && !categoryId.startsWith("cat-")) {
            val cat = categoryDao.getCategoryById(categoryId)
            if (cat != null && cat.syncStatus != SyncStatus.SYNCED) {
                runCatching {
                    val cloudCat = cat.copy(
                        userId = if (cat.isDefault) null else (cat.userId?.takeIf { it.isNotBlank() } ?: currentUserId),
                        createdAt = cat.createdAt.ifBlank { nowIsoUtc() }
                    )
                    client.from("categories").upsert(cloudCat)
                    categoryDao.insertOrUpdate(cloudCat.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }
        if (!tripId.isNullOrBlank()) {
            val trip = tripDao.getTripById(tripId)
            if (trip != null && trip.syncStatus != SyncStatus.SYNCED) {
                runCatching {
                    val cloudTrip = trip.copy(
                        userId = if (trip.userId.isBlank() || trip.userId == "default-user" || trip.userId == "user@spendwise.app") currentUserId else trip.userId,
                        createdAt = trip.createdAt.ifBlank { nowIsoUtc() }
                    )
                    client.from("trips").upsert(cloudTrip)
                    tripDao.insertOrUpdate(cloudTrip.copy(syncStatus = SyncStatus.SYNCED))
                }
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
        date: String = todayIsoDate(),
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
            createdAt = nowIsoUtc(),
            syncStatus = SyncStatus.PENDING_INSERT
        )
        transactionDao.insertOrUpdate(tx)

        // Upload to Supabase if online
        if (supabaseProvider.isConfigured && currentUserId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    ensureParentsExistInCloud(canonicalCatId, tripId, currentUserId)
                    supabaseProvider.getClient().from("transactions").upsert(tx)
                    transactionDao.insertOrUpdate(tx.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Saved locally. Supabase upload queued/error: ${e.message}")
                }
            }
        }
    }

    suspend fun insertTransaction(transaction: TransactionEntity) {
        val currentUserId = getEffectiveUserId()
        val canonicalCatId = resolveCanonicalCategoryId(transaction.categoryId)
        val cleanTx = transaction.copy(
            userId = if (transaction.userId.isBlank() || transaction.userId == "default-user") currentUserId else transaction.userId,
            categoryId = canonicalCatId,
            createdAt = transaction.createdAt.ifBlank { nowIsoUtc() }
        )
        transactionDao.insertOrUpdate(cleanTx)
        if (supabaseProvider.isConfigured && cleanTx.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    ensureParentsExistInCloud(cleanTx.categoryId, cleanTx.tripId, currentUserId)
                    supabaseProvider.getClient().from("transactions").upsert(cleanTx)
                    transactionDao.insertOrUpdate(cleanTx.copy(syncStatus = SyncStatus.SYNCED))
                } catch (e: Exception) {
                    Log.w("FinanceRepository", "Error uploading transaction to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        val currentUserId = getEffectiveUserId()
        val canonicalCatId = resolveCanonicalCategoryId(transaction.categoryId)
        val cleanTx = transaction.copy(
            userId = if (transaction.userId.isBlank() || transaction.userId == "default-user") currentUserId else transaction.userId,
            categoryId = canonicalCatId,
            createdAt = transaction.createdAt.ifBlank { nowIsoUtc() }
        )
        val updated = cleanTx.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        transactionDao.insertOrUpdate(updated)
        if (supabaseProvider.isConfigured && updated.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    ensureParentsExistInCloud(updated.categoryId, updated.tripId, currentUserId)
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
        val currentUserId = getEffectiveUserId()
        val cleanCat = category.copy(
            userId = if (category.isDefault) null else (category.userId?.takeIf { it.isNotBlank() } ?: currentUserId),
            createdAt = category.createdAt.ifBlank { nowIsoUtc() }
        )
        categoryDao.insertOrUpdate(cleanCat)
        if (supabaseProvider.isConfigured && cleanCat.userId != null && cleanCat.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("categories").upsert(cleanCat)
                    categoryDao.insertOrUpdate(cleanCat.copy(syncStatus = SyncStatus.SYNCED))
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
            createdAt = nowIsoUtc(),
            syncStatus = if (existingId != null) SyncStatus.PENDING_UPDATE else SyncStatus.PENDING_INSERT
        )
        budgetDao.insertOrUpdate(budget)

        if (supabaseProvider.isConfigured && currentUserId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    ensureParentsExistInCloud(canonicalCatId, null, currentUserId)
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
        val currentUserId = getEffectiveUserId()
        val cleanTrip = trip.copy(
            userId = if (trip.userId.isBlank() || trip.userId == "default-user" || trip.userId == "user@spendwise.app") currentUserId else trip.userId,
            createdAt = trip.createdAt.ifBlank { nowIsoUtc() }
        )
        tripDao.insertOrUpdate(cleanTrip)
        if (supabaseProvider.isConfigured && cleanTrip.userId != "demo-user") {
            withContext(Dispatchers.IO) {
                try {
                    supabaseProvider.getClient().from("trips").upsert(cleanTrip)
                    tripDao.insertOrUpdate(cleanTrip.copy(syncStatus = SyncStatus.SYNCED))
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
