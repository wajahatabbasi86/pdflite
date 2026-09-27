package com.trendoc.pdflite.ui.common

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.trendoc.pdflite.util.renderPdfRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A crisp render of the part of the page that was on screen, in content pixels. */
private class Tile(val bitmap: Bitmap, val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Keeps a zoomed page sharp. Place inside [ZoomPanBox]'s content, above the page image and
 * below any overlays.
 *
 * Magnifying the page bitmap only enlarges its pixels, and rendering the whole page at 6x
 * would need hundreds of MB. Instead, once a pinch or pan settles, this renders **only the
 * visible region** at exactly screen resolution (PdfRenderer takes a transform matrix) and
 * draws it over that region. Memory stays at one viewport-sized bitmap at any zoom level.
 *
 * The tile is placed in content coordinates, so it moves with the page while the user keeps
 * panning — it just may not cover the new area until the next render arrives a moment later.
 *
 * Assumes the [ZoomPanBox] viewport is the page itself (content size == viewport size), which
 * is how every page-editing screen uses it.
 */
@OptIn(FlowPreview::class)
@Composable
fun BoxScope.SharpZoomLayer(
    state: ZoomPanState,
    uri: Uri,
    pageIndex: Int,
    pageWidthPt: Float,
    contentWidthPx: Float,
    contentHeightPx: Float
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    var tile by remember(uri, pageIndex) { mutableStateOf<Tile?>(null) }

    LaunchedEffect(uri, pageIndex, contentWidthPx, contentHeightPx) {
        snapshotFlow { state.scale to state.offset }
            .debounce(120)
            .collectLatest { (scale, _) ->
                if (scale <= 1.05f || contentWidthPx <= 0f || contentHeightPx <= 0f) {
                    tile = null
                    return@collectLatest
                }
                val topLeft = state.viewportToContent(androidx.compose.ui.geometry.Offset.Zero, contentWidthPx, contentHeightPx)
                val bottomRight = state.viewportToContent(
                    androidx.compose.ui.geometry.Offset(contentWidthPx, contentHeightPx), contentWidthPx, contentHeightPx
                )
                val left = topLeft.x.coerceIn(0f, contentWidthPx)
                val top = topLeft.y.coerceIn(0f, contentHeightPx)
                val right = bottomRight.x.coerceIn(0f, contentWidthPx)
                val bottom = bottomRight.y.coerceIn(0f, contentHeightPx)
                val widthPx = ((right - left) * scale).roundToInt()
                val heightPx = ((bottom - top) * scale).roundToInt()
                if (widthPx < 8 || heightPx < 8) return@collectLatest

                // Page points -> content px -> magnified screen px, shifted so the visible
                // region's top-left lands at the bitmap origin.
                val pxPerPoint = contentWidthPx / pageWidthPt.coerceAtLeast(1f) * scale
                val matrix = Matrix().apply {
                    postScale(pxPerPoint, pxPerPoint)
                    postTranslate(-left * scale, -top * scale)
                }
                val bitmap = withContext(Dispatchers.IO) {
                    renderPdfRegion(context, uri, pageIndex, widthPx, heightPx, matrix)
                } ?: return@collectLatest
                tile = Tile(bitmap, left, top, right - left, bottom - top)
            }
    }

    val current = tile ?: return
    with(density) {
        Image(
            bitmap = current.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset { IntOffset(current.left.roundToInt(), current.top.roundToInt()) }
                .requiredSize(current.width.toDp(), current.height.toDp())
        )
    }
}
