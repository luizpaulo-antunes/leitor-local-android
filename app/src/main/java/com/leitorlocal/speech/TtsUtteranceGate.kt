package com.leitorlocal.speech

import com.leitorlocal.model.SpeechChunk

internal data class ActiveTtsUtterance(
    val generation: Long,
    val utteranceId: String,
    val chunk: SpeechChunk,
)

/**
 * Gives every platform-TTS request a unique generation. Android may deliver callbacks from an
 * utterance after stop() has returned, so resolving callbacks through a single mutable "current
 * chunk" can make an old onDone skip the new paragraph.
 *
 * The service owns this gate on its main thread. Keeping the state machine independent from the
 * Android TTS API makes its stale-callback contract deterministic and directly testable.
 */
internal class TtsUtteranceGate {
    private var generation = 0L
    private var active: ActiveTtsUtterance? = null

    fun begin(chunk: SpeechChunk): ActiveTtsUtterance {
        val nextGeneration = ++generation
        return ActiveTtsUtterance(
            generation = nextGeneration,
            utteranceId = "$UTTERANCE_ID_PREFIX$nextGeneration",
            chunk = chunk,
        ).also { active = it }
    }

    fun resolve(utteranceId: String?): ActiveTtsUtterance? = active?.takeIf { request ->
        utteranceId == request.utteranceId && request.generation == generation
    }

    fun complete(utteranceId: String?): ActiveTtsUtterance? = resolve(utteranceId)?.also {
        active = null
    }

    fun invalidate() {
        generation += 1
        active = null
    }

    private companion object {
        const val UTTERANCE_ID_PREFIX = "book-"
    }
}
