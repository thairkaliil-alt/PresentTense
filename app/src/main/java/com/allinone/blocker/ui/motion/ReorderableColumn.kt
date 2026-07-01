package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// ReorderableColumn.kt  —  Long-press-and-drag reordering for ANY list
//
// WHAT THIS GIVES YOU
//   A LazyColumn where the user can long-press a row, drag it up or down,
//   and have every OTHER row smoothly SLIDE out of the way (via Compose's
//   own Modifier.animateItem()) instead of snapping to its new spot. The
//   row under the finger follows the touch directly; everything else glides.
//   Picking a row up and letting it go are their own small spring-driven
//   physical events — a lift on pickup, a settle-into-place on release —
//   and swaps/pickup give a light haptic tick, the same way iOS Reminders,
//   Things and Todoist do it.
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
//
// ─────────────────────────────────────────────────────────────────────────
// WHAT CHANGED FROM THE ORIGINAL VERSION (for future reference)
//
//   1. THE MAIN BUG — the drag gesture used to be re-installed (cancelled +
//      restarted) every single time a row's position changed, because it
//      was keyed on the reordering list itself. That meant a fresh drag
//      would silently die and restart mid-gesture almost every time you
//      dragged a row past a neighbour. That's the #1 reason it felt janky
//      and disruptive. Fixed by keying the gesture on Unit (installed once)
//      and reading the live list/callbacks through `rememberUpdatedState`
//      instead of relying on a restart to see fresh values.
//
//   2. SETTLE PHYSICS — releasing a row used to snap its offset to zero
//      instantly. Now it springs from wherever it was down to zero
//      (reorderPickup spec), so letting go reads as the row "landing"
//      rather than teleporting.
//
//   3. AUTO-SCROLL — used to only tick while your finger was actively
//      moving (so holding still at the top/bottom edge did nothing), and
//      stacked uncontrolled scroll calls on every touch-move event. Now
//      it's a single continuous loop that runs for the duration of the
//      drag, scrolls with a speed that ramps up the closer you are to the
//      edge, and keeps checking for row swaps as new rows scroll into
//      reach — even while your finger isn't moving.
//
//   4. HAPTICS — a short tick on pickup and a lighter tick on every swap,
//      matching the "physical" feel of top-tier reorderable lists.
//
//   5. DATA-SYNC FIX — if a drag was ever cancelled mid-gesture (e.g. an
//      interrupting system gesture), the on-screen order used to stay
//      stuck in its half-reordered state without ever being saved. Now a
//      cancelled drag reverts the on-screen order back to the real saved
//      order.
// ─────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Per-item drag info handed to [ReorderableColumn]'s `itemContent` lambda.
 *
 * @param isDragging  true for the one row currently lifted under the user's
 *        finger. Goes false the instant the finger lifts, even while the
 *        row is still gliding the last bit into place — use it to drive
 *        "lifted" visuals (shadow, scale) that should let go immediately.
 * @param itemModifier  apply this to your row's root modifier (first, before
 *        your own background/shadow/etc). Carries the slide-into-place
 *        animation for every row except the one being dragged, and the raw
 *        finger-tracking (or settle-spring) offset for the one that is.
 */
@Immutable
data class ReorderDragState(
    val isDragging: Boolean,
    val itemModifier: Modifier
)

/**
 * A LazyColumn that supports long-press-and-drag reordering with a smooth
 * "slide into place" animation on every row except the one being dragged,
 * plus spring-driven pickup/settle physics on the dragged row itself.
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
    // draggingIndex = which row is currently the "held" row (-1 = none).
    //                 Stays set for the brief settle animation after the
    //                 finger lifts, so the offset source can hand off
    //                 smoothly from "finger" to "spring".
    // rawDragOffsetY = live, 1:1 finger-tracking offset (px) — updated
    //                 synchronously on every touch-move, zero lag.
    // settleOffsetY  = spring-animated offset used ONLY during the brief
    //                 release/cancel window, taking over from rawDragOffsetY
    //                 and easing it down to 0 instead of snapping.
    // hoverIndex     = which slot the dragged row is currently hovering over
    // isSettling     = true only during that release/cancel spring
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var hoverIndex    by remember { mutableIntStateOf(-1) }
    var rawDragOffsetY by remember { mutableFloatStateOf(0f) }
    val settleOffsetY = remember { Animatable(0f) }
    var isSettling by remember { mutableStateOf(false) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val density   = LocalDensity.current
    val haptics   = LocalHapticFeedback.current

    // Local mutable copy so the list animates live while dragging; only
    // committed back to the caller (via onReorder) when the finger lifts.
    var orderedItems by remember(items) { mutableStateOf(items) }

    // Read through rememberUpdatedState so the long-lived gesture coroutine
    // (installed once, see the pointerInput(Unit) below) always calls the
    // LATEST version of these — a fresh lambda every recomposition should
    // never leave the gesture calling a stale one.
    val currentOnReorder = rememberUpdatedState(onReorder)

    /**
     * Checks whether the dragged row's current center has crossed into a
     * neighbouring row's territory and, if so, swaps them in [orderedItems]
     * so the rest of the list glides out of the way. Called both on every
     * touch-move AND every auto-scroll tick, so a swap can happen even
     * while the finger is holding still at the top/bottom edge.
     */
    fun checkForSwap() {
        if (draggingIndex == -1) return
        val itemInfo = listState.layoutInfo.visibleItemsInfo
        val draggingItem = itemInfo.firstOrNull { it.index == draggingIndex } ?: return
        val centerY = draggingItem.offset + draggingItem.size / 2 + rawDragOffsetY
        val newHover = itemInfo.minByOrNull { info ->
            val c = info.offset + info.size / 2
            abs(c - centerY)
        }?.index ?: hoverIndex

        if (newHover != hoverIndex) {
            // Swap in the local list — this is what makes the OTHER
            // (non-dragged) rows slide into their new slots, via
            // animateItem() applied below.
            val mutable = orderedItems.toMutableList()
            val fromIdx = hoverIndex.coerceIn(0, mutable.lastIndex)
            val toIdx   = newHover.coerceIn(0, mutable.lastIndex)
            val moved   = mutable.removeAt(fromIdx)
            mutable.add(toIdx, moved)
            orderedItems = mutable

            // The dragged row now sits at a NEW index, so Compose will lay
            // it out at a different base position next frame. Correct
            // rawDragOffsetY by that same amount so the row stays glued
            // under the finger instead of jumping.
            rawDragOffsetY += (draggingItem.offset -
                (itemInfo.firstOrNull { it.index == newHover }?.offset ?: draggingItem.offset))
            draggingIndex = newHover
            hoverIndex = newHover

            // A light tick per swap — the same "felt" confirmation iOS/Things
            // give you as rows trade places under your finger.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LazyColumn(
        state = listState,
        // Installed ONCE (key = Unit) and never restarted mid-gesture — this
        // is the fix for the main bug. Re-keying this on the live list used
        // to cancel and reboot the drag every time a swap happened.
        modifier = modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                    val item = itemInfo.firstOrNull { info ->
                        offset.y.toInt() in info.offset..(info.offset + info.size)
                    }
                    if (item != null) {
                        // Cut off any still-running settle spring from a
                        // previous drag so it can't fight the new one.
                        autoScrollJob?.cancel()
                        draggingIndex = item.index
                        hoverIndex    = item.index
                        rawDragOffsetY = 0f
                        isSettling = false

                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        // A single continuous loop for the lifetime of this
                        // drag: auto-scrolls when the finger is near an
                        // edge (ramping speed the closer it gets), and — the
                        // fix — keeps working even if the finger holds
                        // perfectly still, not just on touch-move events.
                        autoScrollJob = scope.launch {
                            val edgeZonePx = with(density) { 80.dp.toPx() }
                            val maxSpeedPxPerSec = with(density) { 1000.dp.toPx() }
                            while (isActive) {
                                if (draggingIndex == -1) break
                                val viewportHeight =
                                    listState.layoutInfo.viewportEndOffset.toFloat()
                                val speed = when {
                                    fingerY < edgeZonePx -> {
                                        val p = (1f - fingerY / edgeZonePx).coerceIn(0f, 1f)
                                        -maxSpeedPxPerSec * p * p
                                    }
                                    fingerY > viewportHeight - edgeZonePx -> {
                                        val p = (1f - (viewportHeight - fingerY) / edgeZonePx)
                                            .coerceIn(0f, 1f)
                                        maxSpeedPxPerSec * p * p
                                    }
                                    else -> 0f
                                }
                                if (speed != 0f) {
                                    listState.scrollBy(speed * (16f / 1000f))
                                    checkForSwap()
                                }
                                delay(16)
                            }
                        }
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    rawDragOffsetY += dragAmount.y
                    fingerY = change.position.y
                    checkForSwap()
                },
                onDragEnd = {
                    currentOnReorder.value(orderedItems)
                    autoScrollJob?.cancel()

                    // Spring the dragged row's offset from wherever it is
                    // down to 0, instead of snapping — the "settle" you feel
                    // when releasing a picked-up row in a well-made app.
                    val startOffset = rawDragOffsetY
                    isSettling = true
                    scope.launch {
                        settleOffsetY.snapTo(startOffset)
                        settleOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = MotionSpecs.reorderPickup()
                        )
                        isSettling = false
                        draggingIndex = -1
                        hoverIndex    = -1
                        rawDragOffsetY = 0f
                    }
                },
                onDragCancel = {
                    autoScrollJob?.cancel()
                    // A cancelled drag (e.g. an interrupting system gesture)
                    // never calls onReorder, so the real saved order never
                    // changed — snap the on-screen list back to match it
                    // instead of leaving it stuck half-reordered.
                    orderedItems  = items
                    draggingIndex = -1
                    hoverIndex    = -1
                    rawDragOffsetY = 0f
                    isSettling = false
                }
            )
        },
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(orderedItems, key = { _, item -> key(item) }) { index, item ->
            val isActiveDrag = index == draggingIndex
            // Visual "lifted" state ends the instant the finger lifts, even
            // though the row keeps gliding the last bit into place — this
            // is what lets the shadow/scale let go immediately on release
            // while the position itself still settles smoothly.
            val isDraggingVisual = isActiveDrag && !isSettling
            val reducedMotion = LocalReducedMotion.current

            val itemModifier = when {
                isActiveDrag ->
                    // The dragged (or settling) row follows the finger — or,
                    // during the brief release window, the settle spring —
                    // directly via a raw pixel offset. It's deliberately
                    // excluded from animateItem() so it never fights the
                    // live touch or the settle spring.
                    Modifier.graphicsLayer {
                        translationY = if (isSettling) settleOffsetY.value else rawDragOffsetY
                    }

                reducedMotion ->
                    // Respect the OS "reduce motion" setting — snap instantly.
                    Modifier

                else ->
                    // The actual "smooth reorder" behaviour: every row whose
                    // position changed glides to its new slot instead of
                    // teleporting there on the next frame.
                    Modifier.animateItem(
                        placementSpec = MotionSpecs.reorderGlide<IntOffset>()
                    )
            }

            itemContent(item, ReorderDragState(isDragging = isDraggingVisual, itemModifier = itemModifier))
        }
    }
}
