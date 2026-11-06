package com.leitorlocal.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Lightweight identity for a document exposed by Android's storage providers.
 *
 * URI alone is not enough: providers may replace a file while keeping the same URI. When the
 * provider does not expose last-modified, a small first/last-block hash closes the common
 * same-URI/same-size stale-cache case without reading the complete book.
 */
data class DocumentFingerprint(
    val size: Long,
    val lastModified: Long,
    val sampleHash: String,
) {
    fun writeTo(json: JSONObject): JSONObject = json
        .put(JSON_SIZE, size)
        .put(JSON_LAST_MODIFIED, lastModified)
        .put(JSON_SAMPLE_HASH, sampleHash)

    fun matches(json: JSONObject): Boolean =
        json.optLong(JSON_SIZE, UNKNOWN) == size &&
            json.optLong(JSON_LAST_MODIFIED, UNKNOWN) == lastModified &&
            json.optString(JSON_SAMPLE_HASH) == sampleHash

    fun cacheKey(uri: Uri): String = sha256("${uri}|$size|$lastModified|$sampleHash")

    companion object {
        const val UNKNOWN = -1L
        private const val JSON_SIZE = "sourceSize"
        private const val JSON_LAST_MODIFIED = "sourceLastModified"
        private const val JSON_SAMPLE_HASH = "sourceSampleHash"
        private const val SAMPLE_BYTES = 64 * 1024
        private const val SAMPLE_UNAVAILABLE = "unavailable"

        fun resolve(context: Context, uri: Uri): DocumentFingerprint {
            val resolver = context.contentResolver
            val queriedSize = queryLong(context, uri, OpenableColumns.SIZE)
            val descriptorSize = if (queriedSize >= 0L) queriedSize else runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: UNKNOWN
            }.getOrDefault(UNKNOWN)
            val lastModified = queryLong(
                context,
                uri,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            return DocumentFingerprint(
                size = descriptorSize,
                lastModified = lastModified,
                sampleHash = if (lastModified == UNKNOWN) {
                    sampledHash(context, uri, descriptorSize)
                } else {
                    ""
                },
            )
        }

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

        private fun sampledHash(context: Context, uri: Uri, size: Long): String = runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@runCatching SAMPLE_UNAVAILABLE
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(size.toString().toByteArray())
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val firstBytes = when {
                    size in 0L..(SAMPLE_BYTES * 2L) -> size.toInt()
                    else -> SAMPLE_BYTES
                }
                updateDigest(input, digest, firstBytes)
                if (size > SAMPLE_BYTES * 2L) {
                    runCatching {
                        input.channel.position(size - SAMPLE_BYTES)
                        digest.update(TAIL_MARKER)
                        updateDigest(input, digest, SAMPLE_BYTES)
                    }.onFailure {
                        digest.update(NO_TAIL_MARKER)
                    }
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrDefault(SAMPLE_UNAVAILABLE)

        private fun updateDigest(
            input: FileInputStream,
            digest: MessageDigest,
            byteLimit: Int,
        ) {
            val buffer = ByteArray(8 * 1024)
            var remaining = byteLimit.coerceAtLeast(0)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }

        private fun queryLong(context: Context, uri: Uri, column: String): Long = runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    UNKNOWN
                }
            } ?: UNKNOWN
        }.getOrDefault(UNKNOWN)

        private val TAIL_MARKER = byteArrayOf(0x54, 0x41, 0x49, 0x4c)
        private val NO_TAIL_MARKER = byteArrayOf(0x4e, 0x4f, 0x54, 0x41, 0x49, 0x4c)
    }
}
