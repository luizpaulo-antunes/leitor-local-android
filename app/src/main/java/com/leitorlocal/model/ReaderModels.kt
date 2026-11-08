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
    val visualLabels: List<String> = emptyList(),
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

data class AssistantSource(
    val chunkIndex: Int,
    val pageNumber: Int,
    val excerpt: String,
)

data class AssistantResult(
    val title: String,
    val body: String,
    val sources: List<AssistantSource>,
)

data class BookAssistantState(
    val isOpen: Boolean = false,
    val isWorking: Boolean = false,
    val query: String = "",
    val result: AssistantResult? = null,
)

data class AudioPreparationState(
    val bookId: String = "",
    val isRunning: Boolean = false,
    val isReady: Boolean = false,
    val sampleReady: Boolean = false,
    val startChunkIndex: Int = 0,
    val completedChunks: Int = 0,
    val totalChunks: Int = 0,
    val preparedDurationMillis: Long = 0L,
    val targetDurationMillis: Long = 0L,
    val estimatedWaitMillis: Long? = null,
    val realTimeFactor: Float? = null,
    val message: String = "",
) {
    val progress: Float
        get() = if (targetDurationMillis > 0L) {
            preparedDurationMillis.toFloat().div(targetDurationMillis).coerceIn(0f, 1f)
        } else if (totalChunks > 0) {
            completedChunks.toFloat().div(totalChunks).coerceIn(0f, 1f)
        } else {
            0f
        }
}

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
    val assistant: BookAssistantState = BookAssistantState(),
    val audioPreparation: AudioPreparationState = AudioPreparationState(),
) {
    val currentChunk: SpeechChunk?
        get() = book?.chunks?.getOrNull(playback.currentChunkIndex)

    val currentPage: Int
        get() = currentChunk?.pageIndex ?: 0
}
