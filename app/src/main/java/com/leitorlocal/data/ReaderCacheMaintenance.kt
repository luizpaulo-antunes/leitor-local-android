package com.leitorlocal.data

import android.content.Context
import android.net.Uri
import com.leitorlocal.pdf.PdfFigureAnalyzer
import java.io.File

/** Keeps regenerated book artifacts bounded without adding a database or background service. */
class ReaderCacheMaintenance(context: Context) {
    private val context = context.applicationContext
    private val filesDir = this.context.filesDir

    fun run(activeBookId: String? = null) {
        LEGACY_DIRECTORIES.forEach { name ->
            runCatching { File(filesDir, name).deleteRecursively() }
        }
        trim(File(filesDir, BookCache.CACHE_DIRECTORY), MAX_BOOK_CACHE_BYTES)
        trim(File(filesDir, PageOcrCache.CACHE_DIRECTORY), MAX_OCR_CACHE_BYTES)
        trim(File(filesDir, BookCoverStore.CACHE_DIRECTORY), MAX_COVER_CACHE_BYTES)
        trimBookDirectories(
            directory = File(filesDir, PdfFigureAnalyzer.FIGURE_DIRECTORY),
            limitBytes = MAX_FIGURE_CACHE_BYTES,
            excludedDirectoryName = activeBookId
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?.let { uri ->
                    runCatching {
                        DocumentFingerprint.resolve(context, uri).cacheKey(uri)
                    }.getOrNull()
                },
        )
    }

    private fun trim(directory: File, limitBytes: Long) {
        if (!directory.isDirectory) return
        val files = directory.walkBottomUp()
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .toList()
        var total = files.sumOf(File::length)
        if (total <= limitBytes) return
        files.sortedBy(File::lastModified).forEach { file ->
            if (total <= limitBytes) return@forEach
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) total -= length
        }
        directory.walkBottomUp()
            .filter { it.isDirectory && it != directory }
            .forEach { empty -> runCatching { empty.delete() } }
    }

    /** Figure paths are persisted as a set; evicting a whole book keeps that set consistent. */
    private fun trimBookDirectories(
        directory: File,
        limitBytes: Long,
        excludedDirectoryName: String?,
    ) {
        if (!directory.isDirectory) return
        data class BookDirectory(val directory: File, val bytes: Long, val lastUsedAt: Long)

        val books = directory.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map { bookDirectory ->
                val files = bookDirectory.walkTopDown()
                    .filter { it.isFile && !it.name.endsWith(".tmp") }
                    .toList()
                BookDirectory(
                    directory = bookDirectory,
                    bytes = files.sumOf(File::length),
                    lastUsedAt = files.maxOfOrNull(File::lastModified)
                        ?: bookDirectory.lastModified(),
                )
            }
        var total = books.sumOf(BookDirectory::bytes)
        books.sortedBy(BookDirectory::lastUsedAt).forEach { book ->
            if (total <= limitBytes) return@forEach
            if (book.directory.name == excludedDirectoryName) return@forEach
            if (runCatching { book.directory.deleteRecursively() }.getOrDefault(false)) {
                total -= book.bytes
            }
        }
    }

    companion object {
        private val LEGACY_DIRECTORIES = listOf(
            "processed_books_v1",
            "processed_books_v2",
            "processed_books_v3",
            "processed_books_v4",
            "processed_books_v5",
            "processed_books_v6",
            "processed_books_v7",
            "page_text_ocr_v1",
            "book_covers_v1",
            "book_figures_v1",
        )
        private const val MAX_BOOK_CACHE_BYTES = 64L * 1024L * 1024L
        private const val MAX_OCR_CACHE_BYTES = 64L * 1024L * 1024L
        private const val MAX_COVER_CACHE_BYTES = 48L * 1024L * 1024L
        private const val MAX_FIGURE_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
