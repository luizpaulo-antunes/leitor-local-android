package com.leitorlocal.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralVoiceProfileTest {
    @Test
    fun preservesDiiOriginalBandwidthAndDenoisesMiro() {
        val miro = NeuralVoiceProfiles.forVoice(
            requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME)),
        )
        val dii = NeuralVoiceProfiles.forVoice(
            requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_DII_VOICE_NAME)),
        )

        assertTrue(miro.useNeuralDenoiser)
        assertFalse(dii.useNeuralDenoiser)
        assertTrue(miro.audio.targetActiveRms > dii.audio.targetActiveRms)
    }
}
