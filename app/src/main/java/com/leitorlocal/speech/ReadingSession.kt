package com.leitorlocal.speech

import com.leitorlocal.model.BookContent
import com.leitorlocal.model.PlaybackState
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.SpeechChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReadingSession {
    @Volatile private var chunks: List<SpeechChunk> = emptyList()
    private var pageCount: Int = 0
    private var pagesWithoutText: Int = 0
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    @Volatile var title: String = "Leitor Local"
        private set
    @Volatile var bookId: String = ""
        private set
    @Volatile var speechRate: Float = 1f
        private set
    @Volatile var voiceName: String? = null
        private set

    @Synchronized
    fun configure(
        bookId: String,
        title: String,
        chunks: List<SpeechChunk>,
        startIndex: Int,
        speechRate: Float,
        voiceName: String?,
        pageCount: Int = inferredPageCount(chunks),
        pagesWithoutText: Int = 0,
    ) {
        this.bookId = bookId
        this.title = title
        this.chunks = chunks
        this.pageCount = pageCount.coerceAtLeast(inferredPageCount(chunks))
        this.pagesWithoutText = pagesWithoutText.coerceIn(0, this.pageCount)
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

    /**
     * Returns the book that backs the process-wide playback session. This lets a newly created
     * activity reattach to an ongoing foreground-service playback without parsing and configuring
     * the same book again (which would stop the current audio request).
     */
    @Synchronized
    fun bookSnapshot(): BookContent? {
        if (bookId.isBlank() || chunks.isEmpty()) return null
        return BookContent(
            uri = bookId,
            title = title,
            pageCount = pageCount.coerceAtLeast(inferredPageCount(chunks)),
            chunks = chunks.toList(),
            pagesWithoutText = pagesWithoutText,
        )
    }

    /** Updates both text and book metadata as progressive OCR enriches the active session. */
    @Synchronized
    fun replaceBook(updated: BookContent) {
        if (updated.uri != bookId || updated.chunks.isEmpty()) return
        title = updated.title
        pageCount = updated.pageCount.coerceAtLeast(inferredPageCount(updated.chunks))
        pagesWithoutText = updated.pagesWithoutText.coerceIn(0, pageCount)
        replaceChunks(updated.chunks)
    }

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

    private fun inferredPageCount(content: List<SpeechChunk>): Int =
        (content.maxOfOrNull(SpeechChunk::pageIndex) ?: -1) + 1
}
