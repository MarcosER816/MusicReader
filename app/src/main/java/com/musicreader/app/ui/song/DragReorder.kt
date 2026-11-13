package com.musicreader.app.ui.song

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a LazyColumn whose reorderable items have String keys. The dragged item
 * follows the finger; when its centre passes over another item, [onMove] is called with both keys
 * and the list (owned by the ViewModel) reorders.
 */
@Stable
class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: State<(fromKey: String, toKey: String) -> Unit>,
) {
    var draggingKey: String? by mutableStateOf(null)
        private set

    /** Vertical translation of the dragged item relative to its layout position. */
    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    internal fun start(key: String) {
        draggingKey = key
        dragOffset = 0f
    }

    internal fun drag(deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return

        // Scroll when the dragged item is pushed past the top or bottom edge.
        val top = current.offset + dragOffset
        val bottom = top + current.size
        val overflow = when {
            top < info.viewportStartOffset -> top - info.viewportStartOffset
            bottom > info.viewportEndOffset -> bottom - info.viewportEndOffset
            else -> 0f
        }
        if (overflow != 0f) dragOffset += listState.dispatchRawDelta(overflow)

        val center = current.offset + dragOffset + current.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { it.isReorderTarget(key, center) } ?: return
        // After the move the dragged item is laid out where the target was; keep it under the finger.
        dragOffset += current.offset - target.offset
        onMove.value(key, target.key as String)
    }

    internal fun end() {
        draggingKey = null
        dragOffset = 0f
    }

    private fun LazyListItemInfo.isReorderTarget(draggedKey: String, center: Float): Boolean =
        key is String && key != draggedKey && center >= offset && center <= offset + size
}

@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    onMove: (fromKey: String, toKey: String) -> Unit,
): DragReorderState {
    val currentOnMove = rememberUpdatedState(onMove)
    return remember(listState) { DragReorderState(listState, currentOnMove) }
}

/** Applied to the drag handle of item [key]: dragging the handle moves the item. */
fun Modifier.dragHandle(state: DragReorderState, key: String): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
        onDragEnd = { state.end() },
        onDragCancel = { state.end() },
    )
}

/** Applied to the whole item: lifts and translates it while it is dragged. */
fun Modifier.draggedItem(state: DragReorderState, key: String): Modifier {
    if (state.draggingKey != key) return this
    return zIndex(1f).graphicsLayer {
        translationY = state.dragOffset
        shadowElevation = 16f
        scaleX = 1.02f
        scaleY = 1.02f
    }
}
