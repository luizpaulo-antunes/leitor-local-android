package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedNeuralVoicesTest {
    @Test
    fun exposesPiperSupertonicAndKokoroVoices() {
        assertEquals(15, EMBEDDED_NEURAL_VOICES.size)
        assertNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME))
        assertNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_DII_VOICE_NAME))
        assertEquals(
            setOf(
                EmbeddedNeuralEngine.PIPER_VITS,
                EmbeddedNeuralEngine.SUPERTONIC,
                EmbeddedNeuralEngine.KOKORO,
            ),
            EMBEDDED_NEURAL_VOICES.map { it.engine }.toSet(),
        )
        assertEquals(10, EMBEDDED_NEURAL_VOICES.count { it.engine == EmbeddedNeuralEngine.SUPERTONIC })
        assertEquals(3, EMBEDDED_NEURAL_VOICES.count { it.engine == EmbeddedNeuralEngine.KOKORO })
        assertEquals(EMBEDDED_NEURAL_VOICES.size, EMBEDDED_NEURAL_VOICES.map { it.name }.toSet().size)
    }

    @Test
    fun configuresTheDocumentedKokoroPortugueseSpeakers() {
        val speakerIds = listOf(
            EMBEDDED_NEURAL_KOKORO_DORA_VOICE_NAME,
            EMBEDDED_NEURAL_KOKORO_ALEX_VOICE_NAME,
            EMBEDDED_NEURAL_KOKORO_SANTA_VOICE_NAME,
        ).map { name -> requireNotNull(embeddedNeuralVoice(name)).speakerId }

        assertEquals(listOf(42, 43, 44), speakerIds)
        assertTrue(speakerIds.distinct().size == speakerIds.size)
    }

    @Test
    fun mapsSupertonicStylesInTheBundledBinaryOrderAndUsesQualityMode() {
        val supertonic = EMBEDDED_NEURAL_VOICES.filter {
            it.engine == EmbeddedNeuralEngine.SUPERTONIC
        }

        assertEquals((0..9).toList(), supertonic.map { it.speakerId })
        assertTrue(supertonic.take(5).all { "voz feminina" in it.label })
        assertTrue(supertonic.takeLast(5).all { "voz masculina" in it.label })
        assertTrue(supertonic.all { it.generationSteps == 10 })
    }
}
