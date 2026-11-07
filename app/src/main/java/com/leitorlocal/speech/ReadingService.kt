package com.leitorlocal.speech

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.leitorlocal.data.ReaderPreferences
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.SpeechChunk
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private sealed interface NeuralQueueItem {
    data class Audio(
        val chunk: SpeechChunk,
        val samples: FloatArray,
        val sampleRate: Int,
    ) : NeuralQueueItem

    data class Failure(val cause: Throwable) : NeuralQueueItem
    object End : NeuralQueueItem
}

internal fun neuralPrebufferChunkCount(voice: EmbeddedNeuralVoice): Int =
    if (voice.engine == EmbeddedNeuralEngine.KOKORO) {
        KOKORO_NEURAL_PREBUFFER_CHUNKS
    } else {
        DEFAULT_NEURAL_PREBUFFER_CHUNKS
    }

class ReadingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var preferences: ReaderPreferences
    private lateinit var neuralAudioCache: NeuralAudioCache
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var ttsEngineGeneration = 0L
    @Volatile private var playWhenReady = false
    private val neuralExecutor = Executors.newSingleThreadExecutor()
    private val neuralPrewarmExecutor = Executors.newSingleThreadExecutor()
    private val neuralPlaybackExecutor = Executors.newSingleThreadExecutor()
    private val neuralGenerationLock = Any()
    private val neuralTts by lazy { EmbeddedNeuralTts(applicationContext) }
    private val neuralPlayer = NeuralAudioPlayer()
    @Volatile private var neuralRequestId = 0L
    @Volatile private var neuralQueue: ArrayBlockingQueue<NeuralQueueItem>? = null
    @Volatile private var prewarmRequestId = 0L
    private var ttsKaraokeGeneration = 0L
    private var ttsFallbackRunnable: Runnable? = null
    private val ttsUtterances = TtsUtteranceGate()
    @Volatile private var hasNativeRangeCallbacks = false
    private lateinit var mediaNotificationController: MediaNotificationController
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var audioFocusRequested = false
    private var hasAudioFocus = false
    private var resumeAfterTransientFocusLoss = false
    private var noisyReceiverRegistered = false
    private var hasForegroundNotification = false

    private val playbackAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            resumeAfterTransientFocusLoss = false
            pausePlayback(abandonFocus = true)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (audioFocusRequested) {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    if (resumeAfterTransientFocusLoss && ReadingSession.hasContent()) {
                        resumeAfterTransientFocusLoss = false
                        play(requestFocus = false)
                    }
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    hasAudioFocus = false
                    resumeAfterTransientFocusLoss = playWhenReady
                    pausePlayback(abandonFocus = false)
                }

                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    resumeAfterTransientFocusLoss = false
                    pausePlayback(abandonFocus = true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = ReaderPreferences(this)
        neuralAudioCache = NeuralAudioCache(this)
        audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAudioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
            .build()
        mediaNotificationController = MediaNotificationController(
            service = this,
            audioAttributes = playbackAudioAttributes,
            actions = MediaNotificationActions(
                play = ACTION_PLAY,
                pause = ACTION_PAUSE,
                next = ACTION_NEXT,
                previous = ACTION_PREVIOUS,
            ),
            callbacks = MediaSessionCallbacks(
                onPlay = { play() },
                onPause = { pause() },
                onNext = { move(+1) },
                onPrevious = { move(-1) },
                onStop = { stopReading() },
            ),
        )
        mediaNotificationController.initialize()
        initializeTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PREPARE_NEURAL -> prepareNeuralAudio()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> move(+1)
            ACTION_PREVIOUS -> move(-1)
            ACTION_RECONFIGURE -> reconfigurePlayback()
            ACTION_STOP -> stopReading()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeTts() {
        val engineGeneration = ++ttsEngineGeneration
        tts = TextToSpeech(applicationContext) { status ->
            mainHandler.post { handleTtsInitialized(engineGeneration, status) }
        }
    }

    private fun handleTtsInitialized(engineGeneration: Long, status: Int) {
        if (engineGeneration != ttsEngineGeneration) return
        val engine = tts
        // A callback may arrive after shutdown(); in that case there is no request left to fail
        // and touching the already released MediaSession would be incorrect.
        if (engine == null) return
        if (status != TextToSpeech.SUCCESS) {
            if (!usesEmbeddedNeuralVoice()) {
                transitionToPlaybackError("O mecanismo de voz não pôde ser iniciado.")
            }
            return
        }

        engine.language = Locale.forLanguageTag("pt-BR")
        engine.setAudioAttributes(playbackAudioAttributes)
        engine.setOnUtteranceProgressListener(listener)
        isTtsReady = true
        applySettings()
        if (playWhenReady && !usesEmbeddedNeuralVoice()) speakCurrent()
    }

    private fun play(requestFocus: Boolean = true) {
        if (!ReadingSession.hasContent()) {
            abandonPlaybackAudioFocus()
            stopSelf()
            return
        }

        // A queued prewarm is allowed to finish its current native inference, but it must not
        // continue through future pages ahead of the actual playback producer.
        prewarmRequestId += 1
        resumeAfterTransientFocusLoss = false
        ReadingSession.setStatus(PlaybackStatus.PREPARING)
        enterForeground(isPlaying = true)

        if (requestFocus) {
            when (requestPlaybackAudioFocus()) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> Unit
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    playWhenReady = false
                    resumeAfterTransientFocusLoss = true
                    ReadingSession.setStatus(PlaybackStatus.PAUSED)
                    mediaNotificationController.startOrUpdate(
                        isPlaying = false,
                        asForeground = false,
                    )
                    return
                }
                else -> {
                    transitionToPlaybackError(
                        "Outro aplicativo está usando a saída de áudio. Tente novamente em instantes.",
                    )
                    return
                }
            }
        }

        playWhenReady = true
        registerBecomingNoisyReceiver()
        if (usesEmbeddedNeuralVoice() || isTtsReady) speakCurrent()
    }

    private fun pause() {
        resumeAfterTransientFocusLoss = false
        pausePlayback(abandonFocus = true)
    }

    private fun pausePlayback(abandonFocus: Boolean) {
        playWhenReady = false
        prewarmRequestId += 1
        ttsUtterances.invalidate()
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        ReadingSession.setStatus(PlaybackStatus.PAUSED)
        enterForeground(isPlaying = false)
        unregisterBecomingNoisyReceiver()
        if (abandonFocus) abandonPlaybackAudioFocus()
    }

    private fun move(offset: Int) {
        val wasPlaying = ReadingSession.state.value.status in setOf(
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
        ) || playWhenReady
        prewarmRequestId += 1
        ttsUtterances.invalidate()
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        ReadingSession.moveBy(offset)
        persistPosition()
        if (wasPlaying) {
            playWhenReady = true
            if (usesEmbeddedNeuralVoice() || isTtsReady) speakCurrent()
        } else {
            ReadingSession.setStatus(PlaybackStatus.PAUSED)
            mediaNotificationController.startOrUpdate(
                isPlaying = false,
                asForeground = false,
            )
        }
    }

    /**
     * Applies the settings already written to [ReadingSession]. If audio is active, the current
     * paragraph is restarted immediately with a fresh request/fresh queue. If it is paused, this
     * only updates the platform engine and keeps the session paused; callers may independently use
     * [prepareNeural] to warm an embedded voice without starting playback.
     */
    private fun reconfigurePlayback() {
        val sessionStatus = ReadingSession.state.value.status
        if (
            !ReadingSession.hasContent() ||
            sessionStatus !in setOf(
                PlaybackStatus.PLAYING,
                PlaybackStatus.PREPARING,
                PlaybackStatus.PAUSED,
            )
        ) {
            playWhenReady = false
            stopSelf()
            return
        }
        val wasPlaying = playWhenReady || sessionStatus in setOf(
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
        )
        prewarmRequestId += 1
        playWhenReady = false
        ttsUtterances.invalidate()
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        ReadingSession.clearWordRange()
        applySettings()

        if (wasPlaying) {
            play(requestFocus = !hasAudioFocus)
        } else {
            ReadingSession.setStatus(PlaybackStatus.PAUSED)
            if (hasForegroundNotification) {
                mediaNotificationController.startOrUpdate(
                    isPlaying = false,
                    asForeground = false,
                )
            } else {
                stopSelf()
            }
        }
    }

    private fun speakCurrent() {
        if (usesEmbeddedNeuralVoice()) {
            speakCurrentWithNeuralVoice()
            return
        }
        val engine = tts ?: return
        val chunk = ReadingSession.currentChunk() ?: return
        ttsUtterances.invalidate()
        val request = ttsUtterances.begin(chunk)
        cancelTtsKaraoke()
        ReadingSession.clearWordRange()
        applySettings()
        ReadingSession.setStatus(PlaybackStatus.PLAYING)
        mediaNotificationController.startOrUpdate(
            isPlaying = true,
            asForeground = false,
        )
        val result = runCatching {
            engine.speak(
                chunk.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                request.utteranceId,
            )
        }.getOrElse { failure ->
            if (ttsUtterances.resolve(request.utteranceId) != null) {
                ttsUtterances.invalidate()
                transitionToPlaybackError(
                    failure.message?.takeIf(String::isNotBlank)
                        ?: "A voz selecionada não conseguiu iniciar este trecho.",
                )
            }
            return
        }
        if (result == TextToSpeech.ERROR && ttsUtterances.resolve(request.utteranceId) != null) {
            ttsUtterances.invalidate()
            transitionToPlaybackError("A voz selecionada recusou este trecho. Tente outra voz.")
        }
    }

    private fun speakCurrentWithNeuralVoice() {
        val voice = embeddedNeuralVoice(ReadingSession.voiceName) ?: return
        val startIndex = ReadingSession.state.value.currentChunkIndex
        if (ReadingSession.chunkAt(startIndex) == null) return
        tts?.stop()
        cancelNeuralPlayback()
        val requestId = neuralRequestId
        val queue = ArrayBlockingQueue<NeuralQueueItem>(NEURAL_QUEUE_CAPACITY)
        neuralQueue = queue
        ReadingSession.setStatus(PlaybackStatus.PREPARING)
        mediaNotificationController.startOrUpdate(
            isPlaying = true,
            asForeground = false,
        )

        neuralPlaybackExecutor.execute {
            runCatching { consumeNeuralQueue(requestId, queue, voice) }
                .onFailure { failure ->
                    if (isNeuralRequestActive(requestId)) {
                        handleNeuralPlaybackError(
                            requestId,
                            neuralErrorMessage("reproduzir", failure),
                        )
                    }
                }
        }
        neuralExecutor.execute {
            try {
                val bookId = ReadingSession.bookId
                // One request must use one stable voice/rate profile. Settings changed by the UI
                // are applied through ACTION_RECONFIGURE, which invalidates this request.
                val speechRate = ReadingSession.speechRate
                var index = startIndex
                while (index < ReadingSession.chunkCount() && isNeuralRequestActive(requestId)) {
                    val chunk = ReadingSession.chunkAt(index) ?: break
                    val audio = getOrCreateNeuralAudio(
                        bookId = bookId,
                        chunkIndex = index,
                        text = chunk.text,
                        speechRate = speechRate,
                        voice = voice,
                    )
                    check(audio.samples.isNotEmpty()) { "O modelo não gerou amostras de áudio." }
                    if (!offerNeuralItem(
                            requestId,
                            queue,
                            NeuralQueueItem.Audio(chunk, audio.samples, audio.sampleRate),
                        )
                    ) {
                        return@execute
                    }
                    index = (ReadingSession.indexOfChunk(chunk) ?: index) + 1
                }
                if (isNeuralRequestActive(requestId)) {
                    offerNeuralItem(requestId, queue, NeuralQueueItem.End)
                }
            } catch (failure: Throwable) {
                if (isNeuralRequestActive(requestId)) {
                    offerNeuralItem(requestId, queue, NeuralQueueItem.Failure(failure))
                }
            }
        }
    }

    private fun prepareNeuralAudio() {
        if (!ReadingSession.hasContent() || !usesEmbeddedNeuralVoice()) {
            stopSelf()
            return
        }
        val warmId = ++prewarmRequestId
        val bookId = ReadingSession.bookId
        val startIndex = ReadingSession.state.value.currentChunkIndex
        val speechRate = ReadingSession.speechRate
        val voice = embeddedNeuralVoice(ReadingSession.voiceName) ?: return
        val indices = prewarmIndices(startIndex)

        neuralPrewarmExecutor.execute {
            try {
                for (index in indices) {
                    if (
                        warmId != prewarmRequestId ||
                        playWhenReady ||
                        bookId != ReadingSession.bookId
                    ) {
                        break
                    }
                    val chunk = ReadingSession.chunkAt(index) ?: break
                    getOrCreateNeuralAudio(
                        bookId = bookId,
                        chunkIndex = index,
                        text = chunk.text,
                        speechRate = speechRate,
                        voice = voice,
                    )
                }
            } finally {
                mainHandler.post {
                    if (warmId == prewarmRequestId && !playWhenReady) stopSelf()
                }
            }
        }
    }

    private fun prewarmIndices(startIndex: Int): List<Int> {
        val pages = linkedSetOf<Int>()
        val indices = mutableListOf<Int>()
        var index = startIndex
        while (index < ReadingSession.chunkCount() && indices.size < NEURAL_PREWARM_MAX_CHUNKS) {
            val chunk = ReadingSession.chunkAt(index) ?: break
            if (chunk.pageIndex !in pages && pages.size >= NEURAL_PREWARM_PAGES) break
            pages += chunk.pageIndex
            indices += index
            index += 1
        }
        return indices
    }

    private fun getOrCreateNeuralAudio(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voice: EmbeddedNeuralVoice,
    ): NeuralAudio {
        neuralAudioCache.load(bookId, chunkIndex, text, speechRate, voice.name)?.let { return it }
        return synchronized(neuralGenerationLock) {
            // Playback and prewarm use different executors. The second lookup lets playback read
            // already cached current chunks immediately while a future prewarm is generating, but
            // still guarantees that the native TTS engine itself is never entered concurrently.
            neuralAudioCache.load(bookId, chunkIndex, text, speechRate, voice.name)?.let {
                return@synchronized it
            }
            val generated = neuralTts.generate(text, speechRate, voice)
            neuralAudioCache.save(bookId, chunkIndex, text, speechRate, voice.name, generated)
            generated
        }
    }

    private fun consumeNeuralQueue(
        requestId: Long,
        queue: ArrayBlockingQueue<NeuralQueueItem>,
        voice: EmbeddedNeuralVoice,
    ) {
        val buffered = mutableListOf<NeuralQueueItem.Audio>()
        var pendingTerminal: NeuralQueueItem? = null
        val requiredPrebuffer = neuralPrebufferChunkCount(voice)
        while (buffered.size < requiredPrebuffer && pendingTerminal == null) {
            when (val item = pollNeuralItem(requestId, queue) ?: return) {
                is NeuralQueueItem.Audio -> buffered += item
                else -> pendingTerminal = item
            }
        }

        if (buffered.isEmpty()) {
            handleNeuralTerminal(requestId, pendingTerminal ?: NeuralQueueItem.End)
            return
        }

        val sampleRate = buffered.first().sampleRate
        neuralPlayer.start(
            sampleRate = sampleRate,
            playbackAmplitudeGain = NeuralVoiceProfiles.forVoice(voice).playbackAmplitudeGain,
            onCompletion = { handleNeuralBookDone(requestId) },
            onError = { failure ->
                if (requestId == neuralRequestId) {
                    handleNeuralPlaybackError(
                        requestId,
                        neuralErrorMessage("reproduzir", failure),
                    )
                }
            },
            onProgress = { chunk, wordStart, wordEnd ->
                if (!isNeuralRequestActive(requestId)) return@start
                val chunkIndex = ReadingSession.indexOfChunk(chunk) ?: return@start
                if (ReadingSession.state.value.currentChunkIndex != chunkIndex) {
                    ReadingSession.setIndex(chunkIndex)
                    persistPosition()
                    mainHandler.post {
                        mediaNotificationController.startOrUpdate(
                            isPlaying = true,
                            asForeground = false,
                        )
                    }
                }
                ReadingSession.setStatus(PlaybackStatus.PLAYING)
                ReadingSession.setWordRange(chunkIndex, wordStart, wordEnd)
            },
        )
        ReadingSession.setStatus(PlaybackStatus.PLAYING)
        mainHandler.post {
            mediaNotificationController.startOrUpdate(
                isPlaying = true,
                asForeground = false,
            )
        }

        buffered.forEach { audio ->
            if (!playNeuralItem(requestId, audio, sampleRate)) return
        }
        pendingTerminal?.let {
            handleNeuralTerminal(requestId, it)
            return
        }

        while (isNeuralRequestActive(requestId)) {
            when (val item = pollNeuralItem(requestId, queue) ?: return) {
                is NeuralQueueItem.Audio -> {
                    if (!playNeuralItem(requestId, item, sampleRate)) return
                }
                else -> {
                    handleNeuralTerminal(requestId, item)
                    return
                }
            }
        }
    }

    private fun playNeuralItem(
        requestId: Long,
        item: NeuralQueueItem.Audio,
        expectedSampleRate: Int,
    ): Boolean {
        if (!isNeuralRequestActive(requestId)) return false
        check(item.sampleRate == expectedSampleRate) { "A taxa do áudio neural mudou durante a leitura." }
        return neuralPlayer.write(item.samples, item.chunk) &&
            isNeuralRequestActive(requestId)
    }

    private fun handleNeuralTerminal(requestId: Long, item: NeuralQueueItem) {
        if (!isNeuralRequestActive(requestId)) return
        when (item) {
            NeuralQueueItem.End -> neuralPlayer.finish()
            is NeuralQueueItem.Failure -> handleNeuralPlaybackError(
                requestId,
                neuralErrorMessage("preparar", item.cause),
            )
            is NeuralQueueItem.Audio -> Unit
        }
    }

    private fun handleNeuralBookDone(requestId: Long) {
        if (!isNeuralRequestActive(requestId)) return
        playWhenReady = false
        neuralQueue = null
        unregisterBecomingNoisyReceiver()
        abandonPlaybackAudioFocus()
        ReadingSession.setStatus(PlaybackStatus.STOPPED)
        persistPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
        hasForegroundNotification = false
        stopSelf()
    }

    private fun offerNeuralItem(
        requestId: Long,
        queue: ArrayBlockingQueue<NeuralQueueItem>,
        item: NeuralQueueItem,
    ): Boolean {
        while (isNeuralRequestActive(requestId)) {
            if (queue.offer(item, NEURAL_QUEUE_WAIT_MS, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    private fun pollNeuralItem(
        requestId: Long,
        queue: ArrayBlockingQueue<NeuralQueueItem>,
    ): NeuralQueueItem? {
        while (isNeuralRequestActive(requestId)) {
            queue.poll(NEURAL_QUEUE_WAIT_MS, TimeUnit.MILLISECONDS)?.let { return it }
        }
        return null
    }

    private fun isNeuralRequestActive(requestId: Long): Boolean =
        requestId == neuralRequestId && playWhenReady

    private fun neuralErrorMessage(action: String, failure: Throwable): String {
        val detail = failure.message?.takeIf(String::isNotBlank)?.take(120)
        return buildString {
            append("Não foi possível $action a voz neural offline")
            if (detail != null) append(": $detail") else append('.')
        }
    }

    private fun applySettings() {
        val engine = tts ?: return
        engine.setSpeechRate(ReadingSession.speechRate)
        engine.setPitch(0.98f)
        val requestedVoice = ReadingSession.voiceName
        if (requestedVoice != null) {
            engine.voices?.firstOrNull { it.name == requestedVoice }?.let(engine::setVoice)
        } else {
            engine.language = Locale.forLanguageTag("pt-BR")
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            mainHandler.post {
                val request = ttsUtterances.resolve(utteranceId) ?: return@post
                val chunkIndex = ReadingSession.indexOfChunk(request.chunk) ?: return@post
                if (!playWhenReady || ReadingSession.state.value.currentChunkIndex != chunkIndex) return@post
                ReadingSession.setStatus(PlaybackStatus.PLAYING)
                startTtsKaraokeFallback(chunkIndex)
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            mainHandler.post {
                val request = ttsUtterances.resolve(utteranceId) ?: return@post
                val chunkIndex = ReadingSession.indexOfChunk(request.chunk) ?: return@post
                if (!playWhenReady || ReadingSession.state.value.currentChunkIndex != chunkIndex) return@post
                hasNativeRangeCallbacks = true
                ttsFallbackRunnable?.let(mainHandler::removeCallbacks)
                ReadingSession.setWordRange(chunkIndex, start, end)
            }
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                val request = ttsUtterances.complete(utteranceId) ?: return@post
                val chunkIndex = ReadingSession.indexOfChunk(request.chunk) ?: return@post
                if (ReadingSession.state.value.currentChunkIndex != chunkIndex) return@post
                cancelTtsKaraoke()
                handleChunkDone()
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            mainHandler.post {
                val request = ttsUtterances.complete(utteranceId) ?: return@post
                val chunkIndex = ReadingSession.indexOfChunk(request.chunk) ?: return@post
                if (ReadingSession.state.value.currentChunkIndex == chunkIndex) {
                    cancelTtsKaraoke()
                    if (playWhenReady) {
                        transitionToPlaybackError(
                            "A voz selecionada interrompeu a leitura. Tente reproduzir novamente.",
                        )
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            handlePlatformTtsError(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handlePlatformTtsError(utteranceId)
        }
    }

    private fun startTtsKaraokeFallback(chunkIndex: Int) {
        cancelTtsKaraoke()
        val text = ReadingSession.chunkAt(chunkIndex)?.text ?: return
        val words = KaraokeTiming.words(text)
        if (words.isEmpty()) return
        hasNativeRangeCallbacks = false
        val generation = ttsKaraokeGeneration
        val startedAt = SystemClock.elapsedRealtime()
        val duration = KaraokeTiming.estimatedDurationMillis(text, ReadingSession.speechRate)
        ReadingSession.setWordRange(chunkIndex, words.first().start, words.first().end)
        val update = object : Runnable {
            override fun run() {
                if (
                    generation != ttsKaraokeGeneration ||
                    hasNativeRangeCallbacks ||
                    !playWhenReady ||
                    ReadingSession.state.value.currentChunkIndex != chunkIndex
                ) {
                    return
                }
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val word = KaraokeTiming.wordAtProgress(words, elapsed.toFloat() / duration.toFloat())
                if (word != null) ReadingSession.setWordRange(chunkIndex, word.start, word.end)
                if (elapsed < duration) mainHandler.postDelayed(this, KARAOKE_INTERVAL_MILLIS)
            }
        }
        ttsFallbackRunnable = update
        mainHandler.postDelayed(update, KARAOKE_INTERVAL_MILLIS)
    }

    private fun cancelTtsKaraoke() {
        ttsKaraokeGeneration += 1
        ttsFallbackRunnable?.let(mainHandler::removeCallbacks)
        ttsFallbackRunnable = null
        hasNativeRangeCallbacks = false
    }

    private fun handleChunkDone() {
        if (!playWhenReady) return
        if (ReadingSession.isAtEnd()) {
            playWhenReady = false
            unregisterBecomingNoisyReceiver()
            abandonPlaybackAudioFocus()
            ReadingSession.setStatus(PlaybackStatus.STOPPED)
            persistPosition()
            stopForeground(STOP_FOREGROUND_REMOVE)
            hasForegroundNotification = false
            stopSelf()
        } else {
            ReadingSession.moveBy(+1)
            persistPosition()
            speakCurrent()
        }
    }

    private fun handlePlatformTtsError(utteranceId: String?) {
        mainHandler.post {
            if (ttsUtterances.complete(utteranceId) == null) return@post
            transitionToPlaybackError(
                "A voz selecionada não conseguiu ler este trecho. Tente outra voz.",
            )
        }
    }

    private fun handleNeuralPlaybackError(requestId: Long, message: String) {
        mainHandler.post {
            // The executor may discover a failure immediately before Pause/Next/Reconfigure
            // invalidates its request. Never let that stale failure stop a newer playback.
            if (isNeuralRequestActive(requestId)) transitionToPlaybackError(message)
        }
    }

    private fun transitionToPlaybackError(message: String) {
        playWhenReady = false
        resumeAfterTransientFocusLoss = false
        prewarmRequestId += 1
        ttsUtterances.invalidate()
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        unregisterBecomingNoisyReceiver()
        abandonPlaybackAudioFocus()
        ReadingSession.setStatus(PlaybackStatus.ERROR, message)
        mediaNotificationController.startOrUpdate(
            isPlaying = false,
            asForeground = false,
        )
        if (hasForegroundNotification) {
            stopForeground(STOP_FOREGROUND_DETACH)
            hasForegroundNotification = false
        }
        stopSelf()
    }

    private fun stopReading() {
        playWhenReady = false
        resumeAfterTransientFocusLoss = false
        prewarmRequestId += 1
        ttsUtterances.invalidate()
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        unregisterBecomingNoisyReceiver()
        abandonPlaybackAudioFocus()
        ReadingSession.setStatus(PlaybackStatus.STOPPED)
        persistPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
        hasForegroundNotification = false
        stopSelf()
    }

    private fun persistPosition() {
        preferences.savePosition(
            bookId = ReadingSession.bookId,
            chunkIndex = ReadingSession.state.value.currentChunkIndex,
        )
    }

    private fun usesEmbeddedNeuralVoice(): Boolean =
        embeddedNeuralVoice(ReadingSession.voiceName) != null

    private fun cancelNeuralPlayback() {
        neuralRequestId += 1
        neuralPlayer.stop()
        neuralQueue?.apply {
            clear()
            offer(NeuralQueueItem.End)
        }
        neuralQueue = null
    }

    private fun requestPlaybackAudioFocus(): Int {
        val result = runCatching { audioManager.requestAudioFocus(audioFocusRequest) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        audioFocusRequested = result != AudioManager.AUDIOFOCUS_REQUEST_FAILED
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return result
    }

    private fun abandonPlaybackAudioFocus() {
        val shouldAbandon = audioFocusRequested
        audioFocusRequested = false
        hasAudioFocus = false
        if (shouldAbandon) runCatching { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
    }

    private fun registerBecomingNoisyReceiver() {
        if (noisyReceiverRegistered) return
        noisyReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                this,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            true
        }.getOrDefault(false)
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        noisyReceiverRegistered = false
    }

    private fun enterForeground(isPlaying: Boolean) {
        mediaNotificationController.startOrUpdate(
            isPlaying = isPlaying,
            asForeground = true,
        )
        hasForegroundNotification = true
    }

    override fun onDestroy() {
        playWhenReady = false
        resumeAfterTransientFocusLoss = false
        prewarmRequestId += 1
        ttsUtterances.invalidate()
        cancelTtsKaraoke()
        cancelNeuralPlayback()
        unregisterBecomingNoisyReceiver()
        abandonPlaybackAudioFocus()
        ttsEngineGeneration += 1
        tts?.stop()
        tts?.shutdown()
        tts = null
        neuralExecutor.execute {
            synchronized(neuralGenerationLock) { neuralTts.release() }
        }
        neuralExecutor.shutdown()
        neuralPrewarmExecutor.shutdown()
        neuralPlaybackExecutor.shutdown()
        mediaNotificationController.release()
        super.onDestroy()
    }

    companion object {
        private const val NEURAL_QUEUE_CAPACITY = 8
        private const val NEURAL_PREWARM_PAGES = 3
        private const val NEURAL_PREWARM_MAX_CHUNKS = 18
        private const val NEURAL_QUEUE_WAIT_MS = 200L
        private const val KARAOKE_INTERVAL_MILLIS = 70L
        private const val ACTION_PLAY = "com.leitorlocal.PLAY"
        private const val ACTION_PREPARE_NEURAL = "com.leitorlocal.PREPARE_NEURAL"
        private const val ACTION_PAUSE = "com.leitorlocal.PAUSE"
        private const val ACTION_NEXT = "com.leitorlocal.NEXT"
        private const val ACTION_PREVIOUS = "com.leitorlocal.PREVIOUS"
        private const val ACTION_RECONFIGURE = "com.leitorlocal.RECONFIGURE"
        private const val ACTION_STOP = "com.leitorlocal.STOP"
        fun play(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, ReadingService::class.java).setAction(ACTION_PLAY),
        )

        fun prepareNeural(context: Context) {
            safeSend(context, ACTION_PREPARE_NEURAL)
        }

        fun pause(context: Context) = safeSend(context, ACTION_PAUSE)
        fun next(context: Context) = safeSend(context, ACTION_NEXT)
        fun previous(context: Context) = safeSend(context, ACTION_PREVIOUS)
        fun reconfigure(context: Context) = safeSend(context, ACTION_RECONFIGURE)
        fun stop(context: Context) = safeSend(context, ACTION_STOP)

        private fun safeSend(context: Context, action: String) {
            runCatching {
                context.startService(Intent(context, ReadingService::class.java).setAction(action))
            }
        }
    }
}

private const val DEFAULT_NEURAL_PREBUFFER_CHUNKS = 1
private const val KOKORO_NEURAL_PREBUFFER_CHUNKS = 2
