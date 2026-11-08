package com.leitorlocal.speech

import kotlin.math.abs
import kotlin.math.sqrt

internal data class NeuralAudioQuality(
    val isValid: Boolean,
    val message: String = "",
)

internal fun validateNeuralAudio(audio: NeuralAudio): NeuralAudioQuality {
    if (audio.sampleRate !in 8_000..96_000 || audio.samples.isEmpty()) {
        return NeuralAudioQuality(false, "A voz gerou um áudio vazio ou com taxa inválida.")
    }
    var squareSum = 0.0
    var clippedSamples = 0
    audio.samples.forEach { sample ->
        if (!sample.isFinite()) {
            return NeuralAudioQuality(false, "A voz gerou amostras de áudio inválidas.")
        }
        val absolute = abs(sample)
        squareSum += sample.toDouble() * sample
        if (absolute >= 0.999f) clippedSamples += 1
    }
    val rms = sqrt(squareSum / audio.samples.size)
    if (rms < MINIMUM_RMS) {
        return NeuralAudioQuality(false, "A voz gerou apenas silêncio neste trecho.")
    }
    if (clippedSamples.toDouble() / audio.samples.size > MAXIMUM_CLIPPED_RATIO) {
        return NeuralAudioQuality(false, "A voz gerou áudio saturado neste trecho.")
    }
    return NeuralAudioQuality(true)
}

private const val MINIMUM_RMS = 0.0001
private const val MAXIMUM_CLIPPED_RATIO = 0.35
