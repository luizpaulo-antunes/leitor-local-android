package com.leitorlocal.speech

import kotlin.math.roundToLong

data class KaraokeWord(
    val start: Int,
    val end: Int,
    val startProgress: Float,
)

/**
 * Builds a lightweight word timeline for engines that do not expose word timestamps.
 * Offsets use the same UTF-16 indexes as Android TTS and Compose AnnotatedString.
 */
object KaraokeTiming {
    private val wordPattern = Regex("[\\p{L}\\p{N}]+(?:[’'][\\p{L}\\p{N}]+)*")

    fun words(text: String): List<KaraokeWord> {
        val matches = wordPattern.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val weights = matches.mapIndexed { index, match ->
            wordWeight(match.value) + pauseWeight(
                text.substring(
                    startIndex = match.range.last + 1,
                    endIndex = matches.getOrNull(index + 1)?.range?.first ?: text.length,
                ),
            )
        }
        val totalWeight = weights.sum().coerceAtLeast(1f)
        var elapsedWeight = 0f
        return matches.mapIndexed { index, match ->
            KaraokeWord(
                start = match.range.first,
                end = match.range.last + 1,
                startProgress = (elapsedWeight / totalWeight).coerceIn(0f, 1f),
            ).also {
                elapsedWeight += weights[index]
            }
        }
    }

    fun wordAtProgress(words: List<KaraokeWord>, progress: Float): KaraokeWord? {
        if (words.isEmpty()) return null
        val bounded = progress.coerceIn(0f, 1f)
        return words.lastOrNull { it.startProgress <= bounded } ?: words.first()
    }

    fun estimatedDurationMillis(text: String, speechRate: Float): Long {
        val matches = wordPattern.findAll(text).toList()
        if (matches.isEmpty()) return MIN_DURATION_MILLIS
        val totalWeight = matches.mapIndexed { index, match ->
            wordWeight(match.value) + pauseWeight(
                text.substring(
                    startIndex = match.range.last + 1,
                    endIndex = matches.getOrNull(index + 1)?.range?.first ?: text.length,
                ),
            )
        }.sum()
        return (totalWeight * MILLIS_PER_WEIGHT / speechRate.coerceIn(0.6f, 1.6f))
            .roundToLong()
            .coerceAtLeast(MIN_DURATION_MILLIS)
    }

    private fun wordWeight(word: String): Float =
        0.62f + word.count(Char::isLetterOrDigit).coerceAtMost(16) * 0.075f

    private fun pauseWeight(separator: String): Float = when {
        separator.any { it == '.' || it == '!' || it == '?' || it == '…' } -> 0.85f
        separator.any { it == ';' || it == ':' } -> 0.48f
        ',' in separator -> 0.28f
        '\n' in separator -> 0.2f
        else -> 0f
    }

    private const val MILLIS_PER_WEIGHT = 330f
    private const val MIN_DURATION_MILLIS = 450L
}
