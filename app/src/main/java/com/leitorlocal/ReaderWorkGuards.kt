package com.leitorlocal

import java.util.concurrent.atomic.AtomicLong

/** Invalidates asynchronous book-opening callbacks from older requests. */
internal class LoadRequestTracker {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun isCurrent(token: Long): Boolean = generation.get() == token
}

/** Coalesces word-level playback updates into one persistence event per book/chunk. */
internal class PlaybackPositionTracker {
    private var lastBookId: String? = null
    private var lastChunkIndex: Int = -1

    @Synchronized
    fun shouldPersist(bookId: String, chunkIndex: Int): Boolean {
        if (bookId == lastBookId && chunkIndex == lastChunkIndex) return false
        lastBookId = bookId
        lastChunkIndex = chunkIndex
        return true
    }

    @Synchronized
    fun markPersisted(bookId: String, chunkIndex: Int) {
        lastBookId = bookId
        lastChunkIndex = chunkIndex
    }
}
