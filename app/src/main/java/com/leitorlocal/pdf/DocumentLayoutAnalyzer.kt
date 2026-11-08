package com.leitorlocal.pdf

/** A positioned piece of document text using top-left based coordinates. */
internal data class LayoutTextBlock(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val startsParagraph: Boolean = false,
)

/**
 * Reconstructs a stable reading order for pages with one or two columns.
 *
 * PDFBox and OCR engines usually sort by vertical position first, which alternates two columns
 * line by line. This lightweight XY-cut keeps full-width headings in place and reads each column
 * from top to bottom. It intentionally falls back to ordinary top-to-bottom ordering whenever a
 * page does not contain enough evidence for two columns.
 */
internal object DocumentLayoutAnalyzer {
    fun readingOrder(blocks: List<LayoutTextBlock>, pageWidth: Float): List<LayoutTextBlock> {
        val usable = blocks.filter { it.text.isNotBlank() }
        if (usable.size < MIN_COLUMN_BLOCKS * 2 || pageWidth <= 0f) return topDown(usable)

        val separator = pageWidth / 2f
        val gutter = pageWidth * GUTTER_FRACTION
        val left = usable.filter { it.right <= separator + gutter }
        val right = usable.filter { it.left >= separator - gutter }
        if (left.size < MIN_COLUMN_BLOCKS || right.size < MIN_COLUMN_BLOCKS) return topDown(usable)

        val overlapTop = maxOf(left.minOf(LayoutTextBlock::top), right.minOf(LayoutTextBlock::top))
        val overlapBottom = minOf(left.maxOf(LayoutTextBlock::bottom), right.maxOf(LayoutTextBlock::bottom))
        if (overlapBottom <= overlapTop) return topDown(usable)

        val spanning = usable.filter { block ->
            block.left < separator - gutter && block.right > separator + gutter
        }
        val ordered = mutableListOf<LayoutTextBlock>()
        var sectionTop = Float.NEGATIVE_INFINITY
        topDown(spanning).forEach { divider ->
            ordered += orderSection(
                usable.filter { it !in spanning && it.top >= sectionTop && it.top < divider.top },
                separator,
            )
            ordered += divider
            sectionTop = divider.bottom
        }
        ordered += orderSection(
            usable.filter { it !in spanning && it.top >= sectionTop },
            separator,
        )
        return ordered.distinct()
    }

    fun asText(blocks: List<LayoutTextBlock>, pageWidth: Float): String = buildString {
        readingOrder(blocks, pageWidth).forEachIndexed { index, block ->
            if (index > 0) append(if (block.startsParagraph) "\n\n" else "\n")
            append(block.text.trim())
        }
    }

    private fun orderSection(
        blocks: List<LayoutTextBlock>,
        separator: Float,
    ): List<LayoutTextBlock> {
        if (blocks.isEmpty()) return emptyList()
        val left = blocks.filter { (it.left + it.right) / 2f < separator }
        val right = blocks.filterNot(left::contains)
        return if (left.size >= 2 && right.size >= 2) topDown(left) + topDown(right) else topDown(blocks)
    }

    private fun topDown(blocks: List<LayoutTextBlock>): List<LayoutTextBlock> =
        blocks.sortedWith(compareBy<LayoutTextBlock>({ it.top }, { it.left }))

    private const val MIN_COLUMN_BLOCKS = 3
    private const val GUTTER_FRACTION = 0.025f
}
