package com.leitorlocal.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes cached covers and figures away from the Compose/UI thread. */
@Composable
internal fun rememberFileBitmap(path: String?, maxSidePixels: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, path, maxSidePixels) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeSampled(path, maxSidePixels) }
        }
    }
    return bitmap
}

private fun decodeSampled(path: String, maxSidePixels: Int): Bitmap? {
    val safeMaxSide = maxSidePixels.coerceAtLeast(1)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (
        sampleSize <= Int.MAX_VALUE / 2 &&
        largestSide / sampleSize >= safeMaxSide * 2
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )?.also { File(path).setLastModified(System.currentTimeMillis()) }
}
