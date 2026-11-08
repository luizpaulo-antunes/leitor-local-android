package com.leitorlocal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.ui.LeitorLocalApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()
    private var actionAfterNotificationPermission = PendingNotificationAction.NONE
    private var isActivityResumed = false
    private var hasWindowFocus = false
    private var isKeepingScreenOn = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        when (actionAfterNotificationPermission) {
            PendingNotificationAction.PLAY -> viewModel.play()
            PendingNotificationAction.PREPARE_BOOK -> viewModel.prepareBookAudio()
            PendingNotificationAction.NONE -> Unit
        }
        actionAfterNotificationPermission = PendingNotificationAction.NONE
    }

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.openBook(uri, displayName(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateKeepScreenOn()
                }
            }
        }
        setContent {
            LeitorLocalApp(
                viewModel = viewModel,
                onOpenPdf = { openPdf.launch(arrayOf("application/pdf")) },
                onPlayPause = {
                    if (viewModel.uiState.value.playback.status in setOf(
                            PlaybackStatus.PLAYING,
                            PlaybackStatus.PREPARING,
                        )
                    ) {
                        viewModel.pause()
                    } else {
                        requestNotificationPermissionAndPlay()
                    }
                },
                onPrepareBook = {
                    requestNotificationPermission(PendingNotificationAction.PREPARE_BOOK)
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        viewModel.refreshVoices()
        updateKeepScreenOn()
    }

    override fun onPause() {
        isActivityResumed = false
        updateKeepScreenOn()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocus = hasFocus
        updateKeepScreenOn()
    }

    override fun onDestroy() {
        clearKeepScreenOn()
        super.onDestroy()
    }

    private fun requestNotificationPermissionAndPlay() {
        requestNotificationPermission(PendingNotificationAction.PLAY)
    }

    private fun requestNotificationPermission(action: PendingNotificationAction) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            actionAfterNotificationPermission = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            when (action) {
                PendingNotificationAction.PLAY -> viewModel.play()
                PendingNotificationAction.PREPARE_BOOK -> viewModel.prepareBookAudio()
                PendingNotificationAction.NONE -> Unit
            }
        }
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_ACTIVE_READING) {
            viewModel.resumeActiveReading()
            // A ação representa um evento único. Mantê-la no Intent da Activity faria uma
            // rotação ou recriação posterior reabrir o leitor depois de o usuário voltar à
            // biblioteca.
            intent.action = null
        }
    }

    private fun updateKeepScreenOn() {
        val state = viewModel.uiState.value
        val isReaderSpeaking =
            !state.showingLibrary &&
                state.book != null &&
                state.playback.status in setOf(
                    PlaybackStatus.PLAYING,
                    PlaybackStatus.PREPARING,
                )
        val shouldKeepScreenOn = isActivityResumed && hasWindowFocus && isReaderSpeaking
        if (shouldKeepScreenOn == isKeepingScreenOn) return

        isKeepingScreenOn = shouldKeepScreenOn
        if (shouldKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun clearKeepScreenOn() {
        if (!isKeepingScreenOn) return
        isKeepingScreenOn = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun displayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else "Livro.pdf"
            } else {
                "Livro.pdf"
            }
        } finally {
            cursor?.close()
        }
    }

    companion object {
        const val ACTION_OPEN_ACTIVE_READING = "com.leitorlocal.OPEN_ACTIVE_READING"
    }

    private enum class PendingNotificationAction {
        NONE,
        PLAY,
        PREPARE_BOOK,
    }
}
