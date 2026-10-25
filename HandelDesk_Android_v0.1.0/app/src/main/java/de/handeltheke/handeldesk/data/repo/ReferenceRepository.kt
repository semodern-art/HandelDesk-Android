package de.handeltheke.handeldesk.data.repo

import androidx.room.withTransaction
import de.handeltheke.handeldesk.core.HandelthekeSeoPolicy
import de.handeltheke.handeldesk.data.local.*
import kotlinx.serialization.json.*
import java.util.UUID

class ReferenceRepository(private val db: HandelDeskDatabase) {
    suspend fun createManufacturer(name: String): ManufacturerEntity = db.withTransaction {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Название производителя обязательно" }
        val tempId = minOf(-1, db.references().minManufacturerId() - 1)
        val uuid = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("manufacturer_id", tempId)
            put("name", clean)
            put("image", "")
            put("sort_order", 0)
            putJsonArray("stores") { add(0) }
            put("seo_urls", buildJsonArray {})
        }
        val entity = ManufacturerEntity(uuid, tempId, "", "{}", payload.toString(), "NEW", clean, System.currentTimeMillis())
        db.references().putManufacturers(listOf(entity))
        db.changes().insert(ChangeEntity(
            sessionId = db.settings().get("active_session_id"), entityType = "manufacturer", entityUuid = uuid,
            fieldPath = "__create__", beforeJson = null, afterJson = payload.toString(), operationType = "create", createdAt = System.currentTimeMillis()
        ))
        db.audit().insert(AuditEventEntity(createdAt = System.currentTimeMillis(), sessionId = db.settings().get("active_session_id"), eventType = "manufacturer_create", entityType = "manufacturer", entityUuid = uuid, entityId = tempId, summary = "Создан производитель $clean"))
        entity
    }

    suspend fun createCategory(name: String, parentId: Int = 0, primaryLanguageId: String = "1"): CategoryEntity = db.withTransaction {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Название категории обязательно" }
        val tempId = minOf(-1, db.references().minCategoryId() - 1)
        val uuid = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("category_id", tempId); put("parent_id", parentId); put("image", ""); put("top", 0); put("column", 1); put("sort_order", 0); put("status", 1); put("name", clean)
            putJsonObject("descriptions") {
                putJsonObject(primaryLanguageId) {
                    put("name", clean); put("description", ""); put("meta_title", clean); put("meta_description", ""); put("meta_keyword", "")
                }
            }
            putJsonArray("stores") { add(0) }
            putJsonArray("seo_urls") {
                add(buildJsonObject { put("store_id", 0); put("language_id", primaryLanguageId.toIntOrNull() ?: 1); put("keyword", HandelthekeSeoPolicy.slug(clean)) })
            }
        }
        val entity = CategoryEntity(uuid, tempId, "", "{}", payload.toString(), "NEW", clean, parentId, System.currentTimeMillis())
        db.references().putCategories(listOf(entity))
        db.changes().insert(ChangeEntity(
            sessionId = db.settings().get("active_session_id"), entityType = "category", entityUuid = uuid,
            fieldPath = "__create__", beforeJson = null, afterJson = payload.toString(), operationType = "create", createdAt = System.currentTimeMillis()
        ))
        db.audit().insert(AuditEventEntity(createdAt = System.currentTimeMillis(), sessionId = db.settings().get("active_session_id"), eventType = "category_create", entityType = "category", entityUuid = uuid, entityId = tempId, summary = "Создана категория $clean"))
        entity
    }
}
