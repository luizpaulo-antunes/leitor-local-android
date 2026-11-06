package com.leitorlocal.pdf

import com.tom_roush.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequence
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Removes text that the PDF explicitly marks as a pagination artifact, such as
 * running headers, footers and printed page numbers.
 */
class ArtifactIgnoringTextStripper : PDFTextStripper() {
    private val artifactScopes = mutableListOf<Boolean>()
    private val capturedLines = mutableListOf<PdfTextLine>()
    private val currentLine = StringBuilder()
    private val currentPositions = mutableListOf<TextPosition>()
    private var nextLineStartsParagraph = true
    private var currentLineStartsParagraph = true

    init {
        addOperator(BeginMarkedContentSequenceWithProperties())
        addOperator(BeginMarkedContentSequence())
        addOperator(EndMarkedContentSequence())
    }

    override fun beginMarkedContentSequence(tag: COSName, properties: COSDictionary?) {
        super.beginMarkedContentSequence(tag, properties)
        artifactScopes += artifactScopes.lastOrNull() == true || tag == COSName.ARTIFACT
    }

    override fun endMarkedContentSequence() {
        super.endMarkedContentSequence()
        if (artifactScopes.isNotEmpty()) artifactScopes.removeAt(artifactScopes.lastIndex)
    }

    override fun processTextPosition(text: TextPosition) {
        if (artifactScopes.lastOrNull() != true) super.processTextPosition(text)
    }

    override fun startPage(page: PDPage) {
        capturedLines.clear()
        currentLine.clear()
        currentPositions.clear()
        nextLineStartsParagraph = true
        currentLineStartsParagraph = true
        super.startPage(page)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (currentLine.isEmpty()) {
            currentLineStartsParagraph = nextLineStartsParagraph
            nextLineStartsParagraph = false
        }
        currentLine.append(text)
        currentPositions += textPositions
        super.writeString(text, textPositions)
    }

    override fun writeWordSeparator() {
        if (currentLine.isNotEmpty() && currentLine.lastOrNull()?.isWhitespace() != true) {
            currentLine.append(' ')
        }
        super.writeWordSeparator()
    }

    override fun writeLineSeparator() {
        finishCapturedLine()
        super.writeLineSeparator()
    }

    override fun writeParagraphStart() {
        nextLineStartsParagraph = true
        super.writeParagraphStart()
    }

    override fun writeParagraphEnd() {
        finishCapturedLine()
        nextLineStartsParagraph = true
        super.writeParagraphEnd()
    }

    override fun endPage(page: PDPage) {
        finishCapturedLine()
        super.endPage(page)
    }

    fun pageLines(): List<PdfTextLine> = capturedLines.toList()

    private fun finishCapturedLine() {
        val text = currentLine.toString().replace(WHITESPACE, " ").trim()
        if (text.isNotEmpty()) {
            val fontSizes = currentPositions
                .asSequence()
                .map(TextPosition::getFontSizeInPt)
                .filter { it > 0f && it.isFinite() }
                .sorted()
                .toList()
            val fontSize = fontSizes
                .takeIf(List<Float>::isNotEmpty)
                ?.let { it[it.size / 2] }
                ?: 0f
            val boldPositions = currentPositions.count { position ->
                runCatching { position.font.name }
                    .getOrDefault("")
                    .contains(BOLD_FONT_NAME)
            }
            capturedLines += PdfTextLine(
                text = text,
                fontSizePt = fontSize,
                isBold = currentPositions.isNotEmpty() &&
                    boldPositions * 2 >= currentPositions.size,
                startsParagraph = currentLineStartsParagraph,
            )
        }
        currentLine.clear()
        currentPositions.clear()
        currentLineStartsParagraph = nextLineStartsParagraph
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val BOLD_FONT_NAME = Regex(
            "(?:bold|semibold|demi|black|heavy)",
            RegexOption.IGNORE_CASE,
        )
    }
}

data class PdfTextLine(
    val text: String,
    val fontSizePt: Float,
    val isBold: Boolean,
    val startsParagraph: Boolean,
)
