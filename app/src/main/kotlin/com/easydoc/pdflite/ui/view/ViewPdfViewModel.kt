package com.easydoc.pdflite.ui.view

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.util.PdfErrorMessages
import com.easydoc.pdflite.util.SafFileUtils
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
 * landing screen when EasyDoc is opened via the system "Open with" chooser for a PDF
 * (see [com.easydoc.pdflite.nav.PendingPdfIntent]). No editing, no SAF save dialog — just
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
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (error is PermissionDeniedException) {
                                "EasyDoc wasn't given permission to open this file. Try again from the app that shared it, or select it from within EasyDoc instead."
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
}
