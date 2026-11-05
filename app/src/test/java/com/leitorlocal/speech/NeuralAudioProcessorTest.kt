package com.leitorlocal.speech

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NeuralAudioProcessorTest {
    @Test
    fun raisesQuietSpeechWithoutClipping() {
        val quiet = FloatArray(24_000) { index ->
            if (index % 40 < 20) 0.04f else -0.04f
        }

        val improved = NeuralAudioProcessor.improve(quiet, sampleRate = 24_000)

        assertTrue(improved.maxOf(::abs) > quiet.maxOf(::abs))
        assertTrue(improved.maxOf(::abs) <= 0.94f)
    }

    @Test
    fun preservesEmptyAudio() {
        assertTrue(NeuralAudioProcessor.improve(floatArrayOf(), 22_050).isEmpty())
    }
}
