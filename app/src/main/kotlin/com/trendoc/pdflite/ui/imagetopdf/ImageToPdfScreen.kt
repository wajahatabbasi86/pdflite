package com.trendoc.pdflite.ui.imagetopdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.DashedAddButton
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.common.ResultScreen
import com.trendoc.pdflite.ui.common.rememberDragReorderState
import com.trendoc.pdflite.util.CameraCaptureUtils
import com.trendoc.pdflite.util.SafFileUtils

/** Image(s) -> PDF screen, per docs/REQUIREMENTS.md §6.1. Same reorderable-list shape as
 * Merge, since both are "pick several, order matters, produce one PDF." */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    onDone: () -> Unit,
    viewModel: ImageToPdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openMultipleDocuments
    ) { uris -> viewModel.onImagesPicked(uris) }

    // TakePicture needs the destination Uri decided *before* launch (unlike the gallery
    // pickers above, which hand one back) — held here so the result callback below can see
    // which Uri the camera actually wrote to. Must survive process death: launching the
    // system camera can background (and get killed by) this activity on a memory-constrained
    // device, and plain `remember` state would come back null when it's recreated, silently
    // dropping the photo even though the camera reports success.
    var pendingCaptureUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            viewModel.onImagesPicked(listOf(uri))
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = CameraCaptureUtils.newCaptureUri(context)
            pendingCaptureUri = uri
            takePhotoLauncher.launch(uri)
        }
    }
    val launchCamera: () -> Unit = {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = CameraCaptureUtils.newCaptureUri(context)
            pendingCaptureUri = uri
            takePhotoLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

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

    // Tapping an image row opens it full-screen to view/rotate/caption before the PDF is
    // built — the same file, re-decoded with whatever edits are applied, so what's shown
    // here is exactly what ends up in the PDF page.
    var editingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val editingItem = uiState.images.firstOrNull { it.uri == editingUri }
    if (editingItem != null) {
        ImageEditScreen(
            item = editingItem,
            onRotate = { viewModel.rotateImage(editingItem.uri) },
            onTextChanged = { text -> viewModel.setOverlayText(editingItem.uri, text) },
            onClose = { editingUri = null }
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
        },
        bottomBar = {
            if (uiState.images.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                "$validCount image${if (validCount == 1) "" else "s"} ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { pickImagesLauncher.launch(arrayOf("image/*")) }) {
                            Text("Select Images")
                        }
                        OutlinedButton(onClick = launchCamera) {
                            Text("Take Photo")
                        }
                    }
                }
            } else {
                ImageToPdfStatusStrip(imageCount = uiState.images.size)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DashedAddButton(
                        text = "Add More Images",
                        onClick = { pickImagesLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = launchCamera) {
                        Text("Camera")
                    }
                }

                Text(
                    "PAGE ORDER (${uiState.images.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val density = LocalDensity.current
                val rowHeightPx = with(density) { 68.dp.toPx() }
                val dragState = rememberDragReorderState(
                    rowHeightPx = { rowHeightPx },
                    itemCount = { uiState.images.size },
                    onMove = { from, to -> viewModel.moveImage(from, to - from) }
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.images, key = { _, image -> image.uri }) { index, image ->
                        ImageRow(
                            item = image,
                            isDragging = dragState.draggingIndex == index,
                            dragOffsetPx = if (dragState.draggingIndex == index) dragState.dragOffsetPx else 0f,
                            onDragStart = { dragState.onDragStart(index) },
                            onDrag = { deltaY -> dragState.onDrag(deltaY) },
                            onDragEnd = { dragState.onDragEnd() },
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.images.lastIndex,
                            onMoveUp = { viewModel.moveImage(index, -1) },
                            onMoveDown = { viewModel.moveImage(index, 1) },
                            onRemove = { viewModel.removeImage(image.uri) },
                            onView = { editingUri = image.uri }
                        )
                    }
                }
            }
        }
    }
}

/** "100% Offline" status pill plus the number of images queued, echoing the same
 * offline-first framing used on Home/View PDF — purely presentational. */
@Composable
private fun ImageToPdfStatusStrip(imageCount: Int) {
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
            "$imageCount image${if (imageCount == 1) "" else "s"} queued",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun ImageRow(
    item: ImageItem,
    isDragging: Boolean,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onView: () -> Unit
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
            modifier = Modifier.fillMaxWidth().padding(10.dp),
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
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                    contentDescription = "View and edit ${item.displayName}",
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onView)
                )
                else -> Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Column(
                modifier = Modifier.weight(1f).clickable(enabled = item.thumbnail != null, onClick = onView)
            ) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                item.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } ?: Text(
                    "Tap to view / edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Full-screen view/edit for one queued image — rotate (90° per tap, cumulative) and an
 * optional caption baked into the bottom-left corner, both applied to the same bitmap
 * that ends up in the final PDF page (see [ImageToPdfViewModel.applyEdits]). Not a general
 * photo editor: no crop/filters/free-form text placement — just the two edits actually
 * useful for turning a photo into a document page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageEditScreen(
    item: ImageItem,
    onRotate: () -> Unit,
    onTextChanged: (String) -> Unit,
    onClose: () -> Unit
) {
    var showTextField by remember(item.uri) { mutableStateOf(false) }
    var textDraft by remember(item.uri) { mutableStateOf(item.overlayText) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(item.displayName, maxLines = 1) },
                actions = {
                    IconButton(onClick = onRotate) {
                        Icon(Icons.Filled.RotateRight, contentDescription = "Rotate")
                    }
                    IconButton(onClick = { showTextField = !showTextField }) {
                        Icon(Icons.Filled.TextFields, contentDescription = "Add text")
                    }
                }
            )
        },
        bottomBar = {
            if (showTextField) {
                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textDraft,
                            onValueChange = { textDraft = it },
                            label = { Text("Caption") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            onTextChanged(textDraft)
                            showTextField = false
                        }) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            if (item.thumbnail != null) {
                Image(
                    bitmap = item.thumbnail.asImageBitmap(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
