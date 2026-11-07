package com.leitorlocal.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.leitorlocal.model.SpeechChunk
import java.util.ArrayDeque
import kotlin.math.log10
import kotlin.math.roundToInt

class EmbeddedNeuralTts(private val context: Context) {
    private var engine: OfflineTts? = null
    private var activeEngineKey: String? = null
    private val speechEnhancer = NeuralSpeechEnhancer(context)

    fun generate(text: String, speed: Float, voice: EmbeddedNeuralVoice): NeuralAudio {
        val narrationText = NeuralNarrationText.prepare(text)
        check(narrationText.isNotEmpty()) { "O trecho não contém texto narrável." }
        val profile = NeuralVoiceProfiles.forVoice(voice)
        val tts = engineFor(voice)
        val generated = generateRaw(
            tts = tts,
            text = narrationText,
            speed = speed,
            voice = voice,
            profile = profile,
        )
        return speechEnhancer.enhance(
            samples = generated.samples,
            sampleRate = generated.sampleRate,
            profile = profile,
            narrationText = narrationText,
            speed = speed,
        )
    }

    fun release() {
        speechEnhancer.release()
        engine?.release()
        engine = null
        activeEngineKey = null
    }

    private fun engineFor(voice: EmbeddedNeuralVoice): OfflineTts {
        if (activeEngineKey == voice.engineKey) engine?.let { return it }
        engine?.release()
        engine = null
        activeEngineKey = null
        return createEngine(voice).also {
            engine = it
            activeEngineKey = voice.engineKey
        }
    }

    private fun createEngine(voice: EmbeddedNeuralVoice): OfflineTts {
        piperPhonemizeInitialize(context.applicationContext)
        val profile = NeuralVoiceProfiles.forVoice(voice)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = voice.vitsConfig(),
                kokoro = voice.kokoroConfig(),
                numThreads = voice.threadCount(),
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
            silenceScale = profile.silenceScale,
        )
        return OfflineTts(assetManager = context.assets, config = config)
    }

    private fun generateRaw(
        tts: OfflineTts,
        text: String,
        speed: Float,
        voice: EmbeddedNeuralVoice,
        profile: NeuralVoiceProfile,
    ): NeuralAudio = when (voice.engine) {
        EmbeddedNeuralEngine.PIPER_VITS,
        EmbeddedNeuralEngine.KOKORO,
        -> generateFromPortuguesePhonemes(
            tts = tts,
            text = text,
            speed = speed,
            voice = voice,
            profile = profile,
        )
    }

    private fun generateFromPortuguesePhonemes(
        tts: OfflineTts,
        text: String,
        speed: Float,
        voice: EmbeddedNeuralVoice,
        profile: NeuralVoiceProfile,
    ): NeuralAudio {
        val language = if (voice.engine == EmbeddedNeuralEngine.KOKORO) {
            "pt-br"
        } else {
            tts.lang().ifEmpty { "pt-br" }
        }
        val phonemes = piperPhonemizeText(text, language)
            ?: error("O fonemizador não conseguiu preparar o texto em português.")
        val codepoints = try {
            Array(phonemes.numSentences) { index -> phonemes.getPhonemes(index) }
        } finally {
            phonemes.close()
        }
        val generated = tts.generateWithConfig(
            text = "",
            config = GenerationConfig(
                sid = voice.speakerId,
                speed = speed.coerceIn(0.6f, 1.6f),
                silenceScale = profile.silenceScale,
                phonemeCodepoints = codepoints,
            ),
        )
        return NeuralAudio(generated.samples, generated.sampleRate)
    }

    private fun EmbeddedNeuralVoice.vitsConfig(): OfflineTtsVitsModelConfig =
        if (engine == EmbeddedNeuralEngine.PIPER_VITS) {
            OfflineTtsVitsModelConfig(
                model = "$modelDirectory/$modelFile",
                tokens = "$modelDirectory/tokens.txt",
                dataDir = "",
                noiseScale = noiseScale,
                noiseScaleW = noiseScaleW,
                lengthScale = 1.0f,
            )
        } else {
            OfflineTtsVitsModelConfig()
        }

    private fun EmbeddedNeuralVoice.kokoroConfig(): OfflineTtsKokoroModelConfig =
        if (engine == EmbeddedNeuralEngine.KOKORO) {
            OfflineTtsKokoroModelConfig(
                model = "$modelDirectory/model.int8.onnx",
                voices = "$modelDirectory/voices.bin",
                tokens = "$modelDirectory/tokens.txt",
                dataDir = "",
                lexicon = "",
                lang = "",
                lengthScale = 1.0f,
            )
        } else {
            OfflineTtsKokoroModelConfig()
        }

    private fun EmbeddedNeuralVoice.threadCount(): Int =
        if (engine == EmbeddedNeuralEngine.KOKORO) {
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        } else {
            2
        }
}

class NeuralAudioPlayer {
    private data class PlaybackSegment(
        val chunk: SpeechChunk,
        val startFrame: Int,
        val endFrame: Int,
        val words: List<KaraokeWord>,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var framesWritten = 0
    private val segments = ArrayDeque<PlaybackSegment>()
    private var completion: (() -> Unit)? = null
    private var error: ((Throwable) -> Unit)? = null
    private var progress: ((chunk: SpeechChunk, wordStart: Int, wordEnd: Int) -> Unit)? = null
    private var lastProgressChunk: SpeechChunk? = null
    private var lastProgressWordStart = -1

    private val progressPoll = object : Runnable {
        override fun run() {
            dispatchPlaybackProgress()
            synchronized(this@NeuralAudioPlayer) {
                if (track != null) mainHandler.postDelayed(this, PROGRESS_INTERVAL_MILLIS)
            }
        }
    }

    @Synchronized
    fun start(
        sampleRate: Int,
        playbackAmplitudeGain: Float,
        onCompletion: () -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (chunk: SpeechChunk, wordStart: Int, wordEnd: Int) -> Unit,
    ) {
        stop()
        runCatching {
            val minimumBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
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
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
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
            track = newTrack
            loudnessEnhancer = runCatching {
                LoudnessEnhancer(newTrack.audioSessionId).apply {
                    // Keep the boost in LoudnessEnhancer instead of multiplying PCM samples:
                    // the effect can reduce its actual gain near peaks to avoid clipping.
                    setTargetGain(amplitudeGainToMillibels(playbackAmplitudeGain))
                    enabled = true
                }
            }.getOrNull()
            newTrack.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(audioTrack: AudioTrack?) {
                        audioTrack?.let(::complete)
                    }

                    override fun onPeriodicNotification(audioTrack: AudioTrack?) = Unit
                },
            )
            framesWritten = 0
            segments.clear()
            completion = onCompletion
            error = onError
            progress = onProgress
            lastProgressChunk = null
            lastProgressWordStart = -1
            newTrack.play()
            mainHandler.post(progressPoll)
        }.onFailure { failure ->
            stop()
            onError(failure)
        }
    }

    fun write(samples: FloatArray, chunk: SpeechChunk): Boolean {
        if (samples.isEmpty()) return true
        synchronized(this) {
            val startFrame = framesWritten
            segments.addLast(
                PlaybackSegment(
                    chunk = chunk,
                    startFrame = startFrame,
                    endFrame = startFrame + samples.size,
                    words = KaraokeTiming.words(chunk.text),
                ),
            )
        }
        var offset = 0
        while (offset < samples.size) {
            val current = synchronized(this) { track } ?: return false
            val blockSize = minOf(PLAYBACK_BLOCK_SAMPLES, samples.size - offset)
            val count = runCatching {
                current.write(
                    samples,
                    offset,
                    blockSize,
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

    private fun dispatchPlaybackProgress() {
        val event = synchronized(this) {
            val current = track ?: return
            if (segments.isEmpty()) return
            val playedFrame = current.playbackHeadPosition
            while (segments.size > 1 && playedFrame >= segments.first.endFrame) {
                segments.removeFirst()
            }
            val segment = segments.first
            if (playedFrame < segment.startFrame) return
            val segmentFrames = (segment.endFrame - segment.startFrame).coerceAtLeast(1)
            val segmentProgress =
                (playedFrame - segment.startFrame).toFloat() / segmentFrames.toFloat()
            val word = KaraokeTiming.wordAtProgress(segment.words, segmentProgress) ?: return
            if (segment.chunk === lastProgressChunk && word.start == lastProgressWordStart) return
            lastProgressChunk = segment.chunk
            lastProgressWordStart = word.start
            Triple(segment.chunk, word.start, word.end)
        }
        progress?.invoke(event.first, event.second, event.third)
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
        mainHandler.removeCallbacks(progressPoll)
        val current = track
        track = null
        completion = null
        error = null
        progress = null
        framesWritten = 0
        segments.clear()
        lastProgressChunk = null
        lastProgressWordStart = -1
        loudnessEnhancer?.let { enhancer ->
            runCatching { enhancer.enabled = false }
            runCatching(enhancer::release)
        }
        loudnessEnhancer = null
        if (current == null) return
        runCatching { current.setPlaybackPositionUpdateListener(null) }
        runCatching {
            if (current.playState != AudioTrack.PLAYSTATE_STOPPED) current.stop()
        }
        current.release()
    }

    companion object {
        private const val PLAYBACK_BLOCK_SAMPLES = 4096
        private const val PROGRESS_INTERVAL_MILLIS = 60L

        internal fun amplitudeGainToMillibels(amplitudeGain: Float): Int {
            val safeGain = amplitudeGain.coerceIn(1.0f, 2.0f)
            return (20.0 * log10(safeGain.toDouble()) * 100.0).roundToInt()
        }
    }
}
