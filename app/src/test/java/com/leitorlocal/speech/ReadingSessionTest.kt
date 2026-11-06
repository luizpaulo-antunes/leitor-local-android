package com.leitorlocal.speech

import com.leitorlocal.model.BookContent
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReadingSessionTest {
    @Test
    fun activeBookSnapshotRestoresReaderWithoutResettingPlayback() {
        val first = SpeechChunk(pageIndex = 3, text = "Trecho em reprodução.")
        val current = SpeechChunk(pageIndex = 4, text = "Palavra atual do livro.")
        ReadingSession.configure(
            bookId = "content://teste/ativo.pdf",
            title = "Livro ativo",
            chunks = listOf(first, current),
            startIndex = 1,
            speechRate = 1.2f,
            voiceName = EMBEDDED_NEURAL_MIRO_VOICE_NAME,
            pageCount = 120,
            pagesWithoutText = 7,
        )
        ReadingSession.setStatus(PlaybackStatus.PLAYING)
        ReadingSession.setWordRange(chunkIndex = 1, start = 0, end = 7)

        val restored = ReadingSession.bookSnapshot()
        assertNotNull(restored)
        restored as BookContent

        assertEquals("content://teste/ativo.pdf", restored.uri)
        assertEquals("Livro ativo", restored.title)
        assertEquals(120, restored.pageCount)
        assertEquals(7, restored.pagesWithoutText)
        assertEquals(listOf(first, current), restored.chunks)
        assertEquals(PlaybackStatus.PLAYING, ReadingSession.state.value.status)
        assertEquals(1, ReadingSession.state.value.currentChunkIndex)
        assertEquals(0, ReadingSession.state.value.currentWordStart)
        assertEquals(7, ReadingSession.state.value.currentWordEnd)
    }

    @Test
    fun progressiveBookReplacementRefreshesSnapshotWithoutPausingAudio() {
        val current = SpeechChunk(pageIndex = 2, text = "Trecho atual.")
        ReadingSession.configure(
            bookId = "content://teste/ocr.pdf",
            title = "Antes do OCR",
            chunks = listOf(current),
            startIndex = 0,
            speechRate = 1f,
            voiceName = EMBEDDED_NEURAL_MIRO_VOICE_NAME,
            pageCount = 10,
            pagesWithoutText = 4,
        )
        ReadingSession.setStatus(PlaybackStatus.PLAYING)

        ReadingSession.replaceBook(
            BookContent(
                uri = "content://teste/ocr.pdf",
                title = "Depois do OCR",
                pageCount = 10,
                chunks = listOf(
                    SpeechChunk(pageIndex = 1, text = "Página reconhecida."),
                    current,
                ),
                pagesWithoutText = 3,
            ),
        )

        val restored = ReadingSession.bookSnapshot()
        assertNotNull(restored)
        restored as BookContent
        assertEquals("Depois do OCR", restored.title)
        assertEquals(3, restored.pagesWithoutText)
        assertEquals(1, ReadingSession.state.value.currentChunkIndex)
        assertEquals(PlaybackStatus.PLAYING, ReadingSession.state.value.status)
    }

    @Test
    fun progressiveOcrKeepsCurrentChunkAndWordWhenEarlierPageIsInserted() {
        val first = SpeechChunk(pageIndex = 0, text = "Primeira página.")
        val current = SpeechChunk(pageIndex = 2, text = "Palavra atual da terceira página.")
        ReadingSession.configure(
            bookId = "content://teste/livro.pdf",
            title = "Teste",
            chunks = listOf(first, current),
            startIndex = 1,
            speechRate = 1f,
            voiceName = EMBEDDED_NEURAL_MIRO_VOICE_NAME,
        )
        ReadingSession.setWordRange(chunkIndex = 1, start = 0, end = 7)

        ReadingSession.replaceChunks(
            listOf(
                first,
                SpeechChunk(pageIndex = 1, text = "Página reconhecida pelo OCR."),
                current,
            ),
        )

        assertEquals(2, ReadingSession.state.value.currentChunkIndex)
        assertEquals(current, ReadingSession.currentChunk())
        assertEquals(0, ReadingSession.state.value.currentWordStart)
        assertEquals(7, ReadingSession.state.value.currentWordEnd)
    }
}
