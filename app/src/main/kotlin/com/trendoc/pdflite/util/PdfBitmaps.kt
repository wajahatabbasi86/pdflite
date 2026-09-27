package com.trendoc.pdflite.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer

/**
 * Renders [page] into a bitmap of [width] x [height], optionally handing back a
 * memory-cheaper copy.
 *
 * [PdfRenderer.Page.render] accepts **only** [Bitmap.Config.ARGB_8888] — handing it an
 * RGB_565 bitmap throws [IllegalArgumentException], and since every render path here wraps
 * its work in a `catch (e: Exception)` that failure surfaces as a silently blank page
 * rather than an error. So render always happens at ARGB_8888; when [halveMemory] is set
 * the result is copied down to RGB_565 and the full-depth original is recycled
 * immediately, leaving only the half-size copy resident.
 *
 * Worth it for thumbnails and form pages — a PDF page rendered onto white has no alpha to
 * preserve and the reduced color depth isn't visible at those sizes — but not for the main
 * reading surface in View PDF, which stays ARGB_8888.
 */
fun renderPageBitmap(
    page: PdfRenderer.Page,
    width: Int,
    height: Int,
    halveMemory: Boolean = false
): Bitmap {
    val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    rendered.eraseColor(Color.WHITE)
    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    if (!halveMemory) return rendered

    // copy() can return null under memory pressure — keep the original in that case rather
    // than handing back nothing.
    val compact = rendered.copy(Bitmap.Config.RGB_565, false) ?: return rendered
    rendered.recycle()
    return compact
}

/**
 * Pixel size for rendering a [pageWidthPt] x [pageHeightPt] page to fit inside [maxWidthPx] x
 * [maxHeightPx] **with its aspect ratio kept**, scaling up as well as down.
 *
 * Replaces `page.width.coerceAtMost(max)` / `page.height.coerceAtMost(max)`, which had two
 * faults: PdfRenderer page sizes are in points, so a US Letter page (612pt wide) rendered at
 * 612px and was then stretched to a 1080px screen, soft before any zoom; and capping each
 * side separately squashed any page wider or taller than the cap.
 */
fun fitPageSize(pageWidthPt: Int, pageHeightPt: Int, maxWidthPx: Int, maxHeightPx: Int): Pair<Int, Int> {
    val w = pageWidthPt.coerceAtLeast(1).toFloat()
    val h = pageHeightPt.coerceAtLeast(1).toFloat()
    val factor = minOf(maxWidthPx / w, maxHeightPx / h)
    return (w * factor).toInt().coerceAtLeast(1) to (h * factor).toInt().coerceAtLeast(1)
}

/**
 * Renders just a region of one page into a [widthPx] x [heightPx] bitmap, with [transform]
 * mapping page points (top-left origin) to bitmap pixels. Opens its own renderer so it can
 * run on a background thread alongside any other render. Null on any failure, including OOM.
 */
fun renderPdfRegion(
    context: android.content.Context,
    uri: android.net.Uri,
    pageIndex: Int,
    widthPx: Int,
    heightPx: Int,
    transform: android.graphics.Matrix
): Bitmap? {
    var pfd: android.os.ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    return try {
        pfd = SafFileUtils.openFileDescriptor(context, uri) ?: return null
        renderer = PdfRenderer(pfd)
        if (pageIndex !in 0 until renderer.pageCount) return null
        renderer.openPage(pageIndex).use { page ->
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
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
