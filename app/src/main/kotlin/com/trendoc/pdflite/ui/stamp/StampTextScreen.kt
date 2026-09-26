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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import com.trendoc.pdflite.util.RuledLineFinder
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
                                onNudge = { dxPt, dyPt -> viewModel.moveStamp(selected.id, dxPt, dyPt) },
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
    onNudge: (Float, Float) -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Dragging is fine for getting close, but a fingertip covers several points of page
        // at fit-to-width and hides the very line the text is being aligned to. These move
        // it a fixed, predictable step with nothing under the finger.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Position", style = MaterialTheme.typography.labelMedium)
            Box(Modifier.weight(1f))
            IconButton(onClick = { onNudge(-NUDGE_STEP_PT, 0f) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move left")
            }
            // Positive y is up the page, matching PDF's bottom-left origin.
            IconButton(onClick = { onNudge(0f, NUDGE_STEP_PT) }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = { onNudge(0f, -NUDGE_STEP_PT) }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = { onNudge(NUDGE_STEP_PT, 0f) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move right")
            }
        }
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
                                val xPt = offset.x / pointsToPx
                                val rawYPt = pageSize.height - (offset.y / pointsToPx)
                                // Aim for the printed rule the user was pointing at, so the
                                // text sits on the line instead of needing nudging every
                                // time. Falls back to the tap itself in open space.
                                val baselineYPt = snapBaselineToRule(
                                    bitmap = bitmap,
                                    tapXContentPx = offset.x,
                                    tapYContentPx = offset.y,
                                    contentWidthPx = viewportWidthPx,
                                    pageHeightPt = pageSize.height.toFloat(),
                                    pointsToPx = pointsToPx
                                ) ?: (rawYPt + DEFAULT_FONT_SIZE_PT * BASELINE_LIFT_FRACTION)
                                if (!zoomState.isZoomed) {
                                    onRequestZoom(ZoomFocus(pageIndex, xPt, baselineYPt))
                                }
                                onAddStamp(xPt, baselineYPt)
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
                            zoomScale = zoomState.scale,
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
    zoomScale: Float,
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

    // BasicTextField fills the width it is offered, so an empty box silently claimed the
    // rest of the page and swallowed taps meant for the line below it — tapping a clear
    // spot then created nothing. Sizing to roughly the text's own width keeps the touch
    // target where the text actually is. Helvetica averages about half an em per character.
    val charCount = stamp.text.length.coerceAtLeast(MIN_FIELD_CHARS)
    val fieldWidthDp = with(density) {
        (charCount * stamp.fontSizePt * 0.55f * pointsToPx).toDp()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Dragging the text itself would fight the text field's own cursor placement, so
        // moving is done from a dedicated grip that appears once the box is selected.
        if (selected) {
            // The handle sits inside the zoom layer, so it would be drawn 3x larger at 3x
            // zoom and cover the very line being aimed at. Dividing by the zoom keeps it a
            // constant size on screen whatever the magnification.
            val handleSize = DRAG_HANDLE_SIZE / zoomScale.coerceAtLeast(0.1f)
            Box(
                modifier = Modifier
                    .size(handleSize)
                    .background(accent, CircleShape)
                    .pointerInput(stamp.id, pointsToPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = "Drag to move this text",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(handleSize * 0.7f)
                )
            }
        }
        Box(
            modifier = Modifier
                .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) accent.copy(alpha = 0.7f) else Color.Transparent
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
                    .width(fieldWidthDp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onSelect() }
            )
        }
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

/** How far one arrow tap moves the text, in PDF points. Two points is under a millimetre —
 * fine enough to settle onto a ruled line, coarse enough that crossing a field takes taps
 * rather than dozens. */
private const val NUDGE_STEP_PT = 2f

/** Minimum width of a text box, in characters — an empty one still has to be tappable. */
private const val MIN_FIELD_CHARS = 6

/** Size of the grip shown beside a selected text box for dragging it by hand. */
private val DRAG_HANDLE_SIZE = 28.dp

/**
 * Resolves where a tap's text baseline should sit, snapping onto a printed rule when the tap
 * was near one.
 *
 * Works in the rendered bitmap's own pixels, which may be the magnified render or the
 * fit-to-width one, so the tap is scaled into that space first. Returns null when the page
 * has not rendered yet or the tap was not close to a rule, leaving the caller to place the
 * text exactly where it was tapped.
 */
private fun snapBaselineToRule(
    bitmap: android.graphics.Bitmap?,
    tapXContentPx: Float,
    tapYContentPx: Float,
    contentWidthPx: Float,
    pageHeightPt: Float,
    pointsToPx: Float
): Float? {
    if (bitmap == null || contentWidthPx <= 0f) return null
    val contentToBitmap = bitmap.width / contentWidthPx
    val pointsToBitmapPx = pointsToPx * contentToBitmap
    if (pointsToBitmapPx <= 0f) return null

    val lineTopBitmapPx = RuledLineFinder.findLineTop(
        bitmap = bitmap,
        xPx = tapXContentPx * contentToBitmap,
        yPx = tapYContentPx * contentToBitmap,
        // Only snap to a rule the tap was plausibly aimed at; beyond this the user meant
        // the blank space they touched.
        // Asymmetric: a rule well below the finger is still probably the target, while one
        // above it is more likely a box border or the previous row's line.
        searchAbovePx = (SNAP_SEARCH_ABOVE_PT * pointsToBitmapPx).toInt().coerceAtLeast(1),
        searchBelowPx = (SNAP_SEARCH_BELOW_PT * pointsToBitmapPx).toInt().coerceAtLeast(2),
        sampleHalfWidthPx = (SNAP_SAMPLE_HALF_WIDTH_PT * pointsToBitmapPx).toInt().coerceAtLeast(4)
    ) ?: return null

    // Bitmap y grows downward from the page top; PDF y grows upward from the bottom.
    val lineTopPt = pageHeightPt - (lineTopBitmapPx / pointsToBitmapPx)
    // Sit the baseline just above the rule rather than exactly on it, so descenders clear it.
    return lineTopPt + DEFAULT_FONT_SIZE_PT * BASELINE_LIFT_FRACTION
}

/** How far above the tap to look for a rule. Kept short — a rule above the finger is more
 * often a box border than the line being filled in. */
private const val SNAP_SEARCH_ABOVE_PT = 3f

/** How far below the tap to look. Generous, since the line being written on sits under the
 * text and therefore under the finger. */
private const val SNAP_SEARCH_BELOW_PT = 11f

/** How wide a span either side of the tap must be dark for a row to count as a rule. */
private const val SNAP_SAMPLE_HALF_WIDTH_PT = 28f
