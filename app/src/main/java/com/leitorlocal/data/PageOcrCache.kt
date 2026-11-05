package com.leitorlocal.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class CachedPageOcr(
    val rawText: String,
)

/**
 * Persists successful OCR responses, including successful responses without text.
 * An empty cached value is important: it prevents decorative/blank pages from being
 * rendered and submitted to ML Kit every time the book is opened.
 */
class PageOcrCache(private val context: Context) {
    private val root = File(context.filesDir, CACHE_DIRECTORY).apply { mkdirs() }

    @Synchronized
    fun load(uri: Uri, pageIndex: Int): CachedPageOcr? = runCatching {
        val file = cacheFile(uri, pageIndex)
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        if (json.optString("profile") != OCR_PROFILE) return null
        if (json.optLong("sourceSize", INVALID_SOURCE_SIZE) != sourceSize(uri)) return null
        CachedPageOcr(rawText = json.getString("rawText"))
    }.getOrNull()

    @Synchronized
    fun save(uri: Uri, pageIndex: Int, rawText: String) {
        if (pageIndex < 0) return
        runCatching {
            val destination = cacheFile(uri, pageIndex)
            destination.parentFile?.mkdirs()
            val temporary = File(
                destination.parentFile,
                ".${destination.name}.${System.nanoTime()}.tmp",
            )
            temporary.writeText(
                JSONObject()
                    .put("profile", OCR_PROFILE)
                    .put("sourceSize", sourceSize(uri))
                    .put("rawText", rawText.take(MAX_TEXT_LENGTH))
                    .toString(),
            )
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun cacheFile(uri: Uri, pageIndex: Int): File {
        val bookDirectory = File(root, uri.digest().take(24))
        return File(bookDirectory, "page_$pageIndex.json")
    }

    private fun sourceSize(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length
        } ?: INVALID_SOURCE_SIZE
    }.getOrDefault(INVALID_SOURCE_SIZE)

    private fun Uri.digest(): String = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val CACHE_DIRECTORY = "page_text_ocr_v1"
        private const val OCR_PROFILE = "mlkit-latin-ptbr-v1"
        private const val INVALID_SOURCE_SIZE = -1L
        private const val MAX_TEXT_LENGTH = 200_000
    }
}
