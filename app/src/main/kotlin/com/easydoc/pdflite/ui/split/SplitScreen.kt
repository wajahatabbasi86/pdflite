package com.easydoc.pdflite.ui.split

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.ui.common.ErrorCard
import com.easydoc.pdflite.ui.common.FileIconAvatar
import com.easydoc.pdflite.ui.common.GradientButton
import com.easydoc.pdflite.ui.common.ResultScreen
import com.easydoc.pdflite.util.SafFileUtils

/**
 * Split / Extract Pages screen, per docs/REQUIREMENTS.md §4. Two modes toggled at the
 * top: extract checked pages into one PDF, or split by typed ranges into several.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    onDone: () -> Unit,
    viewModel: SplitViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createDocument
    ) { uri -> viewModel.onSaveLocationChosen(uri) }

    val saveDirLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openDocumentTree
    ) { uri -> viewModel.onSaveDirectoryChosen(uri) }

    // Extract mode -> single save dialog. Split mode -> directory picker (§4.4).
    LaunchedEffect(uiState.readyToSave, uiState.mode) {
        if (uiState.readyToSave) {
            if (uiState.mode == SplitMode.EXTRACT_SELECTED) {
                saveFileLauncher.launch(uiState.defaultSaveName)
            } else {
                saveDirLauncher.launch(null)
            }
        }
    }

    val savedSingle = uiState.savedResultUri
    if (savedSingle != null) {
        ResultScreen(
            fileName = uiState.savedFileName ?: uiState.defaultSaveName,
            resultUri = savedSingle,
            onDone = onDone
        )
        return
    }
    if (uiState.savedResultUris.isNotEmpty()) {
        // Split produced multiple files — the shared ResultScreen expects one Uri, so for
        // now show the first file with a summary; a dedicated multi-file result view is a
        // reasonable later refinement once this flow is validated with real users.
        ResultScreen(
            fileName = uiState.savedFileName ?: "Split files",
            resultUri = uiState.savedResultUris.first(),
            subtitle = "${uiState.savedResultUris.size} files saved",
            onDone = onDone
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Split / Extract Pages") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.fileName == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF")
                    }
                }
            } else {
            SourceDocumentCard(
                fileName = uiState.fileName ?: "",
                pageCount = uiState.pages.size,
                onChange = { pickFileLauncher.launch(arrayOf("application/pdf")) }
            )

            uiState.errorMessage?.let { message ->
                ErrorCard(message = message, onRetry = { viewModel.clearError() })
            }

            if (uiState.isLoadingFile) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }

            if (uiState.pages.isNotEmpty()) {
                Text(
                    "CHOOSE SPLIT MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SplitModeCard(
                    title = "Extract selected pages",
                    description = "Pick individual pages below to pull into one new PDF.",
                    selected = uiState.mode == SplitMode.EXTRACT_SELECTED,
                    onClick = { viewModel.setMode(SplitMode.EXTRACT_SELECTED) }
                )
                SplitModeCard(
                    title = "Split into ranges",
                    description = "Type ranges (e.g. 1-3, 5, 7-9); each range becomes its own file.",
                    selected = uiState.mode == SplitMode.SPLIT_BY_RANGES,
                    onClick = { viewModel.setMode(SplitMode.SPLIT_BY_RANGES) }
                ) {
                    if (uiState.mode == SplitMode.SPLIT_BY_RANGES) {
                        OutlinedTextField(
                            value = uiState.rangeInput,
                            onValueChange = { viewModel.onRangeInputChanged(it) },
                            label = { Text("e.g. 1-3, 5, 7-9") },
                            isError = uiState.rangeError != null,
                            supportingText = {
                                uiState.rangeError?.let { Text(it) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.pages, key = { it.index }) { page ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = uiState.mode == SplitMode.EXTRACT_SELECTED) {
                                    viewModel.togglePage(page.index)
                                }
                                .border(
                                    width = if (page.isSelected) 2.dp else 1.dp,
                                    color = if (page.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Column {
                                page.thumbnail?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .size(120.dp)
                                            .alpha(if (uiState.mode == SplitMode.EXTRACT_SELECTED) 1f else 0.6f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (uiState.mode == SplitMode.EXTRACT_SELECTED) {
                                        Checkbox(
                                            checked = page.isSelected,
                                            onCheckedChange = { viewModel.togglePage(page.index) }
                                        )
                                    }
                                    Text("Page ${page.index + 1}")
                                }
                            }
                        }
                    }
                }

                if (uiState.isProcessing) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val selectedCount = uiState.pages.count { it.isSelected }
                    GradientButton(
                        text = when (uiState.mode) {
                            SplitMode.EXTRACT_SELECTED -> "Extract $selectedCount Page${if (selectedCount == 1) "" else "s"}"
                            SplitMode.SPLIT_BY_RANGES -> "Split"
                        },
                        onClick = {
                            if (uiState.mode == SplitMode.EXTRACT_SELECTED) {
                                viewModel.startExtract()
                            } else {
                                viewModel.startSplit()
                            }
                        },
                        enabled = if (uiState.mode == SplitMode.EXTRACT_SELECTED) uiState.canExtract else uiState.canSplit
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SourceDocumentCard(fileName: String, pageCount: Int, onChange: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FileIconAvatar()
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "$pageCount page${if (pageCount == 1) "" else "s"} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
    }
}

@Composable
private fun SplitModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick)
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            extraContent?.let {
                Box(modifier = Modifier.padding(start = 40.dp)) { it() }
            }
        }
    }
}
