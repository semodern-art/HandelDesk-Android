package de.handeltheke.handeldesk.data.remote

import de.handeltheke.handeldesk.security.SecureBridgeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class BridgeException(message: String, val status: Int? = null) : Exception(message)

class BridgeClient(private val settings: SecureBridgeSettings) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
        prettyPrint = false
    }

    suspend fun get(action: String, params: Map<String, String> = emptyMap()): JsonObject = withContext(Dispatchers.IO) {
        request("GET", action, params, null)
    }

    suspend fun post(action: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        request("POST", action, emptyMap(), body)
    }


    suspend fun getBytes(action: String, params: Map<String, String> = emptyMap()): ByteArray = withContext(Dispatchers.IO) {
        val token = settings.token()
        if (token.isBlank()) throw@withContext BridgeException("Device Token не настроен")
        val url = buildUrl(action, params)
        if (!url.startsWith("https://", ignoreCase = true)) throw@withContext BridgeException("Bridge допускается только по HTTPS")
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("X-HandelDesk-Token", token)
        connection.setRequestProperty("User-Agent", "HandelDesk-Android/0.1.0")
        val status = connection.responseCode
        if (status in 300..399) { connection.disconnect(); throw@withContext BridgeException("Bridge вернул redirect ($status). Автопереход запрещён.", status) }
        if (status !in 200..299) {
            val text = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            val message = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "HTTP $status"
            throw@withContext BridgeException(message, status)
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        bytes
    }

    private fun request(method: String, action: String, params: Map<String, String>, body: JsonObject?): JsonObject {
        val token = settings.token()
        if (token.isBlank()) throw BridgeException("Device Token не настроен")
        val url = buildUrl(action, params)
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw BridgeException("Bridge допускается только по HTTPS")
        }
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("X-HandelDesk-Token", token)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "HandelDesk-Android/0.1.0")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        }
        val status = connection.responseCode
        if (status in 300..399) {
            connection.disconnect()
            throw BridgeException("Bridge вернул redirect ($status). Автопереход запрещён.", status)
        }
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }.orEmpty()
        connection.disconnect()
        val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw BridgeException("Bridge вернул некорректный JSON (HTTP $status)", status)
        }
        if (status !in 200..299 || parsed["ok"]?.jsonPrimitive?.booleanOrNull == false) {
            val message = parsed["error"]?.jsonPrimitive?.contentOrNull ?: "HTTP $status"
            throw BridgeException(message, status)
        }
        parsed
    }

    private fun buildUrl(action: String, params: Map<String, String>): String {
        val raw = settings.baseUrl.trim()
        if (raw.isBlank()) throw BridgeException("Bridge URL не настроен")
        val separator = if (raw.contains('?')) '&' else '?'
        val suffix = buildList {
            add("action=${encode(action)}")
            params.forEach { (k, v) -> add("${encode(k)}=${encode(v)}") }
        }.joinToString("&")
        return raw + separator + suffix
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
