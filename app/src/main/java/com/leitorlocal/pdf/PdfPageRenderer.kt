package com.leitorlocal.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PdfPageRenderer(private val context: Context) {
    suspend fun render(uri: Uri, pageIndex: Int, targetWidth: Int = 1200): Bitmap? =
        withContext(Dispatchers.IO) {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            descriptor.use {
                PdfRenderer(it).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        if (page.width <= 0 || page.height <= 0) return@withContext null
                        val requestedWidth = targetWidth.coerceAtLeast(1).toDouble()
                        val requestedHeight = requestedWidth *
                            (page.height.toDouble() / page.width.toDouble())
                        val requestedPixels = requestedWidth * requestedHeight
                        val pixelScale = if (requestedPixels > MAX_RENDER_PIXELS) {
                            sqrt(MAX_RENDER_PIXELS / requestedPixels)
                        } else {
                            1.0
                        }
                        val sideScale = MAX_RENDER_SIDE /
                            maxOf(requestedWidth, requestedHeight)
                        val scale = minOf(1.0, pixelScale, sideScale)
                        val width = (requestedWidth * scale).roundToInt().coerceAtLeast(1)
                        val height = (requestedHeight * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            currentCoroutineContext().ensureActive()
                            bitmap
                        } catch (failure: Throwable) {
                            bitmap.recycle()
                            throw failure
                        }
                    }
                }
            }
        }

    private companion object {
        // 16 megapixels keeps a single ARGB page below roughly 64 MB.
        const val MAX_RENDER_PIXELS = 16_000_000.0
        const val MAX_RENDER_SIDE = 8_192.0
    }
}
