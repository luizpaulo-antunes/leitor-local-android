package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class NeuralPlaybackPolicyTest {
    @Test
    fun reservesEnoughKokoroAudioBeforePlayback() {
        val kokoro = requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_KOKORO_DORA_VOICE_NAME))
        val policy = neuralPrebufferPolicy(kokoro)

        assertEquals(8, policy.minimumChunks)
        assertEquals(90_000L, policy.minimumDurationMillis)
        assertEquals(false, hasRequiredNeuralPrebuffer(policy, 7, 120_000L))
        assertEquals(false, hasRequiredNeuralPrebuffer(policy, 8, 89_999L))
        assertEquals(true, hasRequiredNeuralPrebuffer(policy, 8, 90_000L))
    }

    @Test
    fun startsPiperAsSoonAsItsFirstChunkIsReady() {
        val piper = requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME))
        val policy = neuralPrebufferPolicy(piper)

        assertEquals(1, policy.minimumChunks)
        assertEquals(0L, policy.minimumDurationMillis)
        assertEquals(true, hasRequiredNeuralPrebuffer(policy, 1, 0L))
    }
}
