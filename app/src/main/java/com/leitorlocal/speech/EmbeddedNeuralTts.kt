package com.leitorlocal.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class EmbeddedNeuralTts(private val context: Context) {
    private var engine: OfflineTts? = null

    fun generate(text: String, speed: Float): GeneratedAudio {
        val tts = engine ?: createEngine().also { engine = it }
        val phonemes = piperPhonemizeText(text, tts.lang().ifEmpty { "pt-br" })
            ?: error("O fonemizador não conseguiu preparar o texto em português.")
        val codepoints = try {
            Array(phonemes.numSentences) { index -> phonemes.getPhonemes(index) }
        } finally {
            phonemes.close()
        }
        return tts.generateWithConfig(
            text = "",
            config = GenerationConfig(
                sid = 0,
                speed = speed.coerceIn(0.6f, 1.6f),
                silenceScale = 0.22f,
                phonemeCodepoints = codepoints,
            ),
        )
    }

    fun release() {
        engine?.release()
        engine = null
    }

    private fun createEngine(): OfflineTts {
        piperPhonemizeInitialize(context.applicationContext)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$MODEL_DIRECTORY/$MODEL_FILE",
                    tokens = "$MODEL_DIRECTORY/tokens.txt",
                    dataDir = "",
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
            silenceScale = 0.22f,
        )
        return OfflineTts(assetManager = context.assets, config = config)
    }

    companion object {
        private const val MODEL_DIRECTORY = "vits-piper-pt_BR-miro-high"
        private const val MODEL_FILE = "pt_BR-miro-high.onnx"
    }
}

class NeuralAudioPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var framesWritten = 0
    private var completion: (() -> Unit)? = null
    private var error: ((Throwable) -> Unit)? = null

    @Synchronized
    fun start(sampleRate: Int, onCompletion: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        runCatching {
            val minimumBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBuffer > 0) { "O aparelho não aceitou o formato de áudio neural." }
            val newTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minimumBuffer * 2)
                .build()
            check(newTrack.state == AudioTrack.STATE_INITIALIZED) {
                "Não foi possível abrir a saída de áudio neural."
            }
            newTrack.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(audioTrack: AudioTrack?) {
                        audioTrack?.let(::complete)
                    }

                    override fun onPeriodicNotification(audioTrack: AudioTrack?) = Unit
                },
            )
            track = newTrack
            framesWritten = 0
            completion = onCompletion
            error = onError
            newTrack.play()
        }.onFailure { failure ->
            stop()
            onError(failure)
        }
    }

    fun write(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return true
        var offset = 0
        while (offset < samples.size) {
            val current = synchronized(this) { track } ?: return false
            val blockSize = minOf(PLAYBACK_BLOCK_SAMPLES, samples.size - offset)
            val pcm = ShortArray(blockSize) { index ->
                (samples[offset + index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
            val count = runCatching {
                current.write(
                    pcm,
                    0,
                    pcm.size,
                    AudioTrack.WRITE_BLOCKING,
                )
            }.getOrElse { failure ->
                synchronized(this) {
                    if (track === current) fail(failure)
                }
                return false
            }
            if (count <= 0) {
                synchronized(this) {
                    if (track === current) {
                        fail(IllegalStateException("Falha $count ao enviar o áudio neural."))
                    }
                }
                return false
            }
            offset += count
            synchronized(this) {
                if (track !== current) return false
                framesWritten += count
            }
        }
        return true
    }

    @Synchronized
    fun finish() {
        val current = track ?: return
        if (framesWritten <= 0) {
            fail(IllegalStateException("A voz neural não gerou áudio."))
            return
        }
        val played = current.playbackHeadPosition.toLong() and 0xffffffffL
        if (played >= framesWritten.toLong()) {
            complete(current)
        } else {
            current.notificationMarkerPosition = framesWritten
        }
    }

    @Synchronized
    private fun complete(source: AudioTrack) {
        if (track !== source) return
        val callback = completion
        stop()
        callback?.let { mainHandler.post(it) }
    }

    @Synchronized
    private fun fail(failure: Throwable) {
        val callback = error
        stop()
        callback?.let { mainHandler.post { it(failure) } }
    }

    @Synchronized
    fun stop() {
        val current = track ?: return
        track = null
        completion = null
        error = null
        framesWritten = 0
        runCatching { current.setPlaybackPositionUpdateListener(null) }
        runCatching {
            if (current.playState != AudioTrack.PLAYSTATE_STOPPED) current.stop()
        }
        current.release()
    }

    companion object {
        private const val PLAYBACK_BLOCK_SAMPLES = 4096
    }
}
