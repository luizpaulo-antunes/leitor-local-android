package com.leitorlocal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.leitorlocal.data.ReaderPreferences
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.pdf.PdfBookParser
import com.leitorlocal.pdf.PdfPageRenderer
import com.leitorlocal.speech.ReadingService
import com.leitorlocal.speech.ReadingSession
import com.leitorlocal.speech.TtsVoiceCatalog
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
    private val voiceCatalog = TtsVoiceCatalog(application)
    private val mutableUiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var lastRenderedPage = -1

    init {
        observePlayback()
        loadVoices()
        restoreLastBook()
    }

    fun openBook(uri: Uri, title: String) {
        loadBook(uri, title, startIndex = 0, isNewBook = true)
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
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
    }

    private fun loadVoices() {
        voiceCatalog.load { voices ->
            mutableUiState.update { state ->
                val saved = preferences.load()?.voiceName
                val selected = saved?.takeIf { name -> voices.any { it.name == name } }
                    ?: voices.firstOrNull { !it.requiresNetwork && it.localeTag.equals("pt-BR", true) }?.name
                    ?: voices.firstOrNull()?.name
                state.copy(voices = voices, selectedVoiceName = selected)
            }
        }
    }

    private fun restoreLastBook() {
        val saved = preferences.load() ?: return
        mutableUiState.update {
            it.copy(speechRate = saved.rate, selectedVoiceName = saved.voiceName)
        }
        loadBook(
            uri = saved.uri.toUri(),
            title = saved.title,
            startIndex = saved.chunkIndex,
            isNewBook = false,
        )
    }

    private fun loadBook(
        uri: Uri,
        title: String,
        startIndex: Int,
        isNewBook: Boolean,
    ) {
        loadJob?.cancel()
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
                error = null,
            )
        }

        loadJob = viewModelScope.launch {
            runCatching {
                parser.parse(uri, title) { current, total ->
                    mutableUiState.update {
                        it.copy(loadingMessage = "Preparando página $current de $total…")
                    }
                }
            }.onSuccess { book ->
                if (isNewBook) preferences.saveBook(book.uri, book.title)
                val restoredIndex = startIndex.coerceIn(0, book.chunks.lastIndex)
                val currentState = mutableUiState.value
                ReadingSession.configure(
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
                    )
                }
                renderPage(book.uri, book.chunks[restoredIndex].pageIndex)
            }.onFailure { failure ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        error = failure.message ?: "Não foi possível abrir este PDF.",
                    )
                }
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            ReadingSession.state.collect { playback ->
                mutableUiState.update { it.copy(playback = playback) }
                val book = mutableUiState.value.book ?: return@collect
                val page = book.chunks.getOrNull(playback.currentChunkIndex)?.pageIndex ?: return@collect
                preferences.savePosition(playback.currentChunkIndex)
                if (page != lastRenderedPage) renderPage(book.uri, page)
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
        voiceCatalog.close()
    }
}
