package com.leitorlocal.speech

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

class NeuralAudioCache(context: Context) {
    private val root = File(context.noBackupFilesDir, CACHE_DIRECTORY)
    private var bytesWrittenSinceLastTrim = 0L
    private var initialTrimCompleted = false

    @Synchronized
    fun load(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voiceName: String,
    ): NeuralAudio? {
        ensureInitialTrim()
        val file = cacheFile(bookId, chunkIndex, text, speechRate, voiceName)
        if (!file.isFile) return null

        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                check(input.readInt() == MAGIC) { "Cache de audio invalido." }
                check(input.readInt() == FORMAT_VERSION) { "Versao de cache incompativel." }
                val sampleRate = input.readInt()
                val sampleCount = input.readInt()
                check(sampleRate in 8_000..96_000) { "Taxa de audio invalida." }
                check(sampleCount in 1..MAX_SAMPLE_COUNT) { "Tamanho de audio invalido." }
                val samples = FloatArray(sampleCount) {
                    input.readShort().toFloat() / 32_768f
                }
                file.setLastModified(System.currentTimeMillis())
                NeuralAudio(samples, sampleRate)
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    @Synchronized
    fun save(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voiceName: String,
        audio: NeuralAudio,
    ) {
        ensureInitialTrim()
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) return
        val destination = cacheFile(bookId, chunkIndex, text, speechRate, voiceName)
        if (destination.isFile) return
        destination.parentFile?.mkdirs()

        val temporary = File(destination.parentFile, ".${destination.name}.${System.nanoTime()}.tmp")
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeInt(audio.sampleRate)
                output.writeInt(audio.samples.size)
                audio.samples.forEach { sample ->
                    val pcm16 = (sample.coerceIn(-1f, 1f) * 32_767f).roundToInt()
                    output.writeShort(pcm16)
                }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.parentFile
                ?.listFiles { file ->
                    file.isFile && file.name.startsWith("${chunkIndex}_") && file != destination
                }
                ?.forEach(File::delete)
            bytesWrittenSinceLastTrim += destination.length()
            if (bytesWrittenSinceLastTrim >= TRIM_INTERVAL_BYTES) {
                trimToSize()
                bytesWrittenSinceLastTrim = 0L
            }
        }.onFailure {
            temporary.delete()
        }
    }

    private fun cacheFile(
        bookId: String,
        chunkIndex: Int,
        text: String,
        speechRate: Float,
        voiceName: String,
    ): File {
        val bookDirectory = File(root, sha256(bookId).take(20))
        val rateKey = (speechRate.coerceIn(0.6f, 1.6f) * 1_000).roundToInt()
        val voiceKey = if (voiceName == EMBEDDED_NEURAL_MIRO_VOICE_NAME) "" else "$voiceName\n"
        val contentKey = sha256("$PROCESSING_PROFILE\n$rateKey\n$voiceKey$text").take(20)
        return File(bookDirectory, "${chunkIndex}_$contentKey.pcm")
    }

    private fun trimToSize() {
        val files = root.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedBy(File::lastModified)
            .toList()
        var totalBytes = files.sumOf(File::length)
        files.forEach { file ->
            if (totalBytes <= MAX_CACHE_BYTES) return
            val size = file.length()
            if (file.delete()) totalBytes -= size
        }
        root.walkBottomUp()
            .filter { it.isDirectory && it != root && it.listFiles().isNullOrEmpty() }
            .forEach(File::delete)
    }

    private fun ensureInitialTrim() {
        if (initialTrimCompleted) return
        trimToSize()
        initialTrimCompleted = true
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val CACHE_DIRECTORY = "neural_audio_cache_v2"
        private const val PROCESSING_PROFILE = "neural-voice-profiles-v4-kokoro-int8"
        private const val MAGIC = 0x4c4c4132
        private const val FORMAT_VERSION = 2
        private const val MAX_SAMPLE_COUNT = 96_000 * 60 * 10
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
        private const val TRIM_INTERVAL_BYTES = 16L * 1024 * 1024
    }
}
