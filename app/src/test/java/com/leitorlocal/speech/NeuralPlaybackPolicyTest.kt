package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class NeuralPlaybackPolicyTest {
    @Test
    fun keepsAnExtraChunkReadyForKokoroOnly() {
        val kokoro = requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_KOKORO_DORA_VOICE_NAME))
        val piper = requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME))

        assertEquals(2, neuralPrebufferChunkCount(kokoro))
        assertEquals(1, neuralPrebufferChunkCount(piper))
    }
}
