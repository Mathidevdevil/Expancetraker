package com.spendwise.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.model.*
import com.spendwise.app.data.repository.FinanceRepository
import com.spendwise.app.data.repository.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class CategorySpend(
    val category: CategoryEntity?,
    val amount: Double,
    val percentage: Float
)

data class BudgetAlert(
    val categoryName: String,
    val percent: Float,
    val spent: Double,
    val budget: Double,
    val isExceeded: Boolean
)

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val currencySymbol: String = "₹",
    val recentTransactions: List<TransactionWithDetails> = emptyList(),
    val topCategories: List<CategorySpend> = emptyList(),
    val activeTrips: List<TripEntity> = emptyList(),
    val budgetAlerts: List<BudgetAlert> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val userProfile: UserProfile = UserProfile()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Automatically sync latest cloud data when opening the dashboard
        viewModelScope.launch {
            repository.syncFromCloud()
        }

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        viewModelScope.launch {
            combine(
                repository.transactionsWithDetails,
                repository.allCategories,
                repository.allTrips,
                repository.getBudgetsForMonth(currentMonth, currentYear),
                repository.userProfile
            ) { txs, cats, trips, budgets, profile ->
                var balance = 0.0
                var income = 0.0
                var expense = 0.0
                val catSpendMap = mutableMapOf<String, Double>()

                for (item in txs) {
                    val t = item.transaction
                    if (t.type == "income") {
                        balance += t.amount
                    } else {
                        balance -= t.amount
                    }

                    // Check if current month
                    // Expected format: YYYY-MM-DD
                    val parts = t.date.split("-")
                    if (parts.size == 3) {
                        val m = parts[1].toIntOrNull()
                        val y = parts[0].toIntOrNull()
                        if (m == currentMonth && y == currentYear) {
                            if (t.type == "income") {
                                income += t.amount
                            } else {
                                expense += t.amount
                                t.categoryId?.let { cId ->
                                    catSpendMap[cId] = (catSpendMap[cId] ?: 0.0) + t.amount
                                }
                            }
                        }
                    }
                }

                val catMap = cats.associateBy { it.id }
                val topCats = catSpendMap.map { (catId, amt) ->
                    CategorySpend(
                        category = catMap[catId],
                        amount = amt,
                        percentage = if (expense > 0) ((amt / expense) * 100).toFloat() else 0f
                    )
                }.sortedByDescending { it.amount }.take(5)

                // Budget alerts at 80% and 100%
                val alerts = mutableListOf<BudgetAlert>()
                for (b in budgets) {
                    val spent = catSpendMap[b.categoryId] ?: 0.0
                    val pct = if (b.amount > 0) ((spent / b.amount) * 100).toFloat() else 0f
                    if (pct >= 80f) {
                        alerts.add(
                            BudgetAlert(
                                categoryName = catMap[b.categoryId]?.name ?: "Category",
                                percent = pct,
                                spent = spent,
                                budget = b.amount,
                                isExceeded = pct >= 100f
                            )
                        )
                    }
                }

                DashboardUiState(
                    totalBalance = balance,
                    monthlyIncome = income,
                    monthlyExpense = expense,
                    currencySymbol = repository.selectedCurrencySymbol,
                    recentTransactions = txs.take(6),
                    topCategories = topCats,
                    activeTrips = trips.take(2),
                    budgetAlerts = alerts,
                    categories = cats,
                    userProfile = profile
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    val themeMode: StateFlow<String> = repository.themeMode

    val isLoggedIn: Boolean
        get() = repository.isLoggedIn

    fun logout() {
        repository.logout()
    }

    fun setThemeMode(mode: String) {
        repository.setThemeMode(mode)
    }

    fun updateProfile(name: String, email: String, initials: String? = null) {
        repository.updateProfile(name, email, initials)
    }

    fun quickAddExpense(
        amount: Double,
        categoryId: String?,
        tripId: String?,
        paymentMethod: String,
        note: String?,
        type: String = "expense"
    ) {
        viewModelScope.launch {
            repository.quickAddExpense(amount, categoryId, tripId, paymentMethod, note, type = type)
        }
    }
}
