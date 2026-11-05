package com.leitorlocal.speech

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

object NeuralAudioProcessor {
    fun improve(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0) return samples

        val output = FloatArray(samples.size)
        val cutoff = minOf(7_200f, sampleRate * 0.38f)
        val lowPassAlpha = (1.0 - exp(-2.0 * PI * cutoff / sampleRate)).toFloat()
        var previousInput = 0f
        var highPassed = 0f
        var lowPassed = 0f
        var lowPassedSecondStage = 0f

        samples.forEachIndexed { index, input ->
            highPassed = input - previousInput + DC_BLOCK_FACTOR * highPassed
            previousInput = input
            lowPassed += lowPassAlpha * (highPassed - lowPassed)
            lowPassedSecondStage += lowPassAlpha * (lowPassed - lowPassedSecondStage)
            output[index] = lowPassedSecondStage
        }

        var peak = 0f
        var activeEnergy = 0.0
        var activeSamples = 0
        output.forEach { sample ->
            val magnitude = abs(sample)
            peak = maxOf(peak, magnitude)
            if (magnitude >= ACTIVE_SPEECH_FLOOR) {
                activeEnergy += sample * sample
                activeSamples += 1
            }
        }
        if (peak <= 0f || activeSamples == 0) return output

        val activeRms = sqrt(activeEnergy / activeSamples).toFloat()
        val rmsGain = TARGET_ACTIVE_RMS / activeRms
        val peakGain = TARGET_PEAK / peak
        val gain = minOf(MAX_GAIN, rmsGain, peakGain).coerceAtLeast(MIN_GAIN)

        output.indices.forEach { index ->
            output[index] = (output[index] * gain).coerceIn(-TARGET_PEAK, TARGET_PEAK)
        }
        return output
    }

    private const val DC_BLOCK_FACTOR = 0.995f
    private const val ACTIVE_SPEECH_FLOOR = 0.008f
    private const val TARGET_ACTIVE_RMS = 0.16f
    private const val TARGET_PEAK = 0.94f
    private const val MIN_GAIN = 0.5f
    private const val MAX_GAIN = 2.4f
}
