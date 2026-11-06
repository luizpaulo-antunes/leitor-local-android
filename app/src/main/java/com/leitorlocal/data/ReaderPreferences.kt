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
        synchronized(PROCESS_LOCK) {
            migrateLegacyProgress()
        }
    }

    fun settings(): ReaderSettings = synchronized(PROCESS_LOCK) {
        ReaderSettings(
            rate = preferences.getFloat(KEY_RATE, DEFAULT_RATE).coerceIn(0.6f, 1.6f),
            voiceName = preferences.getString(KEY_VOICE, null),
        )
    }

    fun library(): List<LibraryBook> = synchronized(PROCESS_LOCK) {
        readLibrary().sortedByDescending(LibraryBook::lastOpenedAt)
    }

    fun findBook(uri: String): LibraryBook? = synchronized(PROCESS_LOCK) {
        readLibrary().firstOrNull { it.uri == uri }
    }

    fun saveBook(uri: String, title: String, totalChunks: Int, pageCount: Int) {
        synchronized(PROCESS_LOCK) {
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
        }
    }

    fun markOpened(uri: String) {
        synchronized(PROCESS_LOCK) {
            val books = readLibrary().toMutableList()
            val index = books.indexOfFirst { it.uri == uri }
            if (index < 0) return@synchronized
            books[index] = books[index].copy(lastOpenedAt = System.currentTimeMillis())
            writeLibrary(books)
        }
    }

    fun saveCover(uri: String, coverPath: String) {
        synchronized(PROCESS_LOCK) {
            val books = readLibrary().toMutableList()
            val index = books.indexOfFirst { it.uri == uri }
            if (index < 0) return@synchronized
            books[index] = books[index].copy(coverPath = coverPath)
            writeLibrary(books)
        }
    }

    /**
     * Persists progress for an explicit book. Unlike the legacy overload, this cannot write a
     * delayed playback update into whichever book most recently became active.
     */
    fun savePosition(bookId: String, chunkIndex: Int) {
        synchronized(PROCESS_LOCK) {
            savePositionLocked(bookId, chunkIndex)
        }
    }

    private fun savePositionLocked(bookId: String, chunkIndex: Int) {
        val books = readLibrary().toMutableList()
        val index = books.indexOfFirst { it.uri == bookId }
        if (index < 0) return
        val book = books[index]
        val boundedIndex = chunkIndex.coerceIn(0, (book.totalChunks - 1).coerceAtLeast(0))
        if (book.chunkIndex == boundedIndex) return
        books[index] = book.copy(
            chunkIndex = boundedIndex,
        )
        writeLibrary(books)
    }

    fun saveSettings(rate: Float, voiceName: String?) {
        synchronized(PROCESS_LOCK) {
            preferences.edit {
                putFloat(KEY_RATE, rate)
                putString(KEY_VOICE, voiceName)
            }
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
    }

    companion object {
        /** Shared by the Activity and foreground-service preference instances in this process. */
        private val PROCESS_LOCK = Any()
        private const val DEFAULT_RATE = 0.95f
        private const val KEY_LIBRARY = "library_v2"
        private const val KEY_RATE = "rate"
        private const val KEY_VOICE = "voice"
        private const val LEGACY_URI = "uri"
        private const val LEGACY_TITLE = "title"
        private const val LEGACY_CHUNK = "chunk"
    }
}
