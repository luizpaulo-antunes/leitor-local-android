package com.leitorlocal.data

import android.content.Context
import androidx.core.content.edit
import com.leitorlocal.model.LibraryBook
import org.json.JSONArray
import org.json.JSONObject

data class ReaderSettings(
    val rate: Float,
    val voiceName: String?,
)

class ReaderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("reader_progress", Context.MODE_PRIVATE)

    init {
        migrateLegacyProgress()
    }

    fun settings(): ReaderSettings = ReaderSettings(
        rate = preferences.getFloat(KEY_RATE, DEFAULT_RATE).coerceIn(0.6f, 1.6f),
        voiceName = preferences.getString(KEY_VOICE, null),
    )

    @Synchronized
    fun library(): List<LibraryBook> = readLibrary()
        .sortedByDescending(LibraryBook::lastOpenedAt)

    @Synchronized
    fun findBook(uri: String): LibraryBook? = readLibrary().firstOrNull { it.uri == uri }

    @Synchronized
    fun saveBook(uri: String, title: String, totalChunks: Int, pageCount: Int) {
        val books = readLibrary().toMutableList()
        val existingIndex = books.indexOfFirst { it.uri == uri }
        val existing = books.getOrNull(existingIndex)
        val updated = LibraryBook(
            uri = uri,
            title = title,
            coverPath = existing?.coverPath,
            chunkIndex = existing?.chunkIndex?.coerceIn(0, (totalChunks - 1).coerceAtLeast(0)) ?: 0,
            totalChunks = totalChunks,
            pageCount = pageCount,
            lastOpenedAt = System.currentTimeMillis(),
        )
        if (existingIndex >= 0) books[existingIndex] = updated else books += updated
        writeLibrary(books)
        preferences.edit { putString(KEY_ACTIVE_URI, uri) }
    }

    @Synchronized
    fun markOpened(uri: String) {
        val books = readLibrary().toMutableList()
        val index = books.indexOfFirst { it.uri == uri }
        if (index < 0) return
        books[index] = books[index].copy(lastOpenedAt = System.currentTimeMillis())
        writeLibrary(books)
        preferences.edit { putString(KEY_ACTIVE_URI, uri) }
    }

    @Synchronized
    fun saveCover(uri: String, coverPath: String) {
        val books = readLibrary().toMutableList()
        val index = books.indexOfFirst { it.uri == uri }
        if (index < 0) return
        books[index] = books[index].copy(coverPath = coverPath)
        writeLibrary(books)
    }

    @Synchronized
    fun savePosition(chunkIndex: Int) {
        val activeUri = preferences.getString(KEY_ACTIVE_URI, null) ?: return
        val books = readLibrary().toMutableList()
        val index = books.indexOfFirst { it.uri == activeUri }
        if (index < 0) return
        val book = books[index]
        books[index] = book.copy(
            chunkIndex = chunkIndex.coerceIn(0, (book.totalChunks - 1).coerceAtLeast(0)),
        )
        writeLibrary(books)
    }

    fun saveSettings(rate: Float, voiceName: String?) {
        preferences.edit {
            putFloat(KEY_RATE, rate)
            putString(KEY_VOICE, voiceName)
        }
    }

    private fun readLibrary(): List<LibraryBook> {
        val array = runCatching {
            JSONArray(preferences.getString(KEY_LIBRARY, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val uri = item.optString("uri")
                if (uri.isBlank()) continue
                add(
                    LibraryBook(
                        uri = uri,
                        title = item.optString("title", "Livro.pdf"),
                        coverPath = item.optString("coverPath").takeIf(String::isNotBlank),
                        chunkIndex = item.optInt("chunkIndex", 0).coerceAtLeast(0),
                        totalChunks = item.optInt("totalChunks", 0).coerceAtLeast(0),
                        pageCount = item.optInt("pageCount", 0).coerceAtLeast(0),
                        lastOpenedAt = item.optLong("lastOpenedAt", 0L),
                    ),
                )
            }
        }
    }

    private fun writeLibrary(books: List<LibraryBook>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("uri", book.uri)
                    .put("title", book.title)
                    .put("coverPath", book.coverPath ?: "")
                    .put("chunkIndex", book.chunkIndex)
                    .put("totalChunks", book.totalChunks)
                    .put("pageCount", book.pageCount)
                    .put("lastOpenedAt", book.lastOpenedAt),
            )
        }
        preferences.edit { putString(KEY_LIBRARY, array.toString()) }
    }

    private fun migrateLegacyProgress() {
        if (preferences.contains(KEY_LIBRARY)) return
        val uri = preferences.getString(LEGACY_URI, null)
        if (uri == null) {
            preferences.edit { putString(KEY_LIBRARY, "[]") }
            return
        }
        val legacy = LibraryBook(
            uri = uri,
            title = preferences.getString(LEGACY_TITLE, "Livro.pdf") ?: "Livro.pdf",
            coverPath = null,
            chunkIndex = preferences.getInt(LEGACY_CHUNK, 0).coerceAtLeast(0),
            totalChunks = 0,
            pageCount = 0,
            lastOpenedAt = System.currentTimeMillis(),
        )
        writeLibrary(listOf(legacy))
        preferences.edit { putString(KEY_ACTIVE_URI, uri) }
    }

    companion object {
        private const val DEFAULT_RATE = 0.95f
        private const val KEY_LIBRARY = "library_v2"
        private const val KEY_ACTIVE_URI = "active_uri"
        private const val KEY_RATE = "rate"
        private const val KEY_VOICE = "voice"
        private const val LEGACY_URI = "uri"
        private const val LEGACY_TITLE = "title"
        private const val LEGACY_CHUNK = "chunk"
    }
}
