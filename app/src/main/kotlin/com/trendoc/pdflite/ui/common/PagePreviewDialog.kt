package com.trendoc.pdflite.ui.common

import androidx.compose.ui.res.pluralStringResource
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trendoc.pdflite.util.SafFileUtils
import com.trendoc.pdflite.util.fitPageSize
import com.trendoc.pdflite.util.renderPageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen, zoomable look at one page, for screens whose own page views are thumbnails
 * (Split's page grid, Merge's file list). Pinch or use +/- to zoom, drag to pan in any
 * direction, arrows to change page; zoomed regions are re-rendered sharp by [SharpZoomLayer].
 */
@Composable
fun PagePreviewDialog(uri: Uri, initialPageIndex: Int, pageCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var pageIndex by remember { mutableIntStateOf(initialPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.page_preview_close_preview)) }
                Text(
                    stringResource(R.string.common_page_of, pageIndex + 1, pageCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.common_previous_page))
                }
                IconButton(onClick = { pageIndex++ }, enabled = pageIndex < pageCount - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.common_next_page))
                }
            }

            // Fresh zoom per page, matching View PDF.
            val zoomState = remember(pageIndex) { ZoomPanState(minScale = 1f, maxScale = 6f) }
            var page by remember(pageIndex) { mutableStateOf<Pair<Bitmap, Float>?>(null) }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val boxWidthPx = constraints.maxWidth
                val boxHeightPx = constraints.maxHeight
                LaunchedEffect(uri, pageIndex, boxWidthPx, boxHeightPx) {
                    page = withContext(Dispatchers.IO) {
                        renderFitted(context, uri, pageIndex, boxWidthPx, boxHeightPx)
                    }
                }
                val current = page
                if (current == null) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    val (bitmap, pageWidthPt) = current
                    // The zoom viewport is the fitted page itself, which is what
                    // SharpZoomLayer assumes, and keeps pan bounds on the page.
                    val widthDp = with(density) { bitmap.width.toDp() }
                    val heightDp = with(density) { bitmap.height.toDp() }
                    ZoomPanBox(state = zoomState, modifier = Modifier.size(widthDp, heightDp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.common_page_number, pageIndex + 1),
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        SharpZoomLayer(
                            state = zoomState,
                            uri = uri,
                            pageIndex = pageIndex,
                            pageWidthPt = pageWidthPt,
                            contentWidthPx = bitmap.width.toFloat(),
                            contentHeightPx = bitmap.height.toFloat()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { zoomState.zoomBy(1f / 1.5f) }) { Text(stringResource(R.string.common_zoom_out)) }
                Text(stringResource(R.string.common_percent, (zoomState.scale * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { zoomState.zoomBy(1.5f) }) { Text(stringResource(R.string.common_zoom_in)) }
                TextButton(onClick = { zoomState.reset() }) { Text(stringResource(R.string.page_preview_fit)) }
            }
        }
    }
}

/** The page fitted inside the box with its aspect kept; returns the bitmap and page width in points. */
private fun renderFitted(
    context: android.content.Context,
    uri: Uri,
    pageIndex: Int,
    maxW: Int,
    maxH: Int
): Pair<Bitmap, Float>? {
    var pfd: android.os.ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    return try {
        pfd = SafFileUtils.openFileDescriptor(context, uri) ?: return null
        renderer = PdfRenderer(pfd)
        if (pageIndex !in 0 until renderer.pageCount) return null
        renderer.openPage(pageIndex).use { p ->
            val (w, h) = fitPageSize(p.width, p.height, maxW, maxH)
            renderPageBitmap(p, w, h) to p.width.toFloat()
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    } finally {
        renderer?.close()
        pfd?.close()
    }
}
