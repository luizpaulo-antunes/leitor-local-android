package com.leitorlocal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWorkGuardsTest {
    @Test
    fun `only the newest book request may publish results`() {
        val tracker = LoadRequestTracker()

        val first = tracker.begin()
        val second = tracker.begin()

        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))

        tracker.invalidate()
        assertFalse(tracker.isCurrent(second))
    }

    @Test
    fun `word updates in the same chunk do not persist again`() {
        val tracker = PlaybackPositionTracker()

        assertTrue(tracker.shouldPersist("book-a", 7))
        repeat(20) {
            assertFalse(tracker.shouldPersist("book-a", 7))
        }
        assertTrue(tracker.shouldPersist("book-a", 8))
    }

    @Test
    fun `the same chunk in another book is a distinct position`() {
        val tracker = PlaybackPositionTracker()

        tracker.markPersisted("book-a", 3)

        assertFalse(tracker.shouldPersist("book-a", 3))
        assertTrue(tracker.shouldPersist("book-b", 3))
    }
}
