package com.trendoc.pdflite.ui.view

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.util.SafFileUtils
import kotlin.math.roundToInt

/**
 * View PDF, per the Home layout's sixth entry — a read-only viewer. Reachable both from
 * Home's own "Select PDF" flow and, pre-loaded, when TrenDoc is opened via the system
 * "Open with" chooser for a PDF (see [com.trendoc.pdflite.nav.PendingPdfIntent]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewPdfScreen(
    initialUri: android.net.Uri?,
    onDone: () -> Unit,
    onExtractPage: (android.net.Uri) -> Unit = {},
    onCompress: (android.net.Uri) -> Unit = {},
    viewModel: ViewPdfViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Many scanned/photographed pages are landscape-oriented, so this screen follows the
    // device's physical rotation regardless of the system-wide rotation-lock setting —
    // restored to whatever the rest of the app uses as soon as the user leaves. Every other
    // screen is unaffected.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    // Pre-loaded when opened from outside the app (system "Open with").
    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            viewModel.onDocumentPicked(initialUri)
        }
    }

    // Tapping a page opens it full-screen in PageDetailScreen (below), where pinch-zoom
    // and panning work like a photo viewer, with swipe between pages while zoomed out.
    var selectedPageIndex by remember { mutableStateOf<Int?>(null) }
    val openedPageIndex = selectedPageIndex
    if (openedPageIndex != null && uiState.pages.isNotEmpty()) {
        PageDetailScreen(
            pages = uiState.pages,
            initialIndex = openedPageIndex,
            onClose = { selectedPageIndex = null }
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
                title = { Text(uiState.fileName ?: "View PDF") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.pages.isNotEmpty()) {
                EngineStatusStrip(pageCount = uiState.pages.size)
                uiState.sourceUri?.let { uri ->
                    QuickActionRow(
                        onExtractPage = { onExtractPage(uri) },
                        onCompress = { onCompress(uri) },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                ErrorCard(message = message, onRetry = { viewModel.clearError() })
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.pages, key = { _, page -> System.identityHashCode(page) }) { index, page ->
                        Box {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPageIndex = index },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x14191C1E)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Image(
                                    bitmap = page.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "p. ${(index + 1).toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${uiState.pages.size} page${if (uiState.pages.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HardwareIsolationCard()
            }
        }
    }
}

/** Cosmetic status card reiterating that rendering happens locally — no new capability,
 * just a more prominent restatement of what [EngineStatusStrip] already says. */
@Composable
private fun HardwareIsolationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Hardware Isolated Processing", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Rendered entirely on-device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/** Quick-action bridge row — "Extract Page" and "Compress" hand the currently loaded file
 * straight to Split/Compress pre-loaded (via [PendingSplitUri]/[PendingCompressUri]),
 * matching the design reference's quick-tool bridges. "Share" reuses the same intent the
 * old top-bar icon used. */
@Composable
private fun QuickActionRow(onExtractPage: () -> Unit, onCompress: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionChip(text = "Extract Page", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = onExtractPage)
        QuickActionChip(text = "Compress", icon = Icons.Filled.Compress, onClick = onCompress)
        QuickActionChip(text = "Share", icon = Icons.Filled.Share, onClick = onShare)
    }
}

@Composable
private fun QuickActionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** The slim "100% Offline · Local Engine" status pill shown above the page list, echoing
 * the same offline-first framing used on Home — purely presentational, no new capability. */
@Composable
private fun EngineStatusStrip(pageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "100% Offline · Local Engine",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            "$pageCount page${if (pageCount == 1) "" else "s"} rendered",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * Full-screen page viewer, opened by tapping a page in [ViewPdfScreen]'s list — a photo
 * -viewer-style experience: pinch-zoom and free panning on the current page, swipe left
 * /right to move between pages while not zoomed in, and a presentation-mode toggle that
 * hides all chrome for a distraction-free, edge-to-edge slideshow view.
 *
 * Left/right navigation is handled by [ZoomableFullPage] itself rather than a
 * [androidx.compose.foundation.pager.HorizontalPager]: a pager's own swipe-gesture
 * detector runs alongside a page's pan gesture and competes for the same single-finger
 * drag, which is what silently broke panning in earlier attempts at this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageDetailScreen(pages: List<Bitmap>, initialIndex: Int, onClose: () -> Unit) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    var presentationMode by remember { mutableStateOf(false) }
    var showPageNavigator by remember { mutableStateOf(false) }

    // Hoisted (rather than internal to ZoomableFullPage) so the floating zoom +/- buttons
    // can drive the same state the pinch gesture does. remember(currentIndex) recreates
    // fresh state objects on page change — the same "reset zoom per page" behavior the
    // old key(currentIndex) wrapper gave, without needing to key the whole subtree.
    val scaleState = remember(currentIndex) { mutableStateOf(1f) }
    val offsetState = remember(currentIndex) { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            if (!presentationMode) {
                TopAppBar(
                    title = { Text("${currentIndex + 1} / ${pages.size}") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentIndex-- }, enabled = currentIndex > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                        }
                        IconButton(onClick = { currentIndex++ }, enabled = currentIndex < pages.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                        }
                        IconButton(onClick = { presentationMode = true }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Presentation mode")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!presentationMode) {
                PageNavigatorBar(
                    currentIndex = currentIndex,
                    pageCount = pages.size,
                    expanded = showPageNavigator,
                    onToggleExpanded = { showPageNavigator = !showPageNavigator },
                    onJumpTo = { index -> currentIndex = index.coerceIn(0, pages.lastIndex) },
                    thumbnailFor = { index -> pages[index] }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            ZoomableFullPage(
                bitmap = pages[currentIndex].asImageBitmap(),
                scaleState = scaleState,
                offsetState = offsetState,
                modifier = Modifier.fillMaxSize().padding(if (presentationMode) PaddingValues(0.dp) else innerPadding),
                onSwipeNext = { if (currentIndex < pages.lastIndex) currentIndex++ },
                onSwipePrevious = { if (currentIndex > 0) currentIndex-- },
                onTap = { if (presentationMode) presentationMode = false }
            )
            if (!presentationMode) {
                ZoomControlPanel(
                    scale = scaleState.value,
                    onZoomIn = { scaleState.value = (scaleState.value + 0.5f).coerceIn(1f, 5f) },
                    onZoomOut = {
                        val newScale = (scaleState.value - 0.5f).coerceIn(1f, 5f)
                        scaleState.value = newScale
                        if (newScale <= 1f) offsetState.value = Offset.Zero
                    },
                    onReset = { scaleState.value = 1f; offsetState.value = Offset.Zero },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp)
                )
            }
            if (presentationMode) {
                IconButton(
                    onClick = { presentationMode = false },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Exit presentation mode",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Floating +/-/reset zoom control, matching the design reference's zoom pill. Drives the
 * same [scaleState]/[offsetState] the pinch gesture writes to, via the [onZoomIn]/
 * [onZoomOut]/[onReset] callbacks the caller wires to those same state objects. */
@Composable
private fun ZoomControlPanel(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onZoomIn, enabled = scale < 5f) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            Text(
                "${(scale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onZoomOut, enabled = scale > 1f) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Filled.FitScreen, contentDescription = "Reset zoom")
            }
        }
    }
}

/** The bottom page-navigator bar — a compact "N / total" row that expands into a scrubber
 * slider plus a horizontal thumbnail filmstrip when tapped, matching the design
 * reference's page navigator. Thumbnails reuse the already-rendered page bitmaps (no
 * separate lower-res thumbnail pass) since [pages] is a small in-memory list already. */
@Composable
private fun PageNavigatorBar(
    currentIndex: Int,
    pageCount: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onJumpTo: (Int) -> Unit,
    thumbnailFor: (Int) -> Bitmap
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Page Navigator", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${currentIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { onJumpTo(it.roundToInt()) },
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                    steps = (pageCount - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pageCount) { index ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (index == currentIndex) 2.dp else 1.dp,
                                    color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onJumpTo(index) }
                        ) {
                            Image(
                                bitmap = thumbnailFor(index).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pinch-to-zoom and free up/down/left/right panning for a single full-screen page, plus
 * swipe-left/right page navigation when not zoomed in — all decided by one gesture
 * handler so nothing else (a pager, a scrollable) can claim the same touch first.
 *
 * At scale 1x, a single-finger drag doesn't pan (there's nothing to pan yet); it's
 * tracked instead as a candidate page swipe, committed via [onSwipeNext]/[onSwipePrevious]
 * once released past a distance threshold. Once zoomed in (scale > 1f), or with a second
 * finger down (pinch), the same drag pans/zooms the page instead.
 */
@Composable
private fun ZoomableFullPage(
    bitmap: ImageBitmap,
    scaleState: androidx.compose.runtime.MutableState<Float>,
    offsetState: androidx.compose.runtime.MutableState<Offset>,
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrevious: () -> Unit = {},
    onTap: () -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        fun clamp(o: Offset, scale: Float): Offset {
            val bx = (widthPx * (scale - 1f) / 2f).coerceAtLeast(0f)
            val by = (heightPx * (scale - 1f) / 2f).coerceAtLeast(0f)
            return Offset(o.x.coerceIn(-bx, bx), o.y.coerceIn(-by, by))
        }

        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var swipeAccumX = 0f
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val isMultitouch = event.changes.size > 1
                            val scale = scaleState.value
                            if (isMultitouch || scale > 1f) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                offsetState.value = clamp(
                                    if (newScale <= 1f) Offset.Zero else offsetState.value + panChange,
                                    newScale
                                )
                                scaleState.value = newScale
                            } else {
                                swipeAccumX += panChange.x
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (scaleState.value <= 1f) {
                            val threshold = widthPx * 0.15f
                            when {
                                swipeAccumX <= -threshold -> onSwipeNext()
                                swipeAccumX >= threshold -> onSwipePrevious()
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scaleState.value > 1f) {
                                scaleState.value = 1f
                                offsetState.value = Offset.Zero
                            } else {
                                scaleState.value = 3f
                            }
                        }
                    )
                }
                .graphicsLayer(
                    scaleX = scaleState.value,
                    scaleY = scaleState.value,
                    translationX = offsetState.value.x,
                    translationY = offsetState.value.y
                )
        )
    }
}
