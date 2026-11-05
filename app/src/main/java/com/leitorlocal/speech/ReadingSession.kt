package com.leitorlocal.speech

import com.leitorlocal.model.PlaybackState
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.SpeechChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReadingSession {
    private var chunks: List<SpeechChunk> = emptyList()
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    var title: String = "Leitor Local"
        private set
    var speechRate: Float = 1f
        private set
    var voiceName: String? = null
        private set

    @Synchronized
    fun configure(
        title: String,
        chunks: List<SpeechChunk>,
        startIndex: Int,
        speechRate: Float,
        voiceName: String?,
    ) {
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

    fun hasContent(): Boolean = chunks.isNotEmpty()

    @Synchronized
    fun setStatus(status: PlaybackStatus, error: String? = null) {
        mutableState.value = mutableState.value.copy(status = status, error = error)
    }

    @Synchronized
    fun moveBy(offset: Int): SpeechChunk? {
        if (chunks.isEmpty()) return null
        val next = (mutableState.value.currentChunkIndex + offset).coerceIn(0, chunks.lastIndex)
        mutableState.value = mutableState.value.copy(currentChunkIndex = next, error = null)
        return chunks[next]
    }

    @Synchronized
    fun setIndex(index: Int) {
        if (chunks.isEmpty()) return
        mutableState.value = mutableState.value.copy(
            currentChunkIndex = index.coerceIn(0, chunks.lastIndex),
            error = null,
        )
    }

    fun isAtEnd(): Boolean = chunks.isEmpty() || mutableState.value.currentChunkIndex >= chunks.lastIndex
}
