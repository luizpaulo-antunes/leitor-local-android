package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EmbeddedNeuralVoicesTest {
    @Test
    fun exposesTwoDistinctBrazilianNeuralVoices() {
        assertEquals(2, EMBEDDED_NEURAL_VOICES.size)
        assertNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME))
        assertNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_DII_VOICE_NAME))
        assertNotEquals(
            embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME)?.modelFile,
            embeddedNeuralVoice(EMBEDDED_NEURAL_DII_VOICE_NAME)?.modelFile,
        )
    }
}
