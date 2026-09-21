package com.trendoc.pdflite.ui.view

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
                title = { Text(uiState.fileName ?: "View PDF") },
                actions = {
                    uiState.sourceUri?.let { uri ->
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPageIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Image(
                                bitmap = page.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "${uiState.pages.size} page${if (uiState.pages.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Keyed on the page index so zoom/pan state resets to 1x when switching pages,
            // instead of carrying the previous page's zoom level over.
            key(currentIndex) {
                ZoomableFullPage(
                    bitmap = pages[currentIndex].asImageBitmap(),
                    modifier = Modifier.fillMaxSize().padding(if (presentationMode) PaddingValues(0.dp) else innerPadding),
                    onSwipeNext = { if (currentIndex < pages.lastIndex) currentIndex++ },
                    onSwipePrevious = { if (currentIndex > 0) currentIndex-- },
                    onTap = { if (presentationMode) presentationMode = false }
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
    modifier: Modifier = Modifier,
    onSwipeNext: () -> Unit = {},
    onSwipePrevious: () -> Unit = {},
    onTap: () -> Unit = {}
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        fun clamp(o: Offset): Offset {
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
                            if (isMultitouch || scale > 1f) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                offset = clamp(if (newScale <= 1f) Offset.Zero else offset + panChange)
                                scale = newScale
                            } else {
                                swipeAccumX += panChange.x
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (scale <= 1f) {
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
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                            }
                        }
                    )
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
