package com.spendwise.app.data.local

import androidx.room.*
import com.spendwise.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE syncStatus != 'PENDING_DELETE' ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markForDeletion(id: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deletePermanently(id: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE syncStatus != 'PENDING_DELETE' ORDER BY date DESC, createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE syncStatus != 'PENDING_DELETE' AND tripId = :tripId ORDER BY date DESC")
    fun getTransactionsForTrip(tripId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markForDeletion(id: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deletePermanently(id: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE syncStatus != 'PENDING_DELETE' AND month = :month AND year = :year")
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE syncStatus != 'PENDING_DELETE'")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("UPDATE budgets SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markForDeletion(id: String)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deletePermanently(id: String)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE syncStatus != 'PENDING_DELETE' ORDER BY startDate DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)

    @Query("UPDATE trips SET syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun markForDeletion(id: String)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deletePermanently(id: String)
}
