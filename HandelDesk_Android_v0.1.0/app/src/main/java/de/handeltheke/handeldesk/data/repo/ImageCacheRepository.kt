package de.handeltheke.handeldesk.data.repo

import android.content.Context
import de.handeltheke.handeldesk.data.local.HandelDeskDatabase
import de.handeltheke.handeldesk.data.remote.BridgeClient
import de.handeltheke.handeldesk.domain.JsonTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

class ImageCacheRepository(
    context: Context,
    private val db: HandelDeskDatabase,
    private val bridge: BridgeClient,
) {
    private val root = File(context.filesDir, "image-cache").apply { mkdirs() }

    data class Progress(val current: Int, val total: Int, val path: String)

    fun cachedFile(remotePath: String): File? {
        if (remotePath.isBlank()) return null
        val file = fileFor(remotePath)
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    suspend fun cache(remotePath: String): File = withContext(Dispatchers.IO) {
        require(remotePath.isNotBlank()) { "Пустой путь изображения" }
        val target = fileFor(remotePath)
        if (target.isFile && target.length() > 0) return@withContext target
        val bytes = bridge.getBytes("image_fetch", mapOf("path" to remotePath))
        require(bytes.size >= 16) { "Bridge вернул пустое изображение" }
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (target.exists()) target.delete()
        require(tmp.renameTo(target)) { "Не удалось сохранить кеш изображения" }
        target
    }

    suspend fun prepare(mode: String, onProgress: (Progress) -> Unit = {}) {
        if (mode == "none") return
        val paths = linkedSetOf<String>()
        db.products().all().forEach { product ->
            val payload = JsonTools.objectOf(product.localJson)
            val main = payload["product"]?.jsonObject?.get("image")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (main.isNotBlank()) paths += main
            if (mode == "all") {
                (payload["images"] as? JsonArray).orEmpty().forEach { row ->
                    row.jsonObject["image"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let(paths::add)
                }
            }
        }
        paths.forEachIndexed { index, path ->
            onProgress(Progress(index, paths.size, path))
            runCatching { cache(path) } // one broken image must not abort the entire pre-flight package
        }
        onProgress(Progress(paths.size, paths.size, "Готово"))
    }

    suspend fun cacheStats(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val files = root.listFiles()?.filter(File::isFile).orEmpty()
        files.size to files.sumOf(File::length)
    }

    private fun fileFor(remotePath: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(remotePath.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val ext = remotePath.substringAfterLast('.', "img").lowercase().takeIf { it in setOf("jpg","jpeg","png","gif","webp") } ?: "img"
        return File(root, "$digest.$ext")
    }
}
