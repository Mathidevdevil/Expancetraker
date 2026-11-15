package com.spendwise.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

enum class SyncStatus {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE
}

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String? = null,
    val name: String,
    val type: String, // "expense" | "income"
    val icon: String = "Tag",
    val color: String = "#6366F1",
    val isDefault: Boolean = false,
    val createdAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val destination: String,
    val startDate: String, // YYYY-MM-DD
    val endDate: String,   // YYYY-MM-DD
    val budget: Double,
    val notes: String? = null,
    val createdAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val categoryId: String? = null,
    val tripId: String? = null,
    val type: String, // "expense" | "income"
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val note: String? = null,
    val paymentMethod: String = "upi", // cash, upi, card, bank_transfer, net_banking
    val createdAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val categoryId: String,
    val month: Int, // 1-12
    val year: Int,
    val amount: Double,
    val createdAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

data class TransactionWithDetails(
    val transaction: TransactionEntity,
    val category: CategoryEntity?,
    val trip: TripEntity?
)
