package com.leitorlocal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leitorlocal.ReaderViewModel
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.model.VoiceOption
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5B4B),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A44),
    background = Color(0xFFFAF8F2),
    surface = Color(0xFFFFFDF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCDB2),
    secondary = Color(0xFFE4B39C),
    background = Color(0xFF181815),
    surface = Color(0xFF22221E),
)

@Composable
fun LeitorLocalApp(
    viewModel: ReaderViewModel,
    onOpenPdf: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val darkMode = if (LocalInspectionMode.current) false else
        (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    MaterialTheme(colorScheme = if (darkMode) DarkColors else LightColors) {
        Scaffold(
            topBar = {
                ReaderTopBar(
                    title = state.book?.title ?: "Leitor Local",
                    showOpenButton = state.book != null,
                    onOpenPdf = onOpenPdf,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
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

                if (state.isLoading) LoadingOverlay(state.loadingMessage)

                state.error?.let { message ->
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
private fun ReaderTopBar(title: String, showOpenButton: Boolean, onOpenPdf: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            if (showOpenButton) TextButton(onClick = onOpenPdf) { Text("Outro PDF") }
        },
    )
}

@Composable
private fun WelcomeContent(onOpenPdf: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Seu livro, lido no seu celular.",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sem conta, sem servidor e sem enviar o PDF para a internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenPdf) { Text("Abrir um PDF") }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRateChanged: (Float) -> Unit,
    onVoiceSelected: (String?) -> Unit,
) {
    val book = state.book ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.TopCenter,
        ) {
            val bitmap = state.pageBitmap
            if (bitmap == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
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

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Página ${state.currentPage + 1} de ${book.pageCount}  ·  " +
                "Trecho ${state.playback.currentChunkIndex + 1} de ${book.chunks.size}",
            style = MaterialTheme.typography.labelLarge,
        )
        state.currentChunk?.let { chunk ->
            Text(
                text = chunk.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        if (book.pagesWithoutText > 0) {
            Text(
                text = "${book.pagesWithoutText} página(s) sem texto serão ignoradas até adicionarmos OCR.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        PlaybackControls(
            isPlaying = state.playback.status == PlaybackStatus.PLAYING,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Velocidade", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.speechRate,
                onValueChange = onRateChanged,
                valueRange = 0.6f..1.6f,
                steps = 9,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(String.format(Locale.forLanguageTag("pt-BR"), "%.1fx", state.speechRate))
        }

        VoiceSelector(
            voices = state.voices,
            selectedName = state.selectedVoiceName,
            onVoiceSelected = onVoiceSelected,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPrevious) { Text("‹ Anterior") }
        Button(onClick = onPlayPause) { Text(if (isPlaying) "Pausar" else "Ouvir") }
        OutlinedButton(onClick = onNext) { Text("Próximo ›") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelector(
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.name == selectedName }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            onClick = { expanded = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Voz", style = MaterialTheme.typography.labelMedium)
                    Text(
                        selected?.label ?: "Nenhuma voz em português encontrada",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(voice.label)
                            Text(
                                voice.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onVoiceSelected(voice.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
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
private fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    }
}
