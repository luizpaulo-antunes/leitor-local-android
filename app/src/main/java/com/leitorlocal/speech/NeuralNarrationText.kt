package com.leitorlocal.speech

internal object NeuralNarrationText {
    fun prepare(raw: String): String {
        if (raw.isBlank()) return ""

        val withParagraphPauses = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(SOFT_HYPHEN, "")
            .replace(HYPHENATED_LINE_BREAK) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}"
            }
            .replace(PARAGRAPH_BREAK) { match ->
                val before = match.groupValues[1]
                if (before.lastOrNull()?.let(TERMINAL_PUNCTUATION::contains) == true) {
                    "$before "
                } else {
                    "$before. "
                }
            }
            .replace(SINGLE_LINE_BREAK, " ")

        return withParagraphPauses
            .replace(EM_DASH, "; ")
            .replace(BULLET) { match -> if (match.range.first == 0) "" else ". " }
            .replace(PAGE_ABBREVIATION) { preserveInitialCase(it.value, "página") }
            .replace(CHAPTER_ABBREVIATION) { preserveInitialCase(it.value, "capítulo") }
            .replace(FIGURE_ABBREVIATION) { preserveInitialCase(it.value, "figura") }
            .replace(NUMBER_ABBREVIATION, "número ")
            .replace(PERCENTAGE) { "${it.groupValues[1]} por cento" }
            .replace(REPEATED_DOTS, "…")
            .replace(SPACE_BEFORE_PUNCTUATION, "$1")
            .replace(MISSING_SPACE_AFTER_PUNCTUATION, "$1 ")
            .replace(WHITESPACE, " ")
            .trim()
            .let(::ensureTerminalPunctuation)
    }

    fun pauseAfterMilliseconds(text: String, speed: Float): Int {
        val basePause = when (text.trimEnd().lastOrNull()) {
            '.', '!', '?', '…' -> SENTENCE_PAUSE_MS
            ';', ':' -> CLAUSE_PAUSE_MS
            ',' -> COMMA_PAUSE_MS
            else -> CONTINUATION_PAUSE_MS
        }
        return (basePause / speed.coerceIn(0.6f, 1.6f)).toInt()
    }

    private fun ensureTerminalPunctuation(text: String): String {
        if (text.isEmpty()) return text
        val lastMeaningful = text.dropLastWhile { it in CLOSING_QUOTES }.lastOrNull()
        return if (lastMeaningful?.let(TERMINAL_PUNCTUATION::contains) == true) text else "$text."
    }

    private fun preserveInitialCase(source: String, replacement: String): String =
        if (source.firstOrNull()?.isUpperCase() == true) {
            replacement.replaceFirstChar { it.uppercaseChar() }
        } else {
            replacement
        }

    private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', '…', ';', ':', ',')
    private val CLOSING_QUOTES = setOf('"', '\'', '”', '’', ')', ']', '}')
    private const val SOFT_HYPHEN = "\u00ad"
    private val HYPHENATED_LINE_BREAK = Regex("([\\p{L}])[-‐]\\s*\\n\\s*([\\p{Ll}])")
    private val PARAGRAPH_BREAK = Regex("([^\\n])\\n{2,}")
    private val SINGLE_LINE_BREAK = Regex("\\n+")
    private val EM_DASH = Regex("\\s*[—–]\\s*")
    private val BULLET = Regex("(?:^|\\s)[•▪◦]+\\s*")
    private val PAGE_ABBREVIATION = Regex("\\bp[aá]g\\.(?=\\s*\\d)", RegexOption.IGNORE_CASE)
    private val CHAPTER_ABBREVIATION = Regex("\\bcap\\.(?=\\s*\\d)", RegexOption.IGNORE_CASE)
    private val FIGURE_ABBREVIATION = Regex("\\bfig\\.(?=\\s*\\d)", RegexOption.IGNORE_CASE)
    private val NUMBER_ABBREVIATION = Regex(
        "\\bn\\s*\\.?\\s*[º°o]\\.?\\s*(?=\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val PERCENTAGE = Regex("(\\d+(?:[.,]\\d+)?)\\s*%")
    private val REPEATED_DOTS = Regex("\\.{2,}")
    private val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.;:!?])")
    private val MISSING_SPACE_AFTER_PUNCTUATION = Regex("([,;:!?])(?=[\\p{L}])")
    private val WHITESPACE = Regex("[\\s\\u00a0]+")

    private const val SENTENCE_PAUSE_MS = 70
    private const val CLAUSE_PAUSE_MS = 48
    private const val COMMA_PAUSE_MS = 34
    private const val CONTINUATION_PAUSE_MS = 24
}
