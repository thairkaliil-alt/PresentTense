package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// ReorderableColumn.kt  —  Long-press-and-drag reordering for ANY list
//
// WHAT THIS GIVES YOU
//   A LazyColumn where the user can long-press a row, drag it up or down,
//   and have every OTHER row smoothly SLIDE out of the way (via Compose's
//   own Modifier.animateItem()) instead of snapping to its new spot. The
//   row under the finger follows the touch directly; everything else glides.
//
// WHY IT EXISTS
//   This drag-and-slide mechanism was originally hand-built once for the
//   Strict Alarms list. Pulling it out here means any future screen that
//   needs a reorderable list (blocked-apps priority, whitelist order, a
//   schedule list, etc.) gets the same smooth behaviour for free — no
//   copy-pasting the gesture math, and the feel stays consistent everywhere.
//
// HOW TO USE
//   ReorderableColumn(
//       items        = myList,
//       key          = { it.id },
//       onReorder    = { newOrder -> viewModel.save(newOrder) },
//       modifier     = Modifier.fillMaxSize().padding(16.dp),
//       verticalArrangement = Arrangement.spacedBy(10.dp)
//   ) { item, dragState ->
//       MyCard(
//           item = item,
//           modifier = dragState.itemModifier,           // apply to the card root
//           isDragging = dragState.isDragging,            // for elevation/scale if desired
//       )
//   }
//
//   `dragState.itemModifier` already carries the smooth-slide animation (or
//   nothing, while the card is the one being actively dragged — see below).
//   You're free to add your own elevation/scale reaction to `isDragging`
//   the same way StrictAlarmListScreen's AlarmCard does, but it's optional;
//   the reordering and sliding work correctly without it.
//
// WHAT'S DELIBERATELY LEFT TO THE CALLER
//   - Visual styling of the row (shape, background, padding) — this
//     component only owns the gesture + animation, not the row's looks.
//   - Whether/how to show a drag handle icon — add one inside your own
//     item content if you want the affordance; this stays content-agnostic.
//   - Persisting the new order — onReorder hands back the full reordered
//     list; save it however your screen normally saves things.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Per-item drag info handed to [ReorderableColumn]'s `itemContent` lambda.
 *
 * @param isDragging  true for the one row currently under the user's finger.
 * @param itemModifier  apply this to your row's root modifier (first, before
 *        your own background/shadow/etc). Carries the slide-into-place
 *        animation for every row except the one being dragged, and the raw
 *        finger-tracking offset for the one that is.
 */
@Immutable
data class ReorderDragState(
    val isDragging: Boolean,
    val itemModifier: Modifier
)

/**
 * A LazyColumn that supports long-press-and-drag reordering with a smooth
 * "slide into place" animation on every row except the one being dragged.
 *
 * @param items the list to display, in current order.
 * @param key stable, unique key per item — REQUIRED for the slide animation
 *        to track which row moved where (same rule as any Compose lazy list).
 * @param onReorder called once, when the finger lifts, with the full
 *        reordered list. Nothing is persisted automatically — call your own
 *        save/repository function here.
 * @param modifier applied to the LazyColumn itself.
 * @param verticalArrangement spacing between rows, same as a normal LazyColumn.
 * @param itemContent your row's content — receives the item and a
 *        [ReorderDragState] to wire into your row's modifier.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemContent: @Composable (item: T, dragState: ReorderDragState) -> Unit
) {
    // ── Drag state ──────────────────────────────────────────────────────
    // draggingIndex = which row is currently being dragged (-1 = none)
    // dragOffsetY   = how many pixels the dragged row has moved from its
    //                 original spot — fed straight into its graphicsLayer
    //                 so it tracks the finger 1:1
    // hoverIndex    = which slot the dragged row is currently hovering over
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY   by remember { mutableFloatStateOf(0f) }
    var hoverIndex    by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val density   = LocalDensity.current

    // Local mutable copy so the list animates live while dragging; only
    // committed back to the caller (via onReorder) when the finger lifts.
    var orderedItems by remember(items) { mutableStateOf(items) }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(orderedItems) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                    val item = itemInfo.firstOrNull { info ->
                        offset.y.toInt() in info.offset..(info.offset + info.size)
                    }
                    if (item != null) {
                        draggingIndex = item.index
                        hoverIndex    = item.index
                        dragOffsetY   = 0f
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y

                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                    val draggingItem = itemInfo.firstOrNull { it.index == draggingIndex }
                    if (draggingItem != null) {
                        val centerY = draggingItem.offset + draggingItem.size / 2 + dragOffsetY
                        val newHover = itemInfo.minByOrNull { info ->
                            val c = info.offset + info.size / 2
                            kotlin.math.abs(c - centerY)
                        }?.index ?: hoverIndex

                        if (newHover != hoverIndex) {
                            // Swap in the local list — this is what makes the
                            // OTHER (non-dragged) rows slide into their new
                            // slots, via animateItem() applied below.
                            val mutable = orderedItems.toMutableList()
                            val fromIdx = hoverIndex.coerceIn(0, mutable.lastIndex)
                            val toIdx   = newHover.coerceIn(0, mutable.lastIndex)
                            val moved   = mutable.removeAt(fromIdx)
                            mutable.add(toIdx, moved)
                            orderedItems = mutable

                            // The dragged row now sits at a NEW index, so
                            // Compose will lay it out at a different base
                            // position next frame. Correct dragOffsetY by
                            // that same amount so the row stays glued under
                            // the finger instead of jumping.
                            dragOffsetY += (draggingItem.offset -
                                (itemInfo.firstOrNull { it.index == newHover }?.offset ?: draggingItem.offset))
                            hoverIndex = newHover
                        }
                    }

                    // Auto-scroll when dragging near the top/bottom edge.
                    val viewportHeight = listState.layoutInfo.viewportEndOffset.toFloat()
                    val edgeZone = with(density) { 60.dp.toPx() }
                    val finger = change.position.y
                    scope.launch {
                        when {
                            finger < edgeZone -> listState.scrollBy(-12f)
                            finger > viewportHeight - edgeZone -> listState.scrollBy(12f)
                        }
                    }
                },
                onDragEnd = {
                    onReorder(orderedItems)
                    draggingIndex = -1
                    dragOffsetY   = 0f
                    hoverIndex    = -1
                },
                onDragCancel = {
                    draggingIndex = -1
                    dragOffsetY   = 0f
                    hoverIndex    = -1
                }
            )
        },
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(orderedItems, key = { _, item -> key(item) }) { index, item ->
            val isDragging = index == draggingIndex
            val reducedMotion = LocalReducedMotion.current

            val itemModifier = when {
                isDragging ->
                    // The dragged row follows the finger directly via a raw
                    // pixel offset — animating it too would fight the live
                    // touch and feel laggy, so it's deliberately excluded
                    // from animateItem().
                    Modifier.graphicsLayer { translationY = dragOffsetY }

                reducedMotion ->
                    // Respect the OS "reduce motion" setting — snap instantly.
                    Modifier

                else ->
                    // The actual "smooth reorder" behaviour: every row whose
                    // position changed slides to its new slot instead of
                    // teleporting there on the next frame.
                    Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness    = Spring.StiffnessMedium,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )
                    )
            }

            itemContent(item, ReorderDragState(isDragging = isDragging, itemModifier = itemModifier))
        }
    }
}
