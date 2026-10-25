package de.handeltheke.handeldesk.data.repo

import androidx.room.withTransaction
import de.handeltheke.handeldesk.core.DonorTools
import de.handeltheke.handeldesk.core.HandelthekeSeoPolicy
import de.handeltheke.handeldesk.data.local.*
import de.handeltheke.handeldesk.domain.JsonTools
import de.handeltheke.handeldesk.domain.ProductJson
import kotlinx.serialization.json.*
import java.util.UUID

class ProductEditorRepository(private val db: HandelDeskDatabase) {
    suspend fun updateField(uuid: String, path: String, value: JsonElement): Boolean = db.withTransaction {
        val entity = db.products().get(uuid) ?: error("Товар не найден")
        val local = JsonTools.objectOf(entity.localJson)
        val before = JsonTools.path(local, path) ?: JsonNull
        if (before == value) return@withTransaction false
        var updated = JsonTools.setPath(local, path, value)
        if (entity.productId == null && path.startsWith("descriptions.") && path.endsWith(".name")) {
            val parts = path.split('.')
            val languageId = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val rows = (updated["seo_urls"] as? JsonArray).orEmpty().toMutableList()
            val existingIndex = rows.indexOfFirst { it.jsonObject["language_id"]?.jsonPrimitive?.intOrNull == languageId && (it.jsonObject["store_id"]?.jsonPrimitive?.intOrNull ?: 0) == 0 }
            val existingKeyword = existingIndex.takeIf { it >= 0 }?.let { rows[it].jsonObject["keyword"]?.jsonPrimitive?.contentOrNull }.orEmpty()
            if (existingKeyword.isBlank()) {
                val keyword = HandelthekeSeoPolicy.slug(value.jsonPrimitive.contentOrNull.orEmpty())
                val row = buildJsonObject { put("store_id", 0); put("language_id", languageId); put("keyword", keyword) }
                if (existingIndex >= 0) rows[existingIndex] = row else rows += row
                updated = JsonObject(updated.toMutableMap().apply { put("seo_urls", JsonArray(rows)) })
            }
        }
        recordSetChange(entity, path, before, value)
        savePayload(entity, updated)
        audit("product_field", entity, "$path изменено", buildJsonObject { put("path", path); put("before", before); put("after", value) })
        true
    }

    suspend fun adjustQuantity(uuid: String, delta: Int) = db.withTransaction {
        if (delta == 0) return@withTransaction
        val entity = db.products().get(uuid) ?: error("Товар не найден")
        val local = JsonTools.objectOf(entity.localJson)
        val before = JsonTools.path(local, "product.quantity")?.jsonPrimitive?.intOrNull ?: 0
        val after = before + delta
        val updated = JsonTools.setPath(local, "product.quantity", JsonPrimitive(after))
        val session = activeSessionId()
        db.changes().insert(ChangeEntity(
            sessionId = session, entityType = "product", entityUuid = uuid, fieldPath = "product.quantity",
            beforeJson = "0", afterJson = delta.toString(), operationType = "adjust", createdAt = System.currentTimeMillis()
        ))
        savePayload(entity, updated)
        audit("quantity_adjust", entity, "Остаток ${if (delta > 0) "+" else ""}$delta", buildJsonObject { put("before", before); put("after", after); put("delta", delta) })
    }

    suspend fun createBlank(primaryLanguageId: String = "1"): String = db.withTransaction {
        val uuid = UUID.randomUUID().toString()
        var payload = ProductJson.blank()
        payload = JsonTools.setPath(payload, "descriptions.$primaryLanguageId", buildJsonObject {
            put("name", ""); put("description", ""); put("tag", ""); put("meta_title", ""); put("meta_description", ""); put("meta_keyword", "")
        })
        insertNew(uuid, payload, null)
        uuid
    }

    suspend fun createFromDonor(donorUuid: String, sections: Set<String>, primaryLanguageId: String = "1"): String = db.withTransaction {
        val donor = db.products().get(donorUuid) ?: error("Донор не найден")
        val source = JsonTools.objectOf(donor.localJson)
        var target = ProductJson.blank()
        fun copySection(key: String) { source[key]?.let { target = JsonObject(target.toMutableMap().apply { put(key, it) }) } }
        if ("descriptions" in sections) copySection("descriptions")
        if ("categories" in sections) copySection("categories")
        if ("stores" in sections) copySection("stores")
        if ("related" in sections) copySection("related")
        if ("specials" in sections) copySection("specials")
        if ("discounts" in sections) copySection("discounts")
        if ("images" in sections) copySection("images")
        if ("attributes" in sections) copySection("attributes")
        if ("options" in sections) copySection("options")
        if ("seo_urls" in sections) copySection("seo_urls")
        if ("extension_fields" in sections) copySection("extension_fields")
        if ("manufacturer" in sections) {
            val id = JsonTools.path(source, "product.manufacturer_id") ?: JsonPrimitive(0)
            target = JsonTools.setPath(target, "product.manufacturer_id", id)
        }
        if ("price" in sections) {
            target = JsonTools.setPath(target, "product.price", JsonTools.path(source, "product.price") ?: JsonPrimitive("0.0000"))
        }
        if ("core_settings" in sections) {
            listOf("location","stock_status_id","shipping","points","tax_class_id","date_available","weight","weight_class_id","length","width","height","length_class_id","subtract","minimum","sort_order").forEach { f ->
                JsonTools.path(source, "product.$f")?.let { target = JsonTools.setPath(target, "product.$f", it) }
            }
        }
        // Unique identifiers are always cleared. SEO rows deliberately keep the donor keyword
        // until Quick Replace so the store's exact URL structure is preserved as a template.
        listOf("model","sku","upc","ean","jan","isbn","mpn").forEach { target = JsonTools.setPath(target, "product.$it", JsonPrimitive("")) }
        target = JsonTools.setPath(target, "product.quantity", JsonPrimitive(0))
        if ((target["descriptions"] as? JsonObject).isNullOrEmpty()) {
            target = JsonTools.setPath(target, "descriptions.$primaryLanguageId", buildJsonObject { put("name", ""); put("description", ""); put("tag", ""); put("meta_title", ""); put("meta_description", ""); put("meta_keyword", "") })
        }
        val uuid = UUID.randomUUID().toString()
        insertNew(uuid, target, donor.productId)
        audit("donor_clone", db.products().get(uuid)!!, "Создан из донора #${donor.productId}", buildJsonObject { put("donor_uuid", donorUuid); put("sections", JsonArray(sections.map(::JsonPrimitive))) })
        uuid
    }

    suspend fun donorQuickReplace(uuid: String, oldValue: String, newValue: String): Int = db.withTransaction {
        val entity = db.products().get(uuid) ?: error("Товар не найден")
        var payload = JsonTools.objectOf(entity.localJson)
        var changed = 0
        val pairs = DonorTools.tokenPairs(oldValue, newValue)
        val paths = mutableListOf<String>()
        paths += "product.model"
        val descriptions = payload["descriptions"]?.jsonObject.orEmpty()
        descriptions.keys.forEach { lang ->
            listOf("name","description","tag","meta_title","meta_description","meta_keyword").forEach { paths += "descriptions.$lang.$it" }
        }
        paths.forEach { path ->
            val original = JsonTools.path(payload, path)?.jsonPrimitive?.contentOrNull ?: return@forEach
            var result = original
            pairs.forEach { (a, b) -> result = DonorTools.replaceText(result, a, b).after }
            if (result != original) {
                payload = JsonTools.setPath(payload, path, JsonPrimitive(result))
                recordSetChange(entity.copy(localJson = payload.toString()), path, JsonPrimitive(original), JsonPrimitive(result))
                changed++
            }
        }
        // SEO: replace donor model if present; if empty, derive from translated product name using the strict store policy.
        var seo = payload["seo_urls"] as? JsonArray ?: JsonArray(emptyList())
        if (seo.isEmpty()) {
            val rows = payload["descriptions"]?.jsonObject.orEmpty().keys.mapNotNull { languageId ->
                val name = JsonTools.path(payload, "descriptions.$languageId.name")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (name.isBlank()) null else buildJsonObject { put("store_id", 0); put("language_id", languageId.toIntOrNull() ?: 1); put("keyword", HandelthekeSeoPolicy.slug(name)) }
            }
            seo = JsonArray(rows)
        }
        val newSeo = seo.mapIndexed { index, rowElement ->
            val row = rowElement.jsonObject
            val oldKeyword = row["keyword"]?.jsonPrimitive?.contentOrNull.orEmpty()
            var keyword = oldKeyword
            pairs.forEach { (a, b) -> keyword = DonorTools.replaceText(keyword, a, b).after }
            if (keyword.isBlank()) {
                val languageId = row["language_id"]?.jsonPrimitive?.contentOrNull
                val name = languageId?.let { JsonTools.path(payload, "descriptions.$it.name")?.jsonPrimitive?.contentOrNull }
                    ?: payload["descriptions"]?.jsonObject?.values?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                keyword = HandelthekeSeoPolicy.slug(name)
            } else keyword = HandelthekeSeoPolicy.slug(keyword)
            if (keyword != oldKeyword) {
                recordSetChange(entity.copy(localJson = payload.toString()), "seo_urls.$index.keyword", JsonPrimitive(oldKeyword), JsonPrimitive(keyword))
                changed++
            }
            JsonObject(row.toMutableMap().apply { put("keyword", JsonPrimitive(keyword)) })
        }
        payload = JsonObject(payload.toMutableMap().apply { put("seo_urls", JsonArray(newSeo)) })
        savePayload(entity, payload)
        audit("donor_replace", entity, "Быстрая замена: $oldValue → $newValue", buildJsonObject { put("changed", changed) })
        changed
    }


    suspend fun setLocalNote(uuid: String, note: String) = db.withTransaction {
        val entity = db.products().get(uuid) ?: error("Товар не найден")
        if (entity.localNote == note) return@withTransaction
        db.products().put(entity.copy(localNote = note, updatedAt = System.currentTimeMillis()))
        audit("local_note", entity, "Локальная заметка изменена", buildJsonObject { put("note", note) })
    }

    suspend fun setWorkflowState(uuid: String, state: String) = db.withTransaction {
        require(state in setOf("DRAFT", "IN_PROGRESS", "REVIEW", "READY")) { "Неизвестный рабочий статус" }
        val entity = db.products().get(uuid) ?: error("Товар не найден")
        if (state == "READY") {
            val lang = db.settings().get("primary_language_id")
            val errors = de.handeltheke.handeldesk.domain.ValidationEngine.validate(entity, lang)
                .filter { it.severity == de.handeltheke.handeldesk.domain.Severity.ERROR }
            require(errors.isEmpty()) { "Нельзя отметить готовым: ${errors.first().message}" }
            if (entity.sourceProductId != null) {
                val donor = db.products().getByProductId(entity.sourceProductId)
                if (donor != null) {
                    val checklist = de.handeltheke.handeldesk.domain.ValidationEngine.donorChecklist(entity, donor)
                    require(checklist.ready) { "Donor Checklist содержит ошибки" }
                }
            }
        }
        db.products().put(entity.copy(workflowState = state, isDraft = state != "READY", updatedAt = System.currentTimeMillis()))
        audit("workflow", entity, "Рабочий статус: $state", buildJsonObject { put("state", state) })
    }

    suspend fun addToQueue(uuid: String) {
        val position = db.queue().nextPosition()
        db.queue().put(WorkQueueEntity(uuid, position, System.currentTimeMillis()))
    }

    suspend fun removeFromQueue(uuid: String) = db.queue().remove(uuid)

    private suspend fun insertNew(uuid: String, payload: JsonObject, sourceProductId: Int?) {
        val idx = ProductJson.index(payload, db.settings().get("primary_language_id"))
        val now = System.currentTimeMillis()
        val entity = ProductEntity(
            uuid = uuid, productId = null, sourceProductId = sourceProductId, baseJson = "{}", localJson = payload.toString(),
            state = "NEW", isDraft = true, workflowState = "DRAFT", name = idx.name, model = idx.model, sku = idx.sku,
            jan = idx.jan, price = idx.price, quantity = idx.quantity, manufacturerId = idx.manufacturerId,
            status = idx.status, searchNorm = idx.searchNorm, updatedAt = now
        )
        db.products().put(entity)
        db.changes().insert(ChangeEntity(
            sessionId = activeSessionId(), entityType = "product", entityUuid = uuid, fieldPath = "__create__",
            beforeJson = null, afterJson = payload.toString(), operationType = "create", createdAt = now
        ))
    }

    private suspend fun savePayload(entity: ProductEntity, payload: JsonObject) {
        val idx = ProductJson.index(payload, db.settings().get("primary_language_id"))
        val pending = db.changes().pendingCount("product", entity.uuid) > 0
        db.products().put(entity.copy(
            localJson = payload.toString(), state = if (entity.productId == null) "NEW" else if (pending) "MODIFIED" else "SYNCED",
            name = idx.name, model = idx.model, sku = idx.sku, jan = idx.jan, price = idx.price, quantity = idx.quantity,
            manufacturerId = idx.manufacturerId, status = idx.status, searchNorm = idx.searchNorm, updatedAt = System.currentTimeMillis()
        ))
    }

    private suspend fun recordSetChange(entity: ProductEntity, path: String, before: JsonElement, after: JsonElement) {
        if (entity.productId == null) return // __create__ carries the latest local payload during sync.
        val base = JsonTools.objectOf(entity.baseJson)
        val baseValue = JsonTools.path(base, path) ?: JsonNull
        val existing = db.changes().findSet("product", entity.uuid, path)
        if (after == baseValue) {
            existing?.let { db.changes().delete(it.id) }
            return
        }
        val now = System.currentTimeMillis()
        if (existing == null) {
            db.changes().insert(ChangeEntity(sessionId = activeSessionId(), entityType = "product", entityUuid = entity.uuid, fieldPath = path,
                beforeJson = baseValue.toString(), afterJson = after.toString(), operationType = "set", createdAt = now))
        } else db.changes().update(existing.copy(afterJson = after.toString(), included = true))
    }

    private suspend fun activeSessionId(): String? = db.settings().get("active_session_id")

    private suspend fun audit(type: String, entity: ProductEntity, summary: String, details: JsonObject) {
        db.audit().insert(AuditEventEntity(
            createdAt = System.currentTimeMillis(), sessionId = activeSessionId(), eventType = type,
            entityType = "product", entityUuid = entity.uuid, entityId = entity.productId,
            source = "android", summary = summary, detailsJson = details.toString()
        ))
    }
}
