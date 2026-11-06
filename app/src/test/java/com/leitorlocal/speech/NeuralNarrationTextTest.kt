package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class NeuralNarrationTextTest {
    @Test
    fun preparesBrazilianPortugueseForNaturalNarration() {
        val raw = "Cap. 2 — veja a pág. 17.\n\nFig. 1 mostra 25%."

        val prepared = NeuralNarrationText.prepare(raw)

        assertEquals(
            "Capítulo 2; veja a página 17. Figura 1 mostra 25 por cento.",
            prepared,
        )
    }

    @Test
    fun repairsPdfLineBreaksAndAddsSentenceEnding() {
        val raw = "A retro-\npropagação\ncontinua no n.º 3"

        val prepared = NeuralNarrationText.prepare(raw)

        assertEquals("A retropropagação continua no número 3.", prepared)
    }

    @Test
    fun keepsAControlledPauseBetweenQueuedChunks() {
        assertEquals(70, NeuralNarrationText.pauseAfterMilliseconds("Fim.", speed = 1f))
        assertEquals(34, NeuralNarrationText.pauseAfterMilliseconds("continua,", speed = 1f))
        assertEquals(50, NeuralNarrationText.pauseAfterMilliseconds("Fim.", speed = 1.4f))
    }

    @Test
    fun narratesSeparatedHeadingWithoutDuplicatingPunctuation() {
        assertEquals(
            "Um título. Primeiro parágrafo.",
            NeuralNarrationText.prepare("Um título.\n\nPrimeiro parágrafo."),
        )
    }
}
