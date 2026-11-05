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

    @Test
    fun trimsLongOuterSilenceAndAddsOnlyTheRequestedPause() {
        val source = FloatArray(16_000)
        for (index in 4_000 until 12_000) {
            source[index] = if (index % 2 == 0) 0.1f else -0.1f
        }

        val improved = NeuralAudioProcessor.finish(
            samples = source,
            sampleRate = 16_000,
            trailingSilenceMs = 40,
        )

        assertTrue(improved.size in 9_000..9_300)
        assertTrue(improved.takeLast(640).all { it == 0f })
    }

    @Test
    fun softLimiterContainsTransientPeaks() {
        val source = FloatArray(16_000) { index -> if (index % 2 == 0) 0.1f else -0.1f }
        source[source.size / 2] = 1f

        val improved = NeuralAudioProcessor.finish(
            samples = source,
            sampleRate = 16_000,
            profile = NeuralAudioProcessingProfile(
                targetActiveRms = 0.3f,
                maxGain = 2f,
            ),
        )
        val peak = improved.maxOf(::abs)

        assertTrue(peak > 0.8f)
        assertTrue(peak <= 0.92f)
    }
}
