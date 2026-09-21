package com.trendoc.pdflite.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which small hand-drawn glyph a [ToolAvatar] shows — one per Home tool card. Kept as simple
 * [androidx.compose.foundation.Canvas] shapes in the same spirit as [FileIconAvatar]'s
 * three-line "page" glyph, rather than pulling in material-icons-extended (~2000 icons) for
 * six pictograms.
 */
enum class ToolGlyphType {
    MERGE, SPLIT, COMPRESS, IMAGE_TO_PDF, PDF_TO_IMAGE, VIEW_PDF, FILL_FORM
}

/**
 * The tinted circular avatar shown on every Home tool card (Bento and List layouts alike),
 * each drawing a glyph that hints at what the tool actually does instead of the plain
 * undifferentiated colored square used before this pass.
 */
@Composable
fun ToolAvatar(
    type: ToolGlyphType,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.55f)) {
            when (type) {
                ToolGlyphType.MERGE -> drawMerge(tint)
                ToolGlyphType.SPLIT -> drawSplit(tint)
                ToolGlyphType.COMPRESS -> drawCompress(tint)
                ToolGlyphType.IMAGE_TO_PDF -> drawImageToPdf(tint)
                ToolGlyphType.PDF_TO_IMAGE -> drawPdfToImage(tint)
                ToolGlyphType.VIEW_PDF -> drawViewPdf(tint)
                ToolGlyphType.FILL_FORM -> drawFillForm(tint)
            }
        }
    }
}

/** A small rounded-rect "page" outline, reused by every glyph below as the base page shape. */
private fun DrawScope.page(topLeft: Offset, pageSize: Size, color: Color, strokeWidth: Float = 1.6.dp.toPx()) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = pageSize,
        cornerRadius = CornerRadius(1.5.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )
}

/** Two pages overlapping, folding into one — merge. */
private fun DrawScope.drawMerge(color: Color) {
    val w = size.width * 0.5f
    val h = size.height * 0.72f
    page(Offset(0f, size.height * 0.14f), Size(w, h), color)
    page(Offset(size.width - w, size.height * 0.14f), Size(w, h), color)
    // Arrows pointing inward from each page toward the center, showing them combining.
    val midY = size.height / 2f
    val arrowLen = size.width * 0.08f
    drawLine(color, Offset(w * 0.5f, midY), Offset(w * 0.5f + arrowLen, midY), strokeWidth = 1.6.dp.toPx())
    drawLine(color, Offset(size.width - w * 0.5f, midY), Offset(size.width - w * 0.5f - arrowLen, midY), strokeWidth = 1.6.dp.toPx())
}

/** One page with a dashed cut line down the middle — split. */
private fun DrawScope.drawSplit(color: Color) {
    page(Offset(0f, 0f), size, color)
    drawLine(
        color = color,
        start = Offset(size.width / 2f, size.height * 0.08f),
        end = Offset(size.width / 2f, size.height * 0.92f),
        strokeWidth = 1.6.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
    )
}

/** A page with corner arrows pointing inward — squeezing down in size. */
private fun DrawScope.drawCompress(color: Color) {
    val inset = size.width * 0.12f
    page(Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), color)
    val armLen = size.width * 0.14f
    val strokeWidth = 1.5.dp.toPx()
    // Four short arms at the corners, each pointing toward the page's center.
    drawLine(color, Offset(inset - armLen, inset - armLen), Offset(inset, inset), strokeWidth = strokeWidth)
    drawLine(color, Offset(size.width - inset + armLen, inset - armLen), Offset(size.width - inset, inset), strokeWidth = strokeWidth)
    drawLine(color, Offset(inset - armLen, size.height - inset + armLen), Offset(inset, size.height - inset), strokeWidth = strokeWidth)
    drawLine(color, Offset(size.width - inset + armLen, size.height - inset + armLen), Offset(size.width - inset, size.height - inset), strokeWidth = strokeWidth)
}

/** A small mountain/photo glyph feeding into a page — image(s) becoming a PDF. */
private fun DrawScope.drawImageToPdf(color: Color) {
    val photoSize = Size(size.width * 0.42f, size.height * 0.6f)
    page(Offset(0f, size.height * 0.2f), photoSize, color)
    drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(photoSize.width * 0.3f, size.height * 0.36f))
    drawLine(
        color,
        Offset(size.width * 0.55f, size.height / 2f),
        Offset(size.width * 0.78f, size.height / 2f),
        strokeWidth = 1.6.dp.toPx()
    )
    val pageSize = Size(size.width * 0.3f, size.height * 0.72f)
    page(Offset(size.width - pageSize.width, size.height * 0.14f), pageSize, color)
}

/** A page feeding into a small mountain/photo glyph — the reverse of [drawImageToPdf]. */
private fun DrawScope.drawPdfToImage(color: Color) {
    val pageSize = Size(size.width * 0.3f, size.height * 0.72f)
    page(Offset(0f, size.height * 0.14f), pageSize, color)
    drawLine(
        color,
        Offset(size.width * 0.4f, size.height / 2f),
        Offset(size.width * 0.62f, size.height / 2f),
        strokeWidth = 1.6.dp.toPx()
    )
    val photoSize = Size(size.width * 0.42f, size.height * 0.6f)
    page(Offset(size.width - photoSize.width, size.height * 0.2f), photoSize, color)
    drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(size.width - photoSize.width * 0.7f, size.height * 0.36f))
}

/** A page with a small eye-shaped outline over it — read-only viewing. */
private fun DrawScope.drawViewPdf(color: Color) {
    val pageSize = Size(size.width * 0.6f, size.height * 0.92f)
    page(Offset(size.width * 0.2f, size.height * 0.04f), pageSize, color)
    val eyeCenter = Offset(size.width / 2f, size.height * 0.55f)
    drawOval(
        color = color,
        topLeft = Offset(eyeCenter.x - size.width * 0.28f, eyeCenter.y - size.height * 0.14f),
        size = Size(size.width * 0.56f, size.height * 0.28f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(color, radius = 1.8.dp.toPx(), center = eyeCenter)
}

/** A page with a checked checkbox and a couple of filled-in lines — a form with values
 * already entered, distinguishing this from Split's blank-page dashed-line glyph. */
private fun DrawScope.drawFillForm(color: Color) {
    val pageSize = Size(size.width * 0.66f, size.height * 0.92f)
    val pageTopLeft = Offset(size.width * 0.17f, size.height * 0.04f)
    page(pageTopLeft, pageSize, color)
    val lineX = pageTopLeft.x + pageSize.width * 0.2f
    var lineY = pageTopLeft.y + pageSize.height * 0.28f
    for (w in listOf(0.5f, 0.35f)) {
        drawLine(
            color,
            Offset(lineX, lineY),
            Offset(lineX + pageSize.width * w, lineY),
            strokeWidth = 1.4.dp.toPx()
        )
        lineY += pageSize.height * 0.16f
    }
    // A small checked checkbox in the lower-left of the page.
    val boxSize = pageSize.width * 0.22f
    val boxTopLeft = Offset(pageTopLeft.x + pageSize.width * 0.14f, lineY + pageSize.height * 0.05f)
    drawRoundRect(
        color = color,
        topLeft = boxTopLeft,
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(1.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3.dp.toPx())
    )
    val checkStart = Offset(boxTopLeft.x + boxSize * 0.2f, boxTopLeft.y + boxSize * 0.55f)
    val checkMid = Offset(boxTopLeft.x + boxSize * 0.42f, boxTopLeft.y + boxSize * 0.78f)
    val checkEnd = Offset(boxTopLeft.x + boxSize * 0.82f, boxTopLeft.y + boxSize * 0.22f)
    drawLine(color, checkStart, checkMid, strokeWidth = 1.3.dp.toPx())
    drawLine(color, checkMid, checkEnd, strokeWidth = 1.3.dp.toPx())
}
