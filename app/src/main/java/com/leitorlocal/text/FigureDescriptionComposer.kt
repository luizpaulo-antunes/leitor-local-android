package com.leitorlocal.text

object FigureDescriptionComposer {
    fun compose(
        caption: String,
        alternativeText: String,
        ocrText: String,
    ): String {
        val parts = mutableListOf<String>()
        addDistinct(parts, caption)
        addDistinct(parts, alternativeText)
        addDistinct(parts, ocrNarration(ocrText))
        if (parts.isEmpty()) return "Há uma figura nesta página."
        if (caption.isBlank()) parts.add(0, "Descrição da figura.")
        return parts.joinToString(" ") { TextPreprocessor.prepareForSpeech(it) }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun ocrNarration(raw: String): String {
        val originalLines = raw.lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 2 }
            .distinctBy { it.lowercase() }
        if (originalLines.isEmpty()) return ""

        val mergedLines = mutableListOf<String>()
        var index = 0
        while (index < originalLines.size) {
            val line = originalLines[index]
            if (line.matches(Regex("^\\d{1,2}[.)]?$")) && index + 1 < originalLines.size) {
                mergedLines += "$line ${originalLines[index + 1]}"
                index += 2
            } else {
                mergedLines += line
                index += 1
            }
        }

        val numbered = mergedLines.mapNotNull { line ->
            NUMBERED_STEP.find(line)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let { number -> number to line }
            }
        }.distinctBy(Pair<Int, String>::first).sortedBy(Pair<Int, String>::first)

        val numberedTexts = numbered.map(Pair<Int, String>::second).toSet()
        val supporting = mergedLines
            .filterNot(numberedTexts::contains)
            .filterNot { it.matches(Regex("^[.…]+$")) }
            .take(MAX_SUPPORTING_LINES)

        return buildString {
            if (numbered.size >= 2) {
                append("Etapas mostradas: ")
                append(numbered.joinToString("; ") { it.second })
                append('.')
                if (supporting.isNotEmpty()) append(" Textos de apoio: ")
            } else {
                append("Texto presente na figura: ")
            }
            append(supporting.joinToString("; "))
        }.take(MAX_OCR_NARRATION_LENGTH).trim()
    }

    private fun addDistinct(parts: MutableList<String>, candidate: String) {
        val cleaned = candidate.replace(Regex("\\s+"), " ").trim().trimEnd()
        if (cleaned.length < 3) return
        val signature = cleaned.lowercase().replace(Regex("[^\\p{L}\\d]+"), " ").trim()
        if (parts.any { existing ->
                val existingSignature = existing.lowercase()
                    .replace(Regex("[^\\p{L}\\d]+"), " ")
                    .trim()
                existingSignature.contains(signature) || signature.contains(existingSignature)
            }
        ) {
            return
        }
        parts += cleaned
    }

    private val NUMBERED_STEP = Regex("^(\\d{1,2})[.)]?\\s+.+")
    private const val MAX_SUPPORTING_LINES = 12
    private const val MAX_OCR_NARRATION_LENGTH = 850
}
