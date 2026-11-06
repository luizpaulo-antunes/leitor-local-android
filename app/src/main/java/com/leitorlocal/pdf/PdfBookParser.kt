package com.leitorlocal.pdf

import android.content.Context
import android.net.Uri
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.FigureContent
import com.leitorlocal.model.SpeechChunk
import com.leitorlocal.model.SpeechTextRange
import com.leitorlocal.model.SpeechTextRole
import com.leitorlocal.data.BookCache
import com.leitorlocal.text.TextPreprocessor
import com.leitorlocal.text.TextLineLayout
import com.leitorlocal.text.FigureDescriptionComposer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfBookParser(private val context: Context) {
    private val cache = BookCache(context.applicationContext)
    private val progressiveOcr = ProgressivePdfOcr(context.applicationContext)

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun parse(
        uri: Uri,
        title: String,
        onProgress: (message: String) -> Unit,
    ): BookContent = withContext(Dispatchers.IO) {
        cache.load(uri, title)?.let { cached ->
            onProgress("Livro pronto.")
            return@withContext cached
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o PDF.")

        val extractedBook = input.use { stream ->
            PDDocument.load(stream).use { document ->
                val totalPages = document.numberOfPages
                val chunks = mutableListOf<SpeechChunk>()
                val rawPages = mutableListOf<String>()
                val layoutLinesByPage = mutableListOf<List<TextLineLayout>>()
                val stripper = ArtifactIgnoringTextStripper().apply {
                    sortByPosition = true
                }

                for (pageIndex in 0 until totalPages) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    rawPages += stripper.getText(document)
                    layoutLinesByPage += stripper.pageLines().map { line ->
                        TextLineLayout(
                            text = line.text,
                            fontSizePt = line.fontSizePt,
                            isBold = line.isBold,
                            startsParagraph = line.startsParagraph,
                        )
                    }
                    onProgress("Extraindo texto da página ${pageIndex + 1} de $totalPages…")
                }

                val captionsByPage = rawPages.map(TextPreprocessor::extractFigureCaptions)
                val figuresByPage = PdfFigureAnalyzer(context.applicationContext).analyze(
                    document = document,
                    bookUri = uri,
                    captionsByPage = captionsByPage,
                    onProgress = { current, total ->
                        onProgress("Analisando figura $current de $total…")
                    },
                )
                val cleanPages = TextPreprocessor.cleanDocumentPages(rawPages)
                val headingsByPage = layoutLinesByPage.map(TextPreprocessor::detectHeadingCandidates)
                var pagesWithoutText = 0
                cleanPages.forEachIndexed { pageIndex, cleanText ->
                    val pageChunks = buildPageChunks(
                        pageIndex = pageIndex,
                        cleanText = cleanText,
                        figures = figuresByPage[pageIndex].orEmpty(),
                        headingCandidates = headingsByPage.getOrElse(pageIndex) { emptyList() },
                    )
                    if (pageChunks.isEmpty()) {
                        pagesWithoutText += 1
                    } else {
                        chunks += pageChunks
                    }
                }

                BookContent(
                    uri = uri.toString(),
                    title = title,
                    pageCount = totalPages,
                    chunks = chunks,
                    pagesWithoutText = pagesWithoutText,
                )
            }
        }

        val readyBook = if (extractedBook.chunks.isEmpty()) {
            bootstrapScannedBook(extractedBook, onProgress)
        } else {
            extractedBook
        }
        if (readyBook.chunks.isEmpty()) {
            error(
                "Este PDF parece ser escaneado, mas o OCR offline não encontrou " +
                    "texto legível nas primeiras páginas.",
            )
        }
        cache.save(readyBook)
        readyBook
    }

    /**
     * Recognizes missing pages around [startPage], merges them in reading order and persists
     * the updated processed-book cache. Cached empty pages are returned without rerunning ML Kit.
     */
    suspend fun enrichWithOcr(
        book: BookContent,
        startPage: Int,
        pagesAhead: Int = ProgressivePdfOcr.DEFAULT_PAGES_AHEAD,
        onProgress: (PageOcrProgress) -> Unit = {},
    ): ProgressiveOcrResult = withContext(Dispatchers.IO) {
        val result = progressiveOcr.enrich(book, startPage, pagesAhead, onProgress)
        if (result.recognizedPages.isNotEmpty()) cache.save(result.book)
        result
    }

    fun close() {
        progressiveOcr.close()
    }

    private suspend fun bootstrapScannedBook(
        initial: BookContent,
        onProgress: (message: String) -> Unit,
    ): BookContent {
        var book = initial
        var startPage = 0
        val pageLimit = minOf(initial.pageCount, MAX_BOOTSTRAP_OCR_PAGES)
        while (book.chunks.isEmpty() && startPage < pageLimit) {
            val result = progressiveOcr.enrich(
                book = book,
                startPage = startPage,
                pagesAhead = BOOTSTRAP_BATCH_SIZE - 1,
            ) { progress ->
                onProgress(
                    "OCR offline da página ${progress.pageIndex + 1} de ${initial.pageCount}…",
                )
            }
            book = result.book
            startPage += BOOTSTRAP_BATCH_SIZE
        }
        return book
    }

    private fun buildPageChunks(
        pageIndex: Int,
        cleanText: String,
        figures: List<AnalyzedFigure>,
        headingCandidates: List<String>,
    ): List<SpeechChunk> = buildList {
        var cursor = 0

        fun addText(text: String) {
            TextPreprocessor.splitIntoStructuredChunks(text, headingCandidates).forEach { chunk ->
                add(
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
                    ),
                )
            }
        }

        figures.sortedBy(AnalyzedFigure::relativePosition).forEach { figure ->
            val captionIndex = figure.caption
                .takeIf(String::isNotBlank)
                ?.let { caption -> cleanText.indexOf(caption, startIndex = cursor, ignoreCase = true) }
                ?: -1
            if (captionIndex >= cursor) {
                addText(cleanText.substring(cursor, captionIndex))
                cursor = captionIndex + figure.caption.length
            } else if (figure.relativePosition >= 0.5f && cursor == 0) {
                addText(cleanText)
                cursor = cleanText.length
            }

            val content = FigureContent(
                imagePath = figure.imagePath,
                caption = figure.caption,
                alternativeText = figure.alternativeText,
                ocrText = figure.ocrText,
            )
            val narration = FigureDescriptionComposer.compose(
                caption = figure.caption,
                alternativeText = figure.alternativeText,
                ocrText = figure.ocrText,
            )
            TextPreprocessor.splitIntoChunks(narration).forEach { chunkText ->
                add(SpeechChunk(pageIndex = pageIndex, text = chunkText, figure = content))
            }
        }

        if (cursor < cleanText.length) addText(cleanText.substring(cursor))
    }

    companion object {
        private const val BOOTSTRAP_BATCH_SIZE = 3
        private const val MAX_BOOTSTRAP_OCR_PAGES = 12
    }
}
