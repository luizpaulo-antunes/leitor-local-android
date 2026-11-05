package com.leitorlocal

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.ui.LeitorLocalApp

class MainActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()
    private var playAfterPermission = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (playAfterPermission) viewModel.play()
        playAfterPermission = false
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
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshVoices()
    }

    private fun requestNotificationPermissionAndPlay() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            playAfterPermission = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.play()
        }
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
}
