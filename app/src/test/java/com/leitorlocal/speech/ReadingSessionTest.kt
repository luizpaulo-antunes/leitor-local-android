package com.leitorlocal.speech

import com.leitorlocal.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionTest {
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
