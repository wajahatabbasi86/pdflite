package com.trendoc.pdflite.ui.fillforms

import androidx.compose.ui.res.pluralStringResource
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.trendoc.pdflite.ui.fillforms.MULTI_VALUE_SEPARATOR
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.common.ResultScreen
import com.trendoc.pdflite.ui.common.SharpZoomLayer
import com.trendoc.pdflite.ui.common.ZoomPanBox
import com.trendoc.pdflite.ui.common.rememberZoomPanState
import com.trendoc.pdflite.util.SafFileUtils

/**
 * Fill Existing PDF Forms, per docs/REQUIREMENTS.md §10. Renders each page that has fields
 * (via [FillFormsViewModel]) and overlays a native input at each field's own on-page
 * position — a text box, a checkbox, a radio dot, or a dropdown, matching the field type
 * PdfBox-Android reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillFormsScreen(
    onDone: () -> Unit,
    onAddText: (android.net.Uri) -> Unit = {},
    initialUri: android.net.Uri? = null,
    viewModel: FillFormsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pre-loaded when reached via View PDF's "Fill Forms" quick-action bridge.
    LaunchedEffect(initialUri) {
        if (initialUri != null) viewModel.onDocumentPicked(initialUri)
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createDocument
    ) { uri -> viewModel.onSaveLocationChosen(uri) }

    LaunchedEffect(uiState.readyToSave) {
        if (uiState.readyToSave) saveFileLauncher.launch(uiState.defaultSaveName)
    }

    val savedUri = uiState.savedResultUri
    if (savedUri != null) {
        ResultScreen(
            fileName = uiState.savedFileName ?: uiState.defaultSaveName,
            resultUri = savedUri,
            onDone = onDone
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                title = { Text(stringResource(R.string.fill_forms_fill_pdf_form)) }
            )
        },
        bottomBar = {
            if (!uiState.hasNoFields && uiState.fileName != null && !uiState.isLoadingFile) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isProcessing) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            GradientButton(
                                text = stringResource(R.string.fill_forms_save),
                                onClick = { viewModel.startSave() },
                                enabled = uiState.canSave
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.errorMessage?.let { message ->
                ErrorCard(message = message, onRetry = { viewModel.clearError() })
            }

            when {
                uiState.isLoadingFile -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.fileName == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                            Text(stringResource(R.string.common_select_pdf))
                        }
                    }
                }
                uiState.hasNoFields -> {
                    // Stating the problem and stopping there is a dead end — and it is the
                    // common case, since scanned claim and application forms carry no
                    // AcroForm fields at all. Hand the same document straight to Add Text,
                    // which types onto the page instead of into fields.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                stringResource(R.string.fill_forms_no_fields),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                stringResource(R.string.fill_forms_no_fields_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            uiState.sourceUri?.let { uri ->
                                Button(onClick = { onAddText(uri) }) { Text(stringResource(R.string.fill_forms_add_text_instead)) }
                            }
                        }
                    }
                }
                else -> {
                    FillFormsStatusStrip(fieldCount = uiState.widgets.size, pageCount = uiState.pages.size)
                    Text(
                        uiState.fileName ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.pages, key = { it.pageIndex }) { page ->
                            // Composed only near the viewport, so in lazy mode only those pages
                            // are ever rendered; a no-op for a page already in hand.
                            LaunchedEffect(page.pageIndex) { viewModel.requestPage(page.pageIndex) }
                            FormPageView(
                                sourceUri = uiState.sourceUri,
                                page = page,
                                bitmap = viewModel.pageBitmaps[page.pageIndex],
                                widgets = uiState.widgets.filter { it.pageIndex == page.pageIndex },
                                fieldValues = uiState.fieldValues,
                                onTextChange = viewModel::setTextValue,
                                onCheckboxToggle = viewModel::toggleCheckbox,
                                onRadioSelect = viewModel::selectRadio,
                                onChoiceSelect = viewModel::selectChoice,
                                onChoiceToggle = viewModel::toggleChoice
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "100% Offline" status pill plus the number of form fields found, echoing the same
 * offline-first framing used on Home/View PDF — purely presentational. */
@Composable
private fun FillFormsStatusStrip(fieldCount: Int, pageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.common_offline_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            pluralStringResource(R.plurals.fill_forms_field_count, fieldCount, fieldCount) + " · " + pluralStringResource(R.plurals.page_count, pageCount, pageCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun FormPageView(
    sourceUri: android.net.Uri?,
    page: FormPage,
    /** Null while the page renders (lazy mode) — layout and fields don't wait for it. */
    bitmap: android.graphics.Bitmap?,
    widgets: List<FormWidget>,
    fieldValues: Map<String, String>,
    onTextChange: (String, String) -> Unit,
    onCheckboxToggle: (String, String) -> Unit,
    onRadioSelect: (String, String) -> Unit,
    onChoiceSelect: (String, String) -> Unit,
    onChoiceToggle: (String, String) -> Unit
) {
    val density = LocalDensity.current

    // The page is laid out at whatever width the parent actually allows — NOT at the
    // bitmap's own pixel width. Sizing the container to bitmap.width.toDp() (411dp for a
    // 1080px render on a 2.625-density screen) overflowed the 16dp screen padding, so the
    // Image scaled itself down to fit and centred vertically inside the taller container,
    // while the widget overlays kept using unscaled, top-anchored coordinates. Every
    // overlay therefore sat ~54px too high and drifted further out the further down the
    // page it was. Deriving the scale from the real width keeps bitmap and overlays in one
    // coordinate space.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val displayWidthDp = maxWidth
        val displayWidthPx = with(density) { displayWidthDp.toPx() }
        // Points -> displayed pixels straight from the page's own size, so layout and every
        // field position are the same whether or not the page image is in memory yet.
        val pointsToPx = displayWidthPx / page.widthPt.coerceAtLeast(1f)
        val displayHeightPx = page.heightPt * pointsToPx
        val displayHeightDp = with(density) { displayHeightPx.toDp() }

        val zoomState = rememberZoomPanState(maxScale = 6f)
        ZoomPanBox(
            state = zoomState,
            modifier = Modifier.width(displayWidthDp).height(displayHeightDp)
        ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // The container is already the page's aspect ratio, so this only guards
                // against a rounding difference reintroducing letterboxing.
                contentScale = ContentScale.FillBounds
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
        }
        if (sourceUri != null) {
            SharpZoomLayer(
                state = zoomState,
                uri = sourceUri,
                pageIndex = page.pageIndex,
                pageWidthPt = page.widthPt,
                contentWidthPx = displayWidthPx,
                contentHeightPx = displayHeightPx
            )
        }
        widgets.forEach { widget ->
            // PDF rects are bottom-left-origin points; flip to the bitmap's top-left-origin
            // pixel space, then to dp, using this specific page's own render scale.
            val leftPx = widget.rect.left * pointsToPx
            val topPx = (page.heightPt - widget.rect.top) * pointsToPx
            val widthPx = widget.rect.width * pointsToPx
            val heightPx = widget.rect.height * pointsToPx
            val leftDp = with(density) { leftPx.toDp() }
            val topDp = with(density) { topPx.toDp() }
            val widthDp = with(density) { widthPx.toDp() }
            val heightDp = with(density) { heightPx.toDp() }

            Box(modifier = Modifier.offset(x = leftDp, y = topDp)) {
                when (widget.kind) {
                    FieldKind.TEXT -> TextFieldOverlay(
                        label = widget.label,
                        value = fieldValues[widget.groupId] ?: "",
                        width = widthDp,
                        height = heightDp,
                        readOnly = widget.isReadOnly,
                        password = widget.isPassword,
                        multiline = widget.isMultiline,
                        maxLength = widget.maxLength,
                        onValueChange = { onTextChange(widget.groupId, it) }
                    )
                    FieldKind.CHECKBOX -> CheckboxOverlay(
                        label = widget.label,
                        checked = fieldValues[widget.groupId] == widget.onValue,
                        // The PDF's own box size. The 48dp tap area is separate (FieldTouchTarget), so
                        // the drawing no longer needs a 20dp floor — which grew it over its label.
                        size = minOf(widthDp, heightDp).coerceAtLeast(10.dp),
                        enabled = !widget.isReadOnly,
                        onClick = { onCheckboxToggle(widget.groupId, widget.onValue ?: "Yes") }
                    )
                    FieldKind.RADIO -> RadioOverlay(
                        // Option first, then its group: "Female, Gender".
                        label = listOfNotNull(widget.onValue, widget.label).joinToString(", "),
                        selected = fieldValues[widget.groupId] == widget.onValue,
                        // The PDF's own box size. The 48dp tap area is separate (FieldTouchTarget), so
                        // the drawing no longer needs a 20dp floor — which grew it over its label.
                        size = minOf(widthDp, heightDp).coerceAtLeast(10.dp),
                        enabled = !widget.isReadOnly,
                        onClick = { onRadioSelect(widget.groupId, widget.onValue ?: return@RadioOverlay) }
                    )
                    FieldKind.CHOICE -> ChoiceOverlay(
                        label = widget.label,
                        value = fieldValues[widget.groupId] ?: "",
                        options = widget.choiceOptions,
                        width = maxOf(widthDp, 80.dp),
                        height = maxOf(heightDp, 28.dp),
                        multiSelect = widget.isMultiSelect,
                        enabled = !widget.isReadOnly,
                        onSelect = {
                            // Multi-select toggles one option in/out; single-select replaces.
                            if (widget.isMultiSelect) onChoiceToggle(widget.groupId, it)
                            else onChoiceSelect(widget.groupId, it)
                        }
                    )
                    FieldKind.SIGNATURE -> SignatureOverlay(
                        label = widget.label,
                        width = maxOf(widthDp, 40.dp),
                        height = maxOf(heightDp, 20.dp)
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun TextFieldOverlay(
    label: String,
    value: String,
    width: Dp,
    height: Dp,
    readOnly: Boolean,
    password: Boolean,
    multiline: Boolean,
    maxLength: Int?,
    onValueChange: (String) -> Unit
) {
    // A read-only field is drawn in muted grey rather than the editable accent tint, so it
    // reads as "issued, not yours to change" before the user even taps it.
    val accent =
        if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    BasicTextField(
        value = value,
        onValueChange = { new ->
            // /MaxLen is a hard limit in the PDF spec; a viewer that lets the user type past
            // it produces a value the field can't actually hold.
            if (maxLength == null || new.length <= maxLength) onValueChange(new)
        },
        readOnly = readOnly,
        singleLine = !multiline,
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .width(maxOf(width, 40.dp))
            .height(maxOf(height, 20.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.5f))
            .padding(horizontal = 2.dp)
            // Named from the PDF field itself; a bare BasicTextField is just "edit box".
            .semantics { contentDescription = label }
    )
}

/** A signature field can't be filled here — signing needs a certificate this app doesn't
 * handle — but it must still be visible, otherwise a form's signature box just isn't there
 * and the user has no idea the document expects one. */
@Composable
private fun SignatureOverlay(label: String, width: Dp, height: Dp) {
    val signatureDescription = stringResource(R.string.fill_forms_signature_description, label)
    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = signatureDescription }
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.fill_forms_signature_placeholder),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CheckboxOverlay(label: String, checked: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    FieldTouchTarget(
        size = size,
        modifier = Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(2.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * A touch target of at least 48dp centred on a form widget drawn at its PDF size.
 * Checkboxes on real forms are often 8–12pt — far below what a finger (or Play's pre-launch
 * accessibility check) needs. The drawn box keeps its exact size and position so it still
 * lines up with the page; only the tappable area grows, shifted back by half the growth.
 */
@Composable
private fun FieldTouchTarget(size: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    val target = maxOf(size, 48.dp)
    val shift = (target - size) / 2
    Box(
        modifier = Modifier
            .offset(x = -shift, y = -shift)
            .size(target)
            .then(modifier),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RadioOverlay(label: String, selected: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    FieldTouchTarget(
        size = size,
        modifier = Modifier
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(size * 0.5f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun ChoiceOverlay(
    label: String,
    value: String,
    options: List<String>,
    width: Dp,
    height: Dp,
    multiSelect: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // The UI state carries a multi-select field's selections as one separator-joined
    // string; split it back out so each option can show its own tick.
    val selected = remember(value) {
        value.split(MULTI_VALUE_SEPARATOR).filter { it.isNotEmpty() }
    }
    val accent =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val choiceDescription = stringResource(
        R.string.fill_forms_choice_description,
        label,
        selected.joinToString(", ").ifBlank { stringResource(R.string.fill_forms_nothing_selected) }
    )
    Box {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.5f))
                // "Country, Canada, drop down list": the field name, then the current value.
                // Set as one node — clickable() and the role otherwise land on separate
                // semantics nodes and TalkBack reads an unnamed list.
                .clearAndSetSemantics {
                    contentDescription = choiceDescription
                    role = Role.DropdownList
                    if (enabled) onClick { expanded = true; true } else disabled()
                }
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                selected.joinToString(", ").ifBlank { stringResource(R.string.fill_forms_select) },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isSelected = option in selected
                DropdownMenuItem(
                    text = { Text(if (multiSelect && isSelected) "✓ $option" else option) },
                    onClick = {
                        onSelect(option)
                        // A multi-select list stays open so several options can be ticked in
                        // one go; a single-select dropdown closes on the first pick.
                        if (!multiSelect) expanded = false
                    }
                )
            }
        }
    }
}
