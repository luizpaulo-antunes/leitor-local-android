package com.leitorlocal.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leitorlocal.ReaderViewModel
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.LibraryBook
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.model.VoiceOption
import java.util.Locale
import kotlin.math.roundToInt

private val LightColors = lightColorScheme(
    primary = Color(0xFF315D49),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEDE1),
    onPrimaryContainer = Color(0xFF153B29),
    secondary = Color(0xFF805A45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCA),
    onSecondaryContainer = Color(0xFF3A1708),
    tertiary = Color(0xFF53644D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E8CD),
    onTertiaryContainer = Color(0xFF132610),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFECE9E1),
    onSurfaceVariant = Color(0xFF454842),
    outline = Color(0xFF7A7E78),
    outlineVariant = Color(0xFFC5C8C1),
    surfaceTint = Color(0xFF315D49),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D3B6),
    onPrimary = Color(0xFF073921),
    primaryContainer = Color(0xFF214C39),
    onPrimaryContainer = Color(0xFFC0EED0),
    secondary = Color(0xFFE4B39C),
    onSecondary = Color(0xFF442A1D),
    secondaryContainer = Color(0xFF5B3F31),
    onSecondaryContainer = Color(0xFFFFDBCA),
    tertiary = Color(0xFFBBCBAD),
    onTertiary = Color(0xFF263422),
    tertiaryContainer = Color(0xFF3C4B37),
    onTertiaryContainer = Color(0xFFD7E8CD),
    background = Color(0xFF171A17),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF20231F),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF3F4540),
    onSurfaceVariant = Color(0xFFC1C8C0),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
    surfaceTint = Color(0xFFA5D3B6),
)

@Composable
fun LeitorLocalApp(
    viewModel: ReaderViewModel,
    onOpenPdf: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkMode = if (LocalInspectionMode.current) false else
        (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    MaterialTheme(colorScheme = if (darkMode) DarkColors else LightColors) {
        Scaffold(
            topBar = {
                ReaderTopBar(
                    title = if (state.showingLibrary) "Leitor Local" else state.book?.title ?: "Leitor Local",
                    showLibraryButton = !state.showingLibrary,
                    openButtonLabel = if (state.showingLibrary) "Adicionar PDF" else "Outro PDF",
                    onShowLibrary = viewModel::showLibrary,
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
private fun LibraryContent(
    books: List<LibraryBook>,
    onOpenPdf: () -> Unit,
    onBookSelected: (LibraryBook) -> Unit,
) {
    if (books.isEmpty()) {
        WelcomeContent(onOpenPdf)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 6.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                Text(
                    text = "Sua biblioteca",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (books.size == 1) "1 livro salvo neste celular" else "${books.size} livros salvos neste celular",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        items(books, key = LibraryBook::uri) { book ->
            LibraryBookCard(book = book, onClick = { onBookSelected(book) })
        }
    }
}

@Composable
private fun LibraryBookCard(book: LibraryBook, onClick: () -> Unit) {
    val coverBitmap = remember(book.coverPath) {
        book.coverPath?.let(BitmapFactory::decodeFile)
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "Capa de ${book.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${(book.progress * 100).roundToInt()}% lido",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (book.pageCount > 0) {
                        Text(
                            text = "${book.pageCount} pág.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeContent(onOpenPdf: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Seu livro, lido no seu celular.",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sem conta, sem servidor e sem enviar o PDF para a internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onOpenPdf,
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Abrir um PDF")
        }
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
    val readingProgress = if (book.chunks.size <= 1) 0f else
        state.playback.currentChunkIndex.toFloat() / (book.chunks.size - 1)
    val isPlaying = state.playback.status in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)
    var settingsExpanded by rememberSaveable(book.uri) { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) settingsExpanded = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 620.dp
        val useSettingsDialog = maxHeight < 700.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compactHeight) 10.dp else 12.dp),
        ) {
            ReaderPage(
                state = state,
                isPlaying = isPlaying,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Spacer(Modifier.height(if (compactHeight) 7.dp else 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Página ${state.currentPage + 1} de ${book.pageCount}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${(readingProgress * 100).roundToInt()}% lido",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { readingProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            OcrStatus(
                isRunning = state.isOcrRunning,
                message = state.ocrMessage,
                pagesWithoutText = book.pagesWithoutText,
            )

            PlaybackControls(
                isPlaying = isPlaying,
                compact = compactHeight,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
            )

            ReaderSettings(
                expanded = settingsExpanded,
                onExpandedChange = { settingsExpanded = !settingsExpanded },
                compact = compactHeight,
                useDialog = useSettingsDialog,
                rate = state.speechRate,
                onRateChanged = onRateChanged,
                voices = state.voices,
                selectedName = state.selectedVoiceName,
                onVoiceSelected = onVoiceSelected,
            )
            Spacer(Modifier.height(if (compactHeight) 6.dp else 10.dp))
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
    // Em aparelhos anteriores o scrim adaptativo do overlay mantém o foco e o contraste.
    val supportsGaussianBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Card(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
            shape = RoundedCornerShape(16.dp),
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
}

@Composable
private fun KaraokeFocusOverlay(state: ReaderUiState) {
    val chunk = state.currentChunk ?: return
    val figureBitmap = remember(chunk.figure?.imagePath) {
        chunk.figure?.imagePath?.let(BitmapFactory::decodeFile)
    }
    val focusedBitmap = figureBitmap ?: state.pageBitmap.takeIf { chunk.figure != null }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val supportsGaussianBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val panelShape = RoundedCornerShape(26.dp)
    val scrimAlpha = when {
        !supportsGaussianBlur && isDark -> 0.18f
        !supportsGaussianBlur -> 0.06f
        isDark -> 0.12f
        else -> 0.03f
    }
    val glassBackground = Brush.verticalGradient(
        colors = when {
            !supportsGaussianBlur && isDark -> listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                Color.Black.copy(alpha = 0.84f),
            )
            !supportsGaussianBlur -> listOf(
                Color.White.copy(alpha = 0.78f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
            )
            isDark -> listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                Color.Black.copy(alpha = 0.62f),
            )
            else -> listOf(
                Color.White.copy(alpha = 0.50f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            )
        },
    )
    val glassBorder = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.08f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.58f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.20f),
            )
        },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.03f else 0.02f),
                        Color.Transparent,
                    ),
                    radius = 1_100f,
                ),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val panelMaxHeight = maxHeight * if (chunk.figure != null) 0.94f else 0.84f
        key(state.playback.currentChunkIndex) {
            val focusScrollState = rememberScrollState()
            var viewportHeightPx by remember { mutableIntStateOf(0) }
            var textTopPx by remember { mutableIntStateOf(0) }
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val activeWordStart = state.playback.currentWordStart
            val activeWordEnd = state.playback.currentWordEnd
            val maxScroll = focusScrollState.maxValue

            LaunchedEffect(
                activeWordStart,
                activeWordEnd,
                textTopPx,
                textLayout,
                viewportHeightPx,
                maxScroll,
            ) {
                val layout = textLayout ?: return@LaunchedEffect
                if (
                    activeWordEnd <= activeWordStart ||
                    activeWordStart !in chunk.text.indices ||
                    viewportHeightPx <= 0 ||
                    maxScroll <= 0
                ) {
                    return@LaunchedEffect
                }
                val wordBox = layout.getBoundingBox(activeWordStart)
                val target = (
                    textTopPx + wordBox.center.y - viewportHeightPx * 0.48f
                ).roundToInt().coerceIn(0, maxScroll)
                val distance = target - focusScrollState.value
                if (kotlin.math.abs(distance) > 8) {
                    focusScrollState.animateScrollBy(distance.toFloat())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .heightIn(min = 220.dp, max = panelMaxHeight)
                    .shadow(
                        elevation = 8.dp,
                        shape = panelShape,
                        clip = false,
                    )
                    .clip(panelShape)
                    .background(glassBackground)
                    .border(width = 1.dp, brush = glassBorder, shape = panelShape)
                    .onSizeChanged { viewportHeightPx = it.height }
                    .semantics { paneTitle = "Trecho em leitura" },
            ) {
                // Um brilho suave no topo reforça o vidro sem reduzir a legibilidade.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.025f else 0.06f),
                                    Color.Transparent,
                                ),
                                endY = 420f,
                            ),
                        ),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(focusScrollState)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Surface(
                        modifier = Modifier.border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = if (isDark) 0.22f else 0.12f,
                            ),
                            shape = CircleShape,
                        ),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = if (supportsGaussianBlur) 0.76f else 0.94f,
                        ),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = when {
                                state.playback.status == PlaybackStatus.PREPARING && chunk.figure != null ->
                                    "Preparando descrição da figura…"
                                state.playback.status == PlaybackStatus.PREPARING ->
                                    "Preparando voz…"
                                chunk.figure != null -> "Figura · lendo agora"
                                else -> "Lendo agora"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (focusedBitmap != null && chunk.figure != null) {
                        Image(
                            bitmap = focusedBitmap.asImageBitmap(),
                            contentDescription = chunk.figure.caption.ifBlank {
                                chunk.figure.alternativeText.ifBlank { "Figura do livro" }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    (focusedBitmap.width.toFloat() / focusedBitmap.height)
                                        .coerceIn(0.55f, 2.4f),
                                )
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    val activeWordColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val activeWordBackground = MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = if (isDark) 0.92f else 0.82f,
                    )
                    val highlightedText = buildAnnotatedString {
                        append(chunk.text)
                        if (
                            activeWordEnd > activeWordStart &&
                            activeWordStart >= 0 &&
                            activeWordEnd <= chunk.text.length
                        ) {
                            addStyle(
                                SpanStyle(
                                    color = activeWordColor,
                                    background = activeWordBackground,
                                ),
                                activeWordStart,
                                activeWordEnd,
                            )
                        }
                    }
                    Text(
                        text = highlightedText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Serif,
                        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.18f,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val contentTop = coordinates.positionInParent().y.roundToInt()
                            if (textTopPx != contentTop) textTopPx = contentTop
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrStatus(
    isRunning: Boolean,
    message: String,
    pagesWithoutText: Int,
) {
    if (!isRunning && pagesWithoutText <= 0) return
    val containerColor = if (isRunning) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isRunning) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isRunning) {
                    message.ifBlank { "OCR offline preparando páginas escaneadas…" }
                } else {
                    "$pagesWithoutText página(s) sem texto legível foram ignoradas."
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackNavigationButton(
                isNext = false,
                description = "Trecho anterior",
                onClick = onPrevious,
            )
            Button(
                onClick = onPlayPause,
                modifier = Modifier.heightIn(min = 50.dp).widthIn(min = 132.dp),
                shape = CircleShape,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(
                    text = if (isPlaying) "Pausar" else "Ouvir",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            PlaybackNavigationButton(
                isNext = true,
                description = "Próximo trecho",
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun PlaybackNavigationButton(
    isNext: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isNext) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                },
                contentDescription = description,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ReaderSettings(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    compact: Boolean,
    useDialog: Boolean,
    rate: Float,
    onRateChanged: (Float) -> Unit,
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
) {
    val selected = voices.firstOrNull { it.name == selectedName }
    val rateLabel = String.format(Locale.forLanguageTag("pt-BR"), "%.1fx", rate)
    val voiceLabel = selected?.label
        ?.substringBefore("—")
        ?.substringBefore("·")
        ?.trim()
        ?.ifBlank { null }
        ?: "Escolher voz"
    val connectionLabel = when {
        selected == null -> ""
        selected.requiresNetwork -> " · online"
        else -> " · offline"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Surface(
            onClick = onExpandedChange,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Ajustes de leitura",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$voiceLabel · $rateLabel$connectionLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
        }
        if (expanded && !useDialog) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            ReaderSettingsControls(
                rate = rate,
                rateLabel = rateLabel,
                onRateChanged = onRateChanged,
                voices = voices,
                selectedName = selectedName,
                onVoiceSelected = onVoiceSelected,
                modifier = Modifier
                    .heightIn(max = if (compact) 220.dp else 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }

    if (expanded && useDialog) {
        Dialog(onDismissRequest = onExpandedChange) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Ajustes de leitura",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "$voiceLabel · $rateLabel$connectionLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = onExpandedChange) { Text("Fechar") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    ReaderSettingsControls(
                        rate = rate,
                        rateLabel = rateLabel,
                        onRateChanged = onRateChanged,
                        voices = voices,
                        selectedName = selectedName,
                        onVoiceSelected = onVoiceSelected,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsControls(
    rate: Float,
    rateLabel: String,
    onRateChanged: (Float) -> Unit,
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Velocidade",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = rateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = rate,
            onValueChange = onRateChanged,
            valueRange = 0.6f..1.6f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        VoiceSelector(
            voices = voices,
            selectedName = selectedName,
            onVoiceSelected = onVoiceSelected,
        )
        Text(
            text = "Miro e Dii são vozes neurais offline. As demais opções exibidas também são do português do Brasil.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
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
        onExpandedChange = { if (voices.isNotEmpty()) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = voices.isNotEmpty(),
                )
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Voz da narração",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selected?.label ?: "Nenhuma voz em português encontrada",
                        maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
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
                val isSelected = voice.name == selectedName
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = voice.label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                buildString {
                                    if (isSelected) append("Selecionada · ")
                                    append(if (voice.requiresNetwork) "Online" else "Offline")
                                    append(" · ${voice.localeTag}")
                                    if (voice.isHighQuality) append(" · alta qualidade")
                                    if (voice.isEmbeddedNeural) append(" · neural incorporada")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Voz selecionada",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
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
