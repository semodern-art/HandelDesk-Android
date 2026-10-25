package de.handeltheke.handeldesk.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products", indices = [
    Index(value = ["productId"], unique = true),
    Index("name"), Index("model"), Index("sku"), Index("jan"), Index("state"), Index("updatedAt")
])
data class ProductEntity(
    @PrimaryKey val uuid: String,
    val productId: Int? = null,
    val sourceProductId: Int? = null,
    val baseRevision: String = "",
    val baseJson: String,
    val localJson: String,
    val state: String = "SYNCED",
    val isDraft: Boolean = false,
    val workflowState: String = "READY",
    val name: String = "",
    val model: String = "",
    val sku: String = "",
    val jan: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val manufacturerId: Int = 0,
    val status: Int = 0,
    val searchNorm: String = "",
    val localNote: String = "",
    val updatedAt: Long,
)

@Entity(tableName = "categories", indices = [Index(value = ["categoryId"], unique = true), Index("parentId"), Index("name")])
data class CategoryEntity(
    @PrimaryKey val uuid: String,
    val categoryId: Int,
    val baseRevision: String = "",
    val baseJson: String,
    val localJson: String,
    val state: String = "SYNCED",
    val name: String = "",
    val parentId: Int = 0,
    val updatedAt: Long,
)

@Entity(tableName = "manufacturers", indices = [Index(value = ["manufacturerId"], unique = true), Index("name")])
data class ManufacturerEntity(
    @PrimaryKey val uuid: String,
    val manufacturerId: Int,
    val baseRevision: String = "",
    val baseJson: String,
    val localJson: String,
    val state: String = "SYNCED",
    val name: String = "",
    val updatedAt: Long,
)

@Entity(tableName = "changes", indices = [Index(value = ["entityType", "entityUuid", "synced", "included"])])
data class ChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String? = null,
    val entityType: String,
    val entityUuid: String,
    val fieldPath: String,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val operationType: String = "set",
    val synced: Boolean = false,
    val included: Boolean = true,
    val createdAt: Long,
)

@Entity(tableName = "audit_events", indices = [Index("createdAt"), Index(value = ["entityType", "entityUuid"])])
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val sessionId: String? = null,
    val eventType: String,
    val entityType: String? = null,
    val entityUuid: String? = null,
    val entityId: Int? = null,
    val source: String = "android",
    val summary: String = "",
    val detailsJson: String? = null,
)

@Entity(tableName = "work_queue")
data class WorkQueueEntity(
    @PrimaryKey val productUuid: String,
    val position: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val snapshotAt: Long? = null,
    val createdAt: Long,
    val closedAt: Long? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
