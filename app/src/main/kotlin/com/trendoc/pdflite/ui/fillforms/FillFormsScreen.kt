package com.trendoc.pdflite.ui.fillforms

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.common.ResultScreen
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
    viewModel: FillFormsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Fill PDF Form") }
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
                                text = "Save Filled PDF",
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
                            Text("Select PDF")
                        }
                    }
                }
                uiState.hasNoFields -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "This PDF doesn't have any fillable fields.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
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
                            FormPageView(
                                page = page,
                                widgets = uiState.widgets.filter { it.pageIndex == page.pageIndex },
                                fieldValues = uiState.fieldValues,
                                onTextChange = viewModel::setTextValue,
                                onCheckboxToggle = viewModel::toggleCheckbox,
                                onRadioSelect = viewModel::selectRadio,
                                onChoiceSelect = viewModel::selectChoice
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
            "100% Offline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            "$fieldCount field${if (fieldCount == 1) "" else "s"} · $pageCount page${if (pageCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun FormPageView(
    page: FormPage,
    widgets: List<FormWidget>,
    fieldValues: Map<String, String>,
    onTextChange: (String, String) -> Unit,
    onCheckboxToggle: (String, String) -> Unit,
    onRadioSelect: (String, String) -> Unit,
    onChoiceSelect: (String, String) -> Unit
) {
    val density = LocalDensity.current
    val bitmapWidthDp = with(density) { page.bitmap.width.toDp() }
    val bitmapHeightDp = with(density) { page.bitmap.height.toDp() }

    Box(modifier = Modifier.width(bitmapWidthDp).height(bitmapHeightDp)) {
        Image(
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        widgets.forEach { widget ->
            // PDF rects are bottom-left-origin points; flip to the bitmap's top-left-origin
            // pixel space, then to dp, using this specific page's own render scale.
            val leftPx = widget.rect.left * page.pxPerPoint
            val topPx = (page.heightPt - widget.rect.top) * page.pxPerPoint
            val widthPx = widget.rect.width * page.pxPerPoint
            val heightPx = widget.rect.height * page.pxPerPoint
            val leftDp = with(density) { leftPx.toDp() }
            val topDp = with(density) { topPx.toDp() }
            val widthDp = with(density) { widthPx.toDp() }
            val heightDp = with(density) { heightPx.toDp() }

            Box(modifier = Modifier.offset(x = leftDp, y = topDp)) {
                when (widget.kind) {
                    FieldKind.TEXT -> TextFieldOverlay(
                        value = fieldValues[widget.groupId] ?: "",
                        width = widthDp,
                        height = heightDp,
                        onValueChange = { onTextChange(widget.groupId, it) }
                    )
                    FieldKind.CHECKBOX -> CheckboxOverlay(
                        checked = fieldValues[widget.groupId] == widget.onValue,
                        size = maxOf(widthDp, heightDp, 20.dp),
                        onClick = { onCheckboxToggle(widget.groupId, widget.onValue ?: "Yes") }
                    )
                    FieldKind.RADIO -> RadioOverlay(
                        selected = fieldValues[widget.groupId] == widget.onValue,
                        size = maxOf(widthDp, heightDp, 20.dp),
                        onClick = { onRadioSelect(widget.groupId, widget.onValue ?: return@RadioOverlay) }
                    )
                    FieldKind.CHOICE -> ChoiceOverlay(
                        value = fieldValues[widget.groupId] ?: "",
                        options = widget.choiceOptions,
                        width = maxOf(widthDp, 80.dp),
                        height = maxOf(heightDp, 28.dp),
                        onSelect = { onChoiceSelect(widget.groupId, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TextFieldOverlay(value: String, width: Dp, height: Dp, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .width(maxOf(width, 40.dp))
            .height(maxOf(height, 20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            .padding(horizontal = 2.dp)
    )
}

@Composable
private fun CheckboxOverlay(checked: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RadioOverlay(selected: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(onClick = onClick),
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

@Composable
private fun ChoiceOverlay(value: String, options: List<String>, width: Dp, height: Dp, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                .clickable { expanded = true }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(value.ifBlank { "Select…" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
