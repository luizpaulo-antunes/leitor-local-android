package com.leitorlocal.pdf

import com.leitorlocal.model.BookContent
import com.leitorlocal.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressivePdfOcrTest {
    @Test
    fun targetsOnlyMissingPagesInCurrentWindow() {
        val book = book(
            pageCount = 6,
            chunks = listOf(
                SpeechChunk(pageIndex = 0, text = "Página zero."),
                SpeechChunk(pageIndex = 2, text = "Página dois."),
                SpeechChunk(pageIndex = 5, text = "Página cinco."),
            ),
        )

        val targets = ProgressiveOcrPlanner.targetPages(book, startPage = 0, pagesAhead = 3)

        assertEquals(listOf(1, 3), targets)
    }

    @Test
    fun mergesRecognizedPageInReadingOrderAndUpdatesCount() {
        val book = book(
            pageCount = 4,
            chunks = listOf(
                SpeechChunk(pageIndex = 0, text = "Primeira página."),
                SpeechChunk(pageIndex = 2, text = "Terceira página."),
            ),
        )

        val updated = ProgressiveOcrPlanner.merge(
            book,
            mapOf(1 to "Este texto veio do reconhecimento óptico offline."),
        )

        assertEquals(listOf(0, 1, 2), updated.chunks.map(SpeechChunk::pageIndex).distinct())
        assertEquals(1, updated.pagesWithoutText)
        assertTrue(updated.chunks.any { it.pageIndex == 1 && "reconhecimento" in it.text })
    }

    @Test
    fun rejectsPageNumbersButAcceptsReadablePortuguese() {
        assertFalse(OcrTextQuality.isUsable("17"))
        assertFalse(OcrTextQuality.isUsable("-- 17 --"))
        assertTrue(OcrTextQuality.isUsable("Capítulo primeiro"))
    }

    private fun book(pageCount: Int, chunks: List<SpeechChunk>) = BookContent(
        uri = "content://livro/teste.pdf",
        title = "Teste",
        pageCount = pageCount,
        chunks = chunks,
        pagesWithoutText = pageCount - chunks.map(SpeechChunk::pageIndex).distinct().size,
    )
}
