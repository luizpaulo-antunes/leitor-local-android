package com.leitorlocal.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class EmbeddedNeuralTts(private val context: Context) {
    private var engine: OfflineTts? = null

    fun generateStreaming(
        text: String,
        speed: Float,
        onSamples: (FloatArray) -> Boolean,
    ) {
        val tts = engine ?: createEngine().also { engine = it }
        tts.generateWithCallback(
            text = text,
            sid = 0,
            speed = speed.coerceIn(0.6f, 1.6f),
        ) { samples ->
            if (onSamples(samples)) 1 else 0
        }
    }

    fun sampleRate(): Int = (engine ?: createEngine().also { engine = it }).sampleRate()

    fun release() {
        engine?.release()
        engine = null
    }

    private fun createEngine(): OfflineTts {
        val dataDirectory = prepareEspeakData()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$MODEL_DIRECTORY/$MODEL_FILE",
                    tokens = "$MODEL_DIRECTORY/tokens.txt",
                    dataDir = dataDirectory.absolutePath,
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

    private fun prepareEspeakData(): File {
        val root = File(context.filesDir, "neural_voice_miro_v1")
        val dataDirectory = File(root, "espeak-ng-data")
        val marker = File(root, ".ready")
        if (marker.isFile && File(dataDirectory, "phontab").isFile) return dataDirectory

        root.deleteRecursively()
        dataDirectory.mkdirs()
        copyAssetDirectory("$MODEL_DIRECTORY/espeak-ng-data", dataDirectory)
        marker.writeText("1")
        return dataDirectory
    }

    private fun copyAssetDirectory(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use(input::copyTo)
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetDirectory("$assetPath/$child", File(destination, child))
        }
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

    @Synchronized
    fun write(samples: FloatArray): Boolean {
        val current = track ?: return false
        if (samples.isEmpty()) return true
        val pcm = ShortArray(samples.size) { index ->
            (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        var offset = 0
        while (offset < pcm.size) {
            val count = current.write(
                pcm,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (count <= 0) {
                fail(IllegalStateException("Falha $count ao enviar o áudio neural."))
                return false
            }
            offset += count
            framesWritten += count
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
}
