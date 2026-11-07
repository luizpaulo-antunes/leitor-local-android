package com.leitorlocal.text

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ceil

object TextPreprocessor {
    private val sentenceLocale = Locale.forLanguageTag("pt-BR")

    fun detectHeadingCandidates(lines: List<TextLineLayout>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val bodyFontSize = lines
            .asSequence()
            .filter { it.text.length >= MIN_BODY_LINE_LENGTH && it.fontSizePt > 0f }
            .map(TextLineLayout::fontSizePt)
            .sorted()
            .toList()
            .let { sizes -> sizes.getOrNull(sizes.size / 2) ?: 0f }

        val candidates = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val previous = lines.getOrNull(index - 1)?.text.orEmpty().trim()
            val hasParagraphBoundary = line.startsParagraph || index == 0 ||
                previous.lastOrNull() in PARAGRAPH_ENDING_PUNCTUATION
            if (!hasParagraphBoundary) {
                index += 1
                continue
            }

            var continuationEnd = index + 1
            while (
                continuationEnd < lines.size &&
                !lines[continuationEnd].startsParagraph &&
                hasContinuousHeadingTypography(line, lines[continuationEnd])
            ) {
                continuationEnd += 1
            }

            val candidate = cleanPage(
                lines.subList(index, continuationEnd).joinToString("\n", transform = TextLineLayout::text),
            ).replace(PARAGRAPH_SEPARATOR, " ").trim()
            val words = candidate.split(WHITESPACE_PATTERN).filter(String::isNotEmpty)
            if (
                candidate.length !in MIN_HEADING_LENGTH..MAX_HEADING_LENGTH ||
                words.size !in 1..MAX_HEADING_WORDS ||
                candidate.matches(PAGE_NUMBER_PATTERN) ||
                candidate.matches(FIGURE_CAPTION_START) ||
                candidate.lastOrNull() in HEADING_REJECTED_ENDINGS
            ) {
                index += 1
                continue
            }

            val next = lines.getOrNull(continuationEnd)?.text.orEmpty().trim()
            val hasLargerFont = bodyFontSize > 0f &&
                line.fontSizePt >= bodyFontSize * HEADING_FONT_RATIO
            val hasTypographicEvidence = line.isBold || hasLargerFont
            val hasExplicitPattern = candidate.matches(EXPLICIT_HEADING_PATTERN) ||
                candidate.isMostlyUppercase()
            val isVisuallySeparate = next.isEmpty() ||
                candidate.length <= (next.length * HEADING_TO_BODY_LENGTH_RATIO).toInt() ||
                next.firstOrNull()?.isUpperCase() == true

            if (
                isVisuallySeparate &&
                (hasTypographicEvidence || hasExplicitPattern)
            ) {
                candidates += candidate
                index = continuationEnd
            } else {
                index += 1
            }
        }
        return candidates.distinctBy { it.lowercase(sentenceLocale) }
    }

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

    fun splitIntoChunks(
        cleanText: String,
        targetLength: Int = DEFAULT_SPEECH_CHUNK_LENGTH,
    ): List<String> {
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

    fun splitIntoStructuredChunks(
        cleanText: String,
        headingCandidates: List<String>,
        targetLength: Int = DEFAULT_SPEECH_CHUNK_LENGTH,
    ): List<StructuredTextChunk> {
        val remainingHeadings = headingCandidates.toMutableList()
        return splitIntoChunks(cleanText, targetLength).map { text ->
            val structured = applyFirstHeading(text, remainingHeadings)
            structured.matchedHeading?.let(remainingHeadings::remove)
            StructuredTextChunk(text = structured.text, ranges = structured.ranges)
        }
    }

    fun prepareForSpeech(text: String): String {
        val normalized = normalizeForSpeech(text)
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

    private fun applyFirstHeading(
        text: String,
        candidates: List<String>,
    ): AppliedHeading {
        val match = candidates
            .asSequence()
            .mapNotNull { rawCandidate ->
                val candidate = normalizeForSpeech(
                    cleanPage(rawCandidate).replace(PARAGRAPH_SEPARATOR, " "),
                )
                if (candidate.isEmpty()) return@mapNotNull null
                val index = text.indexOf(candidate, ignoreCase = true)
                if (index < 0 || !isHeadingBoundary(text, index, candidate.length)) {
                    return@mapNotNull null
                }
                HeadingMatch(raw = rawCandidate, normalized = candidate, index = index)
            }
            .minByOrNull(HeadingMatch::index)
            ?: return AppliedHeading(text)

        val before = text.substring(0, match.index).trimEnd()
        val matchedText = text.substring(match.index, match.index + match.normalized.length)
        var after = text.substring(match.index + match.normalized.length).trimStart()
        if (before.isEmpty() && after.length == 1 && after.single() in TERMINAL_PUNCTUATION) {
            return AppliedHeading(
                text = text,
                ranges = listOf(StructuredTextRange(0, text.length)),
                matchedHeading = match.raw,
            )
        }

        val heading = if (matchedText.lastOrNull() in TERMINAL_PUNCTUATION) {
            matchedText
        } else {
            "$matchedText."
        }
        if (after.firstOrNull() == '.' && heading.endsWith('.')) after = after.drop(1).trimStart()

        val prefix = before.takeIf(String::isNotEmpty)?.plus(PARAGRAPH_SEPARATOR).orEmpty()
        val suffix = after.takeIf(String::isNotEmpty)
            ?.let { "$PARAGRAPH_SEPARATOR$it" }
            .orEmpty()
        val structuredText = prefix + heading + suffix
        return AppliedHeading(
            text = structuredText,
            ranges = listOf(
                StructuredTextRange(
                    start = prefix.length,
                    endExclusive = prefix.length + heading.length,
                ),
            ),
            matchedHeading = match.raw,
        )
    }

    private fun normalizeForSpeech(text: String): String = text
        .replace(BULLET_PATTERN, " ")
        .replace(DASH_PATTERN, ", ")
        .replace(REPEATED_DOTS_PATTERN, "…")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    private fun isHeadingBoundary(text: String, start: Int, length: Int): Boolean {
        val before = text.substring(0, start).trimEnd()
        val end = start + length
        val characterAfter = text.getOrNull(end)
        val validBefore = before.isEmpty() || before.lastOrNull() in PARAGRAPH_ENDING_PUNCTUATION
        val validAfter = characterAfter == null ||
            characterAfter.isWhitespace() ||
            characterAfter in TERMINAL_PUNCTUATION
        return validBefore && validAfter
    }

    private fun String.isMostlyUppercase(): Boolean {
        val letters = count(Char::isLetter)
        if (letters < MIN_UPPERCASE_HEADING_LETTERS) return false
        val uppercase = count(Char::isUpperCase)
        return uppercase.toFloat() / letters >= MIN_UPPERCASE_RATIO
    }

    private fun hasContinuousHeadingTypography(
        first: TextLineLayout,
        continuation: TextLineLayout,
    ): Boolean {
        if (first.isBold != continuation.isBold) return false
        val firstSize = first.fontSizePt
        val continuationSize = continuation.fontSizePt
        if (firstSize <= 0f || continuationSize <= 0f) {
            return firstSize <= 0f && continuationSize <= 0f
        }
        val tolerance = maxOf(
            MIN_HEADING_FONT_CONTINUITY_PT,
            maxOf(firstSize, continuationSize) * HEADING_FONT_CONTINUITY_RATIO,
        )
        return kotlin.math.abs(firstSize - continuationSize) <= tolerance
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
    private const val DEFAULT_SPEECH_CHUNK_LENGTH = 160
    private const val MIN_LETTER_SPACED_TOKENS = 6
    private const val MAX_CAPTION_LINES = 4
    private const val MAX_CAPTION_LENGTH = 500
    private const val MIN_BODY_LINE_LENGTH = 30
    private const val MIN_HEADING_LENGTH = 3
    private const val MAX_HEADING_LENGTH = 100
    private const val MAX_HEADING_WORDS = 14
    private const val MIN_UPPERCASE_HEADING_LETTERS = 4
    private const val MIN_UPPERCASE_RATIO = 0.82f
    private const val HEADING_FONT_RATIO = 1.12f
    private const val HEADING_FONT_CONTINUITY_RATIO = 0.08f
    private const val MIN_HEADING_FONT_CONTINUITY_PT = 0.5f
    private const val HEADING_TO_BODY_LENGTH_RATIO = 0.90f
    private const val PARAGRAPH_SEPARATOR = "\n\n"
    private val CAPTION_ENDING_PUNCTUATION = setOf('.', '!', '?')
    private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', ':', ';', '…')
    private val HEADING_REJECTED_ENDINGS = setOf(',', ';')
    private val PARAGRAPH_ENDING_PUNCTUATION = setOf('.', '!', '?', '…', ':')
    private val FIGURE_CAPTION_START = Regex(
        "^(?:figura|fig\\.?|imagem|gr[aá]fico|tabela)\\s+[\\p{L}\\d][\\p{L}\\d._-]*.*$",
        RegexOption.IGNORE_CASE,
    )
    private val PAGE_NUMBER_PATTERN = Regex(
        "^(?:(?:p[aá]g(?:ina)?\\.?|page)\\s*)?(?:\\d+|[ivxlcdm]+)$",
        RegexOption.IGNORE_CASE,
    )
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val BULLET_PATTERN = Regex("[•▪◦]\\s*")
    private val DASH_PATTERN = Regex("\\s*[—–]\\s*")
    private val REPEATED_DOTS_PATTERN = Regex("\\.{3,}")
    private val WORD_GAP_PATTERN = Regex("\\s{2,}")
    private val LEADING_PAGE_NUMBER_PATTERN = Regex("^\\d{1,4}\\s*")
    private val TRAILING_PAGE_NUMBER_PATTERN = Regex("\\s*\\d{1,4}$")
    private val EXPLICIT_HEADING_PATTERN = Regex(
        "^(?:cap[ií]tulo|parte|pref[aá]cio|introdu[cç][aã]o|pr[oó]logo|" +
            "ep[ií]logo|conclus[aã]o|ap[eê]ndice|se[cç][aã]o)\\b.*$",
        RegexOption.IGNORE_CASE,
    )
}

data class TextLineLayout(
    val text: String,
    val fontSizePt: Float,
    val isBold: Boolean,
    val startsParagraph: Boolean,
)

data class StructuredTextRange(
    val start: Int,
    val endExclusive: Int,
)

data class StructuredTextChunk(
    val text: String,
    val ranges: List<StructuredTextRange> = emptyList(),
)

private data class HeadingMatch(
    val raw: String,
    val normalized: String,
    val index: Int,
)

private data class AppliedHeading(
    val text: String,
    val ranges: List<StructuredTextRange> = emptyList(),
    val matchedHeading: String? = null,
)

data class FigureCaption(
    val text: String,
    val relativePosition: Float,
)
