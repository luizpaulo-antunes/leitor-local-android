package com.leitorlocal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.leitorlocal.data.ReaderPreferences
import com.leitorlocal.data.BookCoverStore
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.model.LibraryBook
import com.leitorlocal.pdf.PdfBookParser
import com.leitorlocal.pdf.PdfPageRenderer
import com.leitorlocal.speech.ReadingService
import com.leitorlocal.speech.ReadingSession
import com.leitorlocal.speech.TtsVoiceCatalog
import com.leitorlocal.speech.EMBEDDED_NEURAL_VOICES
import com.leitorlocal.speech.embeddedNeuralVoice
import com.leitorlocal.model.VoiceOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var lastRenderedPage = -1

    init {
        observePlayback()
        refreshVoices()
        refreshLibraryCovers()
    }

    fun openBook(uri: Uri, title: String) {
        val saved = preferences.findBook(uri.toString())
        loadBook(uri, title, startIndex = saved?.chunkIndex ?: 0)
    }

    fun openLibraryBook(book: LibraryBook) {
        loadBook(book.uri.toUri(), book.title, startIndex = book.chunkIndex)
    }

    fun showLibrary() {
        if (uiState.value.playback.status in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)) pause()
        mutableUiState.update {
            it.copy(showingLibrary = true, library = preferences.library())
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
        mutableUiState.update { it.copy(speechRate = bounded) }
        ReadingSession.updateSettings(bounded, uiState.value.selectedVoiceName)
        preferences.saveSettings(bounded, uiState.value.selectedVoiceName)
    }

    fun selectVoice(name: String?) {
        mutableUiState.update { it.copy(selectedVoiceName = name) }
        ReadingSession.updateSettings(uiState.value.speechRate, name)
        preferences.saveSettings(uiState.value.speechRate, name)
        if (uiState.value.book != null && embeddedNeuralVoice(name) != null) {
            ReadingService.prepareNeural(getApplication())
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
        if (ReadingSession.state.value.status == PlaybackStatus.ERROR) {
            ReadingSession.setStatus(PlaybackStatus.PAUSED)
        }
    }

    fun refreshVoices() {
        if (
            ReadingSession.hasContent() &&
            ReadingSession.state.value.status !in setOf(
                PlaybackStatus.PLAYING,
                PlaybackStatus.PREPARING,
            )
        ) {
            ReadingService.reloadEngine(getApplication())
        }
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
        loadJob?.cancel()
        ocrJob?.cancel()
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
            )
        }

        loadJob = viewModelScope.launch {
            runCatching {
                parser.parse(uri, title) { message ->
                    mutableUiState.update {
                        it.copy(loadingMessage = message)
                    }
                }
            }.onSuccess { book ->
                preferences.saveBook(book.uri, book.title, book.chunks.size, book.pageCount)
                runCatching { coverStore.ensureCover(book.uri.toUri()) }
                    .getOrNull()
                    ?.let { preferences.saveCover(book.uri, it) }
                val restoredIndex = startIndex.coerceIn(0, book.chunks.lastIndex)
                val currentState = mutableUiState.value
                ReadingSession.configure(
                    bookId = book.uri,
                    title = book.title,
                    chunks = book.chunks,
                    startIndex = restoredIndex,
                    speechRate = currentState.speechRate,
                    voiceName = currentState.selectedVoiceName,
                )
                preferences.savePosition(restoredIndex)
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        book = book,
                        playback = ReadingSession.state.value,
                        library = preferences.library(),
                    )
                }
                renderPage(book.uri, book.chunks[restoredIndex].pageIndex)
                ReadingService.prepareNeural(getApplication())
                startProgressiveOcr(
                    initialBook = book,
                    priorityPage = book.chunks[restoredIndex].pageIndex,
                )
            }.onFailure { failure ->
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
                    if (result.book != workingBook) {
                        workingBook = result.book
                        ReadingSession.replaceChunks(workingBook.chunks)
                        val currentIndex = ReadingSession.state.value.currentChunkIndex
                        preferences.saveBook(
                            workingBook.uri,
                            workingBook.title,
                            workingBook.chunks.size,
                            workingBook.pageCount,
                        )
                        preferences.savePosition(currentIndex)
                        mutableUiState.update { state ->
                            if (state.book?.uri != bookUri) state else state.copy(
                                book = workingBook,
                                playback = ReadingSession.state.value,
                                library = preferences.library(),
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                mutableUiState.update { state ->
                    if (state.book?.uri != bookUri) state else state.copy(
                        ocrMessage = "O OCR offline continuará quando este livro for aberto novamente.",
                    )
                }
            } finally {
                mutableUiState.update { state ->
                    if (state.book?.uri != bookUri) state else state.copy(
                        isOcrRunning = false,
                        ocrMessage = "",
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
                val book = mutableUiState.value.book ?: return@collect
                val page = book.chunks.getOrNull(playback.currentChunkIndex)?.pageIndex ?: return@collect
                preferences.savePosition(playback.currentChunkIndex)
                mutableUiState.update { it.copy(library = preferences.library()) }
                if (page != lastRenderedPage) renderPage(book.uri, page)
            }
        }
    }

    private fun refreshLibraryCovers() {
        viewModelScope.launch {
            preferences.library()
                .filter { it.coverPath.isNullOrBlank() }
                .forEach { book ->
                    runCatching { coverStore.ensureCover(book.uri.toUri()) }
                        .getOrNull()
                        ?.let { path ->
                            preferences.saveCover(book.uri, path)
                            mutableUiState.update { it.copy(library = preferences.library()) }
                        }
                }
        }
    }

    private fun renderPage(uri: String, pageIndex: Int) {
        if (pageIndex == lastRenderedPage) return
        lastRenderedPage = pageIndex
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val bitmap = runCatching { renderer.render(uri.toUri(), pageIndex) }.getOrNull()
            if (lastRenderedPage == pageIndex) {
                mutableUiState.update { it.copy(pageBitmap = bitmap) }
            }
        }
    }

    override fun onCleared() {
        ocrJob?.cancel()
        parser.close()
        voiceCatalog.close()
    }

    companion object {
        private const val OCR_PAGES_AHEAD = 3
    }
}
