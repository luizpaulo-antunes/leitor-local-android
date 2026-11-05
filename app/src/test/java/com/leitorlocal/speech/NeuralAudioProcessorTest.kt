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

        val improved = NeuralAudioProcessor.finish(NeuralAudioProcessor.prepare(quiet))

        assertTrue(improved.maxOf(::abs) > quiet.maxOf(::abs))
        assertTrue(improved.maxOf(::abs) <= 0.92f)
    }

    @Test
    fun preservesEmptyAudio() {
        assertTrue(NeuralAudioProcessor.prepare(floatArrayOf()).isEmpty())
        assertTrue(NeuralAudioProcessor.finish(floatArrayOf()).isEmpty())
    }

    @Test
    fun resamplesAudioToTheRequestedRate() {
        val source = FloatArray(22_050) { it.toFloat() / 22_050 }

        val resampled = NeuralAudioProcessor.resample(source, 22_050, 16_000)

        assertTrue(resampled.size == 16_000)
        assertTrue(abs(resampled.last() - source.last()) < 0.001f)
    }
}
