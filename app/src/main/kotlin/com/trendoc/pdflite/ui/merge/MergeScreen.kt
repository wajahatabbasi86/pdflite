package com.trendoc.pdflite.ui.merge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.DragReorderState
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.common.ResultScreen
import com.trendoc.pdflite.ui.common.rememberDragReorderState

import com.trendoc.pdflite.util.SafFileUtils

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

    val validFiles = uiState.files.filter { it.error == null }
    val totalPages = validFiles.sumOf { it.pageCount }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Merge Documents")
                        Text(
                            "Combine multiple PDFs into a unified file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.files.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Manifest summary row, matching the design reference's sticky
                        // bottom-bar treatment — same file/page counts already shown
                        // above the queue, restated here next to the primary action.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Manifest",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${uiState.files.size} file${if (uiState.files.size == 1) "" else "s"} • $totalPages page${if (totalPages == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (uiState.isMerging) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            GradientButton(
                                text = if (validFiles.size >= 2) {
                                    "Merge ${validFiles.size} PDFs ($totalPages Pages)"
                                } else {
                                    "Merge"
                                },
                                onClick = { viewModel.startMerge() },
                                enabled = uiState.canMerge
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.errorMessage?.let { message ->
                ErrorCard(message = message, onRetry = { viewModel.clearError() })
            }

            if (uiState.files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Select at least 2 PDFs to merge", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { pickFilesLauncher.launch(arrayOf("application/pdf")) }) {
                            Text("Select PDFs")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Queue Order", style = MaterialTheme.typography.titleSmall)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "${uiState.files.size} Document${if (uiState.files.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.clearAll() }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }

                val density = LocalDensity.current
                val rowHeightPx = with(density) { 68.dp.toPx() }
                val dragState = rememberDragReorderState(
                    rowHeightPx = { rowHeightPx },
                    itemCount = { uiState.files.size },
                    onMove = { from, to -> viewModel.moveFile(from, to - from) }
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.files, key = { _, file -> file.uri }) { index, file ->
                        MergeFileRow(
                            item = file,
                            isDragging = dragState.draggingIndex == index,
                            dragOffsetPx = if (dragState.draggingIndex == index) dragState.dragOffsetPx else 0f,
                            onDragStart = { dragState.onDragStart(index) },
                            onDrag = { deltaY -> dragState.onDrag(deltaY) },
                            onDragEnd = { dragState.onDragEnd() },
                            onRemove = { viewModel.removeFile(file.uri) }
                        )
                    }
                    item {
                        AddMoreDocumentsCard(
                            onClick = { pickFilesLauncher.launch(arrayOf("application/pdf")) }
                        )
                    }
                }
            }
        }
    }
}

/** The bigger "Add More PDF Documents" card from the design reference — same SAF picker
 * action as before, just styled as its own card with a subtitle and info chips instead
 * of a plain dashed button. */
@Composable
private fun AddMoreDocumentsCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Add More PDF Documents",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Choose files from device storage, Downloads, or SD card",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SAF Native Picker", "Multi-select").forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeFileRow(
    item: MergeFileItem,
    isDragging: Boolean,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffsetPx }
            .zIndex(if (isDragging) 1f else 0f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x14191C1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount.y) },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
            )
            Box {
                when {
                    item.error != null -> Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    }
                    item.thumbnail != null -> Image(
                        bitmap = item.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    )
                    else -> Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                // Page-count badge overlaid on the corner of the thumbnail, matching the
                // design reference — only when we actually know the count (real data).
                if (item.error == null && item.pageCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(topStart = 6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "${item.pageCount}p",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    item.error ?: "${item.pageCount} page${if (item.pageCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
