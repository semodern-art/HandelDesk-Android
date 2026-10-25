package de.handeltheke.handeldesk.data.repo

import androidx.room.withTransaction
import de.handeltheke.handeldesk.data.local.*
import de.handeltheke.handeldesk.data.remote.BridgeClient
import de.handeltheke.handeldesk.domain.JsonTools
import de.handeltheke.handeldesk.domain.ProductJson
import de.handeltheke.handeldesk.domain.Severity
import de.handeltheke.handeldesk.domain.ValidationEngine
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.first

class SyncRepository(
    private val db: HandelDeskDatabase,
    private val bridge: BridgeClient,
) {
    suspend fun buildPayload(): JsonObject {
        val changes = db.changes().includedPending()
        val productGroups = changes.filter { it.entityType == "product" }.groupBy { it.entityUuid }
        val products = productGroups.mapNotNull { (uuid, rows) ->
            val entity = db.products().get(uuid) ?: return@mapNotNull null
            buildSyncEntity(
                uuid = uuid,
                entityIdName = "product_id",
                entityId = entity.productId,
                sourceProductId = entity.sourceProductId,
                baseRevision = entity.baseRevision,
                baseJson = entity.baseJson,
                localJson = entity.localJson,
                rows = rows
            )
        }
        val manufacturerGroups = changes.filter { it.entityType == "manufacturer" }.groupBy { it.entityUuid }
        val manufacturers = manufacturerGroups.mapNotNull { (uuid, rows) ->
            val entity = db.references().manufacturerByUuid(uuid) ?: return@mapNotNull null
            buildReferenceSyncEntity(uuid, entity.manufacturerId, entity.baseRevision, entity.baseJson, entity.localJson, rows)
        }
        val categoryGroups = changes.filter { it.entityType == "category" }.groupBy { it.entityUuid }
        val categories = categoryGroups.mapNotNull { (uuid, rows) ->
            val entity = db.references().categoryByUuid(uuid) ?: return@mapNotNull null
            buildReferenceSyncEntity(uuid, entity.categoryId, entity.baseRevision, entity.baseJson, entity.localJson, rows)
        }
        return buildJsonObject {
            put("products", JsonArray(products))
            put("manufacturers", JsonArray(manufacturers))
            put("categories", JsonArray(categories))
        }
    }

    private fun buildSyncEntity(
        uuid: String,
        entityIdName: String,
        entityId: Int?,
        sourceProductId: Int?,
        baseRevision: String,
        baseJson: String,
        localJson: String,
        rows: List<ChangeEntity>
    ): JsonObject = buildJsonObject {
        put("entity_uuid", uuid)
        if (entityId != null) put(entityIdName, entityId) else put(entityIdName, JsonNull)
        if (sourceProductId != null) put("source_product_id", sourceProductId) else put("source_product_id", JsonNull)
        put("base_revision", baseRevision)
        put("base", JsonTools.objectOf(baseJson))
        put("local", JsonTools.objectOf(localJson))
        put("changes", changesJson(rows))
    }

    private fun buildReferenceSyncEntity(
        uuid: String,
        entityId: Int,
        baseRevision: String,
        baseJson: String,
        localJson: String,
        rows: List<ChangeEntity>
    ): JsonObject = buildJsonObject {
        put("entity_uuid", uuid)
        put("entity_id", entityId)
        if (entityId < 0) put("temp_id", entityId)
        put("base_revision", baseRevision)
        put("base", JsonTools.objectOf(baseJson))
        put("local", JsonTools.objectOf(localJson))
        put("changes", changesJson(rows))
    }

    private fun changesJson(rows: List<ChangeEntity>): JsonArray = buildJsonArray {
        rows.forEach { ch ->
            add(buildJsonObject {
                put("id", ch.id)
                put("field_path", ch.fieldPath)
                put("operation_type", ch.operationType)
                put("before", parseNullable(ch.beforeJson))
                put("after", parseNullable(ch.afterJson))
            })
        }
    }

    suspend fun localValidationErrors(): List<Pair<ProductEntity, List<String>>> {
        val changed = db.products().observeChanged().first()
        val language = db.settings().get("primary_language_id")
        return changed.mapNotNull { entity ->
            val errors = ValidationEngine.validate(entity, language).filter { it.severity == Severity.ERROR }.map { it.message }
            if (errors.isEmpty()) null else entity to errors
        }
    }

    suspend fun preview(): JsonObject {
        val errors = localValidationErrors()
        if (errors.isNotEmpty()) error("Validation блокирует Sync: ${errors.first().first.name}: ${errors.first().second.first()}")
        return bridge.post("sync_preview", buildPayload())
    }

    suspend fun apply(): JsonObject {
        val preview = preview()
        if (preview["apply_allowed"]?.jsonPrimitive?.booleanOrNull != true) error("Bridge не разрешил Apply")
        val payload = buildPayload() // rebuild immediately before apply
        val result = bridge.post("sync_apply", payload)
        db.withTransaction {
            applyIdMap(result["id_map"]?.jsonObject)
            result["results"]?.jsonArray.orEmpty().forEach { itemElement ->
                val item = itemElement.jsonObject
                if (item["ok"]?.jsonPrimitive?.booleanOrNull != true) return@forEach
                val uuid = item["entity_uuid"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val serverWithRevision = item["product"]?.jsonObject ?: return@forEach
                val revision = serverWithRevision["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val payloadServer = JsonObject(serverWithRevision.toMutableMap().apply { remove("revision") })
                val current = db.products().get(uuid) ?: return@forEach
                val index = ProductJson.index(payloadServer, db.settings().get("primary_language_id"))
                val productId = payloadServer["product"]?.jsonObject?.get("product_id")?.jsonPrimitive?.intOrNull ?: current.productId
                db.products().put(current.copy(
                    productId = productId, baseRevision = revision, baseJson = payloadServer.toString(), localJson = payloadServer.toString(),
                    state = "SYNCED", isDraft = false, workflowState = "READY", name = index.name, model = index.model,
                    sku = index.sku, jan = index.jan, price = index.price, quantity = index.quantity,
                    manufacturerId = index.manufacturerId, status = index.status, searchNorm = index.searchNorm,
                    updatedAt = System.currentTimeMillis()
                ))
                db.changes().markEntitySynced("product", uuid)
            }
            result["reference_results"]?.jsonArray.orEmpty().forEach { refElement ->
                val ref = refElement.jsonObject
                if (ref["ok"]?.jsonPrimitive?.booleanOrNull != true) return@forEach
                val type = ref["entity_type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val uuid = ref["entity_uuid"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val serverWithRevision = ref["entity"]?.jsonObject ?: return@forEach
                val revision = serverWithRevision["revision"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val serverPayload = JsonObject(serverWithRevision.toMutableMap().apply { remove("revision") })
                when (type) {
                    "manufacturer" -> {
                        val current = db.references().manufacturerByUuid(uuid) ?: return@forEach
                        val id = ref["entity_id"]?.jsonPrimitive?.intOrNull ?: current.manufacturerId
                        val name = serverPayload["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        db.references().putManufacturers(listOf(current.copy(manufacturerId = id, baseRevision = revision, baseJson = serverPayload.toString(), localJson = serverPayload.toString(), state = "SYNCED", name = name, updatedAt = System.currentTimeMillis())))
                        db.changes().markEntitySynced("manufacturer", uuid)
                    }
                    "category" -> {
                        val current = db.references().categoryByUuid(uuid) ?: return@forEach
                        val id = ref["entity_id"]?.jsonPrimitive?.intOrNull ?: current.categoryId
                        val name = serverPayload["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val parent = serverPayload["parent_id"]?.jsonPrimitive?.intOrNull ?: 0
                        db.references().putCategories(listOf(current.copy(categoryId = id, baseRevision = revision, baseJson = serverPayload.toString(), localJson = serverPayload.toString(), state = "SYNCED", name = name, parentId = parent, updatedAt = System.currentTimeMillis())))
                        db.changes().markEntitySynced("category", uuid)
                    }
                }
            }
            db.audit().insert(AuditEventEntity(
                createdAt = System.currentTimeMillis(), eventType = "sync_result", source = "android",
                summary = "Sync завершён: успешно ${result["success_count"]?.jsonPrimitive?.intOrNull ?: 0}, ошибок ${result["error_count"]?.jsonPrimitive?.intOrNull ?: 0}",
                detailsJson = result.toString()
            ))
        }
        return result
    }

    private suspend fun applyIdMap(idMap: JsonObject?) {
        if (idMap == null) return
        val manufacturers = idMap["manufacturers"]?.jsonObject.orEmpty().mapNotNull { (k, v) ->
            val old = k.toIntOrNull(); val new = v.jsonPrimitive.intOrNull
            if (old != null && new != null) old to new else null
        }.toMap()
        val categories = idMap["categories"]?.jsonObject.orEmpty().mapNotNull { (k, v) ->
            val old = k.toIntOrNull(); val new = v.jsonPrimitive.intOrNull
            if (old != null && new != null) old to new else null
        }.toMap()
        if (manufacturers.isEmpty() && categories.isEmpty()) return
        val now = System.currentTimeMillis()

        manufacturers.forEach { (old, new) ->
            val row = db.references().manufacturerById(old) ?: return@forEach
            val local = JsonTools.objectOf(row.localJson).toMutableMap().apply { put("manufacturer_id", JsonPrimitive(new)) }
            db.references().putManufacturers(listOf(row.copy(manufacturerId = new, localJson = JsonObject(local).toString(), updatedAt = now)))
        }
        categories.forEach { (old, new) ->
            val row = db.references().categoryById(old) ?: return@forEach
            val local = JsonTools.objectOf(row.localJson)
            val oldParent = local["parent_id"]?.jsonPrimitive?.intOrNull ?: row.parentId
            val mappedParent = categories[oldParent] ?: oldParent
            val mapped = local.toMutableMap().apply {
                put("category_id", JsonPrimitive(new)); put("parent_id", JsonPrimitive(mappedParent))
            }
            db.references().putCategories(listOf(row.copy(categoryId = new, parentId = mappedParent, localJson = JsonObject(mapped).toString(), updatedAt = now)))
        }
        // Categories that were not created in this sync can still point at a newly-created parent.
        db.references().categories().forEach { row ->
            val local = JsonTools.objectOf(row.localJson)
            val parent = local["parent_id"]?.jsonPrimitive?.intOrNull ?: row.parentId
            val mapped = categories[parent] ?: return@forEach
            val updated = JsonObject(local.toMutableMap().apply { put("parent_id", JsonPrimitive(mapped)) })
            db.references().putCategories(listOf(row.copy(parentId = mapped, localJson = updated.toString(), updatedAt = now)))
        }
        // Rewrite only known relationship fields. Never globally replace arbitrary negative integers.
        db.products().all().forEach { row ->
            var local = JsonTools.objectOf(row.localJson)
            var changed = false
            val mid = JsonTools.path(local, "product.manufacturer_id")?.jsonPrimitive?.intOrNull ?: 0
            manufacturers[mid]?.let { mapped -> local = JsonTools.setPath(local, "product.manufacturer_id", JsonPrimitive(mapped)); changed = true }
            val cats = (local["categories"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }
            val mappedCats = cats.map { categories[it] ?: it }
            if (mappedCats != cats) { local = JsonObject(local.toMutableMap().apply { put("categories", JsonArray(mappedCats.map(::JsonPrimitive))) }); changed = true }
            if (changed) {
                val idx = ProductJson.index(local, db.settings().get("primary_language_id"))
                db.products().put(row.copy(localJson = local.toString(), manufacturerId = idx.manufacturerId, searchNorm = idx.searchNorm, updatedAt = now))
            }
        }
        db.changes().pending().forEach { change ->
            fun remapValue(text: String?, path: String): String? {
                if (text == null) return null
                val value = parseNullable(text)
                val next: JsonElement = when (path) {
                    "product.manufacturer_id" -> value.jsonPrimitive.intOrNull?.let { JsonPrimitive(manufacturers[it] ?: it) } ?: value
                    "parent_id" -> value.jsonPrimitive.intOrNull?.let { JsonPrimitive(categories[it] ?: it) } ?: value
                    "categories" -> if (value is JsonArray) JsonArray(value.map { e -> e.jsonPrimitive.intOrNull?.let { JsonPrimitive(categories[it] ?: it) } ?: e }) else value
                    else -> value
                }
                return next.toString()
            }
            if (change.fieldPath in setOf("product.manufacturer_id", "parent_id", "categories")) {
                val before = remapValue(change.beforeJson, change.fieldPath)
                val after = remapValue(change.afterJson, change.fieldPath)
                if (before != change.beforeJson || after != change.afterJson) db.changes().update(change.copy(beforeJson = before, afterJson = after))
            }
        }
    }

    private fun parseNullable(text: String?): JsonElement {
        if (text == null) return JsonNull
        return runCatching { JsonTools.json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
    }
}

