package com.easydoc.pdflite.ui.merge

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.util.PdfErrorMessages
import com.easydoc.pdflite.util.SafFileUtils
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
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

/** One row in the Merge file list (docs/REQUIREMENTS.md §3). */
data class MergeFileItem(
    val uri: Uri,
    val displayName: String,
    val pageCount: Int = 0,
    val thumbnail: Bitmap? = null,
    /** Non-null => this file failed to open (corrupted/password-protected); excluded from merge. */
    val error: String? = null
)

data class MergeUiState(
    val files: List<MergeFileItem> = emptyList(),
    val isMerging: Boolean = false,
    val errorMessage: String? = null,
    /** Set once a merge finishes and is waiting for the user to pick a save location. */
    val readyToSave: Boolean = false,
    val defaultSaveName: String = "merged.pdf",
    /** Set once the merged file has been written to the user-chosen Uri. */
    val savedResultUri: Uri? = null,
    val savedFileName: String? = null
) {
    /** §3.4: at least 2 files that opened successfully are required to enable Merge. */
    val canMerge: Boolean
        get() = files.count { it.error == null } >= 2 && !isMerging
}

class MergeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MergeUiState())
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    /** Holds the merged bytes on disk (app cache) until the user picks a save Uri. */
    private var pendingMergedFile: File? = null

    /** Called with newly picked Uris (from OpenMultipleDocuments). Appends, skipping dupes. */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        val existingUris = _uiState.value.files.map { it.uri }.toSet()
        val newUris = uris.filterNot { it in existingUris }
        if (newUris.isEmpty()) return

        // Add placeholder rows immediately so the list feels responsive, then fill in
        // page count / thumbnail / error per-file as each finishes loading.
        _uiState.update { state ->
            state.copy(
                files = state.files + newUris.map { uri ->
                    MergeFileItem(uri = uri, displayName = SafFileUtils.displayName(context, uri))
                }
            )
        }

        newUris.forEach { uri ->
            viewModelScope.launch {
                val metadata = withContext(Dispatchers.IO) { loadMetadata(uri) }
                _uiState.update { state ->
                    state.copy(
                        files = state.files.map { item ->
                            if (item.uri == uri) {
                                metadata.fold(
                                    onSuccess = { (pageCount, thumbnail) ->
                                        item.copy(pageCount = pageCount, thumbnail = thumbnail, error = null)
                                    },
                                    onFailure = { error ->
                                        // §3.4: flag this specific item, exclude it from merge,
                                        // don't fail the whole selection. loadMetadata renders via
                                        // Android's PdfRenderer, which throws SecurityException
                                        // (not PdfBox's InvalidPasswordException) for a password-
                                        // protected file.
                                        item.copy(
                                            error = if (error is SecurityException) {
                                                "Password-protected. Remove the password and try again."
                                            } else {
                                                "Couldn't read this file. It may be corrupted or password-protected."
                                            }
                                        )
                                    }
                                )
                            } else item
                        }
                    )
                }
            }
        }
    }

    /** Renders just the first page as a list thumbnail and reports total page count. */
    private fun loadMetadata(uri: Uri): Result<Pair<Int, Bitmap?>> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val thumbnail = if (pageCount > 0) {
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width.coerceAtMost(300),
                        page.height.coerceAtMost(400),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } else null
            Result.success(pageCount to thumbnail)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    fun removeFile(uri: Uri) {
        _uiState.update { state -> state.copy(files = state.files.filterNot { it.uri == uri }) }
    }

    /** Swaps the item at [index] with the one above/below it. Reordering matters — merge
     * output follows this in-memory list order (§3.5). */
    fun moveFile(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = index + delta
            if (target !in state.files.indices || index !in state.files.indices) return@update state
            val mutable = state.files.toMutableList()
            val tmp = mutable[index]
            mutable[index] = mutable[target]
            mutable[target] = tmp
            state.copy(files = mutable)
        }
    }

    /**
     * Runs the merge in the background (§1.2), writing to a temp file in cache. Once done,
     * the UI is expected to launch a CreateDocument picker and call [onSaveLocationChosen]
     * with the result.
     */
    fun startMerge() {
        val state = _uiState.value
        val validUris = state.files.filter { it.error == null }.map { it.uri }
        if (validUris.size < 2) return

        _uiState.update { it.copy(isMerging = true, errorMessage = null) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) { mergeToTempFile(validUris) }

            result.fold(
                onSuccess = { tempFile ->
                    pendingMergedFile = tempFile
                    _uiState.update {
                        it.copy(isMerging = false, readyToSave = true)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isMerging = false, errorMessage = PdfErrorMessages.forOpenFailure(error))
                    }
                }
            )
        }
    }

    private fun mergeToTempFile(uris: List<Uri>): Result<File> {
        val context = getApplication<Application>()
        val outputFile = File(context.cacheDir, "merge_${UUID.randomUUID()}.pdf")
        val openedStreams = mutableListOf<java.io.InputStream>()
        return try {
            val merger = PDFMergerUtility()
            uris.forEach { uri ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open $uri")
                openedStreams.add(input)
                merger.addSource(input)
            }
            FileOutputStream(outputFile).use { out ->
                merger.destinationStream = out
                // Explicit temp-file-backed memory usage: some Android devices choke on
                // large in-memory merges (§5 already warns about >100MB files elsewhere).
                merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        } finally {
            // PDFMergerUtility does not guarantee it closes every source stream itself
            // (particularly on the failure path) — close them explicitly to avoid fd leaks.
            openedStreams.forEach { runCatching { it.close() } }
        }
    }

    /** Called once the user picks where to save via ACTION_CREATE_DOCUMENT. */
    fun onSaveLocationChosen(destination: Uri?) {
        val tempFile = pendingMergedFile
        if (destination == null || tempFile == null) {
            // User backed out of the save dialog — stay on the reviewed/merged state so
            // they can retry the save without re-merging.
            return
        }

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
                pendingMergedFile = null
                _uiState.update {
                    it.copy(readyToSave = false, savedResultUri = destination, savedFileName = fileName)
                }
            } else {
                _uiState.update {
                    it.copy(
                        readyToSave = false,
                        errorMessage = PdfErrorMessages.SAVE_FAILED_SINGLE
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
