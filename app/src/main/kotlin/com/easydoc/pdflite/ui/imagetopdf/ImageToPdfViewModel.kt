package com.easydoc.pdflite.ui.imagetopdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.util.PdfErrorMessages
import com.easydoc.pdflite.util.SafFileUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
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

/** One entry in the Image(s) -> PDF reorderable list (docs/REQUIREMENTS.md §6.1). */
data class ImageItem(
    val uri: Uri,
    val displayName: String,
    val thumbnail: Bitmap? = null,
    val error: String? = null
)

data class ImageToPdfUiState(
    val images: List<ImageItem> = emptyList(),
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    val readyToSave: Boolean = false,
    val defaultSaveName: String = "images.pdf",
    val savedResultUri: Uri? = null,
    val savedFileName: String? = null
) {
    val canCreate: Boolean
        get() = images.any { it.error == null } && !isCreating
}

/**
 * Image(s) -> PDF, per docs/REQUIREMENTS.md §6.1: each image becomes its own page, sized to
 * that image's own aspect ratio ("fit-to-image", the v1 default the requirements call for)
 * rather than a fixed page size.
 */
class ImageToPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ImageToPdfUiState())
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    private var pendingFile: File? = null

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        val existing = _uiState.value.images.map { it.uri }.toSet()
        val newUris = uris.filterNot { it in existing }
        if (newUris.isEmpty()) return

        _uiState.update { state ->
            state.copy(images = state.images + newUris.map { uri ->
                ImageItem(uri = uri, displayName = SafFileUtils.displayName(context, uri))
            })
        }

        newUris.forEach { uri ->
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { loadThumbnail(uri) }
                _uiState.update { state ->
                    state.copy(images = state.images.map { item ->
                        if (item.uri == uri) {
                            result.fold(
                                onSuccess = { item.copy(thumbnail = it, error = null) },
                                onFailure = { item.copy(error = "Couldn't read this image.") }
                            )
                        } else item
                    })
                }
            }
        }
    }

    private fun loadThumbnail(uri: Uri): Result<Bitmap> {
        val context = getApplication<Application>()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(input, null, options)
            }?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Unable to decode $uri"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { it.copy(images = it.images.filterNot { img -> img.uri == uri }) }
    }

    fun moveImage(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = index + delta
            if (target !in state.images.indices || index !in state.images.indices) return@update state
            val mutable = state.images.toMutableList()
            val tmp = mutable[index]
            mutable[index] = mutable[target]
            mutable[target] = tmp
            state.copy(images = mutable)
        }
    }

    fun startCreate() {
        val validUris = _uiState.value.images.filter { it.error == null }.map { it.uri }
        if (validUris.isEmpty()) return

        _uiState.update { it.copy(isCreating = true, errorMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { buildPdf(validUris) }
            result.fold(
                onSuccess = { file ->
                    pendingFile = file
                    _uiState.update { it.copy(isCreating = false, readyToSave = true) }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            errorMessage = "Couldn't create the PDF. One of the images may be unreadable."
                        )
                    }
                }
            )
        }
    }

    /** Downsampled decode cap for the full-resolution embed — avoids OOM on very large photos
     * while still producing a perfectly usable page image (most phone cameras shoot well
     * above what a screen or printed page can resolve anyway). */
    private fun decodeForEmbedding(uri: Uri): Bitmap? {
        val context = getApplication<Application>()
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }
        var sample = 1
        val maxDimension = 2200
        while ((boundsOptions.outWidth / sample) > maxDimension || (boundsOptions.outHeight / sample) > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun buildPdf(uris: List<Uri>): Result<File> {
        val context = getApplication<Application>()
        val outputFile = File(context.cacheDir, "images_${UUID.randomUUID()}.pdf")
        return try {
            PDDocument().use { document ->
                uris.forEach { uri ->
                    val bitmap = decodeForEmbedding(uri) ?: throw IllegalStateException("Unable to decode $uri")
                    val pdImage = LosslessFactory.createFromImage(document, bitmap)
                    val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                    document.addPage(page)
                    PDPageContentStream(document, page).use { stream ->
                        stream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                    }
                    bitmap.recycle()
                }
                FileOutputStream(outputFile).use { out -> document.save(out) }
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    fun onSaveLocationChosen(destination: Uri?) {
        val tempFile = pendingFile
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
                pendingFile = null
                _uiState.update {
                    it.copy(readyToSave = false, savedResultUri = destination, savedFileName = fileName)
                }
            } else {
                _uiState.update {
                    it.copy(readyToSave = false, errorMessage = PdfErrorMessages.SAVE_FAILED_SINGLE)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
