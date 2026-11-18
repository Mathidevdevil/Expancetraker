package com.spendwise.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.model.*
import com.spendwise.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<TransactionWithDetails> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val searchQuery: String = "",
    val filterType: String = "all", // "all", "expense", "income"
    val filterCategoryId: String? = null,
    val currencySymbol: String = "₹"
)

data class FilterParams(
    val query: String,
    val type: String,
    val categoryId: String?
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filterType = MutableStateFlow("all")
    private val _filterCategoryId = MutableStateFlow<String?>(null)

    private val filterParams = combine(_searchQuery, _filterType, _filterCategoryId) { query, type, catId ->
        FilterParams(query, type, catId)
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.transactionsWithDetails,
        repository.allCategories,
        repository.allTrips,
        filterParams
    ) { txs, cats, trips, filter ->
        val filtered = txs.filter { item ->
            val t = item.transaction
            val matchesType = filter.type == "all" || t.type == filter.type
            val matchesCat = filter.categoryId == null || t.categoryId == filter.categoryId
            val matchesQuery = filter.query.isBlank() ||
                (t.note?.contains(filter.query, ignoreCase = true) == true) ||
                (item.category?.name?.contains(filter.query, ignoreCase = true) == true) ||
                (item.trip?.destination?.contains(filter.query, ignoreCase = true) == true) ||
                (t.amount.toString().contains(filter.query))

            matchesType && matchesCat && matchesQuery
        }

        TransactionsUiState(
            transactions = filtered,
            categories = cats,
            trips = trips,
            searchQuery = filter.query,
            filterType = filter.type,
            filterCategoryId = filter.categoryId,
            currencySymbol = repository.selectedCurrencySymbol
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    val allTransactions: StateFlow<List<TransactionWithDetails>> = repository.transactionsWithDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterTypeChanged(type: String) {
        _filterType.value = type
    }

    fun onFilterCategoryChanged(categoryId: String?) {
        _filterCategoryId.value = categoryId
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }
}
