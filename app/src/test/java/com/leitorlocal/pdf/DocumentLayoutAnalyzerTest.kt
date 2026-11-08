package com.leitorlocal.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLayoutAnalyzerTest {
    @Test
    fun readsEachColumnContinuously() {
        val blocks = listOf(
            block("E", 520f, 30f, 900f),
            block("A", 50f, 20f, 450f),
            block("F", 520f, 40f, 900f),
            block("B", 50f, 30f, 450f),
            block("G", 520f, 50f, 900f),
            block("C", 50f, 40f, 450f),
        )

        assertEquals(
            listOf("A", "B", "C", "E", "F", "G"),
            DocumentLayoutAnalyzer.readingOrder(blocks, 1_000f).map(LayoutTextBlock::text),
        )
    }

    @Test
    fun keepsFullWidthHeadingBeforeColumns() {
        val blocks = listOf(
            block("Direita 1", 520f, 60f, 900f),
            block("Título", 50f, 10f, 950f),
            block("Esquerda 1", 50f, 50f, 450f),
            block("Direita 2", 520f, 70f, 900f),
            block("Esquerda 2", 50f, 60f, 450f),
            block("Direita 3", 520f, 80f, 900f),
            block("Esquerda 3", 50f, 70f, 450f),
        )

        assertEquals(
            "Título",
            DocumentLayoutAnalyzer.readingOrder(blocks, 1_000f).first().text,
        )
    }

    private fun block(text: String, left: Float, top: Float, right: Float) = LayoutTextBlock(
        text = text,
        left = left,
        top = top,
        right = right,
        bottom = top + 8f,
    )
}
