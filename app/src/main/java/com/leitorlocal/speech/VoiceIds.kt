package com.leitorlocal.speech

enum class EmbeddedNeuralEngine {
    PIPER_VITS,
    SUPERTONIC,
    KOKORO,
}

data class EmbeddedNeuralVoice(
    val name: String,
    val label: String,
    val engine: EmbeddedNeuralEngine,
    val modelDirectory: String,
    val modelFile: String = "",
    val speakerId: Int = 0,
    val noiseScale: Float = 0.667f,
    val noiseScaleW: Float = 0.8f,
    val generationSteps: Int = 8,
) {
    val engineKey: String
        get() = "${engine.name}:$modelDirectory"
}

const val EMBEDDED_NEURAL_MIRO_VOICE_NAME = "embedded-neural-pt-br-miro"
const val EMBEDDED_NEURAL_DII_VOICE_NAME = "embedded-neural-pt-br-dii"
const val EMBEDDED_NEURAL_KOKORO_DORA_VOICE_NAME = "embedded-neural-pt-br-kokoro-dora"
const val EMBEDDED_NEURAL_KOKORO_ALEX_VOICE_NAME = "embedded-neural-pt-br-kokoro-alex"
const val EMBEDDED_NEURAL_KOKORO_SANTA_VOICE_NAME = "embedded-neural-pt-br-kokoro-santa"
const val EMBEDDED_NEURAL_VOICE_NAME = EMBEDDED_NEURAL_MIRO_VOICE_NAME

private const val SUPERTONIC_MODEL_DIRECTORY =
    "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
private const val KOKORO_MODEL_DIRECTORY = "kokoro-multi-lang-v1_0"

private val piperVoices = listOf(
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_MIRO_VOICE_NAME,
        label = "Miro — narrador neural natural · offline",
        engine = EmbeddedNeuralEngine.PIPER_VITS,
        modelDirectory = "vits-piper-pt_BR-miro-high",
        modelFile = "pt_BR-miro-high.onnx",
        noiseScale = 0.5f,
        noiseScaleW = 0.65f,
    ),
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_DII_VOICE_NAME,
        label = "Dii — narradora neural expressiva · offline",
        engine = EmbeddedNeuralEngine.PIPER_VITS,
        modelDirectory = "vits-piper-pt_BR-dii-high",
        modelFile = "pt_BR-dii-high.onnx",
        noiseScale = 0.667f,
        noiseScaleW = 0.8f,
    ),
)

private val supertonicVoices = listOf(
    "F1", "F2", "F3", "F4", "F5",
    "M1", "M2", "M3", "M4", "M5",
).mapIndexed { speakerId, style ->
    val description = if (style.startsWith('F')) "voz feminina" else "voz masculina"
    EmbeddedNeuralVoice(
        name = "embedded-neural-pt-br-supertonic-${style.lowercase()}",
        label = "Supertonic $style — $description multilíngue · offline",
        engine = EmbeddedNeuralEngine.SUPERTONIC,
        modelDirectory = SUPERTONIC_MODEL_DIRECTORY,
        speakerId = speakerId,
        generationSteps = 10,
    )
}

private val kokoroVoices = listOf(
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_KOKORO_DORA_VOICE_NAME,
        label = "Kokoro Dora — narradora multilíngue · offline",
        engine = EmbeddedNeuralEngine.KOKORO,
        modelDirectory = KOKORO_MODEL_DIRECTORY,
        speakerId = 42,
    ),
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_KOKORO_ALEX_VOICE_NAME,
        label = "Kokoro Alex — narrador multilíngue · offline",
        engine = EmbeddedNeuralEngine.KOKORO,
        modelDirectory = KOKORO_MODEL_DIRECTORY,
        speakerId = 43,
    ),
    EmbeddedNeuralVoice(
        name = EMBEDDED_NEURAL_KOKORO_SANTA_VOICE_NAME,
        label = "Kokoro Santa — narrador multilíngue · offline",
        engine = EmbeddedNeuralEngine.KOKORO,
        modelDirectory = KOKORO_MODEL_DIRECTORY,
        speakerId = 44,
    ),
)

val EMBEDDED_NEURAL_VOICES = piperVoices + supertonicVoices + kokoroVoices

fun embeddedNeuralVoice(name: String?): EmbeddedNeuralVoice? =
    EMBEDDED_NEURAL_VOICES.firstOrNull { it.name == name }
