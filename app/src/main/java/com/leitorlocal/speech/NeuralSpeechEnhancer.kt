package com.leitorlocal.speech

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig

class NeuralSpeechEnhancer(private val context: Context) {
    private var engine: OfflineSpeechDenoiser? = null

    fun enhance(samples: FloatArray, sampleRate: Int): NeuralAudio {
        val prepared = NeuralAudioProcessor.prepare(samples)
        return runCatching {
            val denoised = getOrCreateEngine().run(prepared, sampleRate)
            check(denoised.samples.isNotEmpty()) { "O redutor de ruido nao gerou audio." }
            NeuralAudio(
                samples = NeuralAudioProcessor.finish(
                    NeuralAudioProcessor.resample(
                        samples = denoised.samples,
                        sourceRate = denoised.sampleRate,
                        targetRate = OUTPUT_SAMPLE_RATE,
                    ),
                ),
                sampleRate = OUTPUT_SAMPLE_RATE,
            )
        }.getOrElse { failure ->
            Log.w(TAG, "Falha no redutor neural; usando tratamento local de seguranca.", failure)
            NeuralAudio(
                samples = NeuralAudioProcessor.finish(
                    NeuralAudioProcessor.resample(
                        samples = prepared,
                        sourceRate = sampleRate,
                        targetRate = OUTPUT_SAMPLE_RATE,
                    ),
                ),
                sampleRate = OUTPUT_SAMPLE_RATE,
            )
        }
    }

    fun release() {
        engine?.release()
        engine = null
    }

    private fun getOrCreateEngine(): OfflineSpeechDenoiser = engine ?: OfflineSpeechDenoiser(
        assetManager = context.assets,
        config = OfflineSpeechDenoiserConfig(
            model = OfflineSpeechDenoiserModelConfig(
                gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(
                    model = MODEL_PATH,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        ),
    ).also { engine = it }

    companion object {
        private const val TAG = "NeuralSpeechEnhancer"
        private const val MODEL_PATH = "speech-enhancement/gtcrn_simple.onnx"
        private const val OUTPUT_SAMPLE_RATE = 16_000
    }
}
