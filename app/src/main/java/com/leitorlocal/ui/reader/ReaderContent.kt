package com.leitorlocal.ui.reader

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.ReaderUiState
import kotlinx.coroutines.delay

@Composable
internal fun ReaderContent(
    state: ReaderUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRateChanged: (Float) -> Unit,
    onVoiceSelected: (String?) -> Unit,
    onPrepareBook: () -> Unit,
    onCancelBookPreparation: () -> Unit,
) {
    val book = state.book ?: return
    val readingProgress = if (book.chunks.size <= 1) {
        0f
    } else {
        state.playback.currentChunkIndex.toFloat() / (book.chunks.size - 1)
    }
    val isPlaying = state.playback.status in setOf(
        PlaybackStatus.PLAYING,
        PlaybackStatus.PREPARING,
    )
    var settingsExpanded by rememberSaveable(book.uri) { mutableStateOf(false) }
    var showPagesWithoutTextToast by remember(book.uri) { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) settingsExpanded = false
    }
    LaunchedEffect(book.uri, book.pagesWithoutText, state.isOcrRunning) {
        showPagesWithoutTextToast = false
        if (!state.isOcrRunning && book.pagesWithoutText > 0) {
            showPagesWithoutTextToast = true
            delay(3_000)
            showPagesWithoutTextToast = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 620.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compactHeight) 10.dp else 12.dp),
        ) {
            ReaderPage(
                state = state,
                isPlaying = isPlaying,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            CompactPlaybackBar(
                currentPage = state.currentPage,
                pageCount = book.pageCount,
                readingProgress = readingProgress,
                isPlaying = isPlaying,
                compact = compactHeight,
                isOcrRunning = state.isOcrRunning,
                ocrMessage = state.ocrMessage,
                rate = state.speechRate,
                voices = state.voices,
                selectedName = state.selectedVoiceName,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenSettings = { settingsExpanded = true },
            )
            Spacer(Modifier.height(if (compactHeight) 4.dp else 6.dp))
        }

        ReaderSettingsDialog(
            visible = settingsExpanded,
            onDismiss = { settingsExpanded = false },
            rate = state.speechRate,
            onRateChanged = onRateChanged,
            voices = state.voices,
            selectedName = state.selectedVoiceName,
            onVoiceSelected = onVoiceSelected,
            audioPreparation = if (
                state.isOcrRunning && !state.audioPreparation.isRunning
            ) {
                state.audioPreparation.copy(
                    message = "O OCR está preparando as páginas escaneadas. O botão será liberado quando ele terminar.",
                )
            } else {
                state.audioPreparation
            },
            canPrepareBook = state.voices
                .firstOrNull { it.name == state.selectedVoiceName }
                ?.isEmbeddedNeural == true && !state.isOcrRunning && !isPlaying,
            onPrepareBook = onPrepareBook,
            onCancelBookPreparation = onCancelBookPreparation,
        )

        if (showPagesWithoutTextToast) {
            ReaderToast(
                message = "${book.pagesWithoutText} página(s) sem texto legível foram ignoradas.",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (compactHeight) 106.dp else 114.dp)
                    .zIndex(10f),
            )
        }
    }
}

@Composable
private fun ReaderPage(
    state: ReaderUiState,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    // RenderEffect (usado por Modifier.blur) existe a partir do Android 12.
    // Em aparelhos anteriores o overlay uniforme mantém o foco e o contraste.
    val supportsGaussianBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // A camada de blur fica limitada ao viewport, evitando uma textura GPU do tamanho
        // de toda a página rolável.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (isPlaying && supportsGaussianBlur) 3.dp else 0.dp)
                .then(
                    if (isPlaying) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            val bitmap = state.pageBitmap
            if (bitmap == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                key(state.currentPage) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Página ${state.currentPage + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }

        if (isPlaying) KaraokeFocusOverlay(state)
    }
}
