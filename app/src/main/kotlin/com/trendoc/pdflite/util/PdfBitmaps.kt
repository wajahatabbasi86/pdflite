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
