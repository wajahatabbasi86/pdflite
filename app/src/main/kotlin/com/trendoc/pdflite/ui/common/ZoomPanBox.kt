package com.trendoc.pdflite.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Zoom and pan state for one [ZoomPanBox]. Hoisted so a screen can drive it itself —
 * Add Text focuses a tapped point, and any screen can offer a "fit" control.
 */
class ZoomPanState(
    val minScale: Float = 1f,
    val maxScale: Float = 6f
) {
    var scale by mutableStateOf(1f)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set

    val isZoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /**
     * Scales to [targetScale] and shifts so the content point ([xPx], [yPx]) — measured in
     * the content's own unscaled pixels — ends up in the middle of the viewport.
     *
     * The transform pivots about the centre, so a point p maps to c + (p - c) * s + offset;
     * solving that for "p lands on c" gives the offset below.
     */
    fun focusOn(xPx: Float, yPx: Float, viewportWidthPx: Float, viewportHeightPx: Float, targetScale: Float) {
        val s = targetScale.coerceIn(minScale, maxScale)
        val cx = viewportWidthPx / 2f
        val cy = viewportHeightPx / 2f
        scale = s
        offset = clampOffset(
            Offset(-(xPx - cx) * s, -(yPx - cy) * s),
            s, viewportWidthPx, viewportHeightPx
        )
    }

    internal fun clampOffset(
        candidate: Offset,
        s: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Offset {
        // How far the scaled content extends past each edge, halved because the transform
        // pivots about the centre. Zero at 1x, which pins the content in place.
        val boundX = (viewportWidthPx * (s - 1f) / 2f).coerceAtLeast(0f)
        val boundY = (viewportHeightPx * (s - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            candidate.x.coerceIn(-boundX, boundX),
            candidate.y.coerceIn(-boundY, boundY)
        )
    }
}

@Composable
fun rememberZoomPanState(minScale: Float = 1f, maxScale: Float = 6f): ZoomPanState =
    remember(minScale, maxScale) { ZoomPanState(minScale, maxScale) }

/**
 * Wraps content in a pinch-to-zoom, drag-to-pan viewport.
 *
 * Gesture split, matching View PDF's page list so the whole app behaves the same way:
 * - **two fingers** always pinch to zoom and drag to pan;
 * - **one finger while zoomed in** pans, because once magnified, reaching what is now off
 *   to the side is the point of dragging;
 * - **one finger at 1x** is left entirely alone — not consumed — so the content's own taps
 *   still land and any enclosing list can still scroll.
 *
 * Content keeps its own untransformed coordinate space: a child's `detectTapGestures` still
 * reports positions as if nothing were scaled, so callers converting taps to PDF points need
 * no zoom-aware arithmetic.
 */
@Composable
fun ZoomPanBox(
    state: ZoomPanState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val viewportHeightPx = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewportWidthPx, viewportHeightPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val multiTouch = event.changes.size > 1
                            if (multiTouch) {
                                val newScale = (state.scale * event.calculateZoom())
                                    .coerceIn(state.minScale, state.maxScale)
                                val pan = event.calculatePan()
                                state.scale = newScale
                                state.offset = state.clampOffset(
                                    if (newScale <= state.minScale) Offset.Zero else state.offset + pan,
                                    newScale, viewportWidthPx, viewportHeightPx
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (state.isZoomed) {
                                state.offset = state.clampOffset(
                                    state.offset + event.calculatePan(),
                                    state.scale, viewportWidthPx, viewportHeightPx
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(
                    scaleX = state.scale,
                    scaleY = state.scale,
                    translationX = state.offset.x,
                    translationY = state.offset.y
                ),
            content = content
        )
    }
}
