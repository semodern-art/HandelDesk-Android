package de.handeltheke.handeldesk.domain

import de.handeltheke.handeldesk.core.HandelthekeSeoPolicy
import de.handeltheke.handeldesk.data.local.ProductEntity
import kotlinx.serialization.json.*

enum class Severity { OFF, WARNING, ERROR }
data class ValidationIssue(val code: String, val severity: Severity, val message: String)
data class DonorCheckItem(val code: String, val label: String, val status: String, val message: String = "")
data class DonorChecklist(val applicable: Boolean, val ready: Boolean, val items: List<DonorCheckItem>)

object ValidationDefaults {
    val values = mapOf(
        "primary_name" to Severity.ERROR,
        "model" to Severity.WARNING,
        "category" to Severity.WARNING,
        "manufacturer" to Severity.WARNING,
        "main_image" to Severity.WARNING,
        "description_primary" to Severity.WARNING,
        "meta_title_primary" to Severity.WARNING,
        "seo_present" to Severity.ERROR,
        "seo_format" to Severity.ERROR,
        "quantity_negative" to Severity.ERROR,
        "donor_model_changed" to Severity.ERROR,
        "donor_unique_fields" to Severity.WARNING,
        "donor_main_image" to Severity.WARNING,
    )
}

object ValidationEngine {
    fun validate(entity: ProductEntity, primaryLanguageId: String?, rules: Map<String, Severity> = ValidationDefaults.values): List<ValidationIssue> {
        val payload = JsonTools.objectOf(entity.localJson)
        val core = payload["product"]?.jsonObject ?: buildJsonObject {}
        val descriptions = payload["descriptions"]?.jsonObject ?: buildJsonObject {}
        val primary = primaryLanguageId?.let { descriptions[it]?.jsonObject } ?: descriptions.values.firstOrNull()?.jsonObject ?: buildJsonObject {}
        val issues = mutableListOf<ValidationIssue>()
        fun issue(code: String, message: String) {
            val severity = rules[code] ?: Severity.OFF
            if (severity != Severity.OFF) issues += ValidationIssue(code, severity, message)
        }
        if (primary["name"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) issue("primary_name", "Не заполнено название на основном языке")
        if (core["model"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) issue("model", "Model пустой")
        if ((payload["categories"] as? JsonArray).isNullOrEmpty()) issue("category", "Не выбрана категория")
        if ((core["manufacturer_id"]?.jsonPrimitive?.intOrNull ?: 0) == 0) issue("manufacturer", "Не выбран производитель")
        if (core["image"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) issue("main_image", "Нет главного изображения")
        if (primary["description"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) issue("description_primary", "Описание на основном языке пустое")
        if (primary["meta_title"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) issue("meta_title_primary", "Meta Title на основном языке пустой")
        if ((core["quantity"]?.jsonPrimitive?.intOrNull ?: 0) < 0) issue("quantity_negative", "Остаток товара отрицательный")
        val seo = (payload["seo_urls"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["keyword"]?.jsonPrimitive?.contentOrNull }.filter(String::isNotBlank)
        if (seo.isEmpty()) issue("seo_present", "SEO URL отсутствует")
        seo.filter { HandelthekeSeoPolicy.slug(it) != it }.forEach { issue("seo_format", "SEO URL нарушает строгую схему Handeltheke: $it") }
        return issues
    }

    fun donorChecklist(newEntity: ProductEntity, donorEntity: ProductEntity): DonorChecklist {
        val local = JsonTools.objectOf(newEntity.localJson)
        val donor = JsonTools.objectOf(donorEntity.localJson)
        val lc = local["product"]?.jsonObject ?: buildJsonObject {}
        val dc = donor["product"]?.jsonObject ?: buildJsonObject {}
        val items = mutableListOf<DonorCheckItem>()
        fun add(code: String, label: String, status: String, message: String = "") { items += DonorCheckItem(code, label, status, message) }
        val model = lc["model"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val donorModel = dc["model"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        add("model", "Model изменён", if (model.isNotBlank() && !model.equals(donorModel, true)) "ok" else "error", "донор: ${donorModel.ifBlank { "—" }} · новый: ${model.ifBlank { "—" }}")
        val ld = local["descriptions"]?.jsonObject.orEmpty(); val dd = donor["descriptions"]?.jsonObject.orEmpty()
        val changedNames = ld.count { (lang, element) ->
            val name = element.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val old = dd[lang]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            name.isNotBlank() && name != old
        }
        add("name", "Название изменено", if (changedNames > 0) "ok" else "warning", "изменённых языков: $changedNames")
        listOf("sku","upc","ean","jan","isbn","mpn").forEach { field ->
            val a = lc[field]?.jsonPrimitive?.contentOrNull.orEmpty().trim(); val b = dc[field]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (a.isNotBlank() && b.isNotBlank() && a.equals(b, true)) add(field, "${field.uppercase()} не совпадает с донором", "warning", "совпадает: $a")
        }
        val image = lc["image"]?.jsonPrimitive?.contentOrNull.orEmpty(); val oldImage = dc["image"]?.jsonPrimitive?.contentOrNull.orEmpty()
        add("image", "Главное фото проверено", if (image.isBlank() || (oldImage.isNotBlank() && image == oldImage)) "warning" else "ok", if (image == oldImage && image.isNotBlank()) "осталось фото донора" else image.ifBlank { "фото отсутствует" })
        val seo = (local["seo_urls"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase() }.filter(String::isNotBlank).toSet()
        val oldSeo = (donor["seo_urls"] as? JsonArray).orEmpty().mapNotNull { it.jsonObject["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase() }.filter(String::isNotBlank).toSet()
        when {
            seo.isEmpty() -> add("seo", "SEO URL создан", "error", "SEO URL отсутствует")
            (seo intersect oldSeo).isNotEmpty() -> add("seo", "SEO URL отличается от донора", "error", "есть совпадающий URL")
            else -> add("seo", "SEO URL отличается от донора", "ok", seo.joinToString(", ").take(240))
        }
        return DonorChecklist(true, items.none { it.status == "error" }, items)
    }
}
