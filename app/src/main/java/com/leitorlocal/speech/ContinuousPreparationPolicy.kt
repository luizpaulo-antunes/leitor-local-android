package com.leitorlocal.speech

import java.util.Locale
import kotlin.math.ceil

internal data class ContinuousPreparationTarget(
    val targetDurationMillis: Long,
    val estimatedWaitMillis: Long?,
)

internal fun continuousPreparationTarget(
    preparedDurationMillis: Long,
    estimatedRemainingAudioMillis: Long?,
    realTimeFactor: Float?,
    minimumReserveMillis: Long,
    sampleDurationMillis: Long = PREPARATION_SAMPLE_MILLIS,
): ContinuousPreparationTarget {
    val prepared = preparedDurationMillis.coerceAtLeast(0L)
    if (prepared < sampleDurationMillis) {
        return ContinuousPreparationTarget(
            targetDurationMillis = sampleDurationMillis,
            estimatedWaitMillis = realTimeFactor?.takeIf { it > 0f }?.let { factor ->
                ((sampleDurationMillis - prepared) * factor).toLong().coerceAtLeast(0L)
            },
        )
    }

    val estimatedTotal = estimatedRemainingAudioMillis
        ?.coerceAtLeast(prepared)
    val measuredFactor = realTimeFactor?.takeIf { it.isFinite() && it > 0f }
    val safeFactor = measuredFactor?.toDouble()?.times(PREPARATION_RTF_SAFETY_FACTOR)
    // Preparing enough audio to guarantee uninterrupted playback until the end of a long book
    // turns a slow local model into an hours-long blocking task. Size the buffer for a useful
    // listening session instead. Playback keeps producing the following chunks from this head
    // start, and the hard cap keeps "start reading" independent from book length.
    val sessionReserve = if (safeFactor != null && safeFactor > 1.0) {
        ceil(PREPARATION_LISTENING_SESSION_MILLIS * (1.0 - 1.0 / safeFactor)).toLong()
    } else {
        0L
    }
    val target = maxOf(minimumReserveMillis, sessionReserve)
        .coerceAtMost(PREPARATION_MAX_RESERVE_MILLIS)
        .let { reserve -> estimatedTotal?.let(reserve::coerceAtMost) ?: reserve }
        .coerceAtLeast(sampleDurationMillis)
    val remaining = (target - prepared).coerceAtLeast(0L)
    return ContinuousPreparationTarget(
        targetDurationMillis = target,
        estimatedWaitMillis = measuredFactor?.let { (remaining * it).toLong().coerceAtLeast(0L) },
    )
}

internal fun estimateAudioDurationMillis(
    processedCharacters: Long,
    processedAudioMillis: Long,
    totalCharacters: Long,
): Long? {
    if (processedCharacters <= 0L || processedAudioMillis <= 0L || totalCharacters <= 0L) return null
    return (processedAudioMillis.toDouble() / processedCharacters * totalCharacters)
        .toLong()
        .coerceAtLeast(processedAudioMillis)
}

internal fun preparationMinimumReserveMillis(voice: EmbeddedNeuralVoice): Long =
    if (voice.engine == EmbeddedNeuralEngine.KOKORO) {
        KOKORO_CONTINUOUS_RESERVE_MILLIS
    } else {
        PIPER_CONTINUOUS_RESERVE_MILLIS
    }

internal fun formatPreparationDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}min"
        hours > 0L -> "${hours}h"
        totalMinutes > 0L -> "${totalMinutes}min"
        else -> "menos de 1min"
    }
}

internal fun formatPreparationDurationPrecise(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L && minutes > 0L -> "$hours h $minutes min"
        hours > 0L -> "$hours h"
        minutes > 0L && seconds > 0L -> "$minutes min $seconds s"
        minutes > 0L -> "$minutes min"
        totalSeconds > 0L -> "$totalSeconds s"
        else -> "0 s"
    }
}

internal fun formatGenerationPace(realTimeFactor: Float): String {
    if (!realTimeFactor.isFinite() || realTimeFactor <= 0f) return "Medindo velocidade…"
    val generatedPerMinuteMillis = (60_000f / realTimeFactor).toLong().coerceAtLeast(1_000L)
    return "1 min de processamento gera ${formatPreparationDurationPrecise(generatedPerMinuteMillis)} de áudio"
}

internal fun formatRealTimeFactor(realTimeFactor: Float): String {
    if (!realTimeFactor.isFinite() || realTimeFactor <= 0f) return "medindo"
    val relativeSpeed = 1f / realTimeFactor
    return String.format(Locale.US, "%.1f× o tempo real", relativeSpeed)
}

internal const val PREPARATION_SAMPLE_MILLIS = 60_000L
private const val KOKORO_CONTINUOUS_RESERVE_MILLIS = 10L * 60_000L
private const val PIPER_CONTINUOUS_RESERVE_MILLIS = 2L * 60_000L
private const val PREPARATION_LISTENING_SESSION_MILLIS = 30L * 60_000L
private const val PREPARATION_MAX_RESERVE_MILLIS = 15L * 60_000L
private const val PREPARATION_RTF_SAFETY_FACTOR = 1.10
