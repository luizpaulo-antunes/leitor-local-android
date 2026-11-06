package com.leitorlocal.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

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
        if (!DocumentFingerprint.resolve(context, uri).matches(json)) return null
        file.setLastModified(System.currentTimeMillis())
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
            val json = JSONObject()
                .put("profile", OCR_PROFILE)
                .put("rawText", rawText.take(MAX_TEXT_LENGTH))
            DocumentFingerprint.resolve(context, uri).writeTo(json)
            temporary.writeText(json.toString())
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun cacheFile(uri: Uri, pageIndex: Int): File {
        val bookDirectory = File(root, DocumentFingerprint.sha256(uri.toString()).take(24))
        return File(bookDirectory, "page_$pageIndex.json")
    }

    companion object {
        const val CACHE_DIRECTORY = "page_text_ocr_v2"
        private const val OCR_PROFILE = "mlkit-latin-ptbr-v2"
        private const val MAX_TEXT_LENGTH = 200_000
    }
}
