package com.leitorlocal.text

import java.text.BreakIterator
import java.util.Locale

object TextPreprocessor {
    private val sentenceLocale = Locale.forLanguageTag("pt-BR")

    fun cleanPage(raw: String): String {
        if (raw.isBlank()) return ""

        val paragraphs = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .fold(mutableListOf(mutableListOf<String>())) { groups, original ->
                val line = original.trim()
                if (line.isEmpty()) {
                    if (groups.last().isNotEmpty()) groups.add(mutableListOf())
                } else {
                    groups.last() += line
                }
                groups
            }
            .filter { it.isNotEmpty() }
            .map(::joinWrappedLines)
            .filter { it.isNotBlank() }

        return paragraphs.joinToString("\n\n")
    }

    fun splitIntoChunks(cleanText: String, targetLength: Int = 420): List<String> {
        if (cleanText.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        cleanText.split(Regex("\\n{2,}")).forEach { paragraph ->
            val iterator = BreakIterator.getSentenceInstance(sentenceLocale)
            iterator.setText(paragraph)
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                val sentence = paragraph.substring(start, end).trim()
                if (sentence.isNotEmpty()) sentences += splitOversized(sentence, 700)
                start = end
                end = iterator.next()
            }
        }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        sentences.forEach { sentence ->
            if (current.isNotEmpty() && current.length + sentence.length + 1 > targetLength) {
                chunks += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun joinWrappedLines(lines: List<String>): String {
        val result = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (index == 0) {
                result.append(line)
                return@forEachIndexed
            }

            val nextStartsLowercase = line.firstOrNull()?.isLowerCase() == true
            if (result.endsWith("-") && nextStartsLowercase) {
                result.deleteCharAt(result.lastIndex)
                result.append(line)
            } else {
                result.append(' ').append(line)
            }
        }
        return result.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun splitOversized(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)
        val parts = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > maxLength) {
            val candidate = remaining.take(maxLength)
            val cut = maxOf(
                candidate.lastIndexOf("; "),
                candidate.lastIndexOf(", "),
                candidate.lastIndexOf(' '),
            ).takeIf { it >= maxLength / 2 } ?: maxLength
            parts += remaining.take(cut).trim()
            remaining = remaining.drop(cut).trim()
        }
        if (remaining.isNotEmpty()) parts += remaining
        return parts
    }
}
