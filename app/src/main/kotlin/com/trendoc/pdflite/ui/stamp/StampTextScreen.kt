package com.trendoc.pdflite.ui.stamp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.rememberZoomPanState
import com.trendoc.pdflite.ui.common.ZoomPanBox
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.common.ResultScreen
import com.trendoc.pdflite.util.SafFileUtils

/**
 * "Add Text" — types onto any PDF, including a flat scan with no form fields.
 *
 * Tap a page to drop a text box at that spot, type into it, drag it to fine-tune, and save.
 * The text is written into the page's own content stream, so the result looks the same in
 * every viewer rather than depending on annotation support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampTextScreen(
    onDone: () -> Unit,
    initialUri: Uri? = null,
    viewModel: StampTextViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoomRender by viewModel.zoomRender.collectAsState()
    // Which page is magnified, and the point on it the magnifier is centred on. Null = every
    // page shown whole. Typing on a form line is near-impossible at fit-to-width on a phone,
    // so tapping a spot zooms straight into it.
    var zoomFocus by remember { mutableStateOf<ZoomFocus?>(null) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createDocument
    ) { uri -> viewModel.onSaveLocationChosen(uri) }

    LaunchedEffect(initialUri) {
        if (initialUri != null) viewModel.onDocumentPicked(initialUri)
    }

    LaunchedEffect(uiState.readyToSave) {
        if (uiState.readyToSave) saveLauncher.launch(uiState.defaultSaveName)
    }

    val savedUri = uiState.savedResultUri
    if (savedUri != null) {
        ResultScreen(
            resultUri = savedUri,
            fileName = uiState.savedFileName ?: "filled.pdf",
            mimeType = "application/pdf",
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
                title = { Text(uiState.fileName ?: "Add Text") }
            )
        },
        bottomBar = {
            if (uiState.pageCount > 0) {
                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier.navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.selectedStamp?.let { selected ->
                            SelectedStampBar(
                                fontSizePt = selected.fontSizePt,
                                onSmaller = { viewModel.setFontSize(selected.id, selected.fontSizePt - 1f) },
                                onLarger = { viewModel.setFontSize(selected.id, selected.fontSizePt + 1f) },
                                onDelete = { viewModel.removeStamp(selected.id) }
                            )
                        }
                        GradientButton(
                            text = if (uiState.isProcessing) "Saving…" else "Save PDF",
                            onClick = { viewModel.startSave() },
                            enabled = !uiState.isProcessing
                        )
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
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.pageCount == 0 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Type onto any PDF — including scanned forms that have no fillable fields.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                            Text("Select PDF")
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (zoomFocus == null) "Tap anywhere on a page to add text."
                            else "Zoomed in — tap another spot to move, or fit the page.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (zoomFocus != null) {
                            TextButton(onClick = {
                                zoomFocus = null
                                viewModel.clearZoomRender()
                            }) { Text("Fit page") }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.pageCount, key = { it }) { index ->
                            StampPageView(
                                pageIndex = index,
                                pageSize = uiState.pageSizes[index],
                                bitmapProvider = {
                                    viewModel.requestPage(index)
                                    viewModel.pageCache[index]
                                },
                                zoomRender = zoomRender?.takeIf { it.pageIndex == index },
                                zoomFocus = zoomFocus?.takeIf { it.pageIndex == index },
                                onRequestZoom = { focus ->
                                    zoomFocus = focus
                                    viewModel.requestZoomRender(index, ZOOM_RENDER_TARGET_PX)
                                },
                                stamps = uiState.stamps.filter { it.pageIndex == index },
                                selectedStampId = uiState.selectedStampId,
                                onAddStamp = { xPt, yPt -> viewModel.addStamp(index, xPt, yPt) },
                                onSelect = viewModel::selectStamp,
                                onTextChange = viewModel::setStampText,
                                onDrag = viewModel::moveStamp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedStampBar(
    fontSizePt: Float,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Text size", style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onSmaller) {
            Icon(Icons.Filled.Remove, contentDescription = "Smaller text")
        }
        Text("${fontSizePt.toInt()}", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onLarger) {
            Icon(Icons.Filled.Add, contentDescription = "Larger text")
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete this text",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * One page plus its text boxes.
 *
 * The page is measured against the width actually available rather than its bitmap's pixel
 * width, and that scale is folded into every points-to-pixels conversion — so a tap, a drag
 * and the saved coordinate all refer to the same spot on the page. Sizing the container to
 * the bitmap's own width instead is what previously left Fill Forms' overlays drifting off
 * their fields.
 */
@Composable
private fun StampPageView(
    pageIndex: Int,
    pageSize: StampPageSize,
    bitmapProvider: () -> android.graphics.Bitmap?,
    zoomRender: ZoomRender?,
    zoomFocus: ZoomFocus?,
    onRequestZoom: (ZoomFocus) -> Unit,
    stamps: List<TextStamp>,
    selectedStampId: String?,
    onAddStamp: (Float, Float) -> Unit,
    onSelect: (String?) -> Unit,
    onTextChange: (String, String) -> Unit,
    onDrag: (String, Float, Float) -> Unit
) {
    val density = LocalDensity.current
    // The magnified render once it arrives; the fit-to-width one until then, so zooming is
    // instant and simply sharpens a moment later.
    val bitmap = zoomRender?.bitmap ?: bitmapProvider()
    val zoomState = rememberZoomPanState(maxScale = MAX_ZOOM_SCALE)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportWidthDp = maxWidth
        val viewportWidthPx = with(density) { viewportWidthDp.toPx() }
        // Points -> pixels at fit-to-width. Stamps are positioned with this and the whole
        // layer is then scaled, so page and text boxes magnify together and stay locked.
        val pointsToPx = viewportWidthPx / pageSize.width.coerceAtLeast(1)
        val pageHeightPx = pageSize.height * pointsToPx
        val pageHeightDp = with(density) { pageHeightPx.toDp() }

        // Tapping a spot jumps the viewport to it. Done as a state change on the shared
        // zoom/pan state rather than a second, parallel transform, so a pinch afterwards
        // continues from wherever the tap left off instead of fighting it.
        LaunchedEffect(zoomFocus, pointsToPx, pageHeightPx) {
            val focus = zoomFocus
            if (focus == null) {
                zoomState.reset()
            } else {
                zoomState.focusOn(
                    xPx = focus.xPt * pointsToPx,
                    yPx = (pageSize.height - focus.yPt) * pointsToPx,
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = pageHeightPx,
                    targetScale = TAP_ZOOM_SCALE
                )
            }
        }

        Card(
            modifier = Modifier.width(viewportWidthDp).height(pageHeightDp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ZoomPanBox(state = zoomState, modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageIndex, pointsToPx, pageSize) {
                            detectTapGestures { offset ->
                                // Content keeps its own untransformed space, so this
                                // conversion is the same zoomed in or out.
                                val xPt = offset.x / pointsToPx
                                val yPt = pageSize.height - (offset.y / pointsToPx)
                                onRequestZoom(ZoomFocus(pageIndex, xPt, yPt))
                                onAddStamp(xPt, yPt)
                            }
                        }
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }

                    stamps.forEach { stamp ->
                        val boxHeightPx = stamp.fontSizePt * pointsToPx * 1.35f
                        val leftDp = with(density) { (stamp.xPt * pointsToPx).toDp() }
                        val topDp = with(density) {
                            ((pageSize.height - stamp.yPt) * pointsToPx - boxHeightPx * 0.8f).toDp()
                        }
                        StampEditor(
                            stamp = stamp,
                            selected = stamp.id == selectedStampId,
                            pointsToPx = pointsToPx,
                            modifier = Modifier.offset(x = leftDp, y = topDp),
                            onSelect = { onSelect(stamp.id) },
                            onTextChange = { onTextChange(stamp.id, it) },
                            onDrag = { dxPx, dyPx ->
                                // Divide out the zoom too: a finger moving 30px across a 3x
                                // view has moved only 10px of page.
                                onDrag(
                                    stamp.id,
                                    dxPx / pointsToPx / zoomState.scale,
                                    -dyPx / pointsToPx / zoomState.scale
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StampEditor(
    stamp: TextStamp,
    selected: Boolean,
    pointsToPx: Float,
    modifier: Modifier,
    onSelect: () -> Unit,
    onTextChange: (String) -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    // The on-screen glyph size has to track the PDF point size, or what the user positions
    // is not what gets written.
    val fontSizeSp = with(density) { (stamp.fontSizePt * pointsToPx).toSp() }
    val accent = MaterialTheme.colorScheme.primary

    // A tap that dropped an empty, unfocused box would be a dead end: the box is small and
    // blank, so the user has to hunt for it and tap again before they can type. Focusing it
    // (and raising the keyboard) the moment it becomes selected makes tap-then-type work as
    // one gesture.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.pointerInput(stamp.id, pointsToPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                } else Modifier
            )
            .background(
                if (selected) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) accent.copy(alpha = 0.7f) else androidx.compose.ui.graphics.Color.Transparent
            )
    ) {
        BasicTextField(
            value = stamp.text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = fontSizeSp
            ),
            modifier = Modifier
                .widthIn(min = 40.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onSelect() }
        )
    }
}

/** The page and point the magnifier is centred on. */
data class ZoomFocus(val pageIndex: Int, val xPt: Float, val yPt: Float)

/** How far a tap magnifies. 3x turns a form's ruled line from roughly two pixels tall into
 * something a fingertip can actually be positioned against. */
private const val TAP_ZOOM_SCALE = 3f

/** Ceiling for pinch-zoom on a page. */
private const val MAX_ZOOM_SCALE = 6f

/** Long edge to re-render a zoomed page at, so magnification adds detail rather than blur. */
const val ZOOM_RENDER_TARGET_PX = 2400
