package de.handeltheke.handeldesk.domain

import de.handeltheke.handeldesk.core.normalizeSearch
import kotlinx.serialization.json.*

data class ProductIndex(
    val name: String,
    val model: String,
    val sku: String,
    val jan: String,
    val price: Double,
    val quantity: Int,
    val manufacturerId: Int,
    val status: Int,
    val searchNorm: String,
)

object ProductJson {
    fun blank(): JsonObject = buildJsonObject {
        putJsonObject("product") {
            listOf("model","sku","upc","ean","jan","isbn","mpn","location","image","date_available").forEach { put(it, "") }
            put("quantity", 0); put("stock_status_id", 0); put("manufacturer_id", 0); put("shipping", 1)
            put("price", "0.0000"); put("points", 0); put("tax_class_id", 0); put("weight", "0.00000000")
            put("weight_class_id", 0); put("length", "0.00000000"); put("width", "0.00000000"); put("height", "0.00000000")
            put("length_class_id", 0); put("subtract", 1); put("minimum", 1); put("sort_order", 0); put("status", 0)
        }
        put("descriptions", buildJsonObject {})
        put("categories", buildJsonArray {})
        putJsonArray("stores") { add(0) }
        listOf("related","images","attributes","options","discounts","specials","seo_urls").forEach { put(it, buildJsonArray {}) }
        put("extension_fields", buildJsonObject {})
    }

    fun index(payload: JsonObject, primaryLanguageId: String? = null): ProductIndex {
        val core = payload["product"]?.jsonObject ?: buildJsonObject {}
        val descriptions = payload["descriptions"]?.jsonObject ?: buildJsonObject {}
        val preferred = primaryLanguageId?.let { descriptions[it]?.jsonObject }
            ?: descriptions.values.firstOrNull()?.jsonObject
        val name = preferred?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val model = core["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sku = core["sku"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val jan = core["jan"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val price = core["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val quantity = core["quantity"]?.jsonPrimitive?.intOrNull ?: 0
        val manufacturer = core["manufacturer_id"]?.jsonPrimitive?.intOrNull ?: 0
        val status = core["status"]?.jsonPrimitive?.intOrNull ?: 0
        val searchable = buildList {
            descriptions.values.forEach { d ->
                d.jsonObject.let { add(it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()); add(it["tag"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
            }
            listOf("model","sku","upc","ean","jan","isbn","mpn").forEach { add(core[it]?.jsonPrimitive?.contentOrNull.orEmpty()) }
            core["product_id"]?.jsonPrimitive?.contentOrNull?.let(::add)
        }.joinToString(" ") { normalizeSearch(it) }
        return ProductIndex(name, model, sku, jan, price, quantity, manufacturer, status, searchable)
    }

    fun decodeEncodedEditorHtml(payload: JsonObject): JsonObject {
        val desc = payload["descriptions"] as? JsonObject ?: return payload
        val tagRegex = Regex("&lt;\\s*/?\\s*(?:p|br|div|span|ul|ol|li|strong|b|em|i|a|h[1-6]|table|thead|tbody|tr|td|th|img)(?:\\s|&gt;)", RegexOption.IGNORE_CASE)
        val decoded = desc.mapValues { (_, element) ->
            val row = element.jsonObject.toMutableMap()
            val raw = row["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (tagRegex.containsMatchIn(raw)) {
                val decoded = raw.replace("&lt;", "<", ignoreCase = true)
                    .replace("&gt;", ">", ignoreCase = true)
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                row["description"] = JsonPrimitive(decoded)
            }
            JsonObject(row)
        }
        return JsonObject(payload.toMutableMap().apply { put("descriptions", JsonObject(decoded)) })
    }
}
