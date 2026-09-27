package com.trendoc.pdflite.ui.fillforms

import com.trendoc.pdflite.util.PageBitmapCache
import com.trendoc.pdflite.R
import com.trendoc.pdflite.di.appContainer
import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.recents.RecentsRepository
import com.trendoc.pdflite.util.PdfErrorMessages
import com.trendoc.pdflite.util.SafFileUtils
import com.trendoc.pdflite.util.renderPageBitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Per docs/REQUIREMENTS.md §10: structured AcroForm fields only, not freeform text editing.
 * SIGNATURE is shown but never editable — see [FormWidget.isReadOnly]. */
enum class FieldKind { TEXT, CHECKBOX, RADIO, CHOICE, SIGNATURE }

/** The separator used to join a multi-select choice's values into the single string the
 * UI state carries, and to split them back apart when writing to the PDF. A literal
 * newline can't appear inside an AcroForm option string, so it can't collide with one. */
const val MULTI_VALUE_SEPARATOR = "\n"

/** A PDF page's field rectangle, in PDF user-space points (origin bottom-left, per the PDF
 * spec) — kept separate from the on-screen pixel position, which [FillFormsScreen] derives
 * from this plus each page's own render scale. */
data class PdfRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val width: Float get() = right - left
    val height: Float get() = top - bottom
}

/** One on-page, tappable widget. A checkbox or text field is exactly one widget per field;
 * a radio *group* is several widgets sharing one [groupId] (the field's fully qualified
 * name) — each widget is its own on-page circle at its own position, and [onValue] is the
 * specific export value *this* widget represents, so tapping it sets the whole group's
 * value to that widget's own on-state. */
data class FormWidget(
    val widgetKey: String,
    val groupId: String,
    val pageIndex: Int,
    val rect: PdfRect,
    val kind: FieldKind,
    val onValue: String? = null,
    val choiceOptions: List<String> = emptyList(),
    /** The field's own /Ff ReadOnly bit. A read-only field still renders — it's part of the
     * document the user is reading — but must not accept edits: it's typically a system
     * reference the issuer generated, and silently altering it produces a document that
     * disagrees with the issuer's records. */
    val isReadOnly: Boolean = false,
    /** /Ff Password bit. Masked on screen so a PIN or SSN isn't shoulder-surfable. */
    val isPassword: Boolean = false,
    /** /Ff Multiline bit — the field is a text *area*, so Enter must insert a newline
     * rather than being swallowed by a single-line editor. */
    val isMultiline: Boolean = false,
    /** /MaxLen, or null when the field sets no limit. */
    val maxLength: Int? = null,
    /** /Ff MultiSelect on a choice field: several options may be selected at once. */
    val isMultiSelect: Boolean = false,
    /** What a screen reader announces for this field; see [fieldLabel]. */
    val label: String = ""
)

/**
 * One page that carries form fields — its size in PDF points only. The rendered bitmap is held
 * separately in [FillFormsViewModel.pageBitmaps], so a page can be laid out, and its fields
 * placed and filled, before (or without) its image being in memory. Field rects are in the
 * same points, so layout depends on this geometry alone, never on a bitmap's pixel size.
 */
data class FormPage(val pageIndex: Int, val widthPt: Float, val heightPt: Float)

data class FillFormsUiState(
    val fileName: String? = null,
    val isLoadingFile: Boolean = false,
    val errorMessage: String? = null,
    val hasNoFields: Boolean = false,
    val pages: List<FormPage> = emptyList(),
    val widgets: List<FormWidget> = emptyList(),
    /** groupId -> current value. Text fields store their text; checkbox/radio store the
     * selected on-value (or "Off"); choice fields store the selected option string. */
    val fieldValues: Map<String, String> = emptyMap(),
    val isProcessing: Boolean = false,
    val readyToSave: Boolean = false,
    val defaultSaveName: String = "filled.pdf",
    val savedResultUri: Uri? = null,
    val savedFileName: String? = null,
    /** The document being filled. Exposed so the screen can hand it to Add Text when the
     * PDF turns out to have no fields — see [hasNoFields]. */
    val sourceUri: Uri? = null,
    /** Large file: pages render on demand as they scroll into view instead of all up front.
     * See [LAZY_THRESHOLD_BYTES]. */
    val isLazy: Boolean = false
) {
    val canSave: Boolean get() = widgets.isNotEmpty() && !isProcessing
}

/**
 * Fill Existing PDF Forms, per docs/REQUIREMENTS.md §10 — detects a picked PDF's AcroForm
 * (if any), renders each page containing fields via [PdfRenderer] (same approach as every
 * other tool's page previews), and overlays native Compose inputs positioned from each
 * field widget's own rectangle. Deliberately narrower than a general PDF editor: structured
 * form fields only, never arbitrary in-place text correction (see §10's own scope notes).
 */
class FillFormsViewModel @JvmOverloads constructor(
    application: Application,
    private val recentsRepository: RecentsRepository = application.appContainer.recents
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FillFormsUiState())
    val uiState: StateFlow<FillFormsUiState> = _uiState.asStateFlow()

    private var sourceUri: Uri? = null
    private var pendingOutput: File? = null

    /**
     * Rendered pages. For a small file every field page is rendered at load and held (the
     * original behaviour — instant scrolling, and such a file can't hold many pages); for a
     * large one only pages near the viewport, bounded by the heap. Replaced per document so
     * the budget matches the mode, and cleared whenever the work on a file ends.
     */
    private var cache = PageBitmapCache(maxPages = 1)
    val pageBitmaps: Map<Int, Bitmap> get() = cache.pages

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        sourceUri = uri
        // The previous file's pages go now, not when the new one finishes loading.
        releasePages()
        val context = getApplication<Application>()
        _uiState.update {
            FillFormsUiState(isLoadingFile = true, fileName = null, sourceUri = uri)
        }

        viewModelScope.launch {
            val fileName = SafFileUtils.displayName(context, uri)
            // Unknown size (a provider that doesn't report one) is treated as large: the safe
            // side, since the eager path is the one that can run out of memory.
            val sizeBytes = SafFileUtils.fileSize(context, uri)
            val lazy = sizeBytes < 0 || sizeBytes > LAZY_THRESHOLD_BYTES
            val result = withContext(Dispatchers.IO) { loadForm(uri, lazy) }
            if (sourceUri != uri) return@launch // another file was picked meanwhile
            result.fold(
                onSuccess = { (pages, widgets, values) ->
                    _uiState.update {
                        it.copy(
                            isLoadingFile = false,
                            fileName = fileName,
                            hasNoFields = widgets.isEmpty(),
                            pages = pages,
                            widgets = widgets,
                            fieldValues = values,
                            defaultSaveName = defaultNameFor(fileName),
                            isLazy = lazy
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoadingFile = false, errorMessage = getApplication<Application>().getString(PdfErrorMessages.forOpenFailure(error)))
                    }
                }
            )
        }
    }

    private fun defaultNameFor(sourceName: String?): String {
        val base = sourceName?.substringBeforeLast(".pdf", sourceName) ?: "document"
        return "${base}_filled.pdf"
    }

    /** Parses the AcroForm (if any) via PdfBox-Android, then renders — via Android's own
     * [PdfRenderer], separately, same as every other tool screen — only the pages that
     * actually contain a field, so a 40-page form with fields on 2 pages doesn't render 38
     * pages nobody needs to see. */
    private fun loadForm(uri: Uri, lazy: Boolean): Result<Triple<List<FormPage>, List<FormWidget>, Map<String, String>>> {
        val context = getApplication<Application>()
        return try {
            val widgets = mutableListOf<FormWidget>()
            val values = mutableMapOf<String, String>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    val acroForm = document.documentCatalog.acroForm
                    val pageList = document.pages.toList()
                    if (acroForm != null) {
                        for (field in acroForm.fieldTree) {
                            val fieldWidgets = field.widgets
                            if (fieldWidgets.isEmpty()) continue // non-terminal node in the field tree
                            val groupId = field.fullyQualifiedName ?: continue
                            val label = field.fieldLabel(fallback = getApplication<Application>().getString(R.string.fill_forms_unnamed_field))
                            // PDField.getValueAsString() is correct for every field type
                            // except PDChoice, where it stringifies the whole List<String>
                            // (producing e.g. "[USA]" instead of "USA") rather than the one
                            // selected value this single-select dropdown actually needs.
                            values[groupId] = if (field is PDChoice) {
                                // getValueAsString() stringifies the whole List<String>
                                // (producing e.g. "[Read, Write]"), so join the selections
                                // ourselves. Keeping *all* of them matters: a multi-select
                                // list box that kept only the first would silently drop the
                                // user's other choices when the document is saved.
                                field.value.joinToString(MULTI_VALUE_SEPARATOR)
                            } else {
                                field.valueAsString
                            }

                            when (field) {
                                is PDTextField -> {
                                    val widget = fieldWidgets.first()
                                    val pageIndex = pageList.indexOf(widget.page)
                                    if (pageIndex < 0) continue
                                    widgets += FormWidget(
                                        label = label,
                                        widgetKey = groupId,
                                        groupId = groupId,
                                        pageIndex = pageIndex,
                                        rect = widget.rectangle.toPdfRect(),
                                        kind = FieldKind.TEXT,
                                        isReadOnly = field.isReadOnly,
                                        // PDTextField exposes multiline/comb but not the
                                        // password bit, so read /Ff directly for that one.
                                        isPassword = field.hasFlag(FLAG_PASSWORD),
                                        isMultiline = field.isMultiline,
                                        maxLength = field.maxLen.takeIf { it > 0 }
                                    )
                                }
                                is PDCheckBox -> {
                                    val widget = fieldWidgets.first()
                                    val pageIndex = pageList.indexOf(widget.page)
                                    if (pageIndex < 0) continue
                                    widgets += FormWidget(
                                        label = label,
                                        widgetKey = groupId,
                                        groupId = groupId,
                                        pageIndex = pageIndex,
                                        rect = widget.rectangle.toPdfRect(),
                                        kind = FieldKind.CHECKBOX,
                                        onValue = field.onValue
                                    )
                                }
                                is PDRadioButton -> {
                                    fieldWidgets.forEachIndexed { i, widget ->
                                        val pageIndex = pageList.indexOf(widget.page)
                                        if (pageIndex < 0) return@forEachIndexed
                                        val onValue = widget.onValueOrNull() ?: return@forEachIndexed
                                        widgets += FormWidget(
                                        label = label,
                                            widgetKey = "$groupId#$i",
                                            groupId = groupId,
                                            pageIndex = pageIndex,
                                            rect = widget.rectangle.toPdfRect(),
                                            kind = FieldKind.RADIO,
                                            onValue = onValue
                                        )
                                    }
                                }
                                is PDChoice -> {
                                    val widget = fieldWidgets.first()
                                    val pageIndex = pageList.indexOf(widget.page)
                                    if (pageIndex < 0) continue
                                    widgets += FormWidget(
                                        label = label,
                                        widgetKey = groupId,
                                        groupId = groupId,
                                        pageIndex = pageIndex,
                                        rect = widget.rectangle.toPdfRect(),
                                        kind = FieldKind.CHOICE,
                                        choiceOptions = field.optionsDisplayValues,
                                        isReadOnly = field.isReadOnly,
                                        isMultiSelect = field.isMultiSelect
                                    )
                                }
                                is PDSignatureField -> {
                                    // Not fillable here — signing needs a certificate this
                                    // app doesn't handle. Previously these fell into the
                                    // else branch and vanished with no trace, so a form's
                                    // signature box simply wasn't there. Showing it as a
                                    // read-only placeholder at least tells the user the
                                    // field exists and has to be signed elsewhere.
                                    val widget = fieldWidgets.first()
                                    val pageIndex = pageList.indexOf(widget.page)
                                    if (pageIndex < 0) continue
                                    widgets += FormWidget(
                                        label = label,
                                        widgetKey = groupId,
                                        groupId = groupId,
                                        pageIndex = pageIndex,
                                        rect = widget.rectangle.toPdfRect(),
                                        kind = FieldKind.SIGNATURE,
                                        isReadOnly = true
                                    )
                                }
                                else -> { /* PDPushButton (a plain button, no value to fill) — ignored. */ }
                            }
                        }
                    }
                }
            } ?: return Result.failure(IllegalStateException("Unable to open $uri"))

            val pagesWithFields = widgets.map { it.pageIndex }.toSortedSet()
            val pages = if (pagesWithFields.isEmpty()) emptyList() else readGeometry(uri, pagesWithFields)
            if (lazy) {
                cache = PageBitmapCache(PageBitmapCache.budgetFor(RENDER_WIDTH_PX.toLong() * RENDER_WIDTH_PX * 3 / 2 * 2))
            } else {
                cache = PageBitmapCache(maxPages = pages.size.coerceAtLeast(1))
                renderAll(uri, pages)
            }
            Result.success(Triple(pages, widgets, values))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Each field page's size in points — cheap: pages are opened, never rendered. */
    private fun readGeometry(uri: Uri, pageIndices: Set<Int>): List<FormPage> {
        val pfd = SafFileUtils.openFileDescriptor(getApplication(), uri) ?: return emptyList()
        return pfd.use {
            PdfRenderer(it).use { renderer ->
                pageIndices.filter { index -> index in 0 until renderer.pageCount }.map { index ->
                    renderer.openPage(index).use { page ->
                        FormPage(index, page.width.toFloat(), page.height.toFloat())
                    }
                }
            }
        }
    }

    /**
     * Small file: every field page up front, as before. One renderer for the lot. An
     * OutOfMemoryError (a small file can still have huge pages) drops what was rendered and
     * leaves the rest to on-demand rendering rather than taking the process down.
     */
    private fun renderAll(uri: Uri, pages: List<FormPage>) {
        val pfd = SafFileUtils.openFileDescriptor(getApplication(), uri) ?: return
        try {
            pfd.use {
                PdfRenderer(it).use { renderer ->
                    for (page in pages) {
                        if (!cache.beginRender(page.pageIndex)) continue
                        cache.put(page.pageIndex, renderOne(renderer, page))
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            cache.clear()
        } catch (e: Exception) {
            // Pages that didn't render here are rendered on demand when shown.
        }
    }

    /**
     * Ensures [pageIndex] is rendered, off the main thread. Called from composition as a page
     * comes into view; an already-cached or in-flight page returns immediately. In eager mode
     * this only ever fills a page that failed at load.
     */
    fun requestPage(pageIndex: Int) {
        val uri = sourceUri ?: return
        val page = _uiState.value.pages.firstOrNull { it.pageIndex == pageIndex } ?: return
        val target = cache
        if (!target.beginRender(pageIndex)) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    SafFileUtils.openFileDescriptor(getApplication(), uri)?.use { pfd ->
                        PdfRenderer(pfd).use { renderer -> renderOne(renderer, page) }
                    }
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
            // Only into the cache it was requested for: a new file replaces the cache.
            if (bitmap != null && target === cache && sourceUri == uri) target.put(pageIndex, bitmap) else target.failed(pageIndex)
        }
    }

    /**
     * One page at [RENDER_WIDTH_PX] wide. Rendered at ARGB_8888 (the only config PdfRenderer
     * accepts) then kept as RGB_565, which halves what stays resident with no visible loss on
     * a white-background document.
     */
    private fun renderOne(renderer: PdfRenderer, page: FormPage): Bitmap =
        renderer.openPage(page.pageIndex).use { p ->
            val height = (page.heightPt * RENDER_WIDTH_PX / page.widthPt).toInt().coerceAtLeast(1)
            renderPageBitmap(page = p, width = RENDER_WIDTH_PX, height = height, halveMemory = true)
        }

    /** Lets go of every rendered page for the current file. */
    private fun releasePages() {
        cache.clear()
    }

    override fun onCleared() {
        super.onCleared()
        // Final teardown: the screen is gone, so the pixels can be freed immediately.
        cache.recycleAll()
        pendingOutput?.delete()
        pendingOutput = null
    }

    fun setTextValue(groupId: String, value: String) {
        _uiState.update { it.copy(fieldValues = it.fieldValues + (groupId to value)) }
    }

    /** Checkbox tap: toggles between this widget's own on-value and "Off". */
    fun toggleCheckbox(groupId: String, onValue: String) {
        _uiState.update { state ->
            val current = state.fieldValues[groupId]
            val next = if (current == onValue) "Off" else onValue
            state.copy(fieldValues = state.fieldValues + (groupId to next))
        }
    }

    /** Radio tap: selects this widget's on-value for the whole group. */
    fun selectRadio(groupId: String, onValue: String) {
        _uiState.update { it.copy(fieldValues = it.fieldValues + (groupId to onValue)) }
    }

    /** Single-select: replaces whatever was chosen. */
    fun selectChoice(groupId: String, value: String) {
        _uiState.update { it.copy(fieldValues = it.fieldValues + (groupId to value)) }
    }

    /** Multi-select list box: toggles one option in or out, leaving the others alone. */
    fun toggleChoice(groupId: String, value: String) {
        _uiState.update { state ->
            val current = state.fieldValues[groupId]
                ?.split(MULTI_VALUE_SEPARATOR)
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val next = if (value in current) current - value else current + value
            state.copy(
                fieldValues = state.fieldValues + (groupId to next.joinToString(MULTI_VALUE_SEPARATOR))
            )
        }
    }

    fun startSave() {
        val uri = sourceUri ?: return
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val values = _uiState.value.fieldValues
            val result = withContext(Dispatchers.IO) { writeValues(uri, values) }
            result.fold(
                onSuccess = { file ->
                    pendingOutput = file
                    _uiState.update { it.copy(isProcessing = false, readyToSave = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = getApplication<Application>().getString(PdfErrorMessages.WRITE_FAILED))
                    }
                }
            )
        }
    }

    private fun writeValues(uri: Uri, values: Map<String, String>): Result<File> {
        val context = getApplication<Application>()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document ->
                    val acroForm = document.documentCatalog.acroForm
                    if (acroForm != null) {
                        for (field in acroForm.fieldTree) {
                            if (field.widgets.isEmpty()) continue
                            val groupId = field.fullyQualifiedName ?: continue
                            val value = values[groupId] ?: continue
                            // A read-only field is never editable in the UI, so its value
                            // here is whatever the document already held — writing it back
                            // would be a no-op at best, and PDFBox rejects the write on some
                            // fields outright. Skip it.
                            if (field.isReadOnly) continue
                            // A signature field has no fillable value at all.
                            if (field is PDSignatureField) continue

                            if (field is PDChoice) {
                                val selected = value.split(MULTI_VALUE_SEPARATOR).filter { it.isNotEmpty() }
                                if (field.isMultiSelect) {
                                    // setValue(String) collapses a choice to one selection,
                                    // discarding the rest of a multi-select list box;
                                    // setValue(List) keeps them all.
                                    field.setValue(selected)
                                } else {
                                    // setValue(List) rejects ANY single-select choice —
                                    // PDChoice throws "The list box does not allow multiple
                                    // selections" even for a one-element list — so an
                                    // ordinary dropdown has to go through setValue(String).
                                    field.setValue(selected.firstOrNull() ?: "")
                                }
                            } else {
                                // PDField.setValue(String) is implemented correctly per field
                                // subtype (text content for PDTextField, on/off state for
                                // PDButton) — one call site for everything else.
                                field.setValue(value)
                            }
                        }

                        // Bake the filled values into the pages themselves.
                        //
                        // Without this the values live only in widget *annotations*. That is
                        // a perfectly valid PDF, but Android's PdfRenderer draws page content
                        // streams and ignores annotation appearance streams entirely — so
                        // TrenDoc's own View PDF shows a saved form as completely blank, and
                        // the user reasonably concludes their answers were lost. Plenty of
                        // other previewers (mail clients, thumbnailers, some printers) behave
                        // the same way.
                        //
                        // Flattening draws each field's appearance into its page and drops the
                        // interactive fields, so the result renders identically everywhere.
                        // The trade-off is that the saved copy is no longer re-fillable; the
                        // original document is untouched, so the user still has the blank form
                        // to fill again.
                        try {
                            acroForm.refreshAppearances()
                            acroForm.flatten()
                        } catch (e: Exception) {
                            // A field whose appearance PDFBox can't build (an exotic font, a
                            // malformed widget) would otherwise lose the whole save. Falling
                            // through leaves the values set as annotations — worse to view,
                            // but the data is still there and correct.
                            android.util.Log.w("FillForms", "Flattening failed; saving unflattened", e)
                        }
                    }
                    val outputFile = File(context.cacheDir, "filled_${UUID.randomUUID()}.pdf")
                    java.io.FileOutputStream(outputFile).use { out -> document.save(out) }
                    Result.success(outputFile)
                }
            } ?: Result.failure(IllegalStateException("Unable to open $uri"))
        } catch (e: Exception) {
            // The user only ever sees a one-line message, so without this the cause of a
            // failed save is unrecoverable — a PDFBox rejection of one field's value looks
            // identical to a corrupt document. Logged, not shown: the text below is what
            // the user reads.
            android.util.Log.w("FillForms", "Writing form values failed", e)
            Result.failure(e)
        }
    }

    fun onSaveLocationChosen(destination: Uri?) {
        val tempFile = pendingOutput
        if (destination == null || tempFile == null) {
            // User backed out of the SAF picker (or it reported no file). Reset readyToSave
            // so a later tap on "Save Filled PDF" can set it true -> false -> true again and
            // retrigger the LaunchedEffect(uiState.readyToSave) that launches the picker —
            // otherwise it would stay stuck at true and silently do nothing on retry.
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
                val pageCount = _uiState.value.pages.size
                pendingOutput = null
                _uiState.update {
                    it.copy(readyToSave = false, savedResultUri = destination, savedFileName = fileName)
                }
                // The work on this file is finished — only the result screen is shown now.
                releasePages()
                recentsRepository.record(
                    uri = destination,
                    displayName = fileName,
                    sizeBytes = SafFileUtils.fileSize(context, destination),
                    pageCount = pageCount,
                    sourceLabel = "Fill Forms"
                )
            } else {
                _uiState.update {
                    it.copy(readyToSave = false, errorMessage = getApplication<Application>().getString(PdfErrorMessages.SAVE_FAILED_SINGLE))
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

private fun com.tom_roush.pdfbox.pdmodel.common.PDRectangle.toPdfRect() =
    PdfRect(left = lowerLeftX, bottom = lowerLeftY, right = upperRightX, top = upperRightY)

/** A radio widget's own "on" export value isn't exposed by any public getter on
 * [PDRadioButton] (only the whole field's aggregate [com.tom_roush.pdfbox.pdmodel.interactive.form.PDButton.getOnValues]
 * is) — but the PDF spec puts it right in the widget's own normal-appearance dictionary as
 * the one state name that isn't "Off", which [com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry.getSubDictionary]
 * already exposes publicly. Mirrors the same kind of direct-but-public structural read the
 * Compress feature already relies on for the one thing PdfBox-Android's own higher-level API
 * doesn't cover. */
private fun com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget.onValueOrNull(): String? {
    val states = appearance?.normalAppearance?.subDictionary?.keys ?: return null
    return states.map { it.name }.firstOrNull { it != "Off" }
}

/** /Ff bit 14 (1-based), i.e. value 1 shl 13 — the text field Password flag. PDTextField
 * surfaces isMultiline/isComb but has no accessor for this one, so read the raw flags. */
private const val FLAG_PASSWORD = 1 shl 13

private fun PDTextField.hasFlag(flag: Int): Boolean =
    (cosObject.getInt("Ff", 0) and flag) != 0

/**
 * The name TalkBack reads for a form field. The /TU "alternate field name" exists in the PDF
 * spec for precisely this — a human-readable name for accessibility — so it wins when present.
 * Otherwise the field's own name, de-mangled: `patient_firstName` reads "patient first name".
 */
private fun com.tom_roush.pdfbox.pdmodel.interactive.form.PDField.fieldLabel(fallback: String): String {
    alternateFieldName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val raw = partialName ?: fullyQualifiedName ?: return fallback
    return raw
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace(Regex("[_.\\-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()
        .ifEmpty { fallback }
}

/** Above this size a form renders its pages on demand rather than all at load. */
const val LAZY_THRESHOLD_BYTES = 10L * 1024 * 1024

/** Render width for form pages; field positions scale from points to this. */
private const val RENDER_WIDTH_PX = 1080
