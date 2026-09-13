package com.easydoc.pdflite.ui.split

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.util.SafFileUtils
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Which of the two modes (§4) the user currently has selected. */
enum class SplitMode { EXTRACT_SELECTED, SPLIT_BY_RANGES }

/** One page in the thumbnail grid. */
data class SplitPageItem(
    val index: Int, // 0-based
    val thumbnail: Bitmap?,
    val isSelected: Boolean = false
)

data class SplitUiState(
    val fileName: String? = null,
    val isLoadingFile: Boolean = false,
    val pages: List<SplitPageItem> = emptyList(),
    val mode: SplitMode = SplitMode.EXTRACT_SELECTED,
    val rangeInput: String = "",
    val rangeError: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val readyToSave: Boolean = false,
    val defaultSaveName: String = "extracted.pdf",
    val savedResultUri: Uri? = null,
    val savedFileName: String? = null,
    /** Split-into-ranges produces >1 file; those are written straight to a chosen
     * SAF directory rather than via a single CreateDocument save dialog (§4.4). */
    val savedResultUris: List<Uri> = emptyList()
) {
    val canExtract: Boolean
        get() = mode == SplitMode.EXTRACT_SELECTED &&
            pages.any { it.isSelected } &&
            !isProcessing

    val canSplit: Boolean
        get() = mode == SplitMode.SPLIT_BY_RANGES &&
            rangeInput.isNotBlank() &&
            rangeError == null &&
            !isProcessing
}

class SplitViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SplitUiState())
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    private var sourceUri: Uri? = null
    private var pendingSingleOutput: File? = null
    private var pendingMultiOutputs: List<File>? = null

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        sourceUri = uri
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(isLoadingFile = true, errorMessage = null, pages = emptyList())
        }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            val result = withContext(Dispatchers.IO) { renderAllPages(uri) }

            result.fold(
                onSuccess = { thumbnails ->
                    _uiState.update {
                        it.copy(
                            isLoadingFile = false,
                            fileName = fileName,
                            pages = thumbnails.mapIndexed { index, bmp ->
                                SplitPageItem(index = index, thumbnail = bmp)
                            },
                            defaultSaveName = defaultNameFor(fileName)
                        )
                    }
                },
                onFailure = { error ->
                    // renderAllPages uses Android's PdfRenderer, which throws SecurityException
                    // (not PdfBox's InvalidPasswordException) for a password-protected file.
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

    private fun defaultNameFor(sourceName: String?): String {
        val base = sourceName?.substringBeforeLast(".pdf", sourceName) ?: "document"
        return "${base}_extracted.pdf"
    }

    /** Renders every page. Fine for the scaffold; §4's "large page-count documents" note
     * calls for lazy/paged rendering later if this becomes a real bottleneck. */
    private fun renderAllPages(uri: Uri): Result<List<Bitmap>> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
            renderer = PdfRenderer(pfd)
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width.coerceAtMost(300),
                        page.height.coerceAtMost(400),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                }
            }
            Result.success(bitmaps)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    fun setMode(mode: SplitMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun togglePage(index: Int) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map {
                    if (it.index == index) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    /** Validates as the user types (§4 edge case: reject bad ranges before enabling the action). */
    fun onRangeInputChanged(input: String) {
        val pageCount = _uiState.value.pages.size
        val error = validateRanges(input, pageCount)
        _uiState.update { it.copy(rangeInput = input, rangeError = error) }
    }

    private fun validateRanges(input: String, pageCount: Int): String? {
        if (input.isBlank()) return null
        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "Enter at least one page or range."

        for (part in parts) {
            val range = part.split("-").map { it.trim() }
            when (range.size) {
                1 -> {
                    val page = range[0].toIntOrNull()
                        ?: return "\"$part\" isn't a valid page number."
                    if (page < 1 || page > pageCount) {
                        return "Page $page is out of range (1-$pageCount)."
                    }
                }
                2 -> {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start == null || end == null) return "\"$part\" isn't a valid range."
                    if (start < 1 || end > pageCount || start > end) {
                        return "\"$part\" is out of range (1-$pageCount)."
                    }
                }
                else -> return "\"$part\" isn't a valid range."
            }
        }
        return null
    }

    /** Parses "1-3, 5, 7-9" into zero-based page index lists, one list per output file. */
    private fun parseRangesToPageGroups(input: String): List<List<Int>> {
        return input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
            val range = part.split("-").map { it.trim() }
            if (range.size == 1) {
                listOf(range[0].toInt() - 1)
            } else {
                (range[0].toInt() - 1..range[1].toInt() - 1).toList()
            }
        }
    }

    fun startExtract() {
        val uri = sourceUri ?: return
        val selectedIndices = _uiState.value.pages.filter { it.isSelected }.map { it.index }
        if (selectedIndices.isEmpty()) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                buildSubsetPdf(uri, selectedIndices.sorted())
            }
            result.fold(
                onSuccess = { file ->
                    pendingSingleOutput = file
                    _uiState.update { it.copy(isProcessing = false, readyToSave = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = errorMessageFor(error))
                    }
                }
            )
        }
    }

    fun startSplit() {
        val uri = sourceUri ?: return
        val input = _uiState.value.rangeInput
        if (input.isBlank() || _uiState.value.rangeError != null) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val groups = parseRangesToPageGroups(input)
            val result = withContext(Dispatchers.IO) {
                buildSubsetPdfs(uri, groups) { i -> "split_part_${i + 1}.pdf" }
            }
            result.fold(
                onSuccess = { files ->
                    pendingMultiOutputs = files
                    _uiState.update { it.copy(isProcessing = false, readyToSave = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = errorMessageFor(error))
                    }
                }
            )
        }
    }

    /** Builds a new PDF containing only [pageIndices] (0-based), in ascending order. */
    private fun buildSubsetPdf(uri: Uri, pageIndices: List<Int>): Result<File> =
        buildSubsetPdfs(uri, listOf(pageIndices)) { null }.map { it.single() }

    /**
     * Builds one output PDF per entry in [pageIndexGroups], opening and parsing the source
     * document via [uri] only once and reusing it for every group (avoids re-opening/re-parsing
     * the whole source PDF per range when splitting into many ranges at once).
     */
    private fun buildSubsetPdfs(
        uri: Uri,
        pageIndexGroups: List<List<Int>>,
        nameFor: (Int) -> String?
    ): Result<List<File>> {
        val context = getApplication<Application>()
        val outputFiles = mutableListOf<File>()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { source ->
                    pageIndexGroups.forEachIndexed { i, pageIndices ->
                        val outputFile = File(context.cacheDir, "split_${UUID.randomUUID()}.pdf")
                        PDDocument().use { output ->
                            pageIndices.forEach { index ->
                                if (index in 0 until source.numberOfPages) {
                                    // importPage (not addPage) properly copies the page's
                                    // resource dictionaries (fonts/images) into this
                                    // document's own COS structure; addPage() across
                                    // documents can silently drop them.
                                    output.importPage(source.getPage(index))
                                }
                            }
                            FileOutputStream(outputFile).use { out -> output.save(out) }
                        }
                        val finalFile = nameFor(i)?.let { name ->
                            val renamed = File(outputFile.parent, name)
                            if (outputFile.renameTo(renamed)) renamed else outputFile
                        } ?: outputFile
                        outputFiles.add(finalFile)
                    }
                }
            } ?: return Result.failure(IllegalStateException("Unable to open $uri"))
            Result.success(outputFiles)
        } catch (e: Exception) {
            outputFiles.forEach { it.delete() }
            Result.failure(e)
        }
    }

    /** Per §1.4: distinguishes password-protected PDFs from generically corrupted/unreadable ones. */
    private fun errorMessageFor(error: Throwable): String = when (error) {
        is InvalidPasswordException ->
            "This PDF is password-protected. Remove the password and try again."
        else ->
            "This file couldn't be read. It may be corrupted or password-protected."
    }

    /** Called once the user picks where to save (Extract mode: single CreateDocument result). */
    fun onSaveLocationChosen(destination: Uri?) {
        val tempFile = pendingSingleOutput
        if (destination == null || tempFile == null) return

        viewModelScope.launch {
            val context = getApplication<Application>()
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destination)?.use { out ->
                        tempFile.inputStream().use { it.copyTo(out) }
                    }
                    tempFile.delete()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                val fileName = SafFileUtils.displayName(context, destination)
                pendingSingleOutput = null
                _uiState.update {
                    it.copy(readyToSave = false, savedResultUri = destination, savedFileName = fileName)
                }
            } else {
                _uiState.update {
                    it.copy(readyToSave = false, errorMessage = "Not enough space to save this file.")
                }
            }
        }
    }

    /** Called once the user picks a destination directory (Split mode: multiple files). */
    fun onSaveDirectoryChosen(directoryUri: Uri?) {
        val files = pendingMultiOutputs
        if (directoryUri == null || files == null) return

        viewModelScope.launch {
            val context = getApplication<Application>()
            val writtenUris = withContext(Dispatchers.IO) {
                try {
                    val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, directoryUri)
                        ?: return@withContext null
                    val results = mutableListOf<Uri>()
                    files.forEach { file ->
                        val newFile = dir.createFile("application/pdf", file.name) ?: return@withContext null
                        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                        results.add(newFile.uri)
                        file.delete()
                    }
                    results
                } catch (e: Exception) {
                    null
                }
            }

            if (writtenUris != null) {
                pendingMultiOutputs = null
                _uiState.update {
                    it.copy(
                        readyToSave = false,
                        savedResultUris = writtenUris,
                        savedFileName = "${writtenUris.size} files"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(readyToSave = false, errorMessage = "Not enough space to save these files.")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
