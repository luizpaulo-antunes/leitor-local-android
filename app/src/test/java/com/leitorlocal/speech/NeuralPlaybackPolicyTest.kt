package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class NeuralPlaybackPolicyTest {
    @Test
    fun reservesEnoughKokoroAudioBeforePlayback() {
        val kokoro = requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_KOKORO_DORA_VOICE_NAME))
        val policy = neuralPrebufferPolicy(kokoro)

        assertEquals(4, policy.minimumChunks)
        assertEquals(30_000L, policy.minimumDurationMillis)
        assertEquals(false, hasRequiredNeuralPrebuffer(policy, 3, 45_000L))
        assertEquals(false, hasRequiredNeuralPrebuffer(policy, 4, 29_999L))
        assertEquals(true, hasRequiredNeuralPrebuffer(policy, 4, 30_000L))
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
