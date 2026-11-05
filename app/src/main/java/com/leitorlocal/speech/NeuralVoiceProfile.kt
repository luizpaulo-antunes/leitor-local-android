package com.leitorlocal.speech

internal data class NeuralVoiceProfile(
    val silenceScale: Float,
    val useNeuralDenoiser: Boolean,
    val audio: NeuralAudioProcessingProfile,
)

internal object NeuralVoiceProfiles {
    fun forVoice(voice: EmbeddedNeuralVoice): NeuralVoiceProfile = when (voice.name) {
        EMBEDDED_NEURAL_DII_VOICE_NAME -> NeuralVoiceProfile(
            // Dii is already a clean 22.05 kHz model. Keeping its original bandwidth
            // avoids the metallic edge introduced by the 16 kHz speech denoiser.
            silenceScale = 0.20f,
            useNeuralDenoiser = false,
            audio = NeuralAudioProcessingProfile(
                targetActiveRms = 0.145f,
                maxGain = 1.55f,
            ),
        )

        else -> NeuralVoiceProfile(
            // Miro benefits from GTCRN because its source model has a faint noise floor.
            silenceScale = 0.20f,
            useNeuralDenoiser = true,
            audio = NeuralAudioProcessingProfile(
                targetActiveRms = 0.15f,
                maxGain = 1.70f,
            ),
        )
    }
}
