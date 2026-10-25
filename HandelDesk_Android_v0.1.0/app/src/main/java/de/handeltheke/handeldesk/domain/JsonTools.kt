package de.handeltheke.handeldesk.domain

import kotlinx.serialization.json.*

object JsonTools {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun objectOf(text: String): JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(buildJsonObject {})

    fun string(obj: JsonObject, key: String, default: String = ""): String = obj[key]?.jsonPrimitive?.contentOrNull ?: default
    fun int(obj: JsonObject, key: String, default: Int = 0): Int = obj[key]?.jsonPrimitive?.intOrNull ?: default
    fun double(obj: JsonObject, key: String, default: Double = 0.0): Double = obj[key]?.jsonPrimitive?.doubleOrNull ?: default

    fun path(root: JsonElement, path: String): JsonElement? {
        var current: JsonElement = root
        for (part in path.split('.')) {
            current = when (current) {
                is JsonObject -> current[part] ?: return null
                is JsonArray -> part.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return current
    }

    fun setPath(root: JsonObject, path: String, value: JsonElement): JsonObject {
        val parts = path.split('.').filter(String::isNotBlank)
        require(parts.isNotEmpty())
        return setRecursive(root, parts, 0, value).jsonObject
    }

    private fun setRecursive(node: JsonElement, parts: List<String>, index: Int, value: JsonElement): JsonElement {
        if (index >= parts.size) return value
        val token = parts[index]
        return when (node) {
            is JsonArray -> {
                val i = token.toIntOrNull() ?: error("Array path requires numeric index: $token")
                val list = node.toMutableList()
                while (list.size <= i) list += JsonNull
                val child = list[i].takeUnless { it is JsonNull } ?: defaultNode(parts.getOrNull(index + 1))
                list[i] = setRecursive(child, parts, index + 1, value)
                JsonArray(list)
            }
            is JsonObject -> {
                val map = node.toMutableMap()
                val child = map[token] ?: defaultNode(parts.getOrNull(index + 1))
                map[token] = setRecursive(child, parts, index + 1, value)
                JsonObject(map)
            }
            else -> setRecursive(defaultNode(token), parts, index, value)
        }
    }

    private fun defaultNode(next: String?): JsonElement = if (next?.toIntOrNull() != null) JsonArray(emptyList()) else buildJsonObject {}

    fun jsonValue(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is JsonElement -> value
        else -> JsonPrimitive(value.toString())
    }
}
