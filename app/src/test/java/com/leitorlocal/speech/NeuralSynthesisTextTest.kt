package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralSynthesisTextTest {
    @Test
    fun splitsLongTextAtNaturalBoundariesWithinTheModelLimit() {
        val text = "Primeiro conceito com uma explicação suficientemente detalhada. " +
            "Segundo conceito, com outros detalhes importantes para o estudo. " +
            "Por fim, uma conclusão curta."

        val pieces = NeuralSynthesisText.split(text, maxCharacters = 70)

        assertTrue(pieces.size >= 3)
        assertTrue(pieces.all { it.length <= 70 })
        assertEquals(text, pieces.joinToString(" "))
    }

    @Test
    fun hardSplitsAnUnbrokenSequenceWithoutLosingCharacters() {
        val text = "a".repeat(155)

        val pieces = NeuralSynthesisText.split(text, maxCharacters = 50)

        assertTrue(pieces.all { it.length <= 50 })
        assertEquals(text, pieces.joinToString(""))
    }
}
