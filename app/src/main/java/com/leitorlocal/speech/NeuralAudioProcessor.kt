package com.leitorlocal.speech

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sqrt

data class NeuralAudioProcessingProfile(
    val targetActiveRms: Float = 0.145f,
    val targetPeak: Float = 0.92f,
    val limiterKnee: Float = 0.80f,
    val minGain: Float = 0.65f,
    val maxGain: Float = 1.60f,
)

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

    fun finish(
        samples: FloatArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        profile: NeuralAudioProcessingProfile = NeuralAudioProcessingProfile(),
        trailingSilenceMs: Int = 0,
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val trimmed = trimOuterSilence(samples, sampleRate)
        if (trimmed.isEmpty()) return trimmed
        val output = trimmed.copyOf()
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
        val rmsGain = profile.targetActiveRms / activeRms
        val gain = minOf(profile.maxGain, rmsGain).coerceAtLeast(profile.minGain)
        val maximumFadeSamples = (output.size / 2).coerceAtLeast(1)
        val fadeSamples = (sampleRate * FADE_DURATION_MS / 1_000)
            .coerceIn(1, maximumFadeSamples)

        output.indices.forEach { index ->
            val faded = when {
                index < fadeSamples -> index.toFloat() / fadeSamples
                index >= output.size - fadeSamples ->
                    (output.size - index - 1).toFloat() / fadeSamples
                else -> 1f
            }.coerceIn(0f, 1f)
            output[index] = softLimit(
                value = output[index] * gain * faded,
                knee = profile.limiterKnee,
                ceiling = profile.targetPeak,
            )
        }

        val paddingSamples = (sampleRate.toLong() * trailingSilenceMs.coerceIn(0, MAX_PAUSE_MS) / 1_000)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        return if (paddingSamples == 0) output else output.copyOf(output.size + paddingSamples)
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

    private fun trimOuterSilence(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0) return samples
        val peak = samples.maxOf { abs(it) }
        if (peak <= 0f) return samples
        val threshold = (peak * RELATIVE_SILENCE_FLOOR)
            .coerceIn(ABSOLUTE_SILENCE_FLOOR, MAX_SILENCE_FLOOR)
        val firstActive = samples.indexOfFirst { abs(it) >= threshold }
        val lastActive = samples.indexOfLast { abs(it) >= threshold }
        if (firstActive < 0 || lastActive < firstActive) return samples

        val leadingGuard = sampleRate * LEADING_GUARD_MS / 1_000
        val trailingGuard = sampleRate * TRAILING_GUARD_MS / 1_000
        val start = (firstActive - leadingGuard).coerceAtLeast(0)
        val endExclusive = (lastActive + trailingGuard + 1).coerceAtMost(samples.size)
        return if (start == 0 && endExclusive == samples.size) {
            samples
        } else {
            samples.copyOfRange(start, endExclusive)
        }
    }

    private fun softLimit(value: Float, knee: Float, ceiling: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= knee) return value
        val range = (ceiling - knee).coerceAtLeast(0.01f)
        val exponent = -((magnitude - knee) / range).toDouble()
        val compressed = knee + range * (1f - exp(exponent).toFloat())
        return sign(value) * compressed.coerceAtMost(ceiling)
    }

    private const val DC_BLOCK_FACTOR = 0.995f
    private const val ACTIVE_SPEECH_FLOOR = 0.01f
    private const val ABSOLUTE_SILENCE_FLOOR = 0.0015f
    private const val MAX_SILENCE_FLOOR = 0.008f
    private const val RELATIVE_SILENCE_FLOOR = 0.015f
    private const val LEADING_GUARD_MS = 12
    private const val TRAILING_GUARD_MS = 20
    private const val FADE_DURATION_MS = 6
    private const val MAX_PAUSE_MS = 180
    private const val DEFAULT_SAMPLE_RATE = 16_000
}
