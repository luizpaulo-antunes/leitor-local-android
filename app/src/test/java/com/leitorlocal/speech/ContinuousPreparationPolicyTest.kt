package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuousPreparationPolicyTest {
    @Test
    fun preparesOneMinuteSampleBeforeCalculatingLongReserve() {
        val target = continuousPreparationTarget(
            preparedDurationMillis = 20_000L,
            estimatedRemainingAudioMillis = 10L * 60L * 60_000L,
            realTimeFactor = 2f,
            minimumReserveMillis = 10L * 60_000L,
        )

        assertEquals(60_000L, target.targetDurationMillis)
        assertEquals(80_000L, target.estimatedWaitMillis)
    }

    @Test
    fun fastGeneratorOnlyNeedsConfiguredRollingReserve() {
        val target = continuousPreparationTarget(
            preparedDurationMillis = 60_000L,
            estimatedRemainingAudioMillis = 20L * 60L * 60_000L,
            realTimeFactor = 0.5f,
            minimumReserveMillis = 10L * 60_000L,
        )

        assertEquals(10L * 60_000L, target.targetDurationMillis)
        assertEquals(270_000L, target.estimatedWaitMillis)
    }

    @Test
    fun slowGeneratorCalculatesReserveNeededToFinishWithoutSynthesisPause() {
        val target = continuousPreparationTarget(
            preparedDurationMillis = 60_000L,
            estimatedRemainingAudioMillis = 60L * 60_000L,
            realTimeFactor = 2f,
            minimumReserveMillis = 10L * 60_000L,
        )

        assertEquals(2_100_000L, target.targetDurationMillis)
        assertEquals(4_080_000L, target.estimatedWaitMillis)
    }

    @Test
    fun estimatesBookDurationFromPreparedCharacters() {
        assertEquals(
            600_000L,
            estimateAudioDurationMillis(
                processedCharacters = 1_000L,
                processedAudioMillis = 60_000L,
                totalCharacters = 10_000L,
            ),
        )
        assertNull(estimateAudioDurationMillis(0L, 60_000L, 10_000L))
    }

    @Test
    fun formatsDurationsForReaderInsteadOfShowingChunkCounts() {
        assertEquals("menos de 1min", formatPreparationDuration(59_999L))
        assertEquals("12min", formatPreparationDuration(12L * 60_000L))
        assertEquals("2h 5min", formatPreparationDuration(125L * 60_000L))
    }

    @Test
    fun formatsPreparationNumbersWithoutHidingSeconds() {
        assertEquals("45 s", formatPreparationDurationPrecise(45_999L))
        assertEquals("1 min 5 s", formatPreparationDurationPrecise(65_000L))
        assertEquals("1 h 55 min", formatPreparationDurationPrecise(115L * 60_000L))
    }

    @Test
    fun explainsGenerationPaceInAudioProducedPerMinute() {
        assertEquals(
            "1 min de processamento gera 30 s de áudio",
            formatGenerationPace(realTimeFactor = 2f),
        )
        assertEquals(
            "1 min de processamento gera 2 min de áudio",
            formatGenerationPace(realTimeFactor = 0.5f),
        )
    }
}
