package com.trendoc.pdflite.ui.picker

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.util.PdfErrorMessages
import com.trendoc.pdflite.util.SafFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reference implementation of the pick-file -> background-process -> progress -> result
 * pattern described in docs/REQUIREMENTS.md §1.2. Every later tool screen (Merge, Split,
 * Compress, Convert) follows this same ViewModel shape, so this class doubles as the
 * template for those.
 */
data class PdfPickerUiState(
    val isLoading: Boolean = false,
    val fileName: String? = null,
    val pageCount: Int = 0,
    val thumbnails: List<Bitmap> = emptyList(),
    val errorMessage: String? = null
)

class PdfPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfPickerUiState())
    val uiState: StateFlow<PdfPickerUiState> = _uiState.asStateFlow()

    /** Called when the user picks a PDF via SafFileUtils.openSingleDocument. */
    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) {
            // No file selected — per §1.4, this is not an error state, just a no-op.
            return
        }

        val context = getApplication<Application>()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)

            val result = withContext(Dispatchers.IO) {
                renderThumbnails(uri)
            }

            result.fold(
                onSuccess = { (pageCount, thumbnails) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            fileName = fileName,
                            pageCount = pageCount,
                            thumbnails = thumbnails,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    // Per §1.4: plain-language error, never a raw stack trace — and a
                    // distinct message when the cause is specifically a password-protected PDF.
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = PdfErrorMessages.forOpenFailure(error))
                    }
                }
            )
        }
    }

    /** Runs on Dispatchers.IO — renders every page of the PDF at [uri] to a bitmap. */
    private fun renderThumbnails(uri: Uri): Result<Pair<Int, List<Bitmap>>> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
            renderer = PdfRenderer(pfd)

            val bitmaps = mutableListOf<Bitmap>()
            for (pageIndex in 0 until renderer.pageCount) {
                renderer.openPage(pageIndex).use { page ->
                    // Thumbnail-sized render; full-resolution rendering is deferred to
                    // the actual tool screens (Split's page grid, PDF->Image conversion).
                    val bitmap = Bitmap.createBitmap(
                        page.width.coerceAtMost(600),
                        page.height.coerceAtMost(800),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                }
            }
            Result.success(renderer.pageCount to bitmaps)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
