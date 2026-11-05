package com.leitorlocal.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PdfPageRenderer(private val context: Context) {
    suspend fun render(uri: Uri, pageIndex: Int, targetWidth: Int = 1200): Bitmap? =
        withContext(Dispatchers.IO) {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            descriptor.use {
                PdfRenderer(it).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val width = targetWidth.coerceAtLeast(1)
                        val height = (width * (page.height.toFloat() / page.width.toFloat()))
                            .roundToInt()
                            .coerceAtLeast(1)
                        createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
}
