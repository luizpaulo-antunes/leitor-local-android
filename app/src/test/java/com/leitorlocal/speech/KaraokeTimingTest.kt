package com.leitorlocal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTimingTest {
    @Test
    fun keepsUtf16OffsetsForPortugueseWords() {
        val text = "Atenção: você também está ouvindo d'água."

        val words = KaraokeTiming.words(text)

        assertEquals(
            listOf("Atenção", "você", "também", "está", "ouvindo", "d'água"),
            words.map { text.substring(it.start, it.end) },
        )
    }

    @Test
    fun movesFromFirstToLastWordAsAudioProgresses() {
        val text = "Primeira palavra, segunda palavra e última."
        val words = KaraokeTiming.words(text)

        val first = KaraokeTiming.wordAtProgress(words, 0f)
        val middle = KaraokeTiming.wordAtProgress(words, 0.55f)
        val last = KaraokeTiming.wordAtProgress(words, 1f)

        assertEquals("Primeira", text.substring(first!!.start, first.end))
        assertTrue(middle!!.start > first.start)
        assertEquals("última", text.substring(last!!.start, last.end))
    }

    @Test
    fun fasterSpeechProducesShorterFallbackTimeline() {
        val text = "Este trecho tem duração estimada para o destaque das palavras."

        val normal = KaraokeTiming.estimatedDurationMillis(text, speechRate = 1f)
        val fast = KaraokeTiming.estimatedDurationMillis(text, speechRate = 1.6f)

        assertTrue(normal > fast)
        assertTrue(fast >= 450L)
    }

    @Test
    fun punctuationAddsTimeBeforeNextWord() {
        val withPause = KaraokeTiming.words("Pare. Continue.")
        val withoutPause = KaraokeTiming.words("Pare continue")

        assertTrue(withPause[1].startProgress > withoutPause[1].startProgress)
    }

    @Test
    fun keepsParagraphWordOffsetsAfterSeparatedHeading() {
        val text = "Um título.\n\nPrimeiro parágrafo do capítulo."

        val words = KaraokeTiming.words(text)
        val paragraphWord = words.first { text.substring(it.start, it.end) == "Primeiro" }

        assertEquals(text.indexOf("Primeiro"), paragraphWord.start)
        assertEquals("Primeiro", text.substring(paragraphWord.start, paragraphWord.end))
    }
}
