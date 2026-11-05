package com.leitorlocal.speech

import com.leitorlocal.model.PlaybackState
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.SpeechChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReadingSession {
    @Volatile private var chunks: List<SpeechChunk> = emptyList()
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    var title: String = "Leitor Local"
        private set
    var bookId: String = ""
        private set
    var speechRate: Float = 1f
        private set
    var voiceName: String? = null
        private set

    @Synchronized
    fun configure(
        bookId: String,
        title: String,
        chunks: List<SpeechChunk>,
        startIndex: Int,
        speechRate: Float,
        voiceName: String?,
    ) {
        this.bookId = bookId
        this.title = title
        this.chunks = chunks
        this.speechRate = speechRate
        this.voiceName = voiceName
        mutableState.value = PlaybackState(
            status = PlaybackStatus.PAUSED,
            currentChunkIndex = startIndex.coerceIn(0, (chunks.lastIndex).coerceAtLeast(0)),
        )
    }

    @Synchronized
    fun updateSettings(rate: Float, voiceName: String?) {
        speechRate = rate.coerceIn(0.6f, 1.6f)
        this.voiceName = voiceName
    }

    fun currentChunk(): SpeechChunk? = chunks.getOrNull(mutableState.value.currentChunkIndex)

    fun chunkAt(index: Int): SpeechChunk? = chunks.getOrNull(index)

    fun chunkCount(): Int = chunks.size

    @Synchronized
    fun indexOfChunk(chunk: SpeechChunk): Int? {
        val identityIndex = chunks.indexOfFirst { it === chunk }
        if (identityIndex >= 0) return identityIndex
        return chunks.indexOf(chunk).takeIf { it >= 0 }
    }

    /**
     * Replaces the progressively enriched book without making the spoken paragraph jump.
     * Existing chunks keep their object identity when OCR pages are merged, while equality is
     * retained as a safe fallback for books restored from cache.
     */
    @Synchronized
    fun replaceChunks(updated: List<SpeechChunk>) {
        if (updated.isEmpty()) return
        val previousState = mutableState.value
        val current = chunks.getOrNull(previousState.currentChunkIndex)
        chunks = updated
        val restoredIndex = current?.let(::indexOfChunk)
            ?: previousState.currentChunkIndex.coerceIn(0, updated.lastIndex)
        val sameChunk = current != null && updated.getOrNull(restoredIndex) == current
        mutableState.value = previousState.copy(
            currentChunkIndex = restoredIndex,
            currentWordStart = if (sameChunk) previousState.currentWordStart else 0,
            currentWordEnd = if (sameChunk) previousState.currentWordEnd else 0,
        )
    }

    fun hasContent(): Boolean = chunks.isNotEmpty()

    @Synchronized
    fun setStatus(status: PlaybackStatus, error: String? = null) {
        mutableState.value = mutableState.value.copy(
            status = status,
            currentWordStart = if (status == PlaybackStatus.PREPARING) 0 else mutableState.value.currentWordStart,
            currentWordEnd = if (status == PlaybackStatus.PREPARING) 0 else mutableState.value.currentWordEnd,
            error = error,
        )
    }

    @Synchronized
    fun moveBy(offset: Int): SpeechChunk? {
        if (chunks.isEmpty()) return null
        val next = (mutableState.value.currentChunkIndex + offset).coerceIn(0, chunks.lastIndex)
        mutableState.value = mutableState.value.copy(
            currentChunkIndex = next,
            currentWordStart = 0,
            currentWordEnd = 0,
            error = null,
        )
        return chunks[next]
    }

    @Synchronized
    fun setIndex(index: Int) {
        if (chunks.isEmpty()) return
        mutableState.value = mutableState.value.copy(
            currentChunkIndex = index.coerceIn(0, chunks.lastIndex),
            currentWordStart = 0,
            currentWordEnd = 0,
            error = null,
        )
    }

    @Synchronized
    fun setWordRange(chunkIndex: Int, start: Int, end: Int) {
        val chunk = chunks.getOrNull(chunkIndex) ?: return
        if (mutableState.value.currentChunkIndex != chunkIndex) return
        val boundedStart = start.coerceIn(0, chunk.text.length)
        val boundedEnd = end.coerceIn(boundedStart, chunk.text.length)
        mutableState.value = mutableState.value.copy(
            currentWordStart = boundedStart,
            currentWordEnd = boundedEnd,
        )
    }

    @Synchronized
    fun clearWordRange() {
        mutableState.value = mutableState.value.copy(currentWordStart = 0, currentWordEnd = 0)
    }

    fun isAtEnd(): Boolean = chunks.isEmpty() || mutableState.value.currentChunkIndex >= chunks.lastIndex
}
