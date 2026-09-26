package com.trendoc.pdflite.ui.stamp

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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A page's size in PDF points, used to lay out placeholders and to convert taps. */
data class StampPageSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

/**
 * One piece of text the user has placed on a page.
 *
 * [xPt]/[yPt] are PDF user-space points with the origin at the page's bottom-left, the same
 * space [PDPageContentStream] draws in — so placement survives any rendering scale, and the
 * save path needs no further conversion. [yPt] is the text *baseline*.
 */
data class TextStamp(
    val id: String,
    val pageIndex: Int,
    val xPt: Float,
    val yPt: Float,
    val text: String,
    val fontSizePt: Float = DEFAULT_FONT_SIZE_PT
)

data class StampUiState(
    val fileName: String? = null,
    val isLoading: Boolean = false,
    val pageSizes: List<StampPageSize> = emptyList(),
    val stamps: List<TextStamp> = emptyList(),
    val selectedStampId: String? = null,
    val errorMessage: String? = null,
    val sourceUri: Uri? = null,
    val isProcessing: Boolean = false,
    val readyToSave: Boolean = false,
    val savedResultUri: Uri? = null,
    val savedFileName: String? = null,
    val defaultSaveName: String = "filled.pdf"
) {
    val pageCount: Int get() = pageSizes.size
    val selectedStamp: TextStamp? get() = stamps.firstOrNull { it.id == selectedStampId }
}

/**
 * "Add Text" — types text onto *any* PDF, including a flat scan.
 *
 * Fill Forms can only touch documents that carry AcroForm fields, which most real-world
 * claim, application and consent forms do not: they are distributed as scanned images, so
 * there is nothing to fill. This draws directly into the page content stream instead, which
 * works regardless of whether the document has form fields, and produces a result every
 * viewer renders identically.
 *
 * Pages render on demand behind a heap-derived cache, the same approach View PDF uses — a
 * long scanned document would otherwise exhaust memory before the first page appeared.
 */
class StampTextViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StampUiState())
    val uiState: StateFlow<StampUiState> = _uiState.asStateFlow()
    private val recentsRepository = RecentsRepository(application)
    private var pendingOutput: File? = null

    private val _pageCache = mutableStateMapOf<Int, Bitmap>()
    val pageCache: Map<Int, Bitmap> get() = _pageCache
    private val accessOrder = ArrayDeque<Int>()
    private val inFlight = mutableSetOf<Int>()

    private val maxCachedPages: Int = run {
        val budgetBytes = Runtime.getRuntime().maxMemory() / 8
        val worstCasePageBytes = MAX_RENDER_WIDTH.toLong() * MAX_RENDER_HEIGHT * 4
        (budgetBytes / worstCasePageBytes).toInt().coerceIn(3, 12)
    }

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        val context = getApplication<Application>()
        clearPageCache()
        _uiState.update {
            it.copy(
                isLoading = true, errorMessage = null, pageSizes = emptyList(),
                stamps = emptyList(), selectedStampId = null, sourceUri = uri
            )
        }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            val result = withContext(Dispatchers.IO) { readPageSizes(uri) }
            result.fold(
                onSuccess = { sizes ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            fileName = fileName,
                            pageSizes = sizes,
                            defaultSaveName = defaultNameFor(fileName)
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = PdfErrorMessages.forOpenFailure(error))
                    }
                }
            )
        }
    }

    private fun defaultNameFor(sourceName: String?): String {
        val base = sourceName?.substringBeforeLast(".pdf", sourceName) ?: "document"
        return "${base}_filled.pdf"
    }

    private fun readPageSizes(uri: Uri): Result<List<StampPageSize>> {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri)
                ?: return Result.failure(IllegalStateException("Unable to open file descriptor"))
            renderer = PdfRenderer(pfd)
            Result.success(
                (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { StampPageSize(it.width, it.height) }
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

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
            clearPageCache()
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * A single page re-rendered at higher resolution, for the zoomed-in editing view.
     *
     * The normal cache renders at display size; magnifying that 3x just enlarges its pixels,
     * and a 66 DPI scan is already soft enough without adding interpolation blur on top.
     * Only one of these is ever held — the page currently being worked on.
     */
    private val _zoomRender = MutableStateFlow<ZoomRender?>(null)
    val zoomRender: StateFlow<ZoomRender?> = _zoomRender.asStateFlow()
    private var zoomJobPage: Int? = null

    fun requestZoomRender(pageIndex: Int, targetLongestSidePx: Int) {
        val current = _zoomRender.value
        if (current?.pageIndex == pageIndex && current.longestSidePx >= targetLongestSidePx) return
        if (zoomJobPage == pageIndex) return
        val uri = _uiState.value.sourceUri ?: return

        zoomJobPage = pageIndex
        viewModelScope.launch {
            val capped = targetLongestSidePx.coerceIn(MAX_RENDER_WIDTH, MAX_ZOOM_RENDER_PX)
            val bitmap = withContext(Dispatchers.IO) { renderAt(uri, pageIndex, capped) }
            zoomJobPage = null
            if (bitmap != null && _uiState.value.sourceUri == uri) {
                _zoomRender.value = ZoomRender(pageIndex, capped, bitmap)
            }
        }
    }

    fun clearZoomRender() {
        _zoomRender.value = null
    }

    private fun renderAt(uri: Uri, pageIndex: Int, targetLongestSidePx: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = SafFileUtils.openFileDescriptor(getApplication(), uri) ?: return null
            renderer = PdfRenderer(pfd)
            if (pageIndex !in 0 until renderer.pageCount) return null
            renderer.openPage(pageIndex).use { page ->
                val longest = maxOf(page.width, page.height).coerceAtLeast(1)
                val factor = targetLongestSidePx.toFloat() / longest
                val bitmap = Bitmap.createBitmap(
                    (page.width * factor).toInt().coerceAtLeast(1),
                    (page.height * factor).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    private fun touch(index: Int) {
        accessOrder.remove(index)
        accessOrder.addLast(index)
    }

    private fun trimToBudget() {
        while (accessOrder.size > maxCachedPages) {
            _pageCache.remove(accessOrder.removeFirst())
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

    // ---------------------------------------------------------------- editing

    /** Places a new, empty text box with its baseline at ([xPt], [yPt]) and selects it so
     * the editor opens straight away — a tap that produced an invisible empty box the user
     * then had to find and tap again would be a dead end. */
    fun addStamp(pageIndex: Int, xPt: Float, yPt: Float) {
        val stamp = TextStamp(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            xPt = xPt,
            // Lift the baseline slightly above the tap. On a printed form the user aims at
            // the ruled line they're filling in, and a baseline exactly on it draws the text
            // with the rule struck through its middle instead of sitting on top of it.
            yPt = yPt + DEFAULT_FONT_SIZE_PT * BASELINE_LIFT_FRACTION,
            text = ""
        )
        _uiState.update { it.copy(stamps = it.stamps + stamp, selectedStampId = stamp.id) }
    }

    fun selectStamp(id: String?) {
        _uiState.update { it.copy(selectedStampId = id) }
    }

    fun setStampText(id: String, text: String) {
        _uiState.update { state ->
            state.copy(stamps = state.stamps.map { if (it.id == id) it.copy(text = text) else it })
        }
    }

    /** Nudges a stamp by a delta already converted to PDF points. Positive [dyPt] moves it
     * up the page, matching PDF's bottom-left origin. */
    fun moveStamp(id: String, dxPt: Float, dyPt: Float) {
        _uiState.update { state ->
            state.copy(
                stamps = state.stamps.map { stamp ->
                    if (stamp.id != id) return@map stamp
                    val size = state.pageSizes.getOrNull(stamp.pageIndex)
                    val maxX = (size?.width ?: 612).toFloat()
                    val maxY = (size?.height ?: 792).toFloat()
                    stamp.copy(
                        // Clamped to the page: a stamp dragged off the edge would be saved
                        // into the file but invisible in every viewer.
                        xPt = (stamp.xPt + dxPt).coerceIn(0f, maxX),
                        yPt = (stamp.yPt + dyPt).coerceIn(0f, maxY)
                    )
                }
            )
        }
    }

    fun setFontSize(id: String, sizePt: Float) {
        val clamped = sizePt.coerceIn(MIN_FONT_SIZE_PT, MAX_FONT_SIZE_PT)
        _uiState.update { state ->
            state.copy(stamps = state.stamps.map { if (it.id == id) it.copy(fontSizePt = clamped) else it })
        }
    }

    fun removeStamp(id: String) {
        _uiState.update { state ->
            state.copy(
                stamps = state.stamps.filterNot { it.id == id },
                selectedStampId = if (state.selectedStampId == id) null else state.selectedStampId
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ---------------------------------------------------------------- saving

    fun startSave() {
        val uri = _uiState.value.sourceUri ?: return
        // Empty boxes are just abandoned taps — writing them would be a no-op that still
        // rewrote the file.
        val stamps = _uiState.value.stamps.filter { it.text.isNotBlank() }
        if (stamps.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Add some text to the page first.") }
            return
        }
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { writeStamps(uri, stamps) }
            result.fold(
                onSuccess = { file ->
                    pendingOutput = file
                    _uiState.update { it.copy(isProcessing = false, readyToSave = true) }
                },
                onFailure = { error ->
                    android.util.Log.w("StampText", "Writing stamps failed", error)
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = PdfErrorMessages.WRITE_FAILED)
                    }
                }
            )
        }
    }

    private fun writeStamps(uri: Uri, stamps: List<TextStamp>): Result<File> {
        val context = getApplication<Application>()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    stamps.groupBy { it.pageIndex }.forEach { (pageIndex, pageStamps) ->
                        if (pageIndex !in 0 until document.numberOfPages) return@forEach
                        val page = document.getPage(pageIndex)
                        // APPEND, not OVERWRITE — the existing page (the scan itself) has to
                        // survive; we are adding on top of it. resetContext=true so whatever
                        // graphics state the page left behind can't bleed into our text.
                        PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND, true, true
                        ).use { stream ->
                            pageStamps.forEach { stamp ->
                                stream.beginText()
                                stream.setFont(PDType1Font.HELVETICA, stamp.fontSizePt)
                                stream.newLineAtOffset(stamp.xPt, stamp.yPt)
                                // Standard 14 fonts are WinAnsi; a character outside that set
                                // (an em dash pasted in, non-Latin script) makes showText
                                // throw and would lose the whole save.
                                stream.showText(stamp.text.toWinAnsiSafe())
                                stream.endText()
                            }
                        }
                    }
                    val outputFile = File(context.cacheDir, "stamped_${UUID.randomUUID()}.pdf")
                    java.io.FileOutputStream(outputFile).use { out -> document.save(out) }
                    Result.success(outputFile)
                }
            } ?: Result.failure(IllegalStateException("Unable to open $uri"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun onSaveLocationChosen(destination: Uri?) {
        val tempFile = pendingOutput
        if (destination == null || tempFile == null) {
            _uiState.update { it.copy(readyToSave = false) }
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
                val pageCount = _uiState.value.pageCount
                pendingOutput = null
                _uiState.update {
                    it.copy(readyToSave = false, savedResultUri = destination, savedFileName = fileName)
                }
                recentsRepository.record(
                    uri = destination,
                    displayName = fileName,
                    sizeBytes = SafFileUtils.fileSize(context, destination),
                    pageCount = pageCount,
                    sourceLabel = "Add Text"
                )
            } else {
                _uiState.update {
                    it.copy(readyToSave = false, errorMessage = PdfErrorMessages.SAVE_FAILED_SINGLE)
                }
            }
        }
    }
}

/** Replaces anything the Standard 14 WinAnsi encoding can't represent, so one stray
 * character can't fail the entire save. Curly quotes and dashes map to their ASCII
 * equivalents rather than being dropped, since those arrive routinely from keyboards. */
private fun String.toWinAnsiSafe(): String {
    val mapped = this
        .replace('‘', '\'').replace('’', '\'')
        .replace('“', '"').replace('”', '"')
        .replace('–', '-').replace('—', '-')
        .replace('…', '.')
    return buildString(mapped.length) {
        mapped.forEach { c ->
            // Printable WinAnsi range; everything else (emoji, non-Latin scripts) becomes
            // '?' so the user can see it didn't round-trip instead of silently losing it.
            append(if (c.code in 32..255) c else '?')
        }
    }
}

/** One page re-rendered larger, for the zoomed editing view. */
data class ZoomRender(val pageIndex: Int, val longestSidePx: Int, val bitmap: Bitmap)

/** Ceiling on the zoom render. A single ARGB_8888 bitmap at 3000px on its long edge is
 * ~27 MB — enough detail to position text on a form line, without risking an OOM. */
private const val MAX_ZOOM_RENDER_PX = 3000

private const val MAX_RENDER_WIDTH = 1100
private const val MAX_RENDER_HEIGHT = 1550

const val DEFAULT_FONT_SIZE_PT = 11f

/** How far above the tap the initial baseline sits, as a fraction of the font size. Roughly
 * the descender depth, so text rests on a ruled line rather than through it. */
private const val BASELINE_LIFT_FRACTION = 0.25f
const val MIN_FONT_SIZE_PT = 6f
const val MAX_FONT_SIZE_PT = 36f
