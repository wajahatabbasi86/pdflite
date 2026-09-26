package com.trendoc.pdflite.ui.view

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.recents.RecentsRepository
import com.trendoc.pdflite.util.PdfErrorMessages
import com.trendoc.pdflite.util.SafFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Thrown to open the file descriptor itself — a genuine permission problem (e.g. a caller
 * that handed us a Uri without granting read access), which is a different failure from a
 * password-protected PDF even though both surface as [SecurityException]: [PdfRenderer]'s
 * own constructor throws that same exception type specifically when the PDF requires a
 * password. Wrapping this earlier failure lets [ViewPdfViewModel.onDocumentPicked] tell the
 * two apart instead of always guessing "password-protected." */
private class PermissionDeniedException(cause: Throwable) : Exception(cause)

/** A page's intrinsic size in PDF points, read during the cheap metadata pass. Enough to
 * lay out a correctly-proportioned placeholder before that page's bitmap exists, so the
 * list doesn't reflow as pages stream in. */
data class PdfPageSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

data class ViewPdfUiState(
    val fileName: String? = null,
    val isLoading: Boolean = false,
    /** One entry per page — sizes only, never bitmaps. The bitmaps live in
     * [ViewPdfViewModel.pageCache], which is bounded; this list is not, but a few bytes per
     * page is nothing next to a few megabytes per page. */
    val pageSizes: List<PdfPageSize> = emptyList(),
    val errorMessage: String? = null,
    val sourceUri: Uri? = null,
    /** Whether this PDF has any AcroForm fields — checked in the background, off the render
     * path (see [ViewPdfViewModel.checkHasFormFields]), so the "Fill Forms" quick-action
     * chip simply pops in a moment after the pages themselves appear rather than delaying
     * them. Defaults to false, so the chip is just absent until the check resolves. */
    val hasFormFields: Boolean = false
) {
    val pageCount: Int get() = pageSizes.size
}

/**
 * Read-only "View PDF" — the app's own PDF viewer, used both from Home (§2) and as the
 * landing screen when TrenDoc is opened via the system "Open with" chooser for a PDF
 * (see [com.trendoc.pdflite.nav.PendingPdfIntent]). No editing, no SAF save dialog — just
 * let the user scroll the pages and, if they want, share/open the original file in
 * another app.
 *
 * Pages are rendered **on demand**, never all at once. Opening a document only reads each
 * page's dimensions (cheap, no rasterizing); the UI then asks for the bitmaps it actually
 * needs to draw via [requestPage], and [pageCache] holds a bounded window of them sized
 * from the device's own heap budget. Rendering every page up front — the previous
 * behavior — cost roughly 1.9 MB per US-Letter page held simultaneously, so a 100-page
 * document needed ~190 MB and a long manual was a guaranteed OutOfMemoryError.
 */
class ViewPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ViewPdfUiState())
    val uiState: StateFlow<ViewPdfUiState> = _uiState.asStateFlow()
    private val recentsRepository = RecentsRepository(application)

    /** Rendered base pages, keyed by page index. A snapshot map so Compose recomposes the
     * moment a page finishes rendering. Read-only to callers — [requestPage] is the only
     * way in, and eviction is this class's business. */
    private val _pageCache = mutableStateMapOf<Int, Bitmap>()
    val pageCache: Map<Int, Bitmap> get() = _pageCache

    /** Page indices in least-recently-requested order. */
    private val accessOrder = ArrayDeque<Int>()
    private val inFlight = mutableSetOf<Int>()

    /**
     * How many rendered pages to hold at once. An eighth of the heap is the conventional
     * bitmap-cache budget; dividing by the worst-case size of one page at the render cap
     * below turns that into a page count. Floored at 4 so scrolling always has the current
     * page plus neighbours in hand even on a small-heap device, and capped at 16 so a
     * large-heap device doesn't hoard.
     */
    private val maxCachedPages: Int = run {
        val budgetBytes = Runtime.getRuntime().maxMemory() / 8
        val worstCasePageBytes = MAX_RENDER_WIDTH.toLong() * MAX_RENDER_HEIGHT * 4
        (budgetBytes / worstCasePageBytes).toInt().coerceIn(4, 16)
    }

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        val context = getApplication<Application>()
        clearPageCache()
        _uiState.update {
            it.copy(isLoading = true, errorMessage = null, pageSizes = emptyList(), sourceUri = uri, hasFormFields = false)
        }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            val result = withContext(Dispatchers.IO) { readPageSizes(uri) }
            result.fold(
                onSuccess = { sizes ->
                    _uiState.update { it.copy(isLoading = false, fileName = fileName, pageSizes = sizes) }
                    recentsRepository.record(
                        uri = uri,
                        displayName = fileName,
                        sizeBytes = SafFileUtils.fileSize(context, uri),
                        pageCount = sizes.size,
                        sourceLabel = "View PDF"
                    )
                    // Separate, later pass — never blocks the pages the reader is waiting on.
                    checkHasFormFields(uri)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (error is PermissionDeniedException) {
                                "TrenDoc wasn't given permission to open this file. Try again from the app that shared it, or select it from within TrenDoc instead."
                            } else {
                                PdfErrorMessages.forOpenFailure(error)
                            }
                        )
                    }
                }
            )
        }
    }

    /**
     * Reads every page's dimensions without rasterizing any of them. [PdfRenderer.openPage]
     * parses the page's box geometry only — no pixels — so this stays fast even on a long
     * document, and is what replaces the old render-everything pass.
     */
    private fun readPageSizes(uri: Uri): Result<List<PdfPageSize>> {
        // Opening the descriptor and constructing PdfRenderer both throw SecurityException,
        // but for different reasons — split them into their own try/catch so a permission
        // problem (this app was handed a Uri it was never granted read access to) doesn't
        // get misreported as "password-protected" (which is what PdfRenderer's own
        // constructor throws SecurityException for, once the descriptor is already open).
        val pfd = try {
            SafFileUtils.openFileDescriptor(getApplication(), uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
        } catch (e: SecurityException) {
            return Result.failure(PermissionDeniedException(e))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        var renderer: PdfRenderer? = null
        return try {
            renderer = PdfRenderer(pfd)
            val sizes = (0 until renderer.pageCount).map { i ->
                renderer.openPage(i).use { page -> PdfPageSize(page.width, page.height) }
            }
            Result.success(sizes)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd.close()
        }
    }

    /**
     * Ensures page [index] is rendered and in [pageCache], rendering it off the main thread
     * if it isn't. Safe to call from composition on every recomposition: an index already
     * cached or already being rendered returns immediately.
     */
    fun requestPage(index: Int) {
        if (index in _pageCache || index in inFlight) {
            touch(index)
            return
        }
        val uri = _uiState.value.sourceUri ?: return
        if (index !in 0 until _uiState.value.pageCount) return

        inFlight += index
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { renderSinglePage(uri, index) }
            inFlight -= index
            // The reader may have opened a different document while this was rendering.
            if (bitmap != null && _uiState.value.sourceUri == uri) {
                _pageCache[index] = bitmap
                touch(index)
                trimToBudget()
            }
        }
    }

    private fun renderSinglePage(uri: Uri, index: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri) ?: return null
            renderer = PdfRenderer(pfd)
            if (index !in 0 until renderer.pageCount) return null
            renderer.openPage(index).use { page ->
                // Display resolution, not thumbnail — this is the actual reading surface.
                val bitmap = Bitmap.createBitmap(
                    page.width.coerceAtMost(MAX_RENDER_WIDTH),
                    page.height.coerceAtMost(MAX_RENDER_HEIGHT),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            // OutOfMemoryError is an Error, not an Exception, so the catch above never saw
            // it — this is exactly the crash the on-demand cache exists to prevent, kept as
            // a backstop for a single pathologically large page. Drop what's cached and
            // give up on this one page rather than taking the process down.
            clearPageCache()
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    /** Marks [index] as most recently used. */
    private fun touch(index: Int) {
        accessOrder.remove(index)
        accessOrder.addLast(index)
    }

    /**
     * Drops least-recently-requested pages until the cache is back within budget. Evicted
     * bitmaps are dereferenced rather than [Bitmap.recycle]d: a page that just scrolled out
     * of view can still be held by an in-flight composition, and drawing a recycled bitmap
     * crashes. Letting GC reclaim them is a beat slower and entirely safe.
     */
    private fun trimToBudget() {
        while (accessOrder.size > maxCachedPages) {
            val evicted = accessOrder.removeFirst()
            _pageCache.remove(evicted)
        }
    }

    private fun clearPageCache() {
        _pageCache.clear()
        accessOrder.clear()
    }

    override fun onCleared() {
        super.onCleared()
        clearPageCache()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Cheap AcroForm presence check for the "Fill Forms" quick-action chip — opens the
     * document a second time via PdfBox-Android (a different parser from the [PdfRenderer]
     * used for display) but only reads the catalog's field list length, never walking widget
     * rectangles or annotation appearance streams the way [com.trendoc.pdflite.ui.fillforms.FillFormsViewModel]
     * does. Runs after the pages are already showing, so a slow parse on a huge PDF never
     * delays what the reader is waiting on — the chip just appears a moment later. Silently
     * leaves hasFormFields false on any failure; this is a nice-to-have shortcut; the Fill
     * Forms tool itself remains the source of truth and re-parses regardless. */
    private fun checkHasFormFields(uri: Uri) {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { document ->
                            document.documentCatalog.acroForm?.fields?.isNotEmpty() == true
                        }
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            // Guard against a stale result landing after the reader has already opened a
            // different file (e.g. picked a new PDF while this check was still running).
            if (found) _uiState.update { if (it.sourceUri == uri) it.copy(hasFormFields = true) else it }
        }
    }

    /** Re-renders one page at a resolution that actually matches how far the reader has
     * zoomed in, used by the full-screen zoom viewer. [PdfRenderer] rasterizes to a fixed
     * bitmap — unlike a true vector viewer, TrenDoc can't redraw infinitely sharp text at
     * any zoom level from one bitmap, so instead it re-renders a fresh, higher-resolution
     * bitmap targeting [targetLongestSidePx] (the caller picks this based on the current
     * zoom scale — see [PageDetailScreen]) each time the reader zooms further than the
     * bitmap already in hand supports. Opens its own short-lived [PdfRenderer] rather than
     * reusing [renderPages]'s — that one is long gone by the time a page is tapped open.
     * Capped well below what would risk an OOM on a single bitmap. */
    suspend fun renderPageAtResolution(pageIndex: Int, targetLongestSidePx: Int): Bitmap? {
        val uri = _uiState.value.sourceUri ?: return null
        val cappedTarget = targetLongestSidePx.coerceIn(1100, 4500)
        return withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = SafFileUtils.openFileDescriptor(getApplication(), uri) ?: return@withContext null
                renderer = PdfRenderer(pfd)
                if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                renderer.openPage(pageIndex).use { page ->
                    val longestSide = maxOf(page.width, page.height).coerceAtLeast(1)
                    val factor = cappedTarget.toFloat() / longestSide
                    val width = (page.width * factor).toInt().coerceAtLeast(1)
                    val height = (page.height * factor).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                // Same reasoning as renderSinglePage: an Error slips past catch(Exception),
                // and a zoomed page is the single largest bitmap this app ever allocates.
                // Falling back to null just leaves the reader on the resolution they had.
                null
            } finally {
                renderer?.close()
                pfd?.close()
            }
        }
    }
}

/** Caps on the base (unzoomed) render. A US-Letter page is 612×792 points, so it renders
 * at its natural size and these only bite on unusually large pages. */
private const val MAX_RENDER_WIDTH = 1100
private const val MAX_RENDER_HEIGHT = 1550
