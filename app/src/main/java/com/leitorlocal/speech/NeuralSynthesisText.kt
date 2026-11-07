package com.leitorlocal.speech

internal object NeuralSynthesisText {
    fun split(text: String, maxCharacters: Int): List<String> {
        require(maxCharacters >= MINIMUM_CHUNK_SIZE)
        var remaining = text.trim().replace(WHITESPACE, " ")
        if (remaining.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        while (remaining.length > maxCharacters) {
            val minimumNaturalBoundary = maxCharacters / 2
            val naturalBoundary = findBoundary(
                text = remaining,
                endInclusive = maxCharacters - 1,
                minimumIndex = minimumNaturalBoundary,
                characters = STRONG_BOUNDARIES,
            ).takeIf { it >= 0 } ?: findBoundary(
                text = remaining,
                endInclusive = maxCharacters - 1,
                minimumIndex = minimumNaturalBoundary,
                characters = SOFT_BOUNDARIES,
            ).takeIf { it >= 0 } ?: remaining.lastIndexOf(
                char = ' ',
                startIndex = maxCharacters - 1,
            ).takeIf { it >= minimumNaturalBoundary }

            val endExclusive = when {
                naturalBoundary == null -> maxCharacters
                remaining[naturalBoundary].isWhitespace() -> naturalBoundary
                else -> naturalBoundary + 1
            }
            result += remaining.substring(0, endExclusive).trim()
            remaining = remaining.substring(endExclusive).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }

    private fun findBoundary(
        text: String,
        endInclusive: Int,
        minimumIndex: Int,
        characters: Set<Char>,
    ): Int {
        for (index in endInclusive downTo minimumIndex) {
            if (text[index] in characters) return index
        }
        return -1
    }

    private val WHITESPACE = Regex("\\s+")
    private val STRONG_BOUNDARIES = setOf('.', '!', '?', ';', ':')
    private val SOFT_BOUNDARIES = setOf(',', '—', '–')
    private const val MINIMUM_CHUNK_SIZE = 20
}
