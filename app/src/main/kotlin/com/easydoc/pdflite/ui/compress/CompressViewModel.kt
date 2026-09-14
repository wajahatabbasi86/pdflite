package com.easydoc.pdflite.ui.compress

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.util.PdfErrorMessages
import com.easydoc.pdflite.util.SafFileUtils
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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

enum class CompressionLevel(val label: String, val jpegQuality: Float, val downscale: Float) {
    LOW("Low", 0.85f, 1.0f),
    MEDIUM("Medium", 0.65f, 0.85f),
    HIGH("High", 0.4f, 0.6f)
}

data class CompressUiState(
    val fileName: String? = null,
    val isLoadingFile: Boolean = false,
    val originalSizeBytes: Long = -1,
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val isCompressing: Boolean = false,
    val errorMessage: String? = null,
    val readyToSave: Boolean = false,
    val defaultSaveName: String = "compressed.pdf",
    val savedResultUri: Uri? = null,
    val savedFileName: String? = null,
    val compressedSizeBytes: Long = -1
) {
    val canCompress: Boolean get() = fileName != null && !isCompressing
}

/**
 * Compress PDF, per docs/REQUIREMENTS.md §5: re-encode embedded images at a chosen quality/
 * downsample level via PdfBox-Android. Never fabricates a reduction — if the source has no
 * compressible images (e.g. text-only), the result is honestly reported even if the size
 * barely changes.
 */
class CompressViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    private var sourceUri: Uri? = null
    private var pendingFile: File? = null

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        sourceUri = uri
        val context = getApplication<Application>()
        _uiState.update { it.copy(isLoadingFile = true, errorMessage = null) }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            val size = SafFileUtils.fileSize(context, uri)
            val validated = withContext(Dispatchers.IO) { validateOpens(uri) }
            validated.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoadingFile = false,
                            fileName = fileName,
                            originalSizeBytes = size,
                            defaultSaveName = defaultNameFor(fileName)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoadingFile = false, errorMessage = PdfErrorMessages.forOpenFailure(error))
                    }
                }
            )
        }
    }

    private fun defaultNameFor(sourceName: String?): String {
        val base = sourceName?.substringBeforeLast(".pdf", sourceName) ?: "document"
        return "compressed_$base.pdf"
    }

    private fun validateOpens(uri: Uri): Result<Unit> {
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { }
            } ?: return Result.failure(IllegalStateException("Unable to open $uri"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setLevel(level: CompressionLevel) = _uiState.update { it.copy(level = level) }

    fun startCompress() {
        val uri = sourceUri ?: return
        val level = _uiState.value.level
        _uiState.update { it.copy(isCompressing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { compress(uri, level) }
            result.fold(
                onSuccess = { file ->
                    pendingFile = file
                    _uiState.update {
                        it.copy(isCompressing = false, readyToSave = true, compressedSizeBytes = file.length())
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isCompressing = false, errorMessage = PdfErrorMessages.forOpenFailure(error))
                    }
                }
            )
        }
    }

    private fun compress(uri: Uri, level: CompressionLevel): Result<File> {
        val context = getApplication<Application>()
        val outputFile = File(context.cacheDir, "compress_${UUID.randomUUID()}.pdf")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    document.pages.forEach { page ->
                        val resources = page.resources ?: return@forEach
                        // PDResources has no public "replace at this name" API (only add()`,
                        // which allocates a *new* name — useless here, since the page's content
                        // stream already references the old name). The XObject sub-dictionary
                        // is reachable from the resources COS dictionary, so replace the entry
                        // there directly; the name stays the same, so every "/Im1 Do" operator
                        // in the content stream keeps working unchanged.
                        val xObjectDict = resources.cosObject
                            .getDictionaryObject(COSName.XOBJECT) as? COSDictionary
                            ?: return@forEach
                        for (name in resources.xObjectNames.toList()) {
                            val xObject = resources.getXObject(name)
                            if (xObject is PDImageXObject) {
                                val recompressed = recompressImage(document, xObject, level)
                                if (recompressed != null) {
                                    xObjectDict.setItem(name, recompressed.cosObject)
                                }
                            }
                        }
                    }
                    FileOutputStream(outputFile).use { out -> document.save(out) }
                }
            } ?: return Result.failure(IllegalStateException("Unable to open $uri"))
            Result.success(outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    /** Re-encodes one embedded image at the chosen quality/downscale. Returns null (leave the
     * original in place) if re-encoding fails for this particular image, rather than failing
     * the whole compress — one odd image shouldn't sink an otherwise-fine document. */
    private fun recompressImage(
        document: PDDocument,
        original: PDImageXObject,
        level: CompressionLevel
    ): PDImageXObject? {
        return try {
            val bitmap = original.image ?: return null
            val targetWidth = (bitmap.width * level.downscale).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * level.downscale).toInt().coerceAtLeast(1)
            val scaled = if (level.downscale < 1.0f) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }
            JPEGFactory.createFromImage(document, scaled, level.jpegQuality)
        } catch (e: Exception) {
            null
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
