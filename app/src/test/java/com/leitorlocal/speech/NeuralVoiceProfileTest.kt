package com.leitorlocal.speech

import org.junit.Assert.assertEquals
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

    @Test
    fun boostsMiroBy43PercentWithoutChangingDiiPlaybackGain() {
        val miro = NeuralVoiceProfiles.forVoice(
            requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_MIRO_VOICE_NAME)),
        )
        val dii = NeuralVoiceProfiles.forVoice(
            requireNotNull(embeddedNeuralVoice(EMBEDDED_NEURAL_DII_VOICE_NAME)),
        )

        assertEquals(1.20f, dii.playbackAmplitudeGain, 0.0001f)
        assertEquals(
            dii.playbackAmplitudeGain * 1.43f,
            miro.playbackAmplitudeGain,
            0.0001f,
        )
        assertEquals(158, NeuralAudioPlayer.amplitudeGainToMillibels(dii.playbackAmplitudeGain))
        assertEquals(469, NeuralAudioPlayer.amplitudeGainToMillibels(miro.playbackAmplitudeGain))
    }
}
