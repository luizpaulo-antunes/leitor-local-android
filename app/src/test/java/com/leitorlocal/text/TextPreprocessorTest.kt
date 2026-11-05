package com.leitorlocal.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPreprocessorTest {
    @Test
    fun joinsWrappedLinesAndHyphenatedWords() {
        val raw = """
            Esta é uma frase que foi
            quebrada pelo PDF e contém uma pala-
            vra dividida.

            Este é outro parágrafo.
        """.trimIndent()

        assertEquals(
            "Esta é uma frase que foi quebrada pelo PDF e contém uma palavra dividida.\n\n" +
                "Este é outro parágrafo.",
            TextPreprocessor.cleanPage(raw),
        )
    }

    @Test
    fun producesChunksSmallerThanTtsLimit() {
        val text = (1..80).joinToString(" ") { "Esta é a frase número $it." }
        val chunks = TextPreprocessor.splitIntoChunks(text, targetLength = 180)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 700 })
        assertTrue(chunks.joinToString(" ").contains("frase número 80"))
    }

    @Test
    fun removesRepeatedHeadersFootersAndPageNumbers() {
        val pages = listOf(
            "Prefácio 5\nPrimeiro parágrafo do livro.\n5",
            "Prefácio 6\nSegundo parágrafo do livro.\n6",
            "Prefácio 7\nTerceiro parágrafo do livro.\n7",
        )

        val cleaned = TextPreprocessor.cleanDocumentPages(pages)

        assertEquals("Primeiro parágrafo do livro.", cleaned[0])
        assertEquals("Segundo parágrafo do livro.", cleaned[1])
        assertEquals("Terceiro parágrafo do livro.", cleaned[2])
    }

    @Test
    fun preparesHeadingsAndDashesForMoreNaturalSpeech() {
        assertEquals("Capítulo primeiro.", TextPreprocessor.prepareForSpeech("Capítulo primeiro"))
        assertEquals(
            "Ele parou, respirou fundo.",
            TextPreprocessor.prepareForSpeech("Ele parou — respirou fundo."),
        )
    }
}
