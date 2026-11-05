package com.leitorlocal.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.leitorlocal.MainActivity
import com.leitorlocal.R
import com.leitorlocal.data.ReaderPreferences
import com.leitorlocal.model.PlaybackStatus
import java.util.Locale

class ReadingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var preferences: ReaderPreferences
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var playWhenReady = false

    override fun onCreate() {
        super.onCreate()
        preferences = ReaderPreferences(this)
        createNotificationChannel()
        initializeTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
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
                ReadingSession.setStatus(PlaybackStatus.ERROR, "O mecanismo de voz não pôde ser iniciado.")
                stopSelf()
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
            if (playWhenReady) speakCurrent()
        }
    }

    private fun reloadEngine() {
        playWhenReady = false
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
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))
        playWhenReady = true
        if (isTtsReady) speakCurrent()
    }

    private fun pause() {
        playWhenReady = false
        tts?.stop()
        ReadingSession.setStatus(PlaybackStatus.PAUSED)
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = false))
    }

    private fun move(offset: Int) {
        val wasPlaying = ReadingSession.state.value.status == PlaybackStatus.PLAYING || playWhenReady
        tts?.stop()
        ReadingSession.moveBy(offset)
        persistPosition()
        if (wasPlaying) {
            playWhenReady = true
            if (isTtsReady) speakCurrent()
        } else {
            ReadingSession.setStatus(PlaybackStatus.PAUSED)
            updateNotification(false)
        }
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        val chunk = ReadingSession.currentChunk() ?: return
        applySettings()
        ReadingSession.setStatus(PlaybackStatus.PLAYING)
        updateNotification(true)
        engine.speak(
            chunk.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "book-${ReadingSession.state.value.currentChunkIndex}",
        )
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
            ReadingSession.setStatus(PlaybackStatus.PLAYING)
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                if (!playWhenReady) return@post
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
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            handleTtsError()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handleTtsError()
        }
    }

    private fun handleTtsError() {
        mainHandler.post {
            playWhenReady = false
            ReadingSession.setStatus(
                PlaybackStatus.ERROR,
                "A voz selecionada não conseguiu ler este trecho. Tente outra voz.",
            )
            updateNotification(false)
        }
    }

    private fun stopReading() {
        playWhenReady = false
        tts?.stop()
        ReadingSession.setStatus(PlaybackStatus.STOPPED)
        persistPosition()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistPosition() {
        preferences.savePosition(ReadingSession.state.value.currentChunkIndex)
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

    private fun buildNotification(isPlaying: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pausar" else "Continuar"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(ReadingSession.title)
            .setContentText(ReadingSession.currentChunk()?.text?.take(100) ?: "Leitura em voz alta")
            .setContentIntent(openApp)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Anterior",
                servicePendingIntent(ACTION_PREVIOUS, 1),
            )
            .addAction(playPauseIcon, playPauseLabel, servicePendingIntent(playPauseAction, 2))
            .addAction(
                android.R.drawable.ic_media_next,
                "Próximo",
                servicePendingIntent(ACTION_NEXT, 3),
            )
            .build()
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
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "book_reading"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_PLAY = "com.leitorlocal.PLAY"
        private const val ACTION_PAUSE = "com.leitorlocal.PAUSE"
        private const val ACTION_NEXT = "com.leitorlocal.NEXT"
        private const val ACTION_PREVIOUS = "com.leitorlocal.PREVIOUS"
        private const val ACTION_RELOAD_ENGINE = "com.leitorlocal.RELOAD_ENGINE"
        private const val ACTION_STOP = "com.leitorlocal.STOP"

        fun play(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, ReadingService::class.java).setAction(ACTION_PLAY),
        )

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
