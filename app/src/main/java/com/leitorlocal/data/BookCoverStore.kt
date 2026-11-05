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
import java.security.MessageDigest

class BookCoverStore(context: Context) {
    private val renderer = PdfPageRenderer(context.applicationContext)
    private val directory = File(context.filesDir, "book_covers_v1").apply { mkdirs() }
    private val mutex = Mutex()

    suspend fun ensureCover(uri: Uri): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val destination = File(directory, "${uri.digest()}.jpg")
            if (destination.isFile && destination.length() > 0) {
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

    private fun Uri.digest(): String = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
