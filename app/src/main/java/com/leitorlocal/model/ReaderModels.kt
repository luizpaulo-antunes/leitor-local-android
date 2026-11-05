package com.leitorlocal.model

import android.graphics.Bitmap

data class SpeechChunk(
    val pageIndex: Int,
    val text: String,
)

data class BookContent(
    val uri: String,
    val title: String,
    val pageCount: Int,
    val chunks: List<SpeechChunk>,
    val pagesWithoutText: Int,
)

enum class PlaybackStatus {
    STOPPED,
    PLAYING,
    PAUSED,
    ERROR,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.STOPPED,
    val currentChunkIndex: Int = 0,
    val error: String? = null,
)

data class VoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
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
    val error: String? = null,
) {
    val currentChunk: SpeechChunk?
        get() = book?.chunks?.getOrNull(playback.currentChunkIndex)

    val currentPage: Int
        get() = currentChunk?.pageIndex ?: 0
}
