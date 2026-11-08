package com.leitorlocal.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Uses the model bundled in the APK; no Play Services download or network is involved. */
class FigureVisualAnalyzer : AutoCloseable {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(MIN_CONFIDENCE)
            .build(),
    )

    suspend fun describe(bitmap: Bitmap): List<String> = withContext(NonCancellable) {
        suspendCancellableCoroutine { continuation ->
            runCatching {
                labeler.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { labels ->
                        val descriptions = labels
                            .sortedByDescending { it.confidence }
                            .map { translate(it.text) }
                            .distinctBy(String::lowercase)
                            .take(MAX_LABELS)
                        if (continuation.isActive) continuation.resume(descriptions)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
            }.onFailure {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    }

    override fun close() = labeler.close()

    private fun translate(label: String): String = PORTUGUESE_LABELS[label.lowercase()] ?: label

    companion object {
        private const val MIN_CONFIDENCE = 0.70f
        private const val MAX_LABELS = 4
        private val PORTUGUESE_LABELS = mapOf(
            "person" to "pessoa",
            "people" to "pessoas",
            "child" to "criança",
            "face" to "rosto",
            "portrait" to "retrato",
            "building" to "edifício",
            "house" to "casa",
            "room" to "ambiente interno",
            "furniture" to "móveis",
            "car" to "carro",
            "vehicle" to "veículo",
            "road" to "estrada",
            "tree" to "árvore",
            "plant" to "planta",
            "flower" to "flor",
            "nature" to "natureza",
            "landscape" to "paisagem",
            "sky" to "céu",
            "water" to "água",
            "animal" to "animal",
            "bird" to "ave",
            "dog" to "cachorro",
            "cat" to "gato",
            "food" to "comida",
            "diagram" to "diagrama",
            "drawing" to "desenho",
            "illustration" to "ilustração",
            "map" to "mapa",
            "text" to "texto",
            "document" to "documento",
            "book" to "livro",
            "photograph" to "fotografia",
        )
    }
}
