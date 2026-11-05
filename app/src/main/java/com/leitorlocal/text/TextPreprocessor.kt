package com.leitorlocal.text

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ceil

object TextPreprocessor {
    private val sentenceLocale = Locale.forLanguageTag("pt-BR")

    fun cleanDocumentPages(rawPages: List<String>): List<String> {
        val pageLines = rawPages.map(::normalizedLines)
        val populatedPages = pageLines.count { it.isNotEmpty() }
        val repeatedMargins = mutableMapOf<String, MutableSet<Int>>()

        pageLines.forEachIndexed { pageIndex, lines ->
            marginLines(lines).forEach { line ->
                val signature = marginSignature(line) ?: return@forEach
                repeatedMargins.getOrPut(signature, ::mutableSetOf) += pageIndex
            }
        }

        val minimumOccurrences = maxOf(2, ceil(populatedPages * 0.15).toInt())
        val ignoredSignatures = repeatedMargins
            .filterValues { pages -> pages.size >= minimumOccurrences }
            .keys

        return pageLines.map { lines ->
            val marginIndexes = marginIndexes(lines)
            val filtered = lines.filterIndexed { index, line ->
                if (index !in marginIndexes) return@filterIndexed true
                val isPageNumber = line.matches(PAGE_NUMBER_PATTERN)
                val isRepeatedMargin = marginSignature(line) in ignoredSignatures
                val isLetterSpacedHeader = isLetterSpacedRunningHeader(line)
                !isPageNumber && !isRepeatedMargin && !isLetterSpacedHeader
            }
            cleanPage(filtered.joinToString("\n"))
        }
    }

    fun cleanPage(raw: String): String {
        if (raw.isBlank()) return ""

        val paragraphs = normalizeCharacters(raw)
            .lines()
            .fold(mutableListOf(mutableListOf<String>())) { groups, original ->
                val line = normalizeLetterSpacing(original.trim())
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

    fun extractFigureCaptions(raw: String): List<FigureCaption> {
        val lines = normalizeCharacters(raw).lines()
        if (lines.isEmpty()) return emptyList()

        val captions = mutableListOf<FigureCaption>()
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val firstLine = lines[lineIndex].trim()
            if (!firstLine.matches(FIGURE_CAPTION_START)) {
                lineIndex += 1
                continue
            }

            val captionLines = mutableListOf<String>()
            var current = lineIndex
            while (current < lines.size && captionLines.size < MAX_CAPTION_LINES) {
                val line = lines[current].trim()
                if (line.isBlank()) break
                captionLines += line
                current += 1
                if (line.lastOrNull() in CAPTION_ENDING_PUNCTUATION) break
            }
            val caption = cleanPage(captionLines.joinToString("\n"))
                .take(MAX_CAPTION_LENGTH)
            if (caption.isNotBlank()) {
                captions += FigureCaption(
                    text = caption,
                    relativePosition = lineIndex.toFloat() / lines.size.coerceAtLeast(1),
                )
            }
            lineIndex = maxOf(current, lineIndex + 1)
        }
        return captions
    }

    fun splitIntoChunks(cleanText: String, targetLength: Int = 240): List<String> {
        if (cleanText.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        cleanText.split(Regex("\\n{2,}")).forEach { originalParagraph ->
            val paragraph = prepareForSpeech(originalParagraph)
            val sentences = mutableListOf<String>()
            val iterator = BreakIterator.getSentenceInstance(sentenceLocale)
            iterator.setText(paragraph)
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                val sentence = paragraph.substring(start, end).trim()
                if (sentence.isNotEmpty()) sentences += splitOversized(sentence, targetLength)
                start = end
                end = iterator.next()
            }

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
        }
        return chunks
    }

    fun prepareForSpeech(text: String): String {
        val normalized = text
            .replace(Regex("[•▪◦]\\s*"), " ")
            .replace(Regex("\\s*[—–]\\s*"), ", ")
            .replace(Regex("\\.{3,}"), "…")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return normalized
        val looksLikeHeading = normalized.length <= 100 &&
            normalized.none { it in ".!?;:,…" }
        return if (looksLikeHeading) "$normalized." else normalized
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

    private fun normalizedLines(raw: String): List<String> = normalizeCharacters(raw)
        .lines()
        .map(String::trim)

    private fun normalizeCharacters(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u00ad', '-')
        .replace('\u00a0', ' ')

    private fun normalizeLetterSpacing(line: String): String {
        val tokens = line.split(WHITESPACE_PATTERN).filter(String::isNotEmpty)
        if (tokens.isEmpty()) return line
        val isolatedLetters = tokens.count { token ->
            token.length == 1 && token.single().isLetter()
        }
        if (isolatedLetters < MIN_LETTER_SPACED_TOKENS || isolatedLetters * 5 < tokens.size * 4) {
            return line
        }

        return line.split(WORD_GAP_PATTERN).joinToString(" ") { word ->
            val letters = word.trim().split(WHITESPACE_PATTERN).filter(String::isNotEmpty)
            if (letters.isNotEmpty() && letters.all { it.length == 1 && it.single().isLetter() }) {
                letters.joinToString("")
            } else {
                word.trim()
            }
        }
    }

    private fun isLetterSpacedRunningHeader(line: String): Boolean {
        val trimmed = line.trim()
        val carriesPageNumber = trimmed.firstOrNull()?.isDigit() == true ||
            trimmed.lastOrNull()?.isDigit() == true
        if (!carriesPageNumber) return false

        val title = trimmed
            .replace(LEADING_PAGE_NUMBER_PATTERN, "")
            .replace(TRAILING_PAGE_NUMBER_PATTERN, "")
            .trim()
        val tokens = title.split(WHITESPACE_PATTERN).filter(String::isNotEmpty)
        return tokens.size >= MIN_LETTER_SPACED_TOKENS && tokens.all { token ->
            token.length == 1 && token.single().isUpperCase()
        }
    }

    private fun marginIndexes(lines: List<String>): Set<Int> {
        val nonBlankIndexes = lines.indices.filter { lines[it].isNotBlank() }
        return (nonBlankIndexes.take(MARGIN_LINE_COUNT) + nonBlankIndexes.takeLast(MARGIN_LINE_COUNT)).toSet()
    }

    private fun marginLines(lines: List<String>): List<String> =
        marginIndexes(lines).map(lines::get).filter(String::isNotBlank)

    private fun marginSignature(line: String): String? {
        val compact = line.lowercase(sentenceLocale)
            .replace(Regex("\\d+"), "#")
            .replace(Regex("\\s+"), " ")
            .trim()
        return compact.takeIf { it.length in 2..120 }
    }

    private const val MARGIN_LINE_COUNT = 3
    private const val MIN_LETTER_SPACED_TOKENS = 6
    private const val MAX_CAPTION_LINES = 4
    private const val MAX_CAPTION_LENGTH = 500
    private val CAPTION_ENDING_PUNCTUATION = setOf('.', '!', '?')
    private val FIGURE_CAPTION_START = Regex(
        "^(?:figura|fig\\.?|imagem|gr[aá]fico|tabela)\\s+[\\p{L}\\d][\\p{L}\\d._-]*.*$",
        RegexOption.IGNORE_CASE,
    )
    private val PAGE_NUMBER_PATTERN = Regex(
        "^(?:(?:p[aá]g(?:ina)?\\.?|page)\\s*)?(?:\\d+|[ivxlcdm]+)$",
        RegexOption.IGNORE_CASE,
    )
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val WORD_GAP_PATTERN = Regex("\\s{2,}")
    private val LEADING_PAGE_NUMBER_PATTERN = Regex("^\\d{1,4}\\s*")
    private val TRAILING_PAGE_NUMBER_PATTERN = Regex("\\s*\\d{1,4}$")
}

data class FigureCaption(
    val text: String,
    val relativePosition: Float,
)
