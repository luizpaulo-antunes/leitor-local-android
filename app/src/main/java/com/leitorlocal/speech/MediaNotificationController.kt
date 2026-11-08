package com.leitorlocal.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.leitorlocal.MainActivity
import com.leitorlocal.R
import com.leitorlocal.model.AudioPreparationState
import com.leitorlocal.model.PlaybackStatus

internal data class MediaNotificationActions(
    val play: String,
    val pause: String,
    val next: String,
    val previous: String,
    val cancelPreparation: String,
)

internal data class MediaSessionCallbacks(
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onStop: () -> Unit,
)

/**
 * Owns the Android media surface for an active reading session. Playback remains controlled by
 * [ReadingService]; this class only translates its state and callbacks to MediaSession and the
 * foreground notification.
 */
internal class MediaNotificationController(
    private val service: Service,
    private val audioAttributes: AudioAttributes,
    private val actions: MediaNotificationActions,
    private val callbacks: MediaSessionCallbacks,
) {
    private lateinit var mediaSession: MediaSession

    fun initialize() {
        createNotificationChannel()
        mediaSession = MediaSession(service, MEDIA_SESSION_TAG).apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = callbacks.onPlay()

                    override fun onPause() = callbacks.onPause()

                    override fun onSkipToNext() = callbacks.onNext()

                    override fun onSkipToPrevious() = callbacks.onPrevious()

                    override fun onStop() = callbacks.onStop()
                },
            )
            setSessionActivity(openAppPendingIntent())
            setPlaybackToLocal(audioAttributes)
            isActive = true
        }
    }

    fun startOrUpdate(isPlaying: Boolean, asForeground: Boolean) {
        val notification = buildNotification(isPlaying)
        if (asForeground) {
            service.startForeground(NOTIFICATION_ID, notification)
        } else {
            service.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    fun startPreparation(state: AudioPreparationState) {
        val progressMax = 1_000
        val progress = (state.progress * progressMax).toInt().coerceIn(0, progressMax)
        val notification = Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Preparando leitura contínua")
            .setContentText(
                if (state.preparedDurationMillis > 0L) {
                    buildString {
                        append(formatPreparationDuration(state.preparedDurationMillis))
                        append(" de áudio prontos")
                        state.estimatedWaitMillis?.takeIf { it > 0L }?.let {
                            append(" · espera estimada ")
                            append(formatPreparationDuration(it))
                        }
                    }
                } else {
                    "Medindo a velocidade da voz offline"
                },
            )
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(buildPreparationPublicNotification())
            .setShowWhen(false)
            .setProgress(progressMax, progress, state.targetDurationMillis <= 0L)
            .addAction(
                notificationAction(
                    iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                    label = "Cancelar",
                    intent = servicePendingIntent(actions.cancelPreparation, 4),
                ),
            )
            .build()
        service.startForeground(NOTIFICATION_ID, notification)
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            service.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = service.getString(R.string.notification_channel_description)
        }
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        updateMediaSession(isPlaying)
        val playPauseAction = if (isPlaying) actions.pause else actions.play
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) "Pausar" else "Continuar"

        return Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(ReadingSession.title)
            .setContentText(ReadingSession.currentChunk()?.text?.take(100) ?: "Leitura em voz alta")
            .setContentIntent(openAppPendingIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(buildRedactedPublicNotification(isPlaying))
            .setShowWhen(false)
            .addAction(
                notificationAction(
                    iconRes = android.R.drawable.ic_media_previous,
                    label = "Anterior",
                    intent = servicePendingIntent(actions.previous, 1),
                ),
            )
            .addAction(
                notificationAction(
                    iconRes = playPauseIcon,
                    label = playPauseLabel,
                    intent = servicePendingIntent(playPauseAction, 2),
                ),
            )
            .addAction(
                notificationAction(
                    iconRes = android.R.drawable.ic_media_next,
                    label = "Próximo",
                    intent = servicePendingIntent(actions.next, 3),
                ),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun buildRedactedPublicNotification(isPlaying: Boolean): Notification =
        Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(if (isPlaying) "Leitura em andamento" else "Leitura pausada")
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .build()

    private fun buildPreparationPublicNotification(): Notification =
        Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText("Preparando áudio offline")
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .build()

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_ACTIVE_READING
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            service,
            requestCode,
            Intent(service, ReadingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationAction(
        iconRes: Int,
        label: String,
        intent: PendingIntent,
    ): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(service, iconRes),
        label,
        intent,
    ).build()

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
                // MediaSession metadata can be read by lock-screen surfaces and other media
                // controllers. Keep it generic so book titles and excerpts never leak outside
                // the app's private notification.
                .putString(
                    MediaMetadata.METADATA_KEY_TITLE,
                    service.getString(R.string.app_name),
                )
                .putString(
                    MediaMetadata.METADATA_KEY_ARTIST,
                    if (isPlaying) "Leitura em andamento" else "Leitura pausada",
                )
                .putString(
                    MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
                    "Livro aberto no aplicativo",
                )
                .build(),
        )
        val playbackState = PlaybackState.Builder()
            .setActions(MEDIA_SESSION_ACTIONS)
            .setState(
                state,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (isPlaying) 1f else 0f,
            )
        ReadingSession.state.value.error?.let(playbackState::setErrorMessage)
        mediaSession.setPlaybackState(playbackState.build())
        mediaSession.isActive = true
    }

    private companion object {
        const val CHANNEL_ID = "book_reading"
        const val MEDIA_SESSION_TAG = "LeitorLocalReading"
        const val NOTIFICATION_ID = 41
        const val MEDIA_SESSION_ACTIONS =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_STOP
    }
}
