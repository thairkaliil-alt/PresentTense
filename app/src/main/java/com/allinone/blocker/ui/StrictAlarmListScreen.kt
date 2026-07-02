package com.allinone.blocker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.allinone.blocker.data.effectiveAlarmCount
import com.allinone.blocker.data.nextTriggerMillis
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.STRICT_ALARM_INTERVAL_MINUTES
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.ReorderableColumn
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmListScreen(
    onBack: () -> Unit,
    onAddAlarm: () -> Unit = {},
    onQuickAdd: () -> Unit = {},
    onEditAlarm: (String) -> Unit = {},
    onOpenSleepCalculator: () -> Unit = {},
    justAddedAlarms: List<StrictAlarmEntry>? = null,
    onJustAddedAlarmsConsumed: () -> Unit = {},
    justSavedAlarm: StrictAlarmEntry? = null,
    onJustSavedAlarmConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val alarms by BlockerRepository.strictAlarms.collectAsState()

    // ── Swipe-to-delete reveal state ────────────────────────────────────────
    // Tracks the id of whichever ONE card currently has its red delete action
    // revealed. Only one at a time — opening a new card's action snaps any
    // previously-open one shut, the same behaviour as Gmail, Files by Google,
    // and every other well-made "swipe to reveal an action" list.
    var revealedAlarmId by remember { mutableStateOf<String?>(null) }

    // ── Undo snackbar for a just-created batch (from Quick Add) ────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(justAddedAlarms) {
        val created = justAddedAlarms ?: return@LaunchedEffect
        if (created.isEmpty()) {
            onJustAddedAlarmsConsumed()
            return@LaunchedEffect
        }

        val count = created.size
        val message = if (count == 1) "1 alarm added" else "$count alarms added"

        // Indefinite duration + a manual 5s timer gives us exact control over
        // how long it stays up, instead of Material3's fixed Short(~4s)/Long(~10s).
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message           = message,
                actionLabel       = "Undo",
                withDismissAction = false,
                duration          = androidx.compose.material3.SnackbarDuration.Indefinite
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                val ids = created.map { it.id }
                BlockerRepository.removeStrictAlarmEntries(ids)
                AlarmScheduler.cancelAll(context, created)
            }
        }
        scope.launch {
            delay(5000L)
            snackbarHostState.currentSnackbarData?.dismiss()
        }

        onJustAddedAlarmsConsumed()
    }

    // ── Quick "Alarm set for X from now" confirmation ───────────────────────
    // This one used to reuse the Snackbar host below, but a snackbar's job is
    // messages that might need an action (Undo, etc.) — this is a pure
    // "here's what just happened" confirmation, so it gets its own compact
    // floating pill instead. See AlarmSetToast.kt for the full reasoning.
    var alarmToastVisible by remember { mutableStateOf(false) }
    var alarmToastRelativeTime by remember { mutableStateOf("") }

    LaunchedEffect(justSavedAlarm) {
        val saved = justSavedAlarm ?: return@LaunchedEffect
        val next  = saved.nextTriggerMillis()
        if (next != null) {
            alarmToastRelativeTime = formatRelativeAlarmTime(next)
            alarmToastVisible = true
            scope.launch {
                delay(2600L)
                alarmToastVisible = false
            }
        }
        onJustSavedAlarmConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Snackbar(
                snackbarData    = data,
                containerColor  = CardSurface,
                contentColor    = MaterialTheme.colorScheme.onBackground,
                actionColor     = AccentBlue
            )
        } },
        topBar = {
            TopAppBar(
                title = { Text("Alarms", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSleepCalculator) {
                        Icon(Icons.Filled.Bedtime, contentDescription = "Sleep calculator")
                    }
                    IconButton(onClick = onQuickAdd) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Quick add multiple alarms")
                    }
                    IconButton(onClick = onAddAlarm) {
                        Icon(Icons.Filled.Add, contentDescription = "Add alarm")
                    }
                }
            )
        }
    ) { pad ->

        if (alarms.isEmpty()) {
            EmptyAlarmsState(
                modifier  = Modifier.padding(pad).fillMaxSize(),
                onAddAlarm = onAddAlarm
            )
            return@Scaffold
        }

        ReorderableColumn(
            items     = alarms,
            key       = { it.id },
            onReorder = { newOrder -> BlockerRepository.reorderStrictAlarms(newOrder) },
            modifier  = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { entry, dragState ->
            // Elevation and scale animate up while this card is the one
            // being dragged — purely visual, on top of the reordering
            // and sliding that ReorderableColumn already handles.
         val elevation by animateDpAsState(
                targetValue   = if (dragState.isDragging) 16.dp else 2.dp,
                animationSpec = MotionSpecs.reorderPickup(),
                label         = "cardElevation"
            )
            val scale by animateFloatAsState(
                targetValue   = if (dragState.isDragging) 1.045f else 1f,
                animationSpec = MotionSpecs.reorderPickup(),
                label         = "cardScale"
            )
            AlarmCard(
                entry        = entry,
                isDragging   = dragState.isDragging,
                elevation    = elevation,
                scale        = scale,
                modifier     = dragState.itemModifier,
                isRevealed   = revealedAlarmId == entry.id,
                onRevealChange = { open ->
                    revealedAlarmId = if (open) entry.id else null
                },
                onToggle   = { checked ->
                    BlockerRepository.setStrictAlarmEntryEnabled(entry.id, checked)
                    AlarmScheduler.schedule(context, entry.copy(enabled = checked))
                },
                onClick    = { onEditAlarm(entry.id) },
                onDelete   = {
                    // The reveal-then-confirm swipe already IS the
                    // confirmation — no dialog. We delete right away and
                    // give a few seconds of Undo via the same snackbar host
                    // used for the Quick Add batch, so there's still a
                    // safety net without an extra tap.
                    val removed = entry
                    if (revealedAlarmId == removed.id) revealedAlarmId = null
                    BlockerRepository.removeStrictAlarmEntry(removed.id)
                    AlarmScheduler.cancel(context, removed)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message           = "Alarm deleted",
                            actionLabel       = "Undo",
                            withDismissAction = false,
                            duration          = androidx.compose.material3.SnackbarDuration.Short
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            BlockerRepository.addStrictAlarmEntry(removed)
                            if (removed.enabled) AlarmScheduler.schedule(context, removed)
                        }
                    }
                }
            )
        }
        }

        AlarmSetToast(
            visible      = alarmToastVisible,
            relativeTime = alarmToastRelativeTime
        )
    }
}

@Composable
private fun AlarmCard(
    entry: StrictAlarmEntry,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    scale: Float,
    modifier: Modifier = Modifier,
    isRevealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val slotCount = entry.effectiveAlarmCount()

    // Matches the app's own shape token for "list item cards" (see Shape.kt)
    // instead of a one-off custom radius — one visual language everywhere.
    val cardShape = MaterialTheme.shapes.large

    // ── Swipe-to-REVEAL delete (not swipe-to-delete) ────────────────────────
    // The old version deleted the instant you swiped past a threshold — one
    // flick and it was gone. Every well-made list (Gmail, Files by Google,
    // Todoist) instead does this in two beats:
    //   1. Swipe left → the card slides over and STOPS, revealing a red
    //      delete button behind it. Nothing is deleted yet.
    //   2. Tap that button, or swipe left again (further, or a fast flick)
    //      → NOW it deletes.
    //   3. Swipe back right, tap the card itself, or open a different
    //      card's action → this one closes with nothing lost.
    // The reveal step itself is the "are you sure" — no dialog needed, but
    // no more accidental one-swipe deletes either.
    //
    // Hand-built with Modifier.draggable + Animatable rather than
    // Material3's SwipeToDismissBox, because SwipeToDismissBox is built for
    // "one swipe = fully dismissed" with no clean way to stop halfway.
    //
    // Note on gestures: this does NOT fight the long-press-to-reorder drag
    // from ReorderableColumn. That drag only starts consuming touches after
    // ~500ms of holding still (detectDragGesturesAfterLongPress), while this
    // swipe only starts consuming once your finger has actually moved
    // sideways past a small threshold. A quick swipe is caught by this
    // gesture before the long-press timer ever fires; a hold-then-drag never
    // moves far enough sideways early on to trip the swipe, so the long
    // press wins instead. Two different "shapes" of touch, so they land on
    // two different actions. Worth double-checking on your phone once this
    // build is up, since gesture feel is one of those things that's hard to
    // fully judge without a real screen.
    val density   = LocalDensity.current
    val haptics   = rememberHaptics()
    val dragScope = rememberCoroutineScope()

    // Width of the red action button once fully revealed.
    val revealWidthPx = with(density) { 78.dp.toPx() }
    // Swiping (or flinging) past this point commits to delete outright —
    // this is the "swipe again, further or faster" gesture.
    val commitPx = revealWidthPx * 1.9f
    // Hard stop so a wild drag can't fling the card off past a sane point.
    val maxDragPx = revealWidthPx * 2.4f

    val offsetX = remember(entry.id) { Animatable(0f) }
    var buzzedReveal by remember(entry.id) { mutableStateOf(false) }

    // A different card was swiped open, or the parent asked this one to
    // close — snap shut instead of ever leaving two cards open at once.
    LaunchedEffect(isRevealed) {
        if (!isRevealed && offsetX.value != 0f) {
            offsetX.animateTo(0f, MotionSpecs.tactile())
            buzzedReveal = false
        }
    }

    // Where the drag/fling ends up decides the outcome: snap shut, settle
    // into the "revealed" position, or commit to deleting.
    suspend fun settleDrag(velocity: Float) {
        val current    = offsetX.value
        val flungFast  = velocity < -900f
        when {
            current <= -commitPx || (flungFast && current <= -revealWidthPx * 0.9f) -> {
                haptics.confirm()
                onRevealChange(false)
                onDelete()
            }
            current <= -revealWidthPx * 0.5f -> {
                offsetX.animateTo(-revealWidthPx, MotionSpecs.tactile())
                onRevealChange(true)
            }
            else -> {
                offsetX.animateTo(0f, MotionSpecs.tactile())
                onRevealChange(false)
                buzzedReveal = false
            }
        }
    }

    fun closeReveal() {
        dragScope.launch {
            offsetX.animateTo(0f, MotionSpecs.tactile())
            onRevealChange(false)
            buzzedReveal = false
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ── Red delete action, sitting behind the card ───────────────────
        // A solid red panel the width of a real tap target (not a floating
        // icon) — this is now a genuine button once revealed, so it needs
        // to be comfortably tappable, not just decorative.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(cardShape)
                .background(AccentRed),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(84.dp)
                    .pressable(onClick = {
                        haptics.confirm()
                        onRevealChange(false)
                        onDelete()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete alarm",
                    tint     = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── Foreground card — slides left to reveal the action above ─────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX          = scale
                    scaleY          = scale
                    shadowElevation = if (isDragging) 24f else 0f
                    // Without this, the shadow this layer casts while
                    // dragging defaults to a plain rectangle — its square
                    // corners then poke out from behind the card's rounded
                    // ones as small black triangles. Giving it the same
                    // shape as the card (and the .shadow() modifier below)
                    // keeps the shadow's outline matching the card exactly.
                    shape           = cardShape
                }
                .shadow(elevation, cardShape, clip = false)
                .background(CardSurface, cardShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragScope.launch {
                            val next = (offsetX.value + delta).coerceIn(-maxDragPx, 0f)
                            offsetX.snapTo(next)
                            // One light tick exactly when the swipe crosses
                            // into "revealed" territory — the same felt
                            // confirmation a physical latch gives you.
                            if (!buzzedReveal && next <= -revealWidthPx) {
                                haptics.tap()
                                buzzedReveal = true
                            } else if (next > -revealWidthPx * 0.5f) {
                                buzzedReveal = false
                            }
                        }
                    },
                    onDragStopped = { velocity -> settleDrag(velocity) }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // pressable gives the card a tactile 4% squeeze on tap,
                    // with no ripple — same feel as every other card in the
                    // app. While the delete action is showing, tapping the
                    // card just closes it back up instead of opening edit —
                    // the same behaviour every swipe-actions list uses.
                    .pressable(onClick = {
                        if (offsetX.value != 0f) closeReveal() else onClick()
                    })
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // ── Hero row: time + toggle ─────────────────────────────────
                // No drag-handle icon here on purpose — the whole card is
                // already the drag target (long-press anywhere to reorder,
                // same as Todoist/Things), so a separate handle glyph was just
                // extra clutter competing with the time for attention.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = formatAlarmTime(entry.hour, entry.minute),
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium,
                        color      = if (entry.enabled) MaterialTheme.colorScheme.onBackground else TextMuted,
                        modifier   = Modifier.weight(1f)
                    )

                    Switch(
                        checked         = entry.enabled,
                        onCheckedChange = { checked ->
                            // Stop the tap-to-edit from firing when toggle is used
                            onToggle(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue
                        )
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── Repeat-days / burst summary ─────────────────────────────
                val repeatLabel = repeatSummary(entry.daysOfWeek)
                val burstLabel  = if (slotCount > 1) "  ·  ${slotCount}× snooze burst" else ""
                Text(
                    text  = repeatLabel + burstLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.enabled) TextSecondary else TextMuted
                )

                // ── Burst sub-times (compact) ─────────────────────────────────
                if (slotCount > 1) {
                    Spacer(Modifier.height(6.dp))
                    val times = (0 until slotCount).joinToString("  ·  ") { i ->
                        val total = entry.hour * 60 + entry.minute + i * STRICT_ALARM_INTERVAL_MINUTES
                        formatAlarmTime((total / 60) % 24, total % 60)
                    }
                    Text(
                        text  = times,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                // ── Label pill (only if set) ────────────────────────────────
                // A small rounded tag instead of a bare line of text — reads as
                // a proper label at a glance, the way a chip does in MD3.
                if (entry.label.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .background(CardSurfaceAlt, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text  = entry.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAlarmsState(modifier: Modifier = Modifier, onAddAlarm: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Alarm,
                contentDescription = null,
                tint     = TextMuted,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No alarms yet",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap the + button to add your first strict alarm.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(20.dp))
           Box(
    modifier = Modifier
        .background(AccentBlue, CircleShape)
        .pointerInput(onAddAlarm) {
            detectTapGestures(
                onTap = { onAddAlarm() }
            )
        }
        .padding(horizontal = 24.dp, vertical = 12.dp)
) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.size(6.dp))
                    Text("Add alarm", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun repeatSummary(daysOfWeek: Set<Int>): String {
    if (daysOfWeek.isEmpty()) return "Once"
    if (daysOfWeek.size == 7) return "Every day"
    val weekdays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    val weekend  = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    if (daysOfWeek == weekdays) return "Weekdays"
    if (daysOfWeek == weekend)  return "Weekends"
    val order = listOf(
        Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun"
    )
    return order.filter { it.first in daysOfWeek }.joinToString(", ") { it.second }
}

private fun formatAlarmTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}

/** "2h 15m", "45m", "3h" — same short style used for lockdown durations. */
private fun formatRelativeAlarmTime(triggerMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val totalMinutes = ((triggerMillis - nowMillis) / 60_000L).coerceAtLeast(0L)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0L && m == 0L -> "less than a minute"
        h == 0L            -> "${m}m"
        m == 0L            -> "${h}h"
        else                -> "${h}h ${m}m"
    }
}
