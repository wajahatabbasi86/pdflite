package com.easydoc.pdflite.ui.split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                    Text("Select PDF")
                }
            } else {
                Text(uiState.fileName ?: "")
            }

            uiState.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(message)
                        Button(onClick = { viewModel.clearError() }) { Text("Try Again") }
                    }
                }
            }

            if (uiState.isLoadingFile) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }

            if (uiState.pages.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.mode == SplitMode.EXTRACT_SELECTED,
                        onClick = { viewModel.setMode(SplitMode.EXTRACT_SELECTED) },
                        label = { Text("Extract selected pages") }
                    )
                    FilterChip(
                        selected = uiState.mode == SplitMode.SPLIT_BY_RANGES,
                        onClick = { viewModel.setMode(SplitMode.SPLIT_BY_RANGES) },
                        label = { Text("Split into ranges") }
                    )
                }

                if (uiState.mode == SplitMode.SPLIT_BY_RANGES) {
                    OutlinedTextField(
                        value = uiState.rangeInput,
                        onValueChange = { viewModel.onRangeInputChanged(it) },
                        label = { Text("Page ranges, e.g. 1-3, 5, 7-9") },
                        isError = uiState.rangeError != null,
                        supportingText = {
                            uiState.rangeError?.let { Text(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                .clickable(enabled = uiState.mode == SplitMode.EXTRACT_SELECTED) {
                                    viewModel.togglePage(page.index)
                                }
                                .border(
                                    width = if (page.isSelected) 2.dp else 0.5.dp,
                                    color = if (page.isSelected) Color(0xFF1D9E75) else Color.Gray
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
                    Button(
                        onClick = {
                            if (uiState.mode == SplitMode.EXTRACT_SELECTED) {
                                viewModel.startExtract()
                            } else {
                                viewModel.startSplit()
                            }
                        },
                        enabled = if (uiState.mode == SplitMode.EXTRACT_SELECTED) uiState.canExtract else uiState.canSplit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.mode == SplitMode.EXTRACT_SELECTED) "Extract" else "Split")
                    }
                }
            }
        }
    }
}
