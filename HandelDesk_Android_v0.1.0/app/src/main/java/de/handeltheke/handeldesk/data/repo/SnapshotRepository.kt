package de.handeltheke.handeldesk.data.repo

import androidx.room.withTransaction
import de.handeltheke.handeldesk.data.local.*
import de.handeltheke.handeldesk.data.remote.BridgeClient
import de.handeltheke.handeldesk.domain.JsonTools
import de.handeltheke.handeldesk.domain.ProductJson
import kotlinx.serialization.json.*

class SnapshotRepository(
    private val db: HandelDeskDatabase,
    private val bridge: BridgeClient,
) {
    data class Progress(val current: Int, val total: Int, val message: String)

    suspend fun refresh(onProgress: suspend (Progress) -> Unit = {}) {
        onProgress(Progress(0, 0, "Проверка Bridge"))
        val schemaRoot = bridge.get("schema")
        val schema = schemaRoot["schema"]?.jsonObject ?: error("Bridge schema отсутствует")
        val protocol = schema["protocol"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(protocol == "0.4.1") { "Неподдерживаемый Bridge protocol: $protocol" }

        val reference = bridge.get("reference")
        val categories = reference["categories"]?.jsonArray.orEmpty().mapNotNull { categoryEntity(it.jsonObject) }
        val manufacturers = reference["manufacturers"]?.jsonArray.orEmpty().mapNotNull { manufacturerEntity(it.jsonObject) }

        db.withTransaction {
            db.settings().put(SettingEntity("bridge_schema", schema.toString()))
            schema["languages"]?.jsonArray?.firstOrNull()?.jsonObject?.get("language_id")?.jsonPrimitive?.contentOrNull?.let {
                db.settings().put(SettingEntity("primary_language_id", it))
            }
            val safeCategories = categories.filter { incoming ->
                val current = db.references().categoryById(incoming.categoryId)
                current == null || current.state == "SYNCED"
            }
            val safeManufacturers = manufacturers.filter { incoming ->
                val current = db.references().manufacturerById(incoming.manufacturerId)
                current == null || current.state == "SYNCED"
            }
            db.references().putCategories(safeCategories)
            db.references().putManufacturers(safeManufacturers)
        }

        var page = 1
        var received = 0
        var total = 0
        do {
            val root = bridge.get("snapshot", mapOf("page" to page.toString(), "per_page" to "100"))
            total = root["total"]?.jsonPrimitive?.intOrNull ?: total
            val products = root["products"]?.jsonArray.orEmpty().mapNotNull { productEntity(it.jsonObject) }
            val safe = mutableListOf<ProductEntity>()
            products.forEach { incoming ->
                val current = incoming.productId?.let { db.products().getByProductId(it) }
                if (current == null || current.state == "SYNCED") safe += incoming
            }
            db.products().putAll(safe)
            received += products.size
            onProgress(Progress(received, total, "Товары $received / $total"))
            val more = root["has_more"]?.jsonPrimitive?.booleanOrNull == true
            page++
        } while (more)

        val now = System.currentTimeMillis()
        db.settings().put(SettingEntity("snapshot_at", now.toString()))
        ensureSession(snapshotAt = now)
        db.audit().insert(AuditEventEntity(
            createdAt = now,
            eventType = "snapshot",
            source = "android",
            summary = "Snapshot обновлён: товаров $received, категорий ${categories.size}, производителей ${manufacturers.size}",
        ))
    }

    suspend fun ensureSession(snapshotAt: Long? = null): String {
        val existing = db.settings().get("active_session_id")
        if (!existing.isNullOrBlank()) return existing
        val id = java.util.UUID.randomUUID().toString()
        db.sessions().put(SessionEntity(id, "Рабочая сессия", snapshotAt, System.currentTimeMillis()))
        db.settings().put(SettingEntity("active_session_id", id))
        return id
    }

    private suspend fun primaryLanguageId(): String? = db.settings().get("primary_language_id")

    private suspend fun productEntity(source: JsonObject): ProductEntity? {
        val product = source["product"]?.jsonObject ?: return null
        val id = product["product_id"]?.jsonPrimitive?.intOrNull ?: return null
        val revision = source["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val payload = JsonObject(source.toMutableMap().apply { remove("revision") })
        val index = ProductJson.index(payload, primaryLanguageId())
        val now = System.currentTimeMillis()
        return ProductEntity(
            uuid = "server-product-$id", productId = id, baseRevision = revision,
            baseJson = payload.toString(), localJson = payload.toString(), state = "SYNCED", isDraft = false,
            name = index.name, model = index.model, sku = index.sku, jan = index.jan, price = index.price,
            quantity = index.quantity, manufacturerId = index.manufacturerId, status = index.status,
            searchNorm = index.searchNorm, updatedAt = now
        )
    }

    private fun categoryEntity(source: JsonObject): CategoryEntity? {
        val id = source["category_id"]?.jsonPrimitive?.intOrNull ?: return null
        val revision = source["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val payload = JsonObject(source.toMutableMap().apply { remove("revision") })
        return CategoryEntity(
            uuid = "server-category-$id", categoryId = id, baseRevision = revision,
            baseJson = payload.toString(), localJson = payload.toString(), state = "SYNCED",
            name = source["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            parentId = source["parent_id"]?.jsonPrimitive?.intOrNull ?: 0,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun manufacturerEntity(source: JsonObject): ManufacturerEntity? {
        val id = source["manufacturer_id"]?.jsonPrimitive?.intOrNull ?: return null
        val revision = source["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val payload = JsonObject(source.toMutableMap().apply { remove("revision") })
        return ManufacturerEntity(
            uuid = "server-manufacturer-$id", manufacturerId = id, baseRevision = revision,
            baseJson = payload.toString(), localJson = payload.toString(), state = "SYNCED",
            name = source["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), updatedAt = System.currentTimeMillis()
        )
    }
}
