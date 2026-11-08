package com.leitorlocal.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralAudioQualityTest {
    @Test
    fun acceptsFiniteSpeechSignal() {
        val samples = FloatArray(24_000) { index ->
            if (index % 2 == 0) 0.08f else -0.08f
        }

        assertTrue(validateNeuralAudio(NeuralAudio(samples, 24_000)).isValid)
    }

    @Test
    fun rejectsSilentAndNonFiniteAudio() {
        assertFalse(validateNeuralAudio(NeuralAudio(FloatArray(24_000), 24_000)).isValid)
        assertFalse(
            validateNeuralAudio(
                NeuralAudio(floatArrayOf(0.1f, Float.NaN, -0.1f), 24_000),
            ).isValid,
        )
    }

    @Test
    fun rejectsHeavilyClippedAudio() {
        val samples = FloatArray(1_000) { if (it < 500) 1f else -1f }

        assertFalse(validateNeuralAudio(NeuralAudio(samples, 24_000)).isValid)
    }
}
