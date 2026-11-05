package com.leitorlocal.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class EmbeddedNeuralTts(private val context: Context) {
    private var engine: OfflineTts? = null

    fun generate(text: String, speed: Float): GeneratedAudio {
        val tts = engine ?: createEngine().also { engine = it }
        return tts.generate(
            text = text,
            sid = 0,
            speed = speed.coerceIn(0.6f, 1.6f),
        )
    }

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

    @Synchronized
    fun play(audio: GeneratedAudio, onCompletion: () -> Unit, onError: (Throwable) -> Unit) {
        stop()
        runCatching {
            require(audio.samples.isNotEmpty()) { "A voz neural não gerou áudio." }
            val bufferBytes = audio.samples.size * Float.SIZE_BYTES
            val newTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(audio.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bufferBytes)
                .build()

            newTrack.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(audioTrack: AudioTrack?) {
                        mainHandler.post {
                            stop()
                            onCompletion()
                        }
                    }

                    override fun onPeriodicNotification(audioTrack: AudioTrack?) = Unit
                },
            )
            val written = newTrack.write(
                audio.samples,
                0,
                audio.samples.size,
                AudioTrack.WRITE_BLOCKING,
            )
            check(written == audio.samples.size) { "Não foi possível preparar o áudio neural." }
            newTrack.notificationMarkerPosition = audio.samples.size
            track = newTrack
            newTrack.play()
        }.onFailure { failure ->
            stop()
            onError(failure)
        }
    }

    @Synchronized
    fun stop() {
        val current = track ?: return
        track = null
        runCatching { current.setPlaybackPositionUpdateListener(null) }
        runCatching {
            if (current.playState != AudioTrack.PLAYSTATE_STOPPED) current.stop()
        }
        current.release()
    }
}
