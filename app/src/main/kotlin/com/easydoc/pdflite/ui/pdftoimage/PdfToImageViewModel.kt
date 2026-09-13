package com.easydoc.pdflite.ui.pdftoimage

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.util.SafFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImageFormat(val label: String, val mimeType: String, val extension: String) {
    JPG("JPG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png")
}

enum class ImageQuality(val label: String, val jpegQuality: Int, val renderScale: Float) {
    LOW("Low", 60, 1.0f),
    MEDIUM("Medium", 80, 1.5f),
    HIGH("High", 95, 2.0f)
}

data class PdfToImageUiState(
    val fileName: String? = null,
    val isLoadingFile: Boolean = false,
    val pageCount: Int = 0,
    val format: ImageFormat = ImageFormat.JPG,
    val quality: ImageQuality = ImageQuality.MEDIUM,
    val rangeInput: String = "",
    val rangeError: String? = null,
    val isConverting: Boolean = false,
    val convertedCount: Int = 0,
    val errorMessage: String? = null,
    val savedResultUris: List<Uri> = emptyList()
) {
    val canConvert: Boolean
        get() = pageCount > 0 && rangeError == null && !isConverting
}

/**
 * PDF -> Image(s), per docs/REQUIREMENTS.md §6.2: choose format/quality and an optional page
 * range (default all), render each page via [PdfRenderer], and save individual image files to
 * a user-chosen SAF directory (there are potentially many output files, so a directory picker
 * is the right save affordance here, not a single CreateDocument dialog — same reasoning as
 * Split's "split into ranges" mode).
 */
class PdfToImageViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PdfToImageUiState())
    val uiState: StateFlow<PdfToImageUiState> = _uiState.asStateFlow()

    private var sourceUri: Uri? = null

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        sourceUri = uri
        val context = getApplication<Application>()
        _uiState.update { it.copy(isLoadingFile = true, errorMessage = null) }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            val result = withContext(Dispatchers.IO) { readPageCount(uri) }
            result.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(isLoadingFile = false, fileName = fileName, pageCount = count)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingFile = false,
                            errorMessage = if (error is SecurityException) {
                                "This PDF is password-protected. Remove the password and try again."
                            } else {
                                "This file couldn't be read. It may be corrupted or password-protected."
                            }
                        )
                    }
                }
            )
        }
    }

    private fun readPageCount(uri: Uri): Result<Int> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
            renderer = PdfRenderer(pfd)
            Result.success(renderer.pageCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    fun setFormat(format: ImageFormat) = _uiState.update { it.copy(format = format) }
    fun setQuality(quality: ImageQuality) = _uiState.update { it.copy(quality = quality) }

    fun onRangeInputChanged(input: String) {
        val error = if (input.isBlank()) null else validateRanges(input, _uiState.value.pageCount)
        _uiState.update { it.copy(rangeInput = input, rangeError = error) }
    }

    private fun validateRanges(input: String, pageCount: Int): String? {
        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "Enter at least one page or range."
        for (part in parts) {
            val range = part.split("-").map { it.trim() }
            when (range.size) {
                1 -> {
                    val page = range[0].toIntOrNull() ?: return "\"$part\" isn't a valid page number."
                    if (page < 1 || page > pageCount) return "Page $page is out of range (1-$pageCount)."
                }
                2 -> {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start == null || end == null) return "\"$part\" isn't a valid range."
                    if (start < 1 || end > pageCount || start > end) return "\"$part\" is out of range (1-$pageCount)."
                }
                else -> return "\"$part\" isn't a valid range."
            }
        }
        return null
    }

    /** Zero-based page indices to convert — every page if [rangeInput] is blank. */
    private fun resolvePageIndices(): List<Int> {
        val input = _uiState.value.rangeInput
        val pageCount = _uiState.value.pageCount
        if (input.isBlank()) return (0 until pageCount).toList()
        return input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.flatMap { part ->
            val range = part.split("-").map { it.trim() }
            if (range.size == 1) {
                listOf(range[0].toInt() - 1)
            } else {
                (range[0].toInt() - 1..range[1].toInt() - 1).toList()
            }
        }.distinct().sorted()
    }

    fun startConvert(directoryUri: Uri?) {
        val uri = sourceUri ?: return
        if (directoryUri == null) return
        val indices = resolvePageIndices()
        if (indices.isEmpty()) return

        _uiState.update { it.copy(isConverting = true, convertedCount = 0, errorMessage = null) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val format = _uiState.value.format
            val quality = _uiState.value.quality
            val baseName = (_uiState.value.fileName ?: "document").substringBeforeLast(".pdf", "document")

            val result = withContext(Dispatchers.IO) {
                convertPages(context.applicationContext, uri, indices, directoryUri, baseName, format, quality) { done ->
                    _uiState.update { it.copy(convertedCount = done) }
                }
            }

            result.fold(
                onSuccess = { uris ->
                    _uiState.update { it.copy(isConverting = false, savedResultUris = uris) }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(isConverting = false, errorMessage = "Couldn't convert this file. It may be corrupted.")
                    }
                }
            )
        }
    }

    private fun convertPages(
        context: android.content.Context,
        uri: Uri,
        indices: List<Int>,
        directoryUri: Uri,
        baseName: String,
        format: ImageFormat,
        quality: ImageQuality,
        onProgress: (Int) -> Unit
    ): Result<List<Uri>> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            val dir = DocumentFile.fromTreeUri(context, directoryUri)
                ?: return Result.failure(IllegalStateException("Unable to open directory"))
            pfd = SafFileUtils.openFileDescriptor(context, uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
            renderer = PdfRenderer(pfd)
            val results = mutableListOf<Uri>()

            indices.forEachIndexed { i, pageIndex ->
                if (pageIndex !in 0 until renderer.pageCount) return@forEachIndexed
                renderer.openPage(pageIndex).use { page ->
                    val width = (page.width * quality.renderScale).toInt().coerceAtLeast(1)
                    val height = (page.height * quality.renderScale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // White background so JPEG (no alpha) doesn't render transparent areas black.
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val fileName = "${baseName}_page${pageIndex + 1}.${format.extension}"
                    val outFile = dir.createFile(format.mimeType, fileName)
                        ?: throw IllegalStateException("Unable to create $fileName")
                    context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                        val compressFormat = if (format == ImageFormat.PNG) {
                            Bitmap.CompressFormat.PNG
                        } else {
                            Bitmap.CompressFormat.JPEG
                        }
                        bitmap.compress(compressFormat, quality.jpegQuality, out)
                    }
                    bitmap.recycle()
                    results.add(outFile.uri)
                }
                onProgress(i + 1)
            }
            Result.success(results)
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
