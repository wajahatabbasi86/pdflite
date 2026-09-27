package com.trendoc.pdflite.util

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf

/**
 * Rendered page bitmaps for one open document, keyed by page index, bounded to [maxPages].
 *
 * A snapshot map, so a composable reading [pages] recomposes the moment a page arrives.
 * Least-recently-used pages are dropped first once the budget is exceeded. Evicted bitmaps
 * are dereferenced, never [Bitmap.recycle]d: a page that just scrolled away can still be held
 * by an in-flight composition, and drawing a recycled bitmap crashes. GC reclaims them.
 *
 * Owners must call [clear] when they are done with the document — a new file is opened, the
 * work on it is finished, or the screen's ViewModel is cleared — so nothing outlives its use.
 */
class PageBitmapCache(private val maxPages: Int) {

    private val cache = mutableStateMapOf<Int, Bitmap>()
    private val accessOrder = ArrayDeque<Int>()
    private val inFlight = mutableSetOf<Int>()

    /** Read-only view for composables; recomposes on change. */
    val pages: Map<Int, Bitmap> get() = cache

    /**
     * True if [index] needs rendering: not cached and not already being rendered. Marks it
     * in-flight when it returns true, so the caller must follow up with [put] or [failed].
     * A cached page is touched as recently used.
     */
    fun beginRender(index: Int): Boolean {
        if (index in cache) {
            touch(index)
            return false
        }
        return inFlight.add(index)
    }

    fun put(index: Int, bitmap: Bitmap) {
        inFlight -= index
        cache[index] = bitmap
        touch(index)
        while (accessOrder.size > maxPages) {
            cache.remove(accessOrder.removeFirst())
        }
    }

    fun failed(index: Int) {
        inFlight -= index
    }

    /** Drops every page. The bitmaps become unreachable and are reclaimed by GC. */
    fun clear() {
        cache.clear()
        accessOrder.clear()
        inFlight.clear()
    }

    /**
     * Frees every page's pixel memory now, instead of when GC gets round to it — which
     * measured at 10–15 s after leaving a screen. Only for the owner's final teardown
     * (`ViewModel.onCleared`), when nothing can draw these bitmaps any more; drawing a
     * recycled bitmap crashes, so never call this while the pages may still be on screen.
     */
    fun recycleAll() {
        val bitmaps = cache.values.toList()
        clear()
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
    }

    val size: Int get() = cache.size

    private fun touch(index: Int) {
        accessOrder.remove(index)
        accessOrder.addLast(index)
    }

    companion object {
        /**
         * Pages that fit in an eighth of the heap — the conventional bitmap-cache budget —
         * at [bytesPerPage] each, clamped to [[floor], [cap]].
         */
        fun budgetFor(bytesPerPage: Long, floor: Int = 4, cap: Int = 16): Int {
            val budgetBytes = Runtime.getRuntime().maxMemory() / 8
            return (budgetBytes / bytesPerPage.coerceAtLeast(1)).toInt().coerceIn(floor, cap)
        }
    }
}
