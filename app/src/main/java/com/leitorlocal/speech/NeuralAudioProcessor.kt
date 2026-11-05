package com.leitorlocal.speech

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

object NeuralAudioProcessor {
    fun prepare(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val output = FloatArray(samples.size)
        var previousInput = 0f
        var highPassed = 0f

        samples.forEachIndexed { index, input ->
            highPassed = input - previousInput + DC_BLOCK_FACTOR * highPassed
            previousInput = input
            output[index] = highPassed
        }
        return output
    }

    fun finish(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val output = samples.copyOf()
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
            val faded = when {
                index < FADE_SAMPLES -> index.toFloat() / FADE_SAMPLES
                index >= output.size - FADE_SAMPLES -> (output.size - index - 1).toFloat() / FADE_SAMPLES
                else -> 1f
            }.coerceIn(0f, 1f)
            output[index] = (output[index] * gain * faded).coerceIn(-TARGET_PEAK, TARGET_PEAK)
        }
        return output
    }

    fun resample(samples: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (samples.isEmpty() || sourceRate <= 0 || targetRate <= 0 || sourceRate == targetRate) {
            return samples
        }
        val outputSize = ((samples.size.toLong() * targetRate) / sourceRate)
            .coerceAtLeast(1)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val ratio = sourceRate.toDouble() / targetRate
        return FloatArray(outputSize) { outputIndex ->
            val sourcePosition = outputIndex * ratio
            val lowerIndex = floor(sourcePosition).toInt().coerceIn(0, samples.lastIndex)
            val upperIndex = (lowerIndex + 1).coerceAtMost(samples.lastIndex)
            val fraction = (sourcePosition - lowerIndex).toFloat()
            samples[lowerIndex] + (samples[upperIndex] - samples[lowerIndex]) * fraction
        }
    }

    private const val DC_BLOCK_FACTOR = 0.995f
    private const val ACTIVE_SPEECH_FLOOR = 0.01f
    private const val TARGET_ACTIVE_RMS = 0.14f
    private const val TARGET_PEAK = 0.92f
    private const val MIN_GAIN = 0.65f
    private const val MAX_GAIN = 1.55f
    private const val FADE_SAMPLES = 96
}
