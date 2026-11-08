package com.leitorlocal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leitorlocal.ReaderViewModel
import com.leitorlocal.ui.library.LibraryContent
import com.leitorlocal.ui.library.WelcomeContent
import com.leitorlocal.ui.reader.ReaderContent
import com.leitorlocal.ui.reader.BookAssistantDialog
import com.leitorlocal.ui.theme.ReaderTheme

@Composable
fun LeitorLocalApp(
    viewModel: ReaderViewModel,
    onOpenPdf: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReaderTheme {
        Scaffold(
            topBar = {
                ReaderTopBar(
                    title = if (state.showingLibrary) {
                        "Leitor Local"
                    } else {
                        state.book?.title ?: "Leitor Local"
                    },
                    showLibraryButton = !state.showingLibrary,
                    openButtonLabel = if (state.showingLibrary) "Adicionar PDF" else "Outro PDF",
                    onShowLibrary = viewModel::showLibrary,
                    onOpenPdf = onOpenPdf,
                    onOpenAssistant = viewModel::openAssistant,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    state.showingLibrary -> LibraryContent(
                        books = state.library,
                        onOpenPdf = onOpenPdf,
                        onBookSelected = viewModel::openLibraryBook,
                    )
                    state.book != null -> ReaderContent(
                        state = state,
                        onPlayPause = onPlayPause,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onRateChanged = viewModel::changeRate,
                        onVoiceSelected = viewModel::selectVoice,
                    )
                    else -> WelcomeContent(onOpenPdf)
                }

                BookAssistantDialog(
                    state = state.assistant,
                    onDismiss = viewModel::closeAssistant,
                    onSearch = viewModel::searchBook,
                    onSummarize = viewModel::summarizeCurrentChapter,
                    onExplain = viewModel::explainCurrentChunk,
                    onSourceSelected = viewModel::openAssistantSource,
                )

                if (state.isLoading) LoadingOverlay(state.loadingMessage)

                (state.error ?: state.playback.error)?.let { message ->
                    ErrorCard(
                        message = message,
                        onDismiss = viewModel::clearError,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    showLibraryButton: Boolean,
    openButtonLabel: String,
    onShowLibrary: () -> Unit,
    onOpenPdf: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            if (showLibraryButton) {
                IconButton(onClick = onShowLibrary) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar para a biblioteca",
                    )
                }
            }
        },
        actions = {
            if (showLibraryButton) {
                IconButton(onClick = onOpenAssistant) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar e entender o livro",
                    )
                }
            }
            IconButton(onClick = onOpenPdf) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = openButtonLabel,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(message.ifBlank { "Preparando o livro…" })
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    }
}
