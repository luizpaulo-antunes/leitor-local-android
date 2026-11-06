package com.leitorlocal.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.leitorlocal.model.PlaybackStatus
import com.leitorlocal.model.ReaderUiState
import com.leitorlocal.model.SpeechTextRole
import com.leitorlocal.ui.rememberFileBitmap
import kotlin.math.roundToInt

private val KaraokeCardColor = Color(0xFFFAF8F5)
private val KaraokeTextColor = Color(0xFF272521)
private val ActiveWordBackground = Color(0xFFC8E6D4)
private val ActiveWordText = Color(0xFF1B4332)

@Composable
internal fun KaraokeFocusOverlay(state: ReaderUiState) {
    val chunk = state.currentChunk ?: return
    val figureBitmap = rememberFileBitmap(chunk.figure?.imagePath, maxSidePixels = 1_600)
    val focusedBitmap = figureBitmap ?: state.pageBitmap.takeIf { chunk.figure != null }
    val panelShape = RoundedCornerShape(20.dp)
    val isReadingHeading = chunk.ranges.any { range ->
        range.role == SpeechTextRole.HEADING &&
            state.playback.currentWordStart in range.start until range.endExclusive
    }
    val badgeLabel = when {
        state.playback.status == PlaybackStatus.PREPARING && chunk.figure != null ->
            "Preparando descrição da figura…"
        state.playback.status == PlaybackStatus.PREPARING -> "Preparando voz…"
        chunk.figure != null -> "Figura · lendo agora"
        isReadingHeading -> "Título · lendo agora"
        else -> "Lendo agora"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val panelMaxHeight = maxHeight * if (chunk.figure != null) 0.94f else 0.84f
        key(state.playback.currentChunkIndex) {
            val focusScrollState = rememberScrollState()
            var viewportHeightPx by remember { mutableIntStateOf(0) }
            var textTopPx by remember { mutableIntStateOf(0) }
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val activeWordStart = state.playback.currentWordStart
            val activeWordEnd = state.playback.currentWordEnd
            val maxScroll = focusScrollState.maxValue

            LaunchedEffect(
                activeWordStart,
                activeWordEnd,
                textTopPx,
                textLayout,
                viewportHeightPx,
                maxScroll,
            ) {
                val layout = textLayout ?: return@LaunchedEffect
                if (
                    activeWordEnd <= activeWordStart ||
                    activeWordStart !in chunk.text.indices ||
                    viewportHeightPx <= 0 ||
                    maxScroll <= 0
                ) {
                    return@LaunchedEffect
                }
                val wordBox = layout.getBoundingBox(activeWordStart)
                val target = (
                    textTopPx + wordBox.center.y - viewportHeightPx * 0.48f
                ).roundToInt().coerceIn(0, maxScroll)
                val distance = target - focusScrollState.value
                if (kotlin.math.abs(distance) > 8) {
                    focusScrollState.animateScrollBy(distance.toFloat())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .heightIn(max = panelMaxHeight)
                    .dropShadow(
                        shape = panelShape,
                        shadow = Shadow(
                            radius = 32.dp,
                            spread = 0.dp,
                            offset = DpOffset(0.dp, 12.dp),
                            color = Color.Black.copy(alpha = 0.12f),
                        ),
                    )
                    .clip(panelShape)
                    .background(KaraokeCardColor)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.06f),
                        shape = panelShape,
                    )
                    .onSizeChanged { viewportHeightPx = it.height }
                    .semantics { paneTitle = "Trecho em leitura" },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(focusScrollState)
                        .padding(24.dp),
                ) {
                    ReadingBadge(badgeLabel)
                    Spacer(Modifier.height(16.dp))
                    if (focusedBitmap != null && chunk.figure != null) {
                        Image(
                            bitmap = focusedBitmap.asImageBitmap(),
                            contentDescription = chunk.figure.caption.ifBlank {
                                chunk.figure.alternativeText.ifBlank { "Figura do livro" }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    (focusedBitmap.width.toFloat() / focusedBitmap.height)
                                        .coerceIn(0.55f, 2.4f),
                                )
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    val hasActiveWord =
                        activeWordEnd > activeWordStart &&
                            activeWordStart >= 0 &&
                            activeWordEnd <= chunk.text.length
                    val headingFontSize = MaterialTheme.typography.titleLarge.fontSize
                    val highlightedText = buildAnnotatedString {
                        append(chunk.text)
                        chunk.ranges
                            .filter { range ->
                                range.role == SpeechTextRole.HEADING &&
                                    range.start >= 0 &&
                                    range.endExclusive in (range.start + 1)..chunk.text.length
                            }
                            .forEach { range ->
                                addStyle(
                                    SpanStyle(
                                        color = ActiveWordText,
                                        fontSize = headingFontSize,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    range.start,
                                    range.endExclusive,
                                )
                            }
                        if (hasActiveWord) {
                            addStyle(
                                SpanStyle(color = ActiveWordText),
                                activeWordStart,
                                activeWordEnd,
                            )
                        }
                    }
                    Text(
                        text = highlightedText,
                        style = MaterialTheme.typography.titleMedium,
                        color = KaraokeTextColor,
                        fontFamily = FontFamily.Serif,
                        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.18f,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier
                            .drawBehind {
                                val layout = textLayout
                                if (!hasActiveWord || layout == null) return@drawBehind

                                val firstLine = layout.getLineForOffset(activeWordStart)
                                val lastLine = layout.getLineForOffset(activeWordEnd - 1)
                                val horizontalPadding = 5.dp.toPx()
                                val verticalPadding = 2.dp.toPx()
                                val cornerRadius = CornerRadius(6.dp.toPx())

                                for (line in firstLine..lastLine) {
                                    val segmentStart = maxOf(
                                        activeWordStart,
                                        layout.getLineStart(line),
                                    )
                                    val segmentEnd = minOf(
                                        activeWordEnd,
                                        layout.getLineEnd(line, visibleEnd = true),
                                    )
                                    if (segmentEnd <= segmentStart) continue

                                    var left = Float.POSITIVE_INFINITY
                                    var top = Float.POSITIVE_INFINITY
                                    var right = Float.NEGATIVE_INFINITY
                                    var bottom = Float.NEGATIVE_INFINITY
                                    for (offset in segmentStart until segmentEnd) {
                                        val bounds = layout.getBoundingBox(offset)
                                        left = minOf(left, bounds.left)
                                        top = minOf(top, bounds.top)
                                        right = maxOf(right, bounds.right)
                                        bottom = maxOf(bottom, bounds.bottom)
                                    }
                                    if (!left.isFinite() || !right.isFinite()) continue

                                    drawRoundRect(
                                        color = ActiveWordBackground,
                                        topLeft = Offset(
                                            x = left - horizontalPadding,
                                            y = top - verticalPadding,
                                        ),
                                        size = Size(
                                            width = right - left + horizontalPadding * 2f,
                                            height = bottom - top + verticalPadding * 2f,
                                        ),
                                        cornerRadius = cornerRadius,
                                    )
                                }
                            }
                            .onGloballyPositioned { coordinates ->
                                val contentTop = coordinates.positionInParent().y.roundToInt()
                                if (textTopPx != contentTop) textTopPx = contentTop
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingBadge(label: String) {
    Surface(
        color = ActiveWordBackground,
        contentColor = ActiveWordText,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AudioWaveIcon(color = ActiveWordText)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AudioWaveIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 18.dp, height = 14.dp)) {
        val barHeights = listOf(0.38f, 0.72f, 1f, 0.72f, 0.38f)
        val spacing = size.width / (barHeights.size + 1)
        val centerY = size.height / 2f
        barHeights.forEachIndexed { index, fraction ->
            val halfHeight = size.height * fraction / 2f
            val x = spacing * (index + 1)
            drawLine(
                color = color,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
