package com.leitorlocal.speech

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

internal data class NeuralPreparationSnapshot(
    val contiguousChunks: Int = 0,
    val preparedDurationMillis: Long = 0L,
    val reachedBookEnd: Boolean = false,
)

class NeuralAudioCache(context: Context) {
    private val noBackupFilesDir = context.noBackupFilesDir
    private val root = File(noBackupFilesDir, CACHE_DIRECTORY)
    private val maintenanceLock = Any()
    @Volatile private var bytesWrittenSinceLastTrim = 0L
    @Volatile private var initialMaintenanceCompleted = false

    fun load(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voiceName: String,
    ): NeuralAudio? {
        ensureInitialMaintenance(bookId)
        val file = cacheFile(bookId, chunkIndex, text, speechRate, voiceName)
        if (!file.isFile) return null

        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file), IO_BUFFER_BYTES)).use { input ->
                check(input.readInt() == MAGIC) { "Cache de áudio inválido." }
                check(input.readInt() == FORMAT_VERSION) { "Versão de cache incompatível." }
                when (input.readInt()) {
                    CODEC_OPUS -> OpusAudioCodec.read(input)
                    CODEC_PCM16 -> readPcm16(input)
                    else -> error("Codec de cache desconhecido.")
                }
            }.also { audio ->
                val now = System.currentTimeMillis()
                file.setLastModified(now)
                file.parentFile?.setLastModified(now)
                recordPreparedAudio(
                    bookId = bookId,
                    chunkIndex = chunkIndex,
                    speechRate = speechRate,
                    voiceName = voiceName,
                    durationMillis = audio.durationMillis(),
                )
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    fun save(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voiceName: String,
        audio: NeuralAudio,
    ): Boolean {
        ensureInitialMaintenance(bookId)
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) return false
        val destination = cacheFile(bookId, chunkIndex, text, speechRate, voiceName)
        if (destination.isFile) {
            recordPreparedAudio(
                bookId,
                chunkIndex,
                speechRate,
                voiceName,
                audio.durationMillis(),
            )
            return true
        }
        destination.parentFile?.mkdirs()

        val temporary = File(destination.parentFile, ".${destination.name}.${System.nanoTime()}.tmp")
        return runCatching {
            DataOutputStream(
                BufferedOutputStream(FileOutputStream(temporary), IO_BUFFER_BYTES),
            ).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT_VERSION)
                if (OpusAudioCodec.supports(audio.sampleRate)) {
                    output.writeInt(CODEC_OPUS)
                    OpusAudioCodec.write(audio, output)
                } else {
                    output.writeInt(CODEC_PCM16)
                    writePcm16(audio, output)
                }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.parentFile?.setLastModified(System.currentTimeMillis())
            val written = destination.length()
            val accumulated = synchronized(maintenanceLock) {
                bytesWrittenSinceLastTrim += written
                bytesWrittenSinceLastTrim
            }
            if (accumulated >= TRIM_INTERVAL_BYTES) {
                synchronized(maintenanceLock) {
                    if (bytesWrittenSinceLastTrim >= TRIM_INTERVAL_BYTES) {
                        trimToSize(protectedBookId = bookId)
                        bytesWrittenSinceLastTrim = 0L
                    }
                }
            }
            recordPreparedAudio(
                bookId,
                chunkIndex,
                speechRate,
                voiceName,
                audio.durationMillis(),
            )
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    internal fun preparationSnapshot(
        bookId: String,
        chunkTexts: List<String>,
        startIndex: Int,
        speechRate: Float,
        voiceName: String,
    ): NeuralPreparationSnapshot {
        if (chunkTexts.isEmpty() || startIndex !in chunkTexts.indices) {
            return NeuralPreparationSnapshot(reachedBookEnd = chunkTexts.isEmpty())
        }
        val indexFile = preparationIndexFile(bookId, speechRate, voiceName)
        val entries = synchronized(PREPARATION_INDEX_LOCK) {
            preparationIndexEntries.getOrPut(indexFile.absolutePath) {
                readPreparationEntries(indexFile)
            }.toMap()
        }
        var index = startIndex
        var durationMillis = 0L
        while (index < chunkTexts.size) {
            val duration = entries[index]?.takeIf { it > 0L } ?: break
            if (!cacheFile(bookId, index, chunkTexts[index], speechRate, voiceName).isFile) break
            durationMillis += duration
            index += 1
        }
        return NeuralPreparationSnapshot(
            contiguousChunks = index - startIndex,
            preparedDurationMillis = durationMillis,
            reachedBookEnd = index >= chunkTexts.size,
        )
    }

    fun markBookPrepared(
        bookId: String,
        chunkCount: Int,
        speechRate: Float,
        voiceName: String,
    ) {
        if (chunkCount <= 0) return
        val marker = preparedMarker(bookId, speechRate, voiceName)
        marker.parentFile?.mkdirs()
        val temporary = File(marker.parentFile, ".${marker.name}.${System.nanoTime()}.tmp")
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.writeInt(PREPARED_MARKER_VERSION)
                output.writeInt(chunkCount)
            }
            if (!temporary.renameTo(marker)) {
                temporary.copyTo(marker, overwrite = true)
                temporary.delete()
            }
            marker.parentFile?.setLastModified(System.currentTimeMillis())
        }.onFailure { temporary.delete() }
    }

    fun isBookPrepared(
        bookId: String,
        chunkCount: Int,
        speechRate: Float,
        voiceName: String,
    ): Boolean {
        if (chunkCount <= 0) return false
        val marker = preparedMarker(bookId, speechRate, voiceName)
        if (!marker.isFile) return false
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(marker))).use { input ->
                input.readInt() == PREPARED_MARKER_VERSION && input.readInt() == chunkCount
            }
        }.getOrDefault(false)
    }

    private fun writePcm16(audio: NeuralAudio, output: DataOutputStream) {
        output.writeInt(audio.sampleRate)
        output.writeInt(audio.samples.size)
        val bytes = ByteArray(PCM_BLOCK_SAMPLES * 2)
        var sampleOffset = 0
        while (sampleOffset < audio.samples.size) {
            val count = minOf(PCM_BLOCK_SAMPLES, audio.samples.size - sampleOffset)
            repeat(count) { localIndex ->
                val pcm = (audio.samples[sampleOffset + localIndex].coerceIn(-1f, 1f) * 32_767f)
                    .roundToInt()
                val byteIndex = localIndex * 2
                bytes[byteIndex] = (pcm shr 8).toByte()
                bytes[byteIndex + 1] = pcm.toByte()
            }
            output.write(bytes, 0, count * 2)
            sampleOffset += count
        }
    }

    private fun readPcm16(input: DataInputStream): NeuralAudio {
        val sampleRate = input.readInt()
        val sampleCount = input.readInt()
        check(sampleRate in 8_000..96_000) { "Taxa de áudio inválida." }
        check(sampleCount in 1..MAX_SAMPLE_COUNT) { "Tamanho de áudio inválido." }
        val samples = FloatArray(sampleCount)
        val bytes = ByteArray(PCM_BLOCK_SAMPLES * 2)
        var sampleOffset = 0
        while (sampleOffset < sampleCount) {
            val count = minOf(PCM_BLOCK_SAMPLES, sampleCount - sampleOffset)
            input.readFully(bytes, 0, count * 2)
            repeat(count) { localIndex ->
                val byteIndex = localIndex * 2
                val pcm = ((bytes[byteIndex].toInt() shl 8) or
                    (bytes[byteIndex + 1].toInt() and 0xff)).toShort()
                samples[sampleOffset + localIndex] = pcm.toFloat() / 32_768f
            }
            sampleOffset += count
        }
        return NeuralAudio(samples, sampleRate)
    }

    private fun cacheFile(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voiceName: String,
    ): File {
        val contentKey = sha256("${profileKey(speechRate, voiceName)}\n$text").take(20)
        return File(bookDirectory(bookId), "${chunkIndex}_$contentKey.lac")
    }

    private fun preparedMarker(bookId: String, speechRate: Float, voiceName: String): File =
        File(bookDirectory(bookId), ".prepared_${profileKey(speechRate, voiceName).take(20)}")

    private fun preparationIndexFile(bookId: String, speechRate: Float, voiceName: String): File =
        File(bookDirectory(bookId), ".progress_${profileKey(speechRate, voiceName).take(20)}")

    private fun recordPreparedAudio(
        bookId: String,
        chunkIndex: Int,
        speechRate: Float,
        voiceName: String,
        durationMillis: Long,
    ) {
        if (chunkIndex < 0 || durationMillis <= 0L) return
        val file = preparationIndexFile(bookId, speechRate, voiceName)
        synchronized(PREPARATION_INDEX_LOCK) {
            val entries = preparationIndexEntries.getOrPut(file.absolutePath) {
                readPreparationEntries(file)
            }
            if (hasValidPreparationHeader(file) && entries[chunkIndex] == durationMillis) return
            file.parentFile?.mkdirs()
            if (!hasValidPreparationHeader(file)) {
                file.delete()
                DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { output ->
                    output.writeInt(PREPARATION_INDEX_MAGIC)
                    output.writeInt(PREPARATION_INDEX_VERSION)
                }
                entries.clear()
            }
            DataOutputStream(BufferedOutputStream(FileOutputStream(file, true))).use { output ->
                output.writeInt(chunkIndex)
                output.writeLong(durationMillis)
            }
            entries[chunkIndex] = durationMillis
        }
    }

    private fun readPreparationEntries(file: File): MutableMap<Int, Long> {
        val entries = linkedMapOf<Int, Long>()
        if (!hasValidPreparationHeader(file)) return entries
        runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                input.readInt()
                input.readInt()
                while (true) {
                    try {
                        val chunkIndex = input.readInt()
                        val durationMillis = input.readLong()
                        if (chunkIndex >= 0 && durationMillis > 0L) {
                            entries[chunkIndex] = durationMillis
                        }
                    } catch (_: EOFException) {
                        break
                    }
                }
            }
        }.onFailure {
            file.delete()
            entries.clear()
        }
        return entries
    }

    private fun hasValidPreparationHeader(file: File): Boolean {
        if (!file.isFile || file.length() < PREPARATION_INDEX_HEADER_BYTES) return false
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                input.readInt() == PREPARATION_INDEX_MAGIC &&
                    input.readInt() == PREPARATION_INDEX_VERSION
            }
        }.getOrDefault(false)
    }

    private fun NeuralAudio.durationMillis(): Long =
        if (sampleRate <= 0) 0L else samples.size.toLong() * 1_000L / sampleRate

    private fun profileKey(speechRate: Float, voiceName: String): String {
        val rateKey = (speechRate.coerceIn(0.6f, 1.6f) * 1_000).roundToInt()
        return sha256("$PROCESSING_PROFILE\n$rateKey\n$voiceName")
    }

    private fun bookDirectory(bookId: String): File = File(root, sha256(bookId).take(20))

    private fun trimToSize(protectedBookId: String?) {
        if (!root.isDirectory) return
        val protectedDirectory = protectedBookId?.let(::bookDirectory)
        data class CachedBook(val directory: File, val bytes: Long, val lastUsedAt: Long)
        val books = root.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map { directory ->
                val files = directory.walkTopDown()
                    .filter { it.isFile && !it.name.endsWith(".tmp") }
                    .toList()
                CachedBook(
                    directory = directory,
                    bytes = files.sumOf(File::length),
                    lastUsedAt = maxOf(
                        directory.lastModified(),
                        files.maxOfOrNull(File::lastModified) ?: 0L,
                    ),
                )
            }
        var totalBytes = books.sumOf(CachedBook::bytes)
        books.sortedBy(CachedBook::lastUsedAt).forEach { book ->
            if (totalBytes <= MAX_CACHE_BYTES) return
            if (book.directory == protectedDirectory) return@forEach
            if (book.directory.deleteRecursively()) totalBytes -= book.bytes
        }
    }

    private fun ensureInitialMaintenance(protectedBookId: String?) {
        if (initialMaintenanceCompleted) return
        synchronized(maintenanceLock) {
            if (initialMaintenanceCompleted) return
            LEGACY_CACHE_DIRECTORIES.forEach { name ->
                runCatching { File(noBackupFilesDir, name).deleteRecursively() }
            }
            trimToSize(protectedBookId)
            initialMaintenanceCompleted = true
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val CACHE_DIRECTORY = "neural_audio_cache_v3"
        private val LEGACY_CACHE_DIRECTORIES = listOf("neural_audio_cache_v1", "neural_audio_cache_v2")
        private const val PROCESSING_PROFILE = "neural-voice-profiles-v6-opus-continuous"
        private const val MAGIC = 0x4c4c4133
        private const val FORMAT_VERSION = 3
        private const val PREPARED_MARKER_VERSION = 1
        private const val PREPARATION_INDEX_MAGIC = 0x4c4c5031
        private const val PREPARATION_INDEX_VERSION = 1
        private const val PREPARATION_INDEX_HEADER_BYTES = 8L
        private const val CODEC_PCM16 = 1
        private const val CODEC_OPUS = 2
        private const val MAX_SAMPLE_COUNT = 96_000 * 60 * 10
        private const val PCM_BLOCK_SAMPLES = 8 * 1024
        private const val IO_BUFFER_BYTES = 64 * 1024
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
        private const val TRIM_INTERVAL_BYTES = 32L * 1024 * 1024
        private val PREPARATION_INDEX_LOCK = Any()
        private val preparationIndexEntries = mutableMapOf<String, MutableMap<Int, Long>>()
    }
}
