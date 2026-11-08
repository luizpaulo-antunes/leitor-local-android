package com.leitorlocal.intelligence

import com.leitorlocal.model.BookContent
import com.leitorlocal.model.SpeechTextRole
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

data class BookInsightSource(
    val chunkIndex: Int,
    val pageNumber: Int,
    val excerpt: String,
)

data class BookInsight(
    val title: String,
    val body: String,
    val sources: List<BookInsightSource>,
)

/** Local, deterministic retrieval and extractive summarization for one book. */
class BookIntelligence(private val book: BookContent) {
    private data class IndexedChunk(
        val index: Int,
        val terms: Map<String, Float>,
        val magnitude: Float,
    )

    private val documentFrequency = mutableMapOf<String, Int>()
    private val indexed: List<IndexedChunk>

    init {
        val termCounts = book.chunks.map { semanticTerms(it.text) }
        termCounts.forEach { terms -> terms.keys.forEach { term -> documentFrequency[term] = (documentFrequency[term] ?: 0) + 1 } }
        indexed = termCounts.mapIndexed { index, counts ->
            val weighted = weight(counts)
            IndexedChunk(index, weighted, magnitude(weighted))
        }
    }

    fun search(query: String, limit: Int = 5): BookInsight {
        val sources = rankedSources(query, limit)
        return BookInsight(
            title = if (query.isBlank()) "Busca no livro" else "Resultados para “${query.trim()}”",
            body = if (sources.isEmpty()) {
                "Não encontrei um trecho suficientemente relacionado. Tente usar outras palavras."
            } else {
                "Encontrei ${sources.size} trecho(s) relacionados, ordenados por relevância."
            },
            sources = sources,
        )
    }

    fun summarizeChapter(currentChunkIndex: Int): BookInsight {
        val range = chapterRange(currentChunkIndex)
        val candidates = range.flatMap { index ->
            sentences(book.chunks[index].text).map { sentence -> index to sentence }
        }
        val selected = candidates
            .map { (index, sentence) -> Triple(index, sentence, sentenceScore(sentence, index, range.first)) }
            .sortedByDescending { it.third }
            .take(MAX_SUMMARY_SENTENCES)
            .sortedBy { it.first }
        val sources = selected.map { (index, sentence) -> source(index, sentence) }.distinctBy { it.chunkIndex }
        val body = selected.joinToString(" ") { it.second }.ifBlank {
            book.chunks.getOrNull(currentChunkIndex)?.text.orEmpty()
        }
        return BookInsight("Resumo do capítulo", body, sources)
    }

    fun explain(currentChunkIndex: Int): BookInsight {
        val chunk = book.chunks.getOrNull(currentChunkIndex)
            ?: return BookInsight("Explicação", "Trecho indisponível.", emptyList())
        val keywords = semanticTerms(chunk.text)
            .keys
            .filterNot { it.startsWith("#") }
            .sortedByDescending { idf(it) }
            .distinct()
            .take(4)
        val related = rankedSources(keywords.joinToString(" "), 4)
            .filterNot { it.chunkIndex == currentChunkIndex }
            .take(3)
        val topic = keywords.joinToString(", ").ifBlank { "o contexto apresentado" }
        return BookInsight(
            title = "Explicação do trecho",
            body = "O trecho trata principalmente de $topic. Os excertos abaixo mostram o contexto relacionado dentro do próprio livro.",
            sources = listOf(source(currentChunkIndex, chunk.text)) + related,
        )
    }

    private fun rankedSources(query: String, limit: Int): List<BookInsightSource> {
        val queryWeights = weight(semanticTerms(query))
        val queryMagnitude = magnitude(queryWeights)
        if (queryMagnitude <= 0f) return emptyList()
        val normalizedQuery = normalize(query)
        return indexed.asSequence()
            .map { chunk ->
                val dot = queryWeights.entries.sumOf { (term, value) ->
                    (value * (chunk.terms[term] ?: 0f)).toDouble()
                }.toFloat()
                val cosine = if (chunk.magnitude > 0f) dot / (queryMagnitude * chunk.magnitude) else 0f
                val phraseBonus = if (
                    normalizedQuery.length >= 5 &&
                    normalize(book.chunks[chunk.index].text).contains(normalizedQuery)
                ) 0.35f else 0f
                chunk.index to cosine + phraseBonus
            }
            .filter { it.second >= MIN_RELEVANCE }
            .sortedByDescending(Pair<Int, Float>::second)
            .take(limit)
            .map { (index, _) -> source(index, book.chunks[index].text) }
            .toList()
    }

    private fun chapterRange(current: Int): IntRange {
        val safe = current.coerceIn(0, book.chunks.lastIndex)
        val headings = book.chunks.indices.filter { index ->
            book.chunks[index].ranges.any { it.role == SpeechTextRole.HEADING }
        }
        val start = headings.lastOrNull { it <= safe } ?: maxOf(0, safe - DEFAULT_CONTEXT_CHUNKS)
        val endExclusive = headings.firstOrNull { it > safe } ?: minOf(book.chunks.size, start + MAX_CHAPTER_CHUNKS)
        return start until endExclusive.coerceAtLeast(start + 1)
    }

    private fun sentenceScore(sentence: String, index: Int, chapterStart: Int): Float {
        val terms = semanticTerms(sentence).keys.filterNot { it.startsWith("#") }
        if (terms.isEmpty()) return 0f
        val salience = terms.sumOf { idf(it).toDouble() }.toFloat() / sqrt(terms.size.toFloat())
        val positionBonus = if (index == chapterStart) 1.25f else 1f
        return salience * positionBonus
    }

    private fun source(index: Int, raw: String): BookInsightSource {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        val excerpt = if (cleaned.length <= MAX_EXCERPT_LENGTH) cleaned else cleaned.take(MAX_EXCERPT_LENGTH).trimEnd() + "…"
        return BookInsightSource(index, book.chunks[index].pageIndex + 1, excerpt)
    }

    private fun semanticTerms(text: String): Map<String, Int> {
        val tokens = TOKEN.findAll(normalize(text))
            .map(MatchResult::value)
            .filter { it.length >= 2 && it !in STOPWORDS }
            .toList()
        val terms = mutableMapOf<String, Int>()
        tokens.forEach { token ->
            val stem = stem(token)
            terms[stem] = (terms[stem] ?: 0) + 1
            SYNONYM_LOOKUP[stem]?.let { concept -> terms["#$concept"] = (terms["#$concept"] ?: 0) + 1 }
        }
        tokens.zipWithNext().forEach { (first, second) ->
            val bigram = "${stem(first)}_${stem(second)}"
            terms[bigram] = (terms[bigram] ?: 0) + 1
        }
        return terms
    }

    private fun weight(counts: Map<String, Int>): Map<String, Float> = counts.mapValues { (term, count) ->
        (1f + ln(count.toFloat())) * idf(term)
    }

    private fun idf(term: String): Float =
        (ln((book.chunks.size + 1.0) / ((documentFrequency[term] ?: 0) + 1.0)) + 1.0).toFloat()

    private fun magnitude(vector: Map<String, Float>): Float =
        sqrt(vector.values.sumOf { (it * it).toDouble() }.toFloat())

    private fun sentences(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(PORTUGUESE)
        iterator.setText(text)
        val output = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { it.length >= 35 }?.let(output::add)
            start = end
            end = iterator.next()
        }
        return output
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(PORTUGUESE), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")

    private fun stem(word: String): String {
        val suffix = PORTUGUESE_SUFFIXES.firstOrNull { word.length - it.length >= 4 && word.endsWith(it) }
        return if (suffix == null) word else word.dropLast(suffix.length)
    }

    companion object {
        private val PORTUGUESE = Locale.forLanguageTag("pt-BR")
        private val DIACRITICS = Regex("\\p{M}+")
        private val TOKEN = Regex("[a-z0-9]+")
        private val PORTUGUESE_SUFFIXES = listOf("amentos", "imentos", "amento", "imento", "acoes", "adores", "adoras", "idades", "mente", "acao", "ador", "adora", "idade", "icos", "icas", "ico", "ica", "ando", "endo", "indo", "es", "os", "as", "s")
        private val STOPWORDS = setOf("a", "ao", "aos", "as", "com", "como", "da", "das", "de", "do", "dos", "e", "em", "entre", "era", "essa", "esse", "esta", "este", "foi", "ha", "isso", "na", "nas", "no", "nos", "o", "os", "ou", "para", "pela", "pelo", "por", "que", "se", "sem", "ser", "sua", "suas", "um", "uma", "uns", "umas")
        private val SYNONYM_GROUPS = mapOf(
            "veiculo" to listOf("carro", "automovel", "veiculo"),
            "crianca" to listOf("crianca", "menino", "menina", "infancia"),
            "trabalho" to listOf("trabalho", "emprego", "profissao", "ocupacao"),
            "dinheiro" to listOf("dinheiro", "valor", "preco", "custo", "capital"),
            "casa" to listOf("casa", "lar", "moradia", "residencia"),
            "ideia" to listOf("ideia", "conceito", "pensamento", "nocao"),
        )
        private val SYNONYM_LOOKUP = buildMap {
            SYNONYM_GROUPS.forEach { (concept, words) -> words.forEach { put(it, concept) } }
        }
        private const val MIN_RELEVANCE = 0.06f
        private const val MAX_EXCERPT_LENGTH = 420
        private const val MAX_SUMMARY_SENTENCES = 5
        private const val DEFAULT_CONTEXT_CHUNKS = 8
        private const val MAX_CHAPTER_CHUNKS = 60
    }
}
