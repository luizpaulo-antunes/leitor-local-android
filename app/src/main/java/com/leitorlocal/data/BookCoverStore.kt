package com.leitorlocal.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.leitorlocal.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class BookCoverStore(context: Context) {
    private val context = context.applicationContext
    private val renderer = PdfPageRenderer(this.context)
    private val directory = File(this.context.filesDir, CACHE_DIRECTORY).apply { mkdirs() }
    private val mutex = Mutex()

    suspend fun ensureCover(uri: Uri): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val fingerprint = DocumentFingerprint.resolve(context, uri)
            val destination = File(directory, "${fingerprint.cacheKey(uri)}.jpg")
            if (destination.isFile && destination.length() > 0) {
                destination.setLastModified(System.currentTimeMillis())
                return@withContext destination.absolutePath
            }

            val cover = renderer.render(uri, pageIndex = 0, targetWidth = 360)
                ?: return@withContext null
            val temporary = File(directory, "${destination.name}.tmp")
            try {
                temporary.outputStream().use { output ->
                    check(cover.compress(Bitmap.CompressFormat.JPEG, 84, output))
                }
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                destination.absolutePath
            } finally {
                cover.recycle()
                temporary.delete()
            }
        }
    }

    companion object {
        const val CACHE_DIRECTORY = "book_covers_v2"
    }
}
