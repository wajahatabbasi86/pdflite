package com.trendoc.pdflite.util

import android.graphics.Bitmap

/**
 * Finds the printed rule a user was aiming at when they tapped a scanned form.
 *
 * A form's fill-in lines are long horizontal runs of dark pixels. Tapping near one and
 * having the text land a few points off means nudging it into place every time, so this
 * looks for the nearest such run and reports where its top edge is — text then sits *on*
 * the line the way it would if it had been typed on the original form.
 *
 * Deliberately conservative: it only reports a line when a clear majority of a horizontal
 * window is dark, and only within a short search distance, so tapping open space still
 * places text exactly where the user tapped rather than snapping somewhere unexpected.
 */
object RuledLineFinder {

    /** Below this luminance a pixel counts as ink. Scans are grey rather than black, and
     * this one is 66 DPI, so the threshold has to be generous. */
    private const val DARK_LUMINANCE = 150

    /** Fraction of the sampled width that must be ink before a row counts as a rule. Ruled
     * lines run edge to edge; a row of body text breaks up well below this. */
    private const val MIN_DARK_RATIO = 0.7f

    /**
     * @param bitmap the rendered page.
     * @param xPx horizontal position of the tap, in [bitmap] pixels.
     * @param yPx vertical position of the tap, in [bitmap] pixels.
     * @param searchRadiusPx how far above and below the tap to look.
     * @param sampleHalfWidthPx how far either side of the tap to sample when deciding
     *   whether a row is a rule.
     * @return the y of the rule's **top** edge in [bitmap] pixels, or null if the tap was
     *   not near one.
     */
    fun findLineTop(
        bitmap: Bitmap,
        xPx: Float,
        yPx: Float,
        searchAbovePx: Int,
        searchBelowPx: Int,
        sampleHalfWidthPx: Int
    ): Float? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val left = (xPx - sampleHalfWidthPx).toInt().coerceIn(0, bitmap.width - 1)
        val right = (xPx + sampleHalfWidthPx).toInt().coerceIn(0, bitmap.width - 1)
        val width = right - left + 1
        if (width < 8) return null

        val top = (yPx - searchAbovePx).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (yPx + searchBelowPx).toInt().coerceIn(0, bitmap.height - 1)
        val height = bottom - top + 1
        if (height < 3) return null

        // One bulk read; getPixel() per pixel would be far too slow to run on a tap.
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)

        val threshold = (width * MIN_DARK_RATIO).toInt()
        val isRule = BooleanArray(height)
        for (row in 0 until height) {
            var dark = 0
            val base = row * width
            for (col in 0 until width) {
                val p = pixels[base + col]
                // Cheap luminance: the green channel tracks perceived brightness closely
                // enough to tell ink from paper, and avoids three unpacks per pixel.
                if ((p shr 8 and 0xFF) < DARK_LUMINANCE) dark++
            }
            isRule[row] = dark >= threshold
        }

        // Prefer the nearest rule at or below the tap. People aim at the line they are
        // writing on and the text sits above it, so a rule below the finger is almost always
        // the intended one; taking whichever was merely closest snapped onto the box border
        // overhead instead of the fill-in line just underneath.
        val tapRow = (yPx.toInt() - top).coerceIn(0, height - 1)
        var bestRow = -1
        var bestDistance = Int.MAX_VALUE
        for (row in tapRow until height) {
            if (!isRule[row]) continue
            bestRow = row
            bestDistance = row - tapRow
            break
        }
        if (bestRow < 0) {
            for (row in tapRow downTo 0) {
                if (!isRule[row]) continue
                bestRow = row
                bestDistance = tapRow - row
                break
            }
        }
        if (bestRow < 0) return null

        var topRow = bestRow
        while (topRow > 0 && isRule[topRow - 1]) topRow--

        return (top + topRow).toFloat()
    }
}
