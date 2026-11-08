package com.leitorlocal.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.leitorlocal.data.PageOcrCache
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class PageOcrText(
    val pageIndex: Int,
    val rawText: String,
    val fromCache: Boolean,
)

/** Renders and recognizes a complete PDF page using the bundled, offline Latin model. */
class OfflinePageOcr(context: Context) : AutoCloseable {
    private val renderer = PdfPageRenderer(context.applicationContext)
    private val cache = PageOcrCache(context.applicationContext)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri, pageIndex: Int): PageOcrText {
        require(pageIndex >= 0) { "A página para OCR deve ser válida." }
        cache.load(uri, pageIndex)?.let { cached ->
            return PageOcrText(pageIndex, cached.rawText, fromCache = true)
        }

        val bitmap = renderer.render(uri, pageIndex, targetWidth = OCR_RENDER_WIDTH)
            ?: error("Não foi possível renderizar a página ${pageIndex + 1} para OCR.")
        val rawText = try {
            // ML Kit may still read the bitmap after process() returns. Once submitted, let
            // that single recognition finish before recycling the underlying pixel buffer.
            withContext(NonCancellable) { recognize(bitmap) }.trim()
        } finally {
            bitmap.recycle()
        }
        // Only successful ML Kit responses reach this point. Empty is a valid result.
        cache.save(uri, pageIndex, rawText)
        return PageOcrText(pageIndex, rawText, fromCache = false)
    }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        runCatching {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks.mapNotNull { block ->
                        val bounds = block.boundingBox ?: return@mapNotNull null
                        LayoutTextBlock(
                            text = block.text,
                            left = bounds.left.toFloat(),
                            top = bounds.top.toFloat(),
                            right = bounds.right.toFloat(),
                            bottom = bounds.bottom.toFloat(),
                            startsParagraph = true,
                        )
                    }
                    val ordered = DocumentLayoutAnalyzer.asText(blocks, bitmap.width.toFloat())
                    if (continuation.isActive) continuation.resume(ordered.ifBlank { result.text })
                }
                .addOnFailureListener { failure ->
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
        }.onFailure { failure ->
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
    }

    override fun close() {
        recognizer.close()
    }

    companion object {
        // A full page at this width remains reasonably small while preserving book glyphs.
        private const val OCR_RENDER_WIDTH = 1_600
    }
}
