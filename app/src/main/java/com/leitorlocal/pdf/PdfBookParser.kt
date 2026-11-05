package com.leitorlocal.pdf

import android.content.Context
import android.net.Uri
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.FigureContent
import com.leitorlocal.model.SpeechChunk
import com.leitorlocal.data.BookCache
import com.leitorlocal.text.TextPreprocessor
import com.leitorlocal.text.FigureDescriptionComposer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfBookParser(private val context: Context) {
    private val cache = BookCache(context.applicationContext)

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

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val totalPages = document.numberOfPages
                val chunks = mutableListOf<SpeechChunk>()
                val rawPages = mutableListOf<String>()
                val stripper = ArtifactIgnoringTextStripper().apply {
                    sortByPosition = true
                }

                for (pageIndex in 0 until totalPages) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    rawPages += stripper.getText(document)
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
                var pagesWithoutText = 0
                cleanPages.forEachIndexed { pageIndex, cleanText ->
                    val pageChunks = buildPageChunks(
                        pageIndex = pageIndex,
                        cleanText = cleanText,
                        figures = figuresByPage[pageIndex].orEmpty(),
                    )
                    if (pageChunks.isEmpty()) {
                        pagesWithoutText += 1
                    } else {
                        chunks += pageChunks
                    }
                }

                if (chunks.isEmpty()) {
                    error(
                        "Este PDF não contém texto selecionável. " +
                            "Ele provavelmente é escaneado e precisará do módulo de OCR.",
                    )
                }

                BookContent(
                    uri = uri.toString(),
                    title = title,
                    pageCount = totalPages,
                    chunks = chunks,
                    pagesWithoutText = pagesWithoutText,
                ).also(cache::save)
            }
        }
    }

    private fun buildPageChunks(
        pageIndex: Int,
        cleanText: String,
        figures: List<AnalyzedFigure>,
    ): List<SpeechChunk> = buildList {
        var cursor = 0

        fun addText(text: String) {
            TextPreprocessor.splitIntoChunks(text).forEach { chunkText ->
                add(SpeechChunk(pageIndex = pageIndex, text = chunkText))
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
}
