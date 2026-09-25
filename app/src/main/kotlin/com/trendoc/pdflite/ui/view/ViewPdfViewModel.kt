package com.trendoc.pdflite.ui.view

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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

data class ViewPdfUiState(
    val fileName: String? = null,
    val isLoading: Boolean = false,
    val pages: List<Bitmap> = emptyList(),
    val errorMessage: String? = null,
    val sourceUri: Uri? = null
)

/**
 * Read-only "View PDF" — the app's own PDF viewer, used both from Home (§2) and as the
 * landing screen when TrenDoc is opened via the system "Open with" chooser for a PDF
 * (see [com.trendoc.pdflite.nav.PendingPdfIntent]). No editing, no SAF save dialog — just
 * render every page and let the user scroll and, if they want, share/open the original
 * file in another app.
 *
 * Renders every page up front at display resolution. Fine for the scaffold and for
 * typical documents; a genuinely huge PDF would benefit from paginated/virtualized
 * rendering instead (the same known tradeoff already noted on Split's page grid).
 */
class ViewPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ViewPdfUiState())
    val uiState: StateFlow<ViewPdfUiState> = _uiState.asStateFlow()
    private val recentsRepository = RecentsRepository(application)

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        val context = getApplication<Application>()
        _uiState.update { it.copy(isLoading = true, errorMessage = null, pages = emptyList(), sourceUri = uri) }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            val result = withContext(Dispatchers.IO) { renderPages(uri) }
            result.fold(
                onSuccess = { pages ->
                    _uiState.update { it.copy(isLoading = false, fileName = fileName, pages = pages) }
                    recentsRepository.record(
                        uri = uri,
                        displayName = fileName,
                        sizeBytes = SafFileUtils.fileSize(context, uri),
                        pageCount = pages.size,
                        sourceLabel = "View PDF"
                    )
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

    private fun renderPages(uri: Uri): Result<List<Bitmap>> {
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
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    // Display resolution, not thumbnail — this is the actual reading surface.
                    val bitmap = Bitmap.createBitmap(
                        page.width.coerceAtMost(1100),
                        page.height.coerceAtMost(1550),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                }
            }
            Result.success(bitmaps)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd.close()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
            } finally {
                renderer?.close()
                pfd?.close()
            }
        }
    }
}
