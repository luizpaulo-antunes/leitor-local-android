package com.leitorlocal.speech

data class EmbeddedNeuralVoice(
    val name: String,
    val label: String,
    val modelDirectory: String,
    val modelFile: String,
    val noiseScale: Float,
    val noiseScaleW: Float,
)

const val EMBEDDED_NEURAL_MIRO_VOICE_NAME = "embedded-neural-pt-br-miro"
const val EMBEDDED_NEURAL_DII_VOICE_NAME = "embedded-neural-pt-br-dii"
const val EMBEDDED_NEURAL_VOICE_NAME = EMBEDDED_NEURAL_MIRO_VOICE_NAME

val EMBEDDED_NEURAL_VOICES = listOf(
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_MIRO_VOICE_NAME,
        label = "Miro — narrador neural natural · offline",
        modelDirectory = "vits-piper-pt_BR-miro-high",
        modelFile = "pt_BR-miro-high.onnx",
        noiseScale = 0.5f,
        noiseScaleW = 0.65f,
    ),
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_DII_VOICE_NAME,
        label = "Dii — narradora neural expressiva · offline",
        modelDirectory = "vits-piper-pt_BR-dii-high",
        modelFile = "pt_BR-dii-high.onnx",
        noiseScale = 0.667f,
        noiseScaleW = 0.8f,
    ),
)

fun embeddedNeuralVoice(name: String?): EmbeddedNeuralVoice? =
    EMBEDDED_NEURAL_VOICES.firstOrNull { it.name == name }
