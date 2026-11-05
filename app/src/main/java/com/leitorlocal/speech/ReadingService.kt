package com.leitorlocal.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.leitorlocal.MainActivity
import com.leitorlocal.R
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

class ReadingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var preferences: ReaderPreferences
    private lateinit var neuralAudioCache: NeuralAudioCache
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var playWhenReady = false
    private val neuralExecutor = Executors.newSingleThreadExecutor()
    private val neuralPlaybackExecutor = Executors.newSingleThreadExecutor()
    private val neuralTts by lazy { EmbeddedNeuralTts(applicationContext) }
    private val neuralPlayer = NeuralAudioPlayer()
    @Volatile private var neuralRequestId = 0L
    @Volatile private var neuralQueue: ArrayBlockingQueue<NeuralQueueItem>? = null
    @Volatile private var prewarmRequestId = 0L
    private var ttsKaraokeGeneration = 0L
    private var ttsFallbackRunnable: Runnable? = null
    private var activeTtsChunk: SpeechChunk? = null
    @Volatile private var hasNativeRangeCallbacks = false
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        preferences = ReaderPreferences(this)
        neuralAudioCache = NeuralAudioCache(this)
        createNotificationChannel()
        initializeMediaSession()
        initializeTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PREPARE_NEURAL -> prepareNeuralAudio()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> move(+1)
            ACTION_PREVIOUS -> move(-1)
            ACTION_RELOAD_ENGINE -> reloadEngine()
            ACTION_STOP -> stopReading()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeTts() {
        tts = TextToSpeech(applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                if (usesEmbeddedNeuralVoice()) {
                    return@TextToSpeech
                } else {
                    ReadingSession.setStatus(PlaybackStatus.ERROR, "O mecanismo de voz não pôde ser iniciado.")
                    stopSelf()
                }
                return@TextToSpeech
            }

            engine.language = Locale.forLanguageTag("pt-BR")
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            engine.setOnUtteranceProgressListener(listener)
            isTtsReady = true
            applySettings()
            if (playWhenReady && !usesEmbeddedNeuralVoice()) speakCurrent()
        }
    }

    private fun reloadEngine() {
        playWhenReady = false
        cancelTtsKaraoke()
        cancelNeuralPlayback()
        isTtsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        initializeTts()
    }

    private fun play() {
        if (!ReadingSession.hasContent()) {
            stopSelf()
            return
        }
        playWhenReady = true
        ReadingSession.setStatus(PlaybackStatus.PREPARING)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        if (usesEmbeddedNeuralVoice() || isTtsReady) speakCurrent()
    }

    private fun pause() {
        playWhenReady = false
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        ReadingSession.setStatus(PlaybackStatus.PAUSED)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = false))
    }

    private fun move(offset: Int) {
        val wasPlaying = ReadingSession.state.value.status in setOf(
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
        ) || playWhenReady
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
            updateNotification(false)
        }
    }

    private fun speakCurrent() {
        if (usesEmbeddedNeuralVoice()) {
            speakCurrentWithNeuralVoice()
            return
        }
        val engine = tts ?: return
        val chunk = ReadingSession.currentChunk() ?: return
        activeTtsChunk = chunk
        cancelTtsKaraoke()
        ReadingSession.clearWordRange()
        applySettings()
        ReadingSession.setStatus(PlaybackStatus.PLAYING)
        updateNotification(true)
        engine.speak(
            chunk.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "$UTTERANCE_PREFIX${ReadingSession.state.value.currentChunkIndex}",
        )
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
        updateNotification(true)

        neuralPlaybackExecutor.execute {
            runCatching { consumeNeuralQueue(requestId, queue, voice) }
                .onFailure { failure ->
                    if (isNeuralRequestActive(requestId)) {
                        handleTtsError(neuralErrorMessage("reproduzir", failure))
                    }
                }
        }
        neuralExecutor.execute {
            try {
                val bookId = ReadingSession.bookId
                var index = startIndex
                while (index < ReadingSession.chunkCount() && isNeuralRequestActive(requestId)) {
                    val chunk = ReadingSession.chunkAt(index) ?: break
                    val audio = getOrCreateNeuralAudio(
                        bookId = bookId,
                        chunkIndex = index,
                        text = chunk.text,
                        speechRate = ReadingSession.speechRate,
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

        neuralExecutor.execute {
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
        val generated = neuralTts.generate(text, speechRate, voice)
        neuralAudioCache.save(bookId, chunkIndex, text, speechRate, voice.name, generated)
        return generated
    }

    private fun consumeNeuralQueue(
        requestId: Long,
        queue: ArrayBlockingQueue<NeuralQueueItem>,
        voice: EmbeddedNeuralVoice,
    ) {
        val buffered = mutableListOf<NeuralQueueItem.Audio>()
        var pendingTerminal: NeuralQueueItem? = null
        while (buffered.size < NEURAL_PREBUFFER_CHUNKS && pendingTerminal == null) {
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
                    handleTtsError(neuralErrorMessage("reproduzir", failure))
                }
            },
            onProgress = { chunk, wordStart, wordEnd ->
                if (!isNeuralRequestActive(requestId)) return@start
                val chunkIndex = ReadingSession.indexOfChunk(chunk) ?: return@start
                if (ReadingSession.state.value.currentChunkIndex != chunkIndex) {
                    ReadingSession.setIndex(chunkIndex)
                    persistPosition()
                    mainHandler.post { updateNotification(true) }
                }
                ReadingSession.setStatus(PlaybackStatus.PLAYING)
                ReadingSession.setWordRange(chunkIndex, wordStart, wordEnd)
            },
        )
        ReadingSession.setStatus(PlaybackStatus.PLAYING)
        mainHandler.post { updateNotification(true) }

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
            is NeuralQueueItem.Failure -> handleTtsError(neuralErrorMessage("preparar", item.cause))
            is NeuralQueueItem.Audio -> Unit
        }
    }

    private fun handleNeuralBookDone(requestId: Long) {
        if (!isNeuralRequestActive(requestId)) return
        playWhenReady = false
        neuralQueue = null
        ReadingSession.setStatus(PlaybackStatus.STOPPED)
        persistPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
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
                val chunkIndex = utteranceChunkIndex(utteranceId) ?: return@post
                if (!playWhenReady || ReadingSession.state.value.currentChunkIndex != chunkIndex) return@post
                ReadingSession.setStatus(PlaybackStatus.PLAYING)
                startTtsKaraokeFallback(chunkIndex)
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            mainHandler.post {
                val chunkIndex = utteranceChunkIndex(utteranceId) ?: return@post
                if (!playWhenReady || ReadingSession.state.value.currentChunkIndex != chunkIndex) return@post
                hasNativeRangeCallbacks = true
                ttsFallbackRunnable?.let(mainHandler::removeCallbacks)
                ReadingSession.setWordRange(chunkIndex, start, end)
            }
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                val chunkIndex = utteranceChunkIndex(utteranceId) ?: return@post
                if (ReadingSession.state.value.currentChunkIndex != chunkIndex) return@post
                cancelTtsKaraoke()
                handleChunkDone()
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            mainHandler.post {
                val chunkIndex = utteranceChunkIndex(utteranceId) ?: return@post
                if (ReadingSession.state.value.currentChunkIndex == chunkIndex) cancelTtsKaraoke()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            handleTtsError()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handleTtsError()
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

    private fun utteranceChunkIndex(utteranceId: String?): Int? =
        utteranceId
            ?.takeIf { it.startsWith(UTTERANCE_PREFIX) }
            ?.let {
                activeTtsChunk?.let(ReadingSession::indexOfChunk)
                    ?: it.removePrefix(UTTERANCE_PREFIX).toIntOrNull()
            }

    private fun handleChunkDone() {
        if (!playWhenReady) return
        if (ReadingSession.isAtEnd()) {
            ReadingSession.setStatus(PlaybackStatus.STOPPED)
            persistPosition()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            ReadingSession.moveBy(+1)
            persistPosition()
            speakCurrent()
        }
    }

    private fun handleTtsError(message: String = "A voz selecionada não conseguiu ler este trecho. Tente outra voz.") {
        mainHandler.post {
            playWhenReady = false
            cancelTtsKaraoke()
            cancelNeuralPlayback()
            ReadingSession.setStatus(
                PlaybackStatus.ERROR,
                message,
            )
            updateNotification(false)
        }
    }

    private fun stopReading() {
        playWhenReady = false
        prewarmRequestId += 1
        cancelTtsKaraoke()
        tts?.stop()
        cancelNeuralPlayback()
        ReadingSession.setStatus(PlaybackStatus.STOPPED)
        persistPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistPosition() {
        preferences.savePosition(ReadingSession.state.value.currentChunkIndex)
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSession(this, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    play()
                }

                override fun onPause() {
                    pause()
                }

                override fun onSkipToNext() {
                    move(+1)
                }

                override fun onSkipToPrevious() {
                    move(-1)
                }

                override fun onStop() {
                    stopReading()
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setSessionActivity(openAppPendingIntent())
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            isActive = true
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        updateMediaSession(isPlaying)
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pausar" else "Continuar"

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(ReadingSession.title)
            .setContentText(ReadingSession.currentChunk()?.text?.take(100) ?: "Leitura em voz alta")
            .setContentIntent(openAppPendingIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Anterior",
                    servicePendingIntent(ACTION_PREVIOUS, 1),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    playPauseIcon,
                    playPauseLabel,
                    servicePendingIntent(playPauseAction, 2),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Próximo",
                    servicePendingIntent(ACTION_NEXT, 3),
                ).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun updateMediaSession(isPlaying: Boolean) {
        val sessionStatus = ReadingSession.state.value.status
        val state = when {
            isPlaying && sessionStatus == PlaybackStatus.PREPARING -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            sessionStatus == PlaybackStatus.ERROR -> PlaybackState.STATE_ERROR
            sessionStatus == PlaybackStatus.STOPPED -> PlaybackState.STATE_STOPPED
            else -> PlaybackState.STATE_PAUSED
        }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, ReadingSession.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .putString(
                    MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
                    ReadingSession.currentChunk()?.text?.take(160) ?: "Leitura em voz alta",
                )
                .build(),
        )
        val playbackState = PlaybackState.Builder()
            .setActions(MEDIA_SESSION_ACTIONS)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (isPlaying) 1f else 0f)
        ReadingSession.state.value.error?.let(playbackState::setErrorMessage)
        mediaSession.setPlaybackState(playbackState.build())
        mediaSession.isActive = true
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ReadingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun updateNotification(isPlaying: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    override fun onDestroy() {
        prewarmRequestId += 1
        cancelTtsKaraoke()
        cancelNeuralPlayback()
        tts?.stop()
        tts?.shutdown()
        tts = null
        neuralExecutor.execute { neuralTts.release() }
        neuralExecutor.shutdown()
        neuralPlaybackExecutor.shutdown()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "book_reading"
        private const val MEDIA_SESSION_TAG = "LeitorLocalReading"
        private const val NOTIFICATION_ID = 41
        private const val NEURAL_QUEUE_CAPACITY = 8
        private const val NEURAL_PREBUFFER_CHUNKS = 2
        private const val NEURAL_PREWARM_PAGES = 3
        private const val NEURAL_PREWARM_MAX_CHUNKS = 18
        private const val NEURAL_QUEUE_WAIT_MS = 200L
        private const val KARAOKE_INTERVAL_MILLIS = 70L
        private const val UTTERANCE_PREFIX = "book-"
        private const val ACTION_PLAY = "com.leitorlocal.PLAY"
        private const val ACTION_PREPARE_NEURAL = "com.leitorlocal.PREPARE_NEURAL"
        private const val ACTION_PAUSE = "com.leitorlocal.PAUSE"
        private const val ACTION_NEXT = "com.leitorlocal.NEXT"
        private const val ACTION_PREVIOUS = "com.leitorlocal.PREVIOUS"
        private const val ACTION_RELOAD_ENGINE = "com.leitorlocal.RELOAD_ENGINE"
        private const val ACTION_STOP = "com.leitorlocal.STOP"
        private const val MEDIA_SESSION_ACTIONS =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_STOP

        fun play(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, ReadingService::class.java).setAction(ACTION_PLAY),
        )

        fun prepareNeural(context: Context) {
            runCatching { send(context, ACTION_PREPARE_NEURAL) }
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun next(context: Context) = send(context, ACTION_NEXT)
        fun previous(context: Context) = send(context, ACTION_PREVIOUS)
        fun reloadEngine(context: Context) = send(context, ACTION_RELOAD_ENGINE)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, ReadingService::class.java).setAction(action))
        }
    }
}
