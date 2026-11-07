package com.leitorlocal.speech

internal data class NeuralVoiceProfile(
    val silenceScale: Float,
    val useNeuralDenoiser: Boolean,
    val playbackAmplitudeGain: Float,
    val audio: NeuralAudioProcessingProfile,
)

internal object NeuralVoiceProfiles {
    fun forVoice(voice: EmbeddedNeuralVoice): NeuralVoiceProfile = when (voice.name) {
        EMBEDDED_NEURAL_MIRO_VOICE_NAME -> NeuralVoiceProfile(
            // Miro benefits from GTCRN because its source model has a faint noise floor.
            silenceScale = 0.20f,
            useNeuralDenoiser = true,
            playbackAmplitudeGain =
                BASE_PLAYBACK_AMPLITUDE_GAIN * MIRO_RELATIVE_AMPLITUDE_GAIN,
            audio = NeuralAudioProcessingProfile(
                targetActiveRms = 0.15f,
                maxGain = 1.70f,
            ),
        )

        else -> NeuralVoiceProfile(
            // Dii and Kokoro are already clean. Preserve each model's
            // original bandwidth instead of forcing them through the 16 kHz denoiser.
            silenceScale = 0.20f,
            useNeuralDenoiser = false,
            playbackAmplitudeGain = BASE_PLAYBACK_AMPLITUDE_GAIN,
            audio = NeuralAudioProcessingProfile(
                targetActiveRms = 0.145f,
                maxGain = 1.55f,
            ),
        )
    }

    private const val BASE_PLAYBACK_AMPLITUDE_GAIN = 1.20f
    private const val MIRO_RELATIVE_AMPLITUDE_GAIN = 1.43f
}
