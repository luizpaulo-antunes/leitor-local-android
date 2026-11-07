package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedNeuralVoicesTest {
    @Test
    fun exposesPiperAndKokoroVoices() {
        assertEquals(5, EMBEDDED_NEURAL_VOICES.size)
        assertNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME))
        assertNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_DII_VOICE_NAME))
        assertEquals(
            setOf(
                EmbeddedNeuralEngine.PIPER_VITS,
                EmbeddedNeuralEngine.KOKORO,
            ),
            EMBEDDED_NEURAL_VOICES.map { it.engine }.toSet(),
        )
        assertEquals(3, EMBEDDED_NEURAL_VOICES.count { it.engine == EmbeddedNeuralEngine.KOKORO })
        assertTrue(
            EMBEDDED_NEURAL_VOICES
                .filter { it.engine == EmbeddedNeuralEngine.KOKORO }
                .all { it.modelDirectory == "kokoro-multi-lang-v1_0" },
        )
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
}
