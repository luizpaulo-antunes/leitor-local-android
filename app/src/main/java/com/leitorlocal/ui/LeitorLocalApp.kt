package com.leitorlocal.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.shadow.Shadow
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leitorlocal.ReaderViewModel
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.LibraryBook
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.model.SpeechTextRole
import com.leitorlocal.model.VoiceOption
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val KaraokeCardColor = Color(0xFFFAF8F5)
private val KaraokeTextColor = Color(0xFF272521)
private val ActiveWordBackground = Color(0xFFC8E6D4)
private val ActiveWordText = Color(0xFF1B4332)

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
                modifier = Modifier.weight(1f).fillMaxWidth(),
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

@Composable
private fun KaraokeFocusOverlay(state: ReaderUiState) {
    val chunk = state.currentChunk ?: return
    val figureBitmap = remember(chunk.figure?.imagePath) {
        chunk.figure?.imagePath?.let(BitmapFactory::decodeFile)
    }
    val focusedBitmap = figureBitmap ?: state.pageBitmap.takeIf { chunk.figure != null }
    val panelShape = RoundedCornerShape(20.dp)
    val isReadingHeading = chunk.ranges.any { range ->
        range.role == SpeechTextRole.HEADING &&
            state.playback.currentWordStart in range.start until range.endExclusive
    }
    val badgeLabel = when {
        state.playback.status == PlaybackStatus.PREPARING && chunk.figure != null ->
            "Preparando descrição da figura…"
        state.playback.status == PlaybackStatus.PREPARING -> "Preparando voz…"
        chunk.figure != null -> "Figura · lendo agora"
        isReadingHeading -> "Título · lendo agora"
        else -> "Lendo agora"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f))
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
                    .heightIn(max = panelMaxHeight)
                    .dropShadow(
                        shape = panelShape,
                        shadow = Shadow(
                            radius = 32.dp,
                            spread = 0.dp,
                            offset = DpOffset(0.dp, 12.dp),
                            color = Color.Black.copy(alpha = 0.12f),
                        ),
                    )
                    .clip(panelShape)
                    .background(KaraokeCardColor)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.06f),
                        shape = panelShape,
                    )
                    .onSizeChanged { viewportHeightPx = it.height }
                    .semantics { paneTitle = "Trecho em leitura" },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(focusScrollState)
                        .padding(24.dp),
                ) {
                    ReadingBadge(badgeLabel)
                    Spacer(Modifier.height(16.dp))
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
                    val hasActiveWord =
                        activeWordEnd > activeWordStart &&
                            activeWordStart >= 0 &&
                            activeWordEnd <= chunk.text.length
                    val headingFontSize = MaterialTheme.typography.titleLarge.fontSize
                    val highlightedText = buildAnnotatedString {
                        append(chunk.text)
                        chunk.ranges
                            .filter { range ->
                                range.role == SpeechTextRole.HEADING &&
                                    range.start >= 0 &&
                                    range.endExclusive in (range.start + 1)..chunk.text.length
                            }
                            .forEach { range ->
                                addStyle(
                                    SpanStyle(
                                        color = ActiveWordText,
                                        fontSize = headingFontSize,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    range.start,
                                    range.endExclusive,
                                )
                            }
                        if (hasActiveWord) {
                            addStyle(
                                SpanStyle(color = ActiveWordText),
                                activeWordStart,
                                activeWordEnd,
                            )
                        }
                    }
                    Text(
                        text = highlightedText,
                        style = MaterialTheme.typography.titleMedium,
                        color = KaraokeTextColor,
                        fontFamily = FontFamily.Serif,
                        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.18f,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier
                            .drawBehind {
                                val layout = textLayout
                                if (!hasActiveWord || layout == null) return@drawBehind

                                val firstLine = layout.getLineForOffset(activeWordStart)
                                val lastLine = layout.getLineForOffset(activeWordEnd - 1)
                                val horizontalPadding = 5.dp.toPx()
                                val verticalPadding = 2.dp.toPx()
                                val cornerRadius = CornerRadius(6.dp.toPx())

                                for (line in firstLine..lastLine) {
                                    val segmentStart = maxOf(
                                        activeWordStart,
                                        layout.getLineStart(line),
                                    )
                                    val segmentEnd = minOf(
                                        activeWordEnd,
                                        layout.getLineEnd(line, visibleEnd = true),
                                    )
                                    if (segmentEnd <= segmentStart) continue

                                    var left = Float.POSITIVE_INFINITY
                                    var top = Float.POSITIVE_INFINITY
                                    var right = Float.NEGATIVE_INFINITY
                                    var bottom = Float.NEGATIVE_INFINITY
                                    for (offset in segmentStart until segmentEnd) {
                                        val bounds = layout.getBoundingBox(offset)
                                        left = minOf(left, bounds.left)
                                        top = minOf(top, bounds.top)
                                        right = maxOf(right, bounds.right)
                                        bottom = maxOf(bottom, bounds.bottom)
                                    }
                                    if (!left.isFinite() || !right.isFinite()) continue

                                    drawRoundRect(
                                        color = ActiveWordBackground,
                                        topLeft = Offset(
                                            x = left - horizontalPadding,
                                            y = top - verticalPadding,
                                        ),
                                        size = Size(
                                            width = right - left + horizontalPadding * 2f,
                                            height = bottom - top + verticalPadding * 2f,
                                        ),
                                        cornerRadius = cornerRadius,
                                    )
                                }
                            }
                            .onGloballyPositioned { coordinates ->
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
private fun ReadingBadge(label: String) {
    Surface(
        color = ActiveWordBackground,
        contentColor = ActiveWordText,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioWaveIcon(color = ActiveWordText)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AudioWaveIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 18.dp, height = 14.dp)) {
        val barHeights = listOf(0.38f, 0.72f, 1f, 0.72f, 0.38f)
        val spacing = size.width / (barHeights.size + 1)
        val centerY = size.height / 2f
        barHeights.forEachIndexed { index, fraction ->
            val halfHeight = size.height * fraction / 2f
            val x = spacing * (index + 1)
            drawLine(
                color = color,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun OcrStatus(
    isRunning: Boolean,
    message: String,
) {
    if (!isRunning) return
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message.ifBlank { "OCR offline preparando páginas escaneadas…" },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ReaderToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 12.dp,
                    spread = 0.dp,
                    offset = DpOffset(0.dp, 4.dp),
                    color = Color.Black.copy(alpha = 0.18f),
                ),
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = shape,
        color = Color(0xFF2D2E2B),
        contentColor = Color.White,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CompactPlaybackBar(
    currentPage: Int,
    pageCount: Int,
    readingProgress: Float,
    isPlaying: Boolean,
    compact: Boolean,
    isOcrRunning: Boolean,
    ocrMessage: String,
    rate: Float,
    voices: List<VoiceOption>,
    selectedName: String?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val selected = voices.firstOrNull { it.name == selectedName }
    val voiceLabel = selected?.label
        ?.substringBefore("—")
        ?.substringBefore("·")
        ?.trim()
        ?.ifBlank { null }
        ?: "Voz"
    val rateLabel = String.format(Locale.forLanguageTag("pt-BR"), "%.1fx", rate)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 5.dp else 7.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 8.dp else 10.dp,
                    vertical = if (compact) 7.dp else 8.dp,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pág. ${currentPage + 1} de $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${(readingProgress * 100).roundToInt()}% lido",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { readingProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            OcrStatus(
                isRunning = isOcrRunning,
                message = ocrMessage,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 5.dp else 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.width(maxWidth.coerceAtMost(520.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaybackNavigationButton(
                        isNext = false,
                        description = "Trecho anterior",
                        onClick = onPrevious,
                    )
                    Surface(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isPlaying) "Pausar" else "Ouvir",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    PlaybackNavigationButton(
                        isNext = true,
                        description = "Próximo trecho",
                        onClick = onNext,
                    )
                    Surface(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 9.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = voiceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = rateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Abrir ajustes de voz e velocidade",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
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
private fun ReaderSettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    rate: Float,
    onRateChanged: (Float) -> Unit,
    voices: List<VoiceOption>,
    selectedName: String?,
    onVoiceSelected: (String?) -> Unit,
) {
    if (!visible) return

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
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
                    TextButton(onClick = onDismiss) { Text("Fechar") }
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
