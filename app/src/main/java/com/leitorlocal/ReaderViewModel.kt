package com.leitorlocal

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.leitorlocal.data.ReaderPreferences
import com.leitorlocal.data.BookCoverStore
import com.leitorlocal.data.ReaderCacheMaintenance
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.model.LibraryBook
import com.leitorlocal.pdf.PdfBookParser
import com.leitorlocal.pdf.PdfPageRenderer
import com.leitorlocal.speech.ReadingService
import com.leitorlocal.speech.ReadingSession
import com.leitorlocal.speech.NeuralAudioCache
import com.leitorlocal.speech.TtsVoiceCatalog
import com.leitorlocal.speech.EMBEDDED_NEURAL_VOICES
import com.leitorlocal.speech.embeddedNeuralVoice
import com.leitorlocal.speech.formatPreparationDuration
import com.leitorlocal.speech.preparationMinimumReserveMillis
import com.leitorlocal.model.VoiceOption
import com.leitorlocal.model.AssistantResult
import com.leitorlocal.model.AssistantSource
import com.leitorlocal.intelligence.BookInsight
import com.leitorlocal.intelligence.BookIntelligence
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val parser = PdfBookParser(application)
    private val renderer = PdfPageRenderer(application)
    private val preferences = ReaderPreferences(application)
    private val coverStore = BookCoverStore(application)
    private val voiceCatalog = TtsVoiceCatalog(application)
    private val neuralAudioCache = NeuralAudioCache(application)
    private val savedSettings = preferences.settings()
    private val mutableUiState = MutableStateFlow(
        ReaderUiState(
            speechRate = savedSettings.rate,
            selectedVoiceName = savedSettings.voiceName,
            library = preferences.library(),
        ),
    )
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var ocrJob: Job? = null
    private var intelligenceJob: Job? = null
    private var bookIntelligence: Pair<String, BookIntelligence>? = null
    private var ocrPausedForAudio = false
    private var lastRenderedPage = -1
    private var lastVoiceRefreshAt = 0L
    private val loadRequests = LoadRequestTracker()
    private val persistedPosition = PlaybackPositionTracker()
    private val cacheMaintenanceJob = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            ReaderCacheMaintenance(application).run(
                activeBookId = ReadingSession.bookId.takeIf(String::isNotBlank),
            )
        }
    }

    init {
        observePlayback()
        observeAudioPreparation()
        refreshVoices(force = true)
        viewModelScope.launch {
            cacheMaintenanceJob.await()
            refreshLibraryCovers()
        }
    }

    fun openBook(uri: Uri, title: String) {
        val saved = preferences.findBook(uri.toString())
        if (restoreActiveReading(expectedBookId = uri.toString())) return
        loadBook(uri, title, startIndex = saved?.chunkIndex ?: 0)
    }

    fun openLibraryBook(book: LibraryBook) {
        if (restoreActiveReading(expectedBookId = book.uri)) return
        loadBook(book.uri.toUri(), book.title, startIndex = book.chunkIndex)
    }

    /**
     * Reattaches a newly created activity to playback owned by [ReadingService]. Parsing and
     * configuring the same book again would flush the neural player, so the UI is rebuilt from
     * the process-wide session snapshot instead.
     */
    fun resumeActiveReading(): Boolean = restoreActiveReading(expectedBookId = null)

    fun showLibrary() {
        loadRequests.invalidate()
        loadJob?.cancel()
        ocrJob?.cancel()
        intelligenceJob?.cancel()
        ocrPausedForAudio = false
        if (uiState.value.playback.status in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)) pause()
        mutableUiState.update {
            it.copy(
                isLoading = false,
                loadingMessage = "",
                showingLibrary = true,
                isOcrRunning = false,
                ocrMessage = "",
                assistant = it.assistant.copy(isOpen = false, isWorking = false),
                library = preferences.library(),
            )
        }
    }

    fun play() {
        if (uiState.value.book != null) ReadingService.play(getApplication())
    }

    fun pause() {
        ReadingService.pause(getApplication())
    }

    fun next() {
        ReadingService.next(getApplication())
    }

    fun previous() {
        ReadingService.previous(getApplication())
    }

    fun changeRate(rate: Float) {
        val bounded = rate.coerceIn(0.6f, 1.6f)
        if (uiState.value.audioPreparation.isRunning) {
            ReadingService.cancelBookPreparation(getApplication())
        }
        mutableUiState.update { it.copy(speechRate = bounded) }
        ReadingSession.updateSettings(bounded, uiState.value.selectedVoiceName)
        preferences.saveSettings(bounded, uiState.value.selectedVoiceName)
        if (canReconfigurePlayback()) ReadingService.reconfigure(getApplication())
        refreshAudioPreparationState()
    }

    fun selectVoice(name: String?) {
        if (name == uiState.value.selectedVoiceName) return
        if (uiState.value.audioPreparation.isRunning) {
            ReadingService.cancelBookPreparation(getApplication())
        }
        mutableUiState.update { it.copy(selectedVoiceName = name) }
        ReadingSession.updateSettings(uiState.value.speechRate, name)
        preferences.saveSettings(uiState.value.speechRate, name)
        if (canReconfigurePlayback()) ReadingService.reconfigure(getApplication())
        if (
            canReconfigurePlayback() &&
            embeddedNeuralVoice(name) != null
        ) {
            ReadingService.prepareNeural(getApplication())
        }
        refreshAudioPreparationState()
    }

    fun prepareBookAudio() {
        val state = uiState.value
        if (state.book == null || embeddedNeuralVoice(state.selectedVoiceName) == null) return
        if (state.isOcrRunning) {
            mutableUiState.update {
                it.copy(
                    audioPreparation = it.audioPreparation.copy(
                        message = "Aguarde o OCR terminar antes de preparar a leitura contínua.",
                    ),
                )
            }
            return
        }
        ReadingService.prepareBook(getApplication())
    }

    fun cancelBookAudioPreparation() {
        ReadingService.cancelBookPreparation(getApplication())
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
        if (ReadingSession.state.value.status == PlaybackStatus.ERROR) {
            ReadingSession.setStatus(PlaybackStatus.PAUSED)
        }
    }

    fun openAssistant() {
        if (uiState.value.book == null) return
        mutableUiState.update { it.copy(assistant = it.assistant.copy(isOpen = true)) }
    }

    fun closeAssistant() {
        intelligenceJob?.cancel()
        mutableUiState.update {
            it.copy(assistant = it.assistant.copy(isOpen = false, isWorking = false))
        }
    }

    fun searchBook(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return
        runIntelligence(query = cleanQuery) { engine, _ -> engine.search(cleanQuery) }
    }

    fun summarizeCurrentChapter() {
        runIntelligence { engine, chunkIndex -> engine.summarizeChapter(chunkIndex) }
    }

    fun explainCurrentChunk() {
        runIntelligence { engine, chunkIndex -> engine.explain(chunkIndex) }
    }

    fun openAssistantSource(chunkIndex: Int) {
        val book = uiState.value.book ?: return
        if (chunkIndex !in book.chunks.indices) return
        if (uiState.value.playback.status in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)) pause()
        ReadingSession.setIndex(chunkIndex)
        preferences.savePosition(book.uri, chunkIndex)
        mutableUiState.update { it.copy(assistant = it.assistant.copy(isOpen = false)) }
    }

    private fun runIntelligence(
        query: String = uiState.value.assistant.query,
        action: (BookIntelligence, Int) -> BookInsight,
    ) {
        val book = uiState.value.book ?: return
        intelligenceJob?.cancel()
        mutableUiState.update {
            it.copy(assistant = it.assistant.copy(isOpen = true, isWorking = true, query = query))
        }
        intelligenceJob = viewModelScope.launch(Dispatchers.Default) {
            val key = "${book.uri}:${book.chunks.size}:${book.chunks.hashCode()}"
            val engine = bookIntelligence?.takeIf { it.first == key }?.second
                ?: BookIntelligence(book).also { bookIntelligence = key to it }
            val insight = action(engine, ReadingSession.state.value.currentChunkIndex)
            mutableUiState.update { state ->
                if (state.book?.uri != book.uri) state else state.copy(
                    assistant = state.assistant.copy(
                        isOpen = true,
                        isWorking = false,
                        result = insight.toUiResult(),
                    ),
                )
            }
        }
    }

    private fun BookInsight.toUiResult() = AssistantResult(
        title = title,
        body = body,
        sources = sources.map { AssistantSource(it.chunkIndex, it.pageNumber, it.excerpt) },
    )

    fun refreshVoices(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastVoiceRefreshAt < VOICE_REFRESH_INTERVAL_MILLIS) return
        lastVoiceRefreshAt = now
        voiceCatalog.load { voices ->
            mutableUiState.update { state ->
                val neuralVoices = EMBEDDED_NEURAL_VOICES.map { voice ->
                    VoiceOption(
                        name = voice.name,
                        label = voice.label,
                        localeTag = "pt-BR",
                        requiresNetwork = false,
                        quality = Int.MAX_VALUE,
                        isHighQuality = true,
                        isEmbeddedNeural = true,
                    )
                }
                val allVoices = neuralVoices + voices
                val saved = preferences.settings().voiceName
                val selected = saved?.takeIf { name -> allVoices.any { it.name == name } }
                    ?: neuralVoices.first().name
                state.copy(voices = allVoices, selectedVoiceName = selected)
            }
        }
    }

    private fun loadBook(
        uri: Uri,
        title: String,
        startIndex: Int,
    ) {
        val requestToken = loadRequests.begin()
        loadJob?.cancel()
        ocrJob?.cancel()
        intelligenceJob?.cancel()
        bookIntelligence = null
        if (ReadingSession.state.value.status != PlaybackStatus.STOPPED) {
            ReadingService.stop(getApplication())
        }
        lastRenderedPage = -1
        mutableUiState.update {
            it.copy(
                isLoading = true,
                loadingMessage = "Abrindo o PDF…",
                book = null,
                pageBitmap = null,
                showingLibrary = false,
                isOcrRunning = false,
                ocrMessage = "",
                error = null,
                assistant = it.assistant.copy(isOpen = false, isWorking = false, result = null),
            )
        }

        loadJob = viewModelScope.launch {
            try {
                cacheMaintenanceJob.await()
                val book = parser.parse(uri, title) { message ->
                    mutableUiState.update { state ->
                        if (!loadRequests.isCurrent(requestToken)) state else state.copy(
                            loadingMessage = message,
                        )
                    }
                }
                if (!loadRequests.isCurrent(requestToken)) return@launch
                preferences.saveBook(book.uri, book.title, book.chunks.size, book.pageCount)
                val coverPath = try {
                    coverStore.ensureCover(book.uri.toUri())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
                if (!loadRequests.isCurrent(requestToken)) return@launch
                coverPath?.let { preferences.saveCover(book.uri, it) }
                val restoredIndex = startIndex.coerceIn(0, book.chunks.lastIndex)
                val currentState = mutableUiState.value
                ReadingSession.configure(
                    bookId = book.uri,
                    title = book.title,
                    chunks = book.chunks,
                    startIndex = restoredIndex,
                    speechRate = currentState.speechRate,
                    voiceName = currentState.selectedVoiceName,
                    pageCount = book.pageCount,
                    pagesWithoutText = book.pagesWithoutText,
                )
                preferences.savePosition(book.uri, restoredIndex)
                persistedPosition.markPersisted(book.uri, restoredIndex)
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        book = book,
                        playback = ReadingSession.state.value,
                        library = preferences.library(),
                    )
                }
                refreshAudioPreparationState()
                renderPage(book.uri, book.chunks[restoredIndex].pageIndex)
                ReadingService.prepareNeural(getApplication())
                startProgressiveOcr(
                    initialBook = book,
                    priorityPage = book.chunks[restoredIndex].pageIndex,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (!loadRequests.isCurrent(requestToken)) return@launch
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        showingLibrary = true,
                        library = preferences.library(),
                        error = failure.message ?: "Não foi possível abrir este PDF.",
                    )
                }
            }
        }
    }

    private fun startProgressiveOcr(initialBook: BookContent, priorityPage: Int) {
        ocrJob?.cancel()
        intelligenceJob?.cancel()
        if (initialBook.pagesWithoutText <= 0 || initialBook.pageCount <= 0) {
            mutableUiState.update { it.copy(isOcrRunning = false, ocrMessage = "") }
            return
        }

        val bookUri = initialBook.uri
        val firstPage = priorityPage.coerceIn(0, initialBook.pageCount - 1)
        ocrJob = viewModelScope.launch {
            var workingBook = initialBook
            mutableUiState.update {
                it.copy(
                    isOcrRunning = true,
                    ocrMessage = "OCR offline preparando as próximas páginas…",
                )
            }
            try {
                ocrWindowStarts(
                    pageCount = initialBook.pageCount,
                    priorityPage = firstPage,
                ).forEach { windowStart ->
                    if (mutableUiState.value.book?.uri != bookUri) return@launch
                    val result = parser.enrichWithOcr(
                        book = workingBook,
                        startPage = windowStart,
                        pagesAhead = OCR_PAGES_AHEAD,
                    ) { progress ->
                        mutableUiState.update { state ->
                            if (state.book?.uri != bookUri) state else state.copy(
                                ocrMessage = "OCR offline · página ${progress.pageIndex + 1} " +
                                    "de ${initialBook.pageCount}",
                            )
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (mutableUiState.value.book?.uri != bookUri) return@launch
                    if (result.book != workingBook) {
                        workingBook = result.book
                        ReadingSession.replaceBook(workingBook)
                        val currentIndex = ReadingSession.state.value.currentChunkIndex
                        preferences.saveBook(
                            workingBook.uri,
                            workingBook.title,
                            workingBook.chunks.size,
                            workingBook.pageCount,
                        )
                        preferences.savePosition(workingBook.uri, currentIndex)
                        persistedPosition.markPersisted(workingBook.uri, currentIndex)
                        mutableUiState.update { state ->
                            if (state.book?.uri != bookUri) state else state.copy(
                                book = workingBook,
                                playback = ReadingSession.state.value,
                                library = preferences.library(),
                            )
                        }
                    }
                }
                mutableUiState.update { state ->
                    if (state.book?.uri != bookUri) state else state.copy(
                        isOcrRunning = false,
                        ocrMessage = "",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableUiState.update { state ->
                    if (state.book?.uri != bookUri) state else state.copy(
                        isOcrRunning = false,
                        ocrMessage = "O OCR offline continuará quando este livro for aberto novamente.",
                    )
                }
            }
        }
    }

    private fun ocrWindowStarts(pageCount: Int, priorityPage: Int): List<Int> = buildList {
        val windowSize = OCR_PAGES_AHEAD + 1
        var page = priorityPage
        while (page < pageCount) {
            add(page)
            page += windowSize
        }
        page = 0
        while (page < priorityPage) {
            add(page)
            page += windowSize
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            ReadingSession.state.collect { playback ->
                mutableUiState.update { it.copy(playback = playback) }
                updateOcrPriority(
                    audioBusy = playback.status in setOf(
                        PlaybackStatus.PLAYING,
                        PlaybackStatus.PREPARING,
                    ) || ReadingSession.audioPreparation.value.isRunning,
                )
                val book = mutableUiState.value.book ?: return@collect
                val page = book.chunks.getOrNull(playback.currentChunkIndex)?.pageIndex ?: return@collect
                if (persistedPosition.shouldPersist(book.uri, playback.currentChunkIndex)) {
                    preferences.savePosition(book.uri, playback.currentChunkIndex)
                    mutableUiState.update { it.copy(library = preferences.library()) }
                }
                if (page != lastRenderedPage) renderPage(book.uri, page)
            }
        }
    }

    private fun observeAudioPreparation() {
        viewModelScope.launch {
            ReadingSession.audioPreparation.collect { preparation ->
                mutableUiState.update { state ->
                    if (
                        preparation.bookId.isNotBlank() &&
                        state.book?.uri != preparation.bookId
                    ) {
                        state
                    } else {
                        state.copy(audioPreparation = preparation)
                    }
                }
                updateOcrPriority(
                    audioBusy = preparation.isRunning ||
                        ReadingSession.state.value.status in setOf(
                            PlaybackStatus.PLAYING,
                            PlaybackStatus.PREPARING,
                        ),
                )
            }
        }
    }

    private fun updateOcrPriority(audioBusy: Boolean) {
        val state = mutableUiState.value
        val book = state.book ?: return
        if (audioBusy) {
            if (ocrJob?.isActive == true) {
                ocrPausedForAudio = true
                ocrJob?.cancel()
                mutableUiState.update {
                    it.copy(
                        isOcrRunning = false,
                        ocrMessage = "OCR pausado para priorizar a voz.",
                    )
                }
            }
            return
        }
        if (ocrPausedForAudio && book.pagesWithoutText > 0 && !state.showingLibrary) {
            ocrPausedForAudio = false
            val currentPage = book.chunks
                .getOrNull(ReadingSession.state.value.currentChunkIndex)
                ?.pageIndex
                ?: 0
            startProgressiveOcr(book, currentPage)
        }
    }

    private fun refreshAudioPreparationState() {
        val book = mutableUiState.value.book ?: return
        val voice = embeddedNeuralVoice(mutableUiState.value.selectedVoiceName) ?: return
        val speechRate = mutableUiState.value.speechRate
        viewModelScope.launch(Dispatchers.IO) {
            val startIndex = ReadingSession.state.value.currentChunkIndex
                .coerceIn(0, (book.chunks.lastIndex).coerceAtLeast(0))
            val snapshot = neuralAudioCache.preparationSnapshot(
                bookId = book.uri,
                chunkTexts = book.chunks.map { it.text },
                startIndex = startIndex,
                speechRate = speechRate,
                voiceName = voice.name,
            )
            val fullBookReady = neuralAudioCache.isBookPrepared(
                bookId = book.uri,
                chunkCount = book.chunks.size,
                speechRate = speechRate,
                voiceName = voice.name,
            )
            val targetDurationMillis = preparationMinimumReserveMillis(voice)
            val ready = fullBookReady || snapshot.reachedBookEnd ||
                snapshot.preparedDurationMillis >= targetDurationMillis
            if (ReadingSession.bookId != book.uri) return@launch
            ReadingSession.setAudioPreparation(
                ReadingSession.audioPreparation.value.copy(
                    bookId = book.uri,
                    isRunning = false,
                    isReady = ready,
                    sampleReady = snapshot.preparedDurationMillis >= 60_000L,
                    startChunkIndex = startIndex,
                    completedChunks = snapshot.contiguousChunks,
                    totalChunks = book.chunks.size,
                    preparedDurationMillis = snapshot.preparedDurationMillis,
                    targetDurationMillis = targetDurationMillis,
                    estimatedWaitMillis = null,
                    realTimeFactor = null,
                    message = if (ready) {
                        "Reserva de ${formatPreparationDuration(snapshot.preparedDurationMillis)} pronta para ouvir."
                    } else if (snapshot.preparedDurationMillis > 0L) {
                        "${formatPreparationDuration(snapshot.preparedDurationMillis)} já estão no cache. Continue para completar a reserva."
                    } else {
                        "Prepare uma reserva a partir da posição atual. Em 1 minuto de áudio você já pode validar a voz."
                    },
                ),
            )
        }
    }

    private fun refreshLibraryCovers() {
        viewModelScope.launch {
            preferences.library()
                .filter { book ->
                    book.coverPath.isNullOrBlank() || !File(book.coverPath).isFile
                }
                .forEach { book ->
                    if (!book.coverPath.isNullOrBlank()) {
                        // Force a distinct UI state before recreating the same deterministic path.
                        preferences.saveCover(book.uri, "")
                        mutableUiState.update { it.copy(library = preferences.library()) }
                    }
                    val path = try {
                        coverStore.ensureCover(book.uri.toUri())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        null
                    }
                    path?.let {
                        preferences.saveCover(book.uri, it)
                        mutableUiState.update { state ->
                            state.copy(library = preferences.library())
                        }
                    }
                }
        }
    }

    private fun restoreActiveReading(expectedBookId: String?): Boolean {
        val playback = ReadingSession.state.value
        if (
            playback.status !in setOf(
                PlaybackStatus.PLAYING,
                PlaybackStatus.PREPARING,
                PlaybackStatus.PAUSED,
            )
        ) {
            return false
        }
        val book = ReadingSession.bookSnapshot() ?: return false
        if (expectedBookId != null && book.uri != expectedBookId) return false

        loadRequests.invalidate()
        loadJob?.cancel()
        ocrJob?.cancel()
        intelligenceJob?.cancel()
        lastRenderedPage = -1
        mutableUiState.update { state ->
            state.copy(
                isLoading = false,
                loadingMessage = "",
                book = book,
                playback = playback,
                pageBitmap = state.pageBitmap.takeIf { state.book?.uri == book.uri },
                selectedVoiceName = ReadingSession.voiceName,
                speechRate = ReadingSession.speechRate,
                library = preferences.library(),
                showingLibrary = false,
                isOcrRunning = false,
                ocrMessage = "",
                error = null,
                audioPreparation = ReadingSession.audioPreparation.value,
            )
        }
        val currentPage = book.chunks
            .getOrNull(playback.currentChunkIndex)
            ?.pageIndex
            ?: 0
        persistedPosition.markPersisted(book.uri, playback.currentChunkIndex)
        renderPage(book.uri, currentPage)
        val audioBusy = playback.status in setOf(
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
        ) || ReadingSession.audioPreparation.value.isRunning
        if (audioBusy) {
            ocrPausedForAudio = book.pagesWithoutText > 0
        } else {
            startProgressiveOcr(initialBook = book, priorityPage = currentPage)
        }
        return true
    }

    private fun renderPage(uri: String, pageIndex: Int) {
        if (pageIndex == lastRenderedPage) return
        lastRenderedPage = pageIndex
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = renderer.render(uri.toUri(), pageIndex)
                currentCoroutineContext().ensureActive()
                if (lastRenderedPage == pageIndex) {
                    mutableUiState.update { it.copy(pageBitmap = bitmap) }
                    bitmap = null // ownership passed to UI state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (lastRenderedPage == pageIndex) lastRenderedPage = -1
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun canReconfigurePlayback(): Boolean =
        uiState.value.book != null &&
            ReadingSession.hasContent() &&
            ReadingSession.state.value.status in setOf(
                PlaybackStatus.PLAYING,
                PlaybackStatus.PREPARING,
                PlaybackStatus.PAUSED,
            )

    override fun onCleared() {
        loadRequests.invalidate()
        loadJob?.cancel()
        renderJob?.cancel()
        ocrJob?.cancel()
        intelligenceJob?.cancel()
        parser.close()
        voiceCatalog.close()
    }

    companion object {
        private const val OCR_PAGES_AHEAD = 3
        private const val VOICE_REFRESH_INTERVAL_MILLIS = 30_000L
    }
}
