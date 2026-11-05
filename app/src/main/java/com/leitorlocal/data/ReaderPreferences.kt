package com.leitorlocal.data

import android.content.Context
import androidx.core.content.edit

data class SavedProgress(
    val uri: String,
    val title: String,
    val chunkIndex: Int,
    val rate: Float,
    val voiceName: String?,
)

class ReaderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("reader_progress", Context.MODE_PRIVATE)

    fun load(): SavedProgress? {
        val uri = preferences.getString(KEY_URI, null) ?: return null
        return SavedProgress(
            uri = uri,
            title = preferences.getString(KEY_TITLE, "Livro") ?: "Livro",
            chunkIndex = preferences.getInt(KEY_CHUNK, 0).coerceAtLeast(0),
            rate = preferences.getFloat(KEY_RATE, 1f).coerceIn(0.6f, 1.6f),
            voiceName = preferences.getString(KEY_VOICE, null),
        )
    }

    fun saveBook(uri: String, title: String) {
        preferences.edit {
            putString(KEY_URI, uri)
            putString(KEY_TITLE, title)
            putInt(KEY_CHUNK, 0)
        }
    }

    fun savePosition(chunkIndex: Int) {
        preferences.edit { putInt(KEY_CHUNK, chunkIndex.coerceAtLeast(0)) }
    }

    fun saveSettings(rate: Float, voiceName: String?) {
        preferences.edit {
            putFloat(KEY_RATE, rate)
            putString(KEY_VOICE, voiceName)
        }
    }

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_TITLE = "title"
        private const val KEY_CHUNK = "chunk"
        private const val KEY_RATE = "rate"
        private const val KEY_VOICE = "voice"
    }
}
