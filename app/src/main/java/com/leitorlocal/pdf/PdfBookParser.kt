package com.leitorlocal.pdf

import android.content.Context
import android.net.Uri
import com.leitorlocal.model.BookContent
import com.leitorlocal.model.SpeechChunk
import com.leitorlocal.text.TextPreprocessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfBookParser(private val context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun parse(
        uri: Uri,
        title: String,
        onProgress: (current: Int, total: Int) -> Unit,
    ): BookContent = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o PDF.")

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val totalPages = document.numberOfPages
                val chunks = mutableListOf<SpeechChunk>()
                var pagesWithoutText = 0
                val rawPages = mutableListOf<String>()
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }

                for (pageIndex in 0 until totalPages) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    rawPages += stripper.getText(document)
                    onProgress(pageIndex + 1, totalPages)
                }

                val cleanPages = TextPreprocessor.cleanDocumentPages(rawPages)
                cleanPages.forEachIndexed { pageIndex, cleanText ->
                    if (cleanText.isBlank()) {
                        pagesWithoutText += 1
                    } else {
                        TextPreprocessor.splitIntoChunks(cleanText).forEach { text ->
                            chunks += SpeechChunk(pageIndex = pageIndex, text = text)
                        }
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
                )
            }
        }
    }
}
