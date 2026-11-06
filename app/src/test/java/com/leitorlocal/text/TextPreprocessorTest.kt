package com.leitorlocal.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPreprocessorTest {
    @Test
    fun joinsWrappedLinesAndHyphenatedWords() {
        val raw = """
            Esta é uma frase que foi
            quebrada pelo PDF e contém uma pala-
            vra dividida.

            Este é outro parágrafo.
        """.trimIndent()

        assertEquals(
            "Esta é uma frase que foi quebrada pelo PDF e contém uma palavra dividida.\n\n" +
                "Este é outro parágrafo.",
            TextPreprocessor.cleanPage(raw),
        )
    }

    @Test
    fun producesChunksSmallerThanTtsLimit() {
        val text = (1..80).joinToString(" ") { "Esta é a frase número $it." }
        val chunks = TextPreprocessor.splitIntoChunks(text, targetLength = 180)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 180 })
        assertTrue(chunks.joinToString(" ").contains("frase número 80"))
    }

    @Test
    fun removesRepeatedHeadersFootersAndPageNumbers() {
        val pages = listOf(
            "Prefácio 5\nPrimeiro parágrafo do livro.\n5",
            "Prefácio 6\nSegundo parágrafo do livro.\n6",
            "Prefácio 7\nTerceiro parágrafo do livro.\n7",
        )

        val cleaned = TextPreprocessor.cleanDocumentPages(pages)

        assertEquals("Primeiro parágrafo do livro.", cleaned[0])
        assertEquals("Segundo parágrafo do livro.", cleaned[1])
        assertEquals("Terceiro parágrafo do livro.", cleaned[2])
    }

    @Test
    fun preparesHeadingsAndDashesForMoreNaturalSpeech() {
        assertEquals("Capítulo primeiro.", TextPreprocessor.prepareForSpeech("Capítulo primeiro"))
        assertEquals(
            "Ele parou, respirou fundo.",
            TextPreprocessor.prepareForSpeech("Ele parou — respirou fundo."),
        )
    }

    @Test
    fun separatesTypographicTitleFromParagraphWithoutAddingAChunk() {
        val raw = "Um rosto no lugar do balão\nEu tenho uma coleção enorme de mangás comprados."
        val clean = TextPreprocessor.cleanPage(raw)
        val headings = TextPreprocessor.detectHeadingCandidates(
            listOf(
                TextLineLayout(
                    text = "Um rosto no lugar do balão",
                    fontSizePt = 15f,
                    isBold = true,
                    startsParagraph = true,
                ),
                TextLineLayout(
                    text = "Eu tenho uma coleção enorme de mangás comprados.",
                    fontSizePt = 11f,
                    isBold = false,
                    startsParagraph = true,
                ),
            ),
        )

        val chunks = TextPreprocessor.splitIntoStructuredChunks(clean, headings)

        assertEquals(1, chunks.size)
        assertEquals(
            "Um rosto no lugar do balão.\n\nEu tenho uma coleção enorme de mangás comprados.",
            chunks.single().text,
        )
        assertEquals(1, chunks.single().ranges.size)
        val range = chunks.single().ranges.single()
        assertEquals("Um rosto no lugar do balão.", chunks.single().text.substring(range.start, range.endExclusive))
    }

    @Test
    fun keepsTwoTypographicallyContinuousTitleLinesInOneRange() {
        val raw = """
            Quando a máquina aprende
            sem entender o problema
            Este é o início de um parágrafo comum e suficientemente longo.
        """.trimIndent()
        val clean = TextPreprocessor.cleanPage(raw)
        val headings = TextPreprocessor.detectHeadingCandidates(
            listOf(
                TextLineLayout(
                    text = "Quando a máquina aprende",
                    fontSizePt = 15f,
                    isBold = true,
                    startsParagraph = true,
                ),
                TextLineLayout(
                    text = "sem entender o problema",
                    fontSizePt = 15.2f,
                    isBold = true,
                    startsParagraph = false,
                ),
                TextLineLayout(
                    text = "Este é o início de um parágrafo comum e suficientemente longo.",
                    fontSizePt = 11f,
                    isBold = false,
                    startsParagraph = true,
                ),
            ),
        )

        val chunks = TextPreprocessor.splitIntoStructuredChunks(clean, headings)

        assertEquals(listOf("Quando a máquina aprende sem entender o problema"), headings)
        assertEquals(1, chunks.size)
        assertEquals(1, chunks.single().ranges.size)
        val range = chunks.single().ranges.single()
        assertEquals(
            "Quando a máquina aprende sem entender o problema.",
            chunks.single().text.substring(range.start, range.endExclusive),
        )
        assertEquals(
            "Quando a máquina aprende sem entender o problema.\n\n" +
                "Este é o início de um parágrafo comum e suficientemente longo.",
            chunks.single().text,
        )
    }

    @Test
    fun matchesHeadingAfterTheSameBulletAndDashNormalizationUsedForSpeech() {
        val raw = "• Aprender — sem decorar\nEste parágrafo começa depois do título."
        val clean = TextPreprocessor.cleanPage(raw)
        val headings = TextPreprocessor.detectHeadingCandidates(
            listOf(
                TextLineLayout(
                    text = "• Aprender — sem decorar",
                    fontSizePt = 15f,
                    isBold = true,
                    startsParagraph = true,
                ),
                TextLineLayout(
                    text = "Este parágrafo começa depois do título.",
                    fontSizePt = 11f,
                    isBold = false,
                    startsParagraph = true,
                ),
            ),
        )

        val chunk = TextPreprocessor.splitIntoStructuredChunks(clean, headings).single()

        assertEquals(1, chunk.ranges.size)
        val range = chunk.ranges.single()
        assertEquals(
            "Aprender, sem decorar.",
            chunk.text.substring(range.start, range.endExclusive),
        )
        assertEquals(
            "Aprender, sem decorar.\n\nEste parágrafo começa depois do título.",
            chunk.text,
        )
    }

    @Test
    fun doesNotTreatOrdinaryWrappedLinesAsTitles() {
        val headings = TextPreprocessor.detectHeadingCandidates(
            listOf(
                TextLineLayout(
                    text = "Esta frase continua",
                    fontSizePt = 11f,
                    isBold = false,
                    startsParagraph = true,
                ),
                TextLineLayout(
                    text = "na linha seguinte do mesmo parágrafo.",
                    fontSizePt = 11f,
                    isBold = false,
                    startsParagraph = false,
                ),
            ),
        )

        assertTrue(headings.isEmpty())
    }

    @Test
    fun recognizesExplicitUppercaseHeadingWithoutFontMetadata() {
        val headings = TextPreprocessor.detectHeadingCandidates(
            listOf(
                TextLineLayout(
                    text = "PARTE DOIS",
                    fontSizePt = 0f,
                    isBold = false,
                    startsParagraph = true,
                ),
                TextLineLayout(
                    text = "Agora começa um novo trecho do livro.",
                    fontSizePt = 0f,
                    isBold = false,
                    startsParagraph = true,
                ),
            ),
        )

        assertEquals(listOf("PARTE DOIS"), headings)
    }

    @Test
    fun removesLetterSpacedRunningHeadersWithAttachedPageNumbers() {
        val pages = listOf(
            "U M  G E R A D O R  D E  T E X T O  M U I T O  B O M17\nTexto principal.",
            "18 C O M P E T E N T E  O  S U F I C I E N T E\nOutro texto.",
        )

        val cleaned = TextPreprocessor.cleanDocumentPages(pages)

        assertEquals("Texto principal.", cleaned[0])
        assertEquals("Outro texto.", cleaned[1])
    }

    @Test
    fun preservesAndNormalizesLetterSpacedSectionTitlesWithoutPageNumbers() {
        assertEquals("PARTE I", TextPreprocessor.cleanPage("P A R T E  I"))
        assertEquals(
            "ESTE É UM AVISO IMPORTANTE",
            TextPreprocessor.cleanDocumentPages(listOf("ESTE É UM AVISO IMPORTANTE")).single(),
        )
    }

    @Test
    fun joinsWordsBrokenBySoftHyphens() {
        assertEquals(
            "A retropropagação funciona com pré-treino.",
            TextPreprocessor.cleanPage("A re\u00ad\ntropropagação funciona com pré\u00adtreino."),
        )
    }

    @Test
    fun extractsMultilineFigureCaptionAndItsPosition() {
        val raw = """
            Cabeçalho do capítulo
            Figura 1.1 Cada volta calcula as chances, sorteia um e
            recomeça com o texto maior.
            O parágrafo seguinte começa aqui.
        """.trimIndent()

        val captions = TextPreprocessor.extractFigureCaptions(raw)

        assertEquals(1, captions.size)
        assertEquals(
            "Figura 1.1 Cada volta calcula as chances, sorteia um e recomeça com o texto maior.",
            captions.single().text,
        )
        assertTrue(captions.single().relativePosition in 0.2f..0.3f)
    }

    @Test
    fun composesNumberedFigureDescriptionInStepOrder() {
        val description = FigureDescriptionComposer.compose(
            caption = "Figura 1.1. O ciclo de geração.",
            alternativeText = "Um texto inicial à esquerda.",
            ocrText = "2 calcula as chances\ntelhado\n1 olha o texto todo\n4 cola no fim e repete\n3 sorteia entre os mais prováveis",
        )

        assertTrue(description.contains("Figura 1.1"))
        assertTrue(description.indexOf("1 olha") < description.indexOf("2 calcula"))
        assertTrue(description.indexOf("2 calcula") < description.indexOf("3 sorteia"))
        assertTrue(description.indexOf("3 sorteia") < description.indexOf("4 cola"))
    }
}
