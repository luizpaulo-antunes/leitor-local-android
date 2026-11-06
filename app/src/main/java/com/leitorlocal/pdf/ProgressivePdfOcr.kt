package com.leitorlocal.pdf

import android.content.Context
import androidx.core.net.toUri
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.SpeechChunk
import com.leitorlocal.model.SpeechTextRange
import com.leitorlocal.model.SpeechTextRole
import com.leitorlocal.text.TextPreprocessor
import com.leitorlocal.text.TextLineLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PageOcrProgress(
    val pageIndex: Int,
    val current: Int,
    val total: Int,
    val fromCache: Boolean,
)

data class ProgressiveOcrResult(
    val book: BookContent,
    val attemptedPages: List<Int>,
    val recognizedPages: List<Int>,
    val cachedPages: List<Int>,
    val failedPages: List<Int>,
)

/**
 * OCRs only missing pages in a short physical-page window. Calls are serialized so page
 * rendering, ML Kit and the persistent caches cannot race when playback changes quickly.
 */
class ProgressivePdfOcr(context: Context) : AutoCloseable {
    private val pageOcr = OfflinePageOcr(context.applicationContext)
    private val mutex = Mutex()

    suspend fun enrich(
        book: BookContent,
        startPage: Int,
        pagesAhead: Int = DEFAULT_PAGES_AHEAD,
        onProgress: (PageOcrProgress) -> Unit = {},
    ): ProgressiveOcrResult = mutex.withLock {
        val targets = ProgressiveOcrPlanner.targetPages(book, startPage, pagesAhead)
        if (targets.isEmpty()) {
            return@withLock ProgressiveOcrResult(
                book = book,
                attemptedPages = emptyList(),
                recognizedPages = emptyList(),
                cachedPages = emptyList(),
                failedPages = emptyList(),
            )
        }

        val recognized = linkedMapOf<Int, String>()
        val cachedPages = mutableListOf<Int>()
        val failedPages = mutableListOf<Int>()
        targets.forEachIndexed { progressIndex, pageIndex ->
            try {
                val result = pageOcr.recognize(book.uri.toUri(), pageIndex)
                if (result.fromCache) cachedPages += pageIndex
                val cleaned = TextPreprocessor.cleanPage(result.rawText)
                if (OcrTextQuality.isUsable(cleaned)) recognized[pageIndex] = cleaned
                onProgress(
                    PageOcrProgress(
                        pageIndex = pageIndex,
                        current = progressIndex + 1,
                        total = targets.size,
                        fromCache = result.fromCache,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failedPages += pageIndex
                onProgress(
                    PageOcrProgress(
                        pageIndex = pageIndex,
                        current = progressIndex + 1,
                        total = targets.size,
                        fromCache = false,
                    ),
                )
            }
        }

        ProgressiveOcrResult(
            book = ProgressiveOcrPlanner.merge(book, recognized),
            attemptedPages = targets,
            recognizedPages = recognized.keys.toList(),
            cachedPages = cachedPages,
            failedPages = failedPages,
        )
    }

    override fun close() {
        pageOcr.close()
    }

    companion object {
        const val DEFAULT_PAGES_AHEAD = 3
    }
}

internal object ProgressiveOcrPlanner {
    fun targetPages(book: BookContent, startPage: Int, pagesAhead: Int): List<Int> {
        if (book.pageCount <= 0) return emptyList()
        val first = startPage.coerceIn(0, book.pageCount - 1)
        val last = (first.toLong() + pagesAhead.coerceAtLeast(0))
            .coerceAtMost((book.pageCount - 1).toLong())
            .toInt()
        val pagesWithContent = book.chunks.asSequence().map(SpeechChunk::pageIndex).toSet()
        return (first..last).filterNot(pagesWithContent::contains)
    }

    fun merge(book: BookContent, recognizedPages: Map<Int, String>): BookContent {
        if (recognizedPages.isEmpty()) return book
        val existingPages = book.chunks.asSequence().map(SpeechChunk::pageIndex).toSet()
        val additions = recognizedPages
            .asSequence()
            .filter { (pageIndex, text) ->
                pageIndex in 0 until book.pageCount && pageIndex !in existingPages && text.isNotBlank()
            }
            .flatMap { (pageIndex, text) ->
                val headingCandidates = TextPreprocessor.detectHeadingCandidates(
                    text.lines()
                        .filter(String::isNotBlank)
                        .map { line ->
                            TextLineLayout(
                                text = line,
                                fontSizePt = 0f,
                                isBold = false,
                                startsParagraph = true,
                            )
                        },
                )
                TextPreprocessor.splitIntoStructuredChunks(text, headingCandidates)
                    .asSequence()
                    .map { chunk ->
                        SpeechChunk(
                            pageIndex = pageIndex,
                            text = chunk.text,
                            ranges = chunk.ranges.map { range ->
                                SpeechTextRange(
                                    start = range.start,
                                    endExclusive = range.endExclusive,
                                    role = SpeechTextRole.HEADING,
                                )
                            },
                        )
                    }
            }
            .toList()
        if (additions.isEmpty()) return book

        val mergedChunks = (book.chunks + additions).sortedBy(SpeechChunk::pageIndex)
        val representedPages = mergedChunks.asSequence().map(SpeechChunk::pageIndex).toSet()
        return book.copy(
            chunks = mergedChunks,
            pagesWithoutText = (book.pageCount - representedPages.size).coerceAtLeast(0),
        )
    }
}

internal object OcrTextQuality {
    fun isUsable(text: String): Boolean {
        if (text.isBlank()) return false
        val letters = text.count(Char::isLetter)
        val visibleCharacters = text.count { !it.isWhitespace() }
        if (letters < MIN_LETTERS || visibleCharacters == 0) return false
        return letters.toFloat() / visibleCharacters >= MIN_LETTER_RATIO
    }

    private const val MIN_LETTERS = 8
    private const val MIN_LETTER_RATIO = 0.35f
}
