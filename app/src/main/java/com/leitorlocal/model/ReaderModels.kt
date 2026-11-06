package com.leitorlocal.model

import android.graphics.Bitmap

data class SpeechChunk(
    val pageIndex: Int,
    val text: String,
    val figure: FigureContent? = null,
    val ranges: List<SpeechTextRange> = emptyList(),
)

data class SpeechTextRange(
    val start: Int,
    val endExclusive: Int,
    val role: SpeechTextRole,
)

enum class SpeechTextRole {
    HEADING,
}

data class FigureContent(
    val imagePath: String?,
    val caption: String,
    val alternativeText: String,
    val ocrText: String,
)

data class BookContent(
    val uri: String,
    val title: String,
    val pageCount: Int,
    val chunks: List<SpeechChunk>,
    val pagesWithoutText: Int,
)

data class LibraryBook(
    val uri: String,
    val title: String,
    val coverPath: String? = null,
    val chunkIndex: Int,
    val totalChunks: Int,
    val pageCount: Int,
    val lastOpenedAt: Long,
) {
    val progress: Float
        get() = if (totalChunks <= 1) {
            if (chunkIndex > 0) 1f else 0f
        } else {
            (chunkIndex.toFloat() / (totalChunks - 1)).coerceIn(0f, 1f)
        }
}

enum class PlaybackStatus {
    STOPPED,
    PREPARING,
    PLAYING,
    PAUSED,
    ERROR,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.STOPPED,
    val currentChunkIndex: Int = 0,
    val currentWordStart: Int = 0,
    val currentWordEnd: Int = 0,
    val error: String? = null,
)

data class VoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
    val quality: Int,
    val isHighQuality: Boolean,
    val isEmbeddedNeural: Boolean = false,
)

data class ReaderUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val book: BookContent? = null,
    val playback: PlaybackState = PlaybackState(),
    val pageBitmap: Bitmap? = null,
    val voices: List<VoiceOption> = emptyList(),
    val selectedVoiceName: String? = null,
    val speechRate: Float = 1f,
    val library: List<LibraryBook> = emptyList(),
    val showingLibrary: Boolean = true,
    val isOcrRunning: Boolean = false,
    val ocrMessage: String = "",
    val error: String? = null,
) {
    val currentChunk: SpeechChunk?
        get() = book?.chunks?.getOrNull(playback.currentChunkIndex)

    val currentPage: Int
        get() = currentChunk?.pageIndex ?: 0
}
