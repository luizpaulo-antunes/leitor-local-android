package com.leitorlocal.pdf

import com.tom_roush.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequence
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties
import com.tom_roush.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Removes text that the PDF explicitly marks as a pagination artifact, such as
 * running headers, footers and printed page numbers.
 */
class ArtifactIgnoringTextStripper : PDFTextStripper() {
    private val artifactScopes = mutableListOf<Boolean>()

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
}
