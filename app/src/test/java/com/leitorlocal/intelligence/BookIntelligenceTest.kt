package com.leitorlocal.intelligence

import com.leitorlocal.model.BookContent
import com.leitorlocal.model.SpeechChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIntelligenceTest {
    private val book = BookContent(
        uri = "book",
        title = "Teste",
        pageCount = 3,
        chunks = listOf(
            SpeechChunk(0, "A bicicleta permaneceu guardada durante todo o inverno."),
            SpeechChunk(1, "O automóvel vermelho percorreu a estrada até a cidade."),
            SpeechChunk(2, "A plantação precisava de chuva para produzir alimentos."),
        ),
        pagesWithoutText = 0,
    )

    @Test
    fun findsRelatedPassageThroughPortugueseSynonym() {
        val result = BookIntelligence(book).search("Onde aparece o carro?")

        assertEquals(2, result.sources.first().pageNumber)
        assertTrue(result.sources.first().excerpt.contains("automóvel"))
    }

    @Test
    fun explanationAlwaysIncludesCurrentPassageAsSource() {
        val result = BookIntelligence(book).explain(2)

        assertEquals(2, result.sources.first().chunkIndex)
        assertEquals(3, result.sources.first().pageNumber)
    }
}
