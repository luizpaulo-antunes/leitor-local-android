package com.leitorlocal.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.SpeechChunk
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class BookCache(private val context: Context) {
    private val directory = File(context.filesDir, "processed_books_v2").apply { mkdirs() }

    fun load(uri: Uri, title: String): BookContent? = runCatching {
        val file = cacheFile(uri)
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        if (root.optLong("sourceSize", -2L) != sourceSize(uri)) return null

        val chunksJson = root.getJSONArray("chunks")
        val chunks = buildList {
            for (index in 0 until chunksJson.length()) {
                val item = chunksJson.getJSONObject(index)
                add(SpeechChunk(pageIndex = item.getInt("page"), text = item.getString("text")))
            }
        }
        if (chunks.isEmpty()) return null
        BookContent(
            uri = uri.toString(),
            title = title,
            pageCount = root.getInt("pageCount"),
            chunks = chunks,
            pagesWithoutText = root.optInt("pagesWithoutText", 0),
        )
    }.getOrNull()

    fun save(book: BookContent) {
        runCatching {
            val uri = book.uri.toUri()
            val chunks = JSONArray()
            book.chunks.forEach { chunk ->
                chunks.put(JSONObject().put("page", chunk.pageIndex).put("text", chunk.text))
            }
            val root = JSONObject()
                .put("sourceSize", sourceSize(uri))
                .put("pageCount", book.pageCount)
                .put("pagesWithoutText", book.pagesWithoutText)
                .put("chunks", chunks)

            val destination = cacheFile(uri)
            val temporary = File(directory, "${destination.name}.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun cacheFile(uri: Uri): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(directory, "$digest.json")
    }

    private fun sourceSize(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }.getOrDefault(-1L)
}
