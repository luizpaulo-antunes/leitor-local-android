package com.leitorlocal.speech

import com.leitorlocal.model.SpeechChunk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TtsUtteranceGateTest {
    @Test
    fun callbackFromPreviousGenerationCannotResolveCurrentChunk() {
        val gate = TtsUtteranceGate()
        val previous = gate.begin(SpeechChunk(pageIndex = 1, text = "Trecho anterior."))
        val currentChunk = SpeechChunk(pageIndex = 2, text = "Trecho atual.")
        val current = gate.begin(currentChunk)

        assertNotEquals(previous.utteranceId, current.utteranceId)
        assertNull(gate.resolve(previous.utteranceId))
        assertSame(currentChunk, gate.resolve(current.utteranceId)?.chunk)
    }

    @Test
    fun invalidationRejectsCallbacksDeliveredAfterStop() {
        val gate = TtsUtteranceGate()
        val request = gate.begin(SpeechChunk(pageIndex = 0, text = "Em reprodução."))

        gate.invalidate()

        assertNull(gate.resolve(request.utteranceId))
        assertNull(gate.complete(request.utteranceId))
    }

    @Test
    fun terminalCallbackCanOnlyCompleteRequestOnce() {
        val gate = TtsUtteranceGate()
        val chunk = SpeechChunk(pageIndex = 0, text = "Fim do trecho.")
        val request = gate.begin(chunk)

        assertSame(chunk, gate.complete(request.utteranceId)?.chunk)
        assertNull(gate.complete(request.utteranceId))
        assertNull(gate.resolve(request.utteranceId))
    }
}
