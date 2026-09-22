package com.trendoc.pdflite.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Minimal long-press drag-to-reorder tracking for a list of roughly fixed-height rows —
 * no external reorder library, since the screens that need this (Merge's file queue,
 * Image-to-PDF's image queue) already expose a real "swap adjacent items" ViewModel method
 * (`moveFile`/`moveImage`, both `(index: Int, delta: Int) -> Unit`). This just decides
 * *when* a drag has crossed enough of a row to trigger one of those swaps — it holds no
 * copy of the list itself, so it can't drift out of sync with the real data.
 *
 * [draggingIndexState]/[dragOffsetState] are hoisted via [remember] in the caller (so they
 * survive recomposition); [rowHeightPx]/[itemCount]/[onMove] are plain lambdas evaluated
 * fresh on every call, which matters because [itemCount] typically reads `uiState.files.size`
 * — a `remember`-wrapped closure would freeze that at whatever it was on first composition.
 */
class DragReorderState(
    private val draggingIndexState: MutableState<Int?>,
    private val dragOffsetState: MutableState<Float>,
    private val rowHeightPx: () -> Float,
    private val itemCount: () -> Int,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    val draggingIndex: Int? get() = draggingIndexState.value
    val dragOffsetPx: Float get() = dragOffsetState.value

    fun onDragStart(index: Int) {
        draggingIndexState.value = index
        dragOffsetState.value = 0f
    }

    fun onDrag(deltaY: Float) {
        var index = draggingIndexState.value ?: return
        var offset = dragOffsetState.value + deltaY
        val rowHeight = rowHeightPx()
        if (rowHeight > 0f) {
            val count = itemCount()
            while (offset > rowHeight / 2 && index < count - 1) {
                onMove(index, index + 1)
                offset -= rowHeight
                index += 1
            }
            while (offset < -rowHeight / 2 && index > 0) {
                onMove(index, index - 1)
                offset += rowHeight
                index -= 1
            }
        }
        draggingIndexState.value = index
        dragOffsetState.value = offset
    }

    fun onDragEnd() {
        draggingIndexState.value = null
        dragOffsetState.value = 0f
    }
}

/** Creates a fresh [DragReorderState] every recomposition (cheap — it holds no state of
 * its own), backed by [MutableState]s that persist via [remember] across recompositions. */
@Composable
fun rememberDragReorderState(
    rowHeightPx: () -> Float,
    itemCount: () -> Int,
    onMove: (from: Int, to: Int) -> Unit
): DragReorderState {
    val draggingIndexState = remember { mutableStateOf<Int?>(null) }
    val dragOffsetState = remember { mutableStateOf(0f) }
    return DragReorderState(draggingIndexState, dragOffsetState, rowHeightPx, itemCount, onMove)
}
