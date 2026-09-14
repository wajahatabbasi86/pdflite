package com.easydoc.pdflite.ui.imagetopdf

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.ui.common.DashedAddButton
import com.easydoc.pdflite.ui.common.ErrorCard
import com.easydoc.pdflite.ui.common.GradientButton
import com.easydoc.pdflite.ui.common.ResultScreen
import com.easydoc.pdflite.util.SafFileUtils

/** Image(s) -> PDF screen, per docs/REQUIREMENTS.md §6.1. Same reorderable-list shape as
 * Merge, since both are "pick several, order matters, produce one PDF." */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    onDone: () -> Unit,
    viewModel: ImageToPdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openMultipleDocuments
    ) { uris -> viewModel.onImagesPicked(uris) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createDocument
    ) { destination -> viewModel.onSaveLocationChosen(destination) }

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

    val validCount = uiState.images.count { it.error == null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Image(s) → PDF")
                        if (uiState.images.isNotEmpty()) {
                            Text(
                                "${uiState.images.size} image${if (uiState.images.size == 1) "" else "s"} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.errorMessage?.let { message ->
                ErrorCard(message = message, onRetry = { viewModel.clearError() })
            }

            if (uiState.images.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { pickImagesLauncher.launch(arrayOf("image/*")) }) {
                        Text("Select Images")
                    }
                }
            } else {
                DashedAddButton(
                    text = "Add More Images",
                    onClick = { pickImagesLauncher.launch(arrayOf("image/*")) }
                )

                Text(
                    "PAGE ORDER (${uiState.images.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.images, key = { it.uri }) { image ->
                        val index = uiState.images.indexOf(image)
                        ImageRow(
                            item = image,
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.images.lastIndex,
                            onMoveUp = { viewModel.moveImage(index, -1) },
                            onMoveDown = { viewModel.moveImage(index, 1) },
                            onRemove = { viewModel.removeImage(image.uri) }
                        )
                    }
                }

                if (uiState.isCreating) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    GradientButton(
                        text = if (validCount > 0) "Create PDF ($validCount image${if (validCount == 1) "" else "s"})" else "Create PDF",
                        onClick = { viewModel.startCreate() },
                        enabled = uiState.canCreate
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageRow(
    item: ImageItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                item.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
