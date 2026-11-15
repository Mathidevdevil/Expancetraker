package com.spendwise.app.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendwise.app.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = runCatching {
        SyncStatus.valueOf(value)
    }.getOrDefault(SyncStatus.SYNCED)
}

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        TripEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spendwise_db"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDefaultCategories(database.categoryDao())
                    populateInitialData(database.transactionDao(), database.budgetDao(), database.tripDao())
                }
            }
        }

        suspend fun populateDefaultCategories(dao: CategoryDao) {
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
            dao.insertAll(defaults)
        }

        suspend fun populateInitialData(txDao: TransactionDao, budgetDao: BudgetDao, tripDao: TripDao) {
            val trip = TripEntity(
                id = "trip-goa-2026",
                userId = "demo-user",
                destination = "Goa Beach Vacation",
                startDate = "2026-11-20",
                endDate = "2026-11-25",
                budget = 35000.0,
                notes = "Resort stay, water sports, beach dining"
            )
            tripDao.insertOrUpdate(trip)

            val budgets = listOf(
                BudgetEntity(id = "budget-1", userId = "demo-user", categoryId = "cat-food", month = 11, year = 2026, amount = 12000.0),
                BudgetEntity(id = "budget-2", userId = "demo-user", categoryId = "cat-groc", month = 11, year = 2026, amount = 8000.0),
                BudgetEntity(id = "budget-3", userId = "demo-user", categoryId = "cat-shop", month = 11, year = 2026, amount = 6000.0),
                BudgetEntity(id = "budget-4", userId = "demo-user", categoryId = "cat-bills", month = 11, year = 2026, amount = 5000.0)
            )
            budgetDao.insertAll(budgets)

            val txs = listOf(
                TransactionEntity(id = "tx-1", userId = "demo-user", categoryId = "cat-sal", type = "income", amount = 95000.0, date = "2026-11-01", note = "Tech Lead Salary", paymentMethod = "bank_transfer"),
                TransactionEntity(id = "tx-2", userId = "demo-user", categoryId = "cat-food", type = "expense", amount = 1450.0, date = "2026-11-03", note = "Royal Bistro Dinner", paymentMethod = "upi"),
                TransactionEntity(id = "tx-3", userId = "demo-user", categoryId = "cat-groc", type = "expense", amount = 3200.0, date = "2026-11-05", note = "Organic Groceries", paymentMethod = "card"),
                TransactionEntity(id = "tx-4", userId = "demo-user", categoryId = "cat-bills", type = "expense", amount = 2499.0, date = "2026-11-07", note = "Fiber Internet & Utilities", paymentMethod = "net_banking"),
                TransactionEntity(id = "tx-5", userId = "demo-user", categoryId = "cat-free", type = "income", amount = 22000.0, date = "2026-11-08", note = "Android Consulting Sprint 1", paymentMethod = "bank_transfer"),
                TransactionEntity(id = "tx-6", userId = "demo-user", categoryId = "cat-shop", type = "expense", amount = 4900.0, date = "2026-11-10", note = "Wireless Headphones", paymentMethod = "upi"),
                TransactionEntity(id = "tx-7", userId = "demo-user", categoryId = "cat-food", tripId = "trip-goa-2026", type = "expense", amount = 6800.0, date = "2026-11-12", note = "Goa Flight Tickets", paymentMethod = "card")
            )
            txDao.insertAll(txs)
        }
    }
}
