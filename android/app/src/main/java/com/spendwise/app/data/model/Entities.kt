package com.spendwise.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val type: String, // "expense" | "income"
    val icon: String = "Tag",
    val color: String = "#6366F1",
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @Transient val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String,
    val destination: String,
    @SerialName("start_date") val startDate: String, // YYYY-MM-DD
    @SerialName("end_date") val endDate: String,   // YYYY-MM-DD
    val budget: Double = 0.0,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @Transient val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("trip_id") val tripId: String? = null,
    val type: String, // "expense" | "income"
    val amount: Double,
    val date: String, // YYYY-MM-DD
    val note: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "upi", // cash, upi, card, bank_transfer, net_banking
    @SerialName("created_at") val createdAt: String = "",
    @Transient val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: String,
    val month: Int, // 1-12
    val year: Int,
    val amount: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
    @Transient val syncStatus: SyncStatus = SyncStatus.SYNCED
)

data class TransactionWithDetails(
    val transaction: TransactionEntity,
    val category: CategoryEntity?,
    val trip: TripEntity?
)
