package com.leitorlocal.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.leitorlocal.data.DocumentFingerprint
import com.leitorlocal.text.FigureCaption
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import kotlin.math.ceil

data class AnalyzedFigure(
    val pageIndex: Int,
    val relativePosition: Float,
    val imagePath: String?,
    val caption: String,
    val alternativeText: String,
    val ocrText: String,
    val visualLabels: List<String>,
)

class PdfFigureAnalyzer(private val context: Context) {
    suspend fun analyze(
        document: PDDocument,
        bookUri: Uri,
        captionsByPage: List<List<FigureCaption>>,
        onProgress: (page: Int, total: Int) -> Unit,
    ): Map<Int, List<AnalyzedFigure>> {
        val alternativesByPage = collectAlternativeDescriptions(document)
        val targetPages = (alternativesByPage.keys + captionsByPage.indices.filter {
            captionsByPage[it].isNotEmpty()
        }).sorted()
        if (targetPages.isEmpty()) return emptyMap()

        val fingerprint = DocumentFingerprint.resolve(context, bookUri)
        val bookDirectory = File(
            context.filesDir,
            "$FIGURE_DIRECTORY/${fingerprint.cacheKey(bookUri)}",
        )
            .apply { mkdirs() }
        val result = mutableMapOf<Int, List<AnalyzedFigure>>()
        val ocr = FigureOcr()
        val visualAnalyzer = FigureVisualAnalyzer()
        try {
            targetPages.forEachIndexed { progressIndex, pageIndex ->
                val captions = captionsByPage.getOrNull(pageIndex).orEmpty()
                val alternatives = alternativesByPage[pageIndex].orEmpty()
                val figureCount = maxOf(captions.size, alternatives.size)
                if (figureCount <= 0 || pageIndex !in 0 until document.numberOfPages) {
                    return@forEachIndexed
                }

                val page = document.getPage(pageIndex)
                val images = collectImages(page.resources)
                    .filter(::isUsefulFigureImage)
                    .sortedByDescending { it.width.toLong() * it.height }
                    .take(figureCount)

                result[pageIndex] = buildList {
                    repeat(figureCount) { figureIndex ->
                        val caption = captions.getOrNull(figureIndex)
                        val alternative = alternatives.getOrNull(figureIndex).orEmpty()
                        val image = images.getOrNull(figureIndex)
                        val bitmap = image?.decodeForAnalysis()
                        try {
                            val imagePath = bitmap?.let {
                                saveFigure(it, bookDirectory, pageIndex, figureIndex)
                            }
                            val ocrText = bitmap?.let { ocr.recognize(it) }.orEmpty()
                            val visualLabels = bitmap?.let { visualAnalyzer.describe(it) }.orEmpty()
                            add(
                                AnalyzedFigure(
                                    pageIndex = pageIndex,
                                    relativePosition = caption?.relativePosition ?: 0.5f,
                                    imagePath = imagePath,
                                    caption = caption?.text.orEmpty(),
                                    alternativeText = alternative,
                                    ocrText = ocrText,
                                    visualLabels = visualLabels,
                                ),
                            )
                        } finally {
                            bitmap?.recycle()
                        }
                    }
                }
                onProgress(progressIndex + 1, targetPages.size)
            }
        } finally {
            ocr.close()
            visualAnalyzer.close()
        }
        return result
    }

    private fun collectAlternativeDescriptions(document: PDDocument): Map<Int, List<String>> {
        val pageIndexes = mutableMapOf<COSDictionary, Int>()
        document.pages.forEachIndexed { index, page -> pageIndexes[page.cosObject] = index }
        val descriptions = mutableMapOf<Int, MutableList<String>>()
        val root = document.documentCatalog.structureTreeRoot ?: return emptyMap()

        fun visit(node: PDStructureNode, inheritedPage: PDPage?) {
            node.kids.forEach { kid ->
                val element = kid as? PDStructureElement ?: return@forEach
                val page = element.page ?: inheritedPage
                val type = element.standardStructureType ?: element.structureType
                if (type.equals("Figure", ignoreCase = true)) {
                    val pageIndex = page?.cosObject?.let(pageIndexes::get)
                    if (pageIndex != null) {
                        descriptions.getOrPut(pageIndex, ::mutableListOf) +=
                            element.alternateDescription.orEmpty().trim()
                    }
                }
                visit(element, page)
            }
        }
        visit(root, null)
        return descriptions
    }

    private fun collectImages(resources: PDResources?): List<PDImageXObject> {
        if (resources == null) return emptyList()
        val images = mutableListOf<PDImageXObject>()
        val visited = mutableSetOf<COSBase>()

        fun collect(current: PDResources?) {
            if (current == null) return
            current.xObjectNames.forEach { name ->
                val xObject = runCatching { current.getXObject(name) }.getOrNull() ?: return@forEach
                if (!visited.add(xObject.cosObject)) return@forEach
                when (xObject) {
                    is PDImageXObject -> if (!xObject.isStencil && !xObject.isEmpty) images += xObject
                    is PDFormXObject -> collect(xObject.resources)
                }
            }
        }
        collect(resources)
        return images
    }

    private fun isUsefulFigureImage(image: PDImageXObject): Boolean =
        image.width >= MIN_IMAGE_SIDE &&
            image.height >= MIN_IMAGE_SIDE &&
            image.width.toLong() * image.height >= MIN_IMAGE_AREA

    private fun PDImageXObject.decodeForAnalysis(): Bitmap? = runCatching {
        val largestSide = maxOf(width, height)
        val subsampling = ceil(largestSide.toDouble() / MAX_IMAGE_SIDE).toInt().coerceAtLeast(1)
        getImage(Rect(0, 0, width, height), subsampling)
    }.getOrNull()

    private fun saveFigure(
        bitmap: Bitmap,
        directory: File,
        pageIndex: Int,
        figureIndex: Int,
    ): String? = runCatching {
        val destination = File(directory, "page_${pageIndex}_figure_$figureIndex.png")
        val temporary = File(directory, ".${destination.name}.tmp")
        temporary.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        destination.absolutePath
    }.getOrNull()

    companion object {
        const val FIGURE_DIRECTORY = "book_figures_v2"
        private const val MIN_IMAGE_SIDE = 160
        private const val MIN_IMAGE_AREA = 80_000L
        private const val MAX_IMAGE_SIDE = 1_800
    }
}
