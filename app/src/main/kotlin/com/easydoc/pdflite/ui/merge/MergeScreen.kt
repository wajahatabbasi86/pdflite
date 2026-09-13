package com.easydoc.pdflite.ui.merge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.ui.common.ResultScreen
import com.easydoc.pdflite.util.SafFileUtils

/**
 * Merge PDFs screen, per docs/REQUIREMENTS.md §3.
 *
 * Note on reordering: the scaffold has no drag-and-drop dependency yet, so reordering
 * uses explicit up/down controls per row instead of drag handles. Swap in a
 * drag-and-drop list (e.g. reorderable) later without touching MergeViewModel —
 * moveFile(index, delta) already models "move by one position" either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    onDone: () -> Unit,
    viewModel: MergeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openMultipleDocuments
    ) { uris ->
        viewModel.onFilesPicked(uris)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createDocument
    ) { destination ->
        viewModel.onSaveLocationChosen(destination)
    }

    // As soon as the merge finishes, prompt the user for a save location (§3.5).
    LaunchedEffect(uiState.readyToSave) {
        if (uiState.readyToSave) {
            saveLauncher.launch(uiState.defaultSaveName)
        }
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
        topBar = { TopAppBar(title = { Text("Merge PDFs") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { pickFilesLauncher.launch(arrayOf("application/pdf")) }) {
                Text(if (uiState.files.isEmpty()) "Select PDFs" else "Add more PDFs")
            }

            uiState.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(message)
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Try Again")
                        }
                    }
                }
            }

            if (uiState.files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select at least 2 PDFs to merge")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.files, key = { it.uri }) { file ->
                        val index = uiState.files.indexOf(file)
                        MergeFileRow(
                            item = file,
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.files.lastIndex,
                            onMoveUp = { viewModel.moveFile(index, -1) },
                            onMoveDown = { viewModel.moveFile(index, 1) },
                            onRemove = { viewModel.removeFile(file.uri) }
                        )
                    }
                }

                if (uiState.isMerging) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Button(
                        onClick = { viewModel.startMerge() },
                        enabled = uiState.canMerge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Merge")
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeFileRow(
    item: MergeFileItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.error != null) {
                Icon(imageVector = Icons.Filled.Warning, contentDescription = "Error")
            } else {
                item.thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName)
                Text(
                    item.error ?: "${item.pageCount} page(s)"
                )
            }

            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }
}
