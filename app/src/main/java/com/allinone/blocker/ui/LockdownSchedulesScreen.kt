package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownSchedule
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.ReorderableColumn
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
// The dedicated schedules manager — reached via the schedule icon in the
// Lockdown screen's top bar. Everything about recurring "lock every night
// 11pm–7am"-style rules lives here: the list of schedules, the empty-state
// hint, and the add/edit dialog, all relocated from the old decluttered
// Lockdown screen without any behavior changes.
//
// The card list below deliberately reuses the exact same drag-to-reorder +
// swipe-to-reveal-delete interaction built for the Strict Alarms list
// (see StrictAlarmListScreen.kt's AlarmCard) via the shared ReorderableColumn
// and MotionSpecs/Haptics helpers, so schedules get the same polished feel
// instead of a second, different-feeling implementation.
// ════════════════════════════════════════════════════════════════════════════

private val DAY_LABELS = mapOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)
private val DAY_ORDER = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

/**
 * A save (new or edited) or a toggle-on that, per [LockdownEngine.wouldBeActiveNow],
 * would start a lockdown the instant it commits — held here so the confirmation
 * dialog below knows exactly what to do if the person taps "Start lockdown now".
 * See the big comment on [LockdownSchedulesScreen] for why this check exists.
 */
private sealed class PendingImmediateStart {
    data class Save(val schedule: LockdownSchedule) : PendingImmediateStart()
    data class Toggle(val schedule: LockdownSchedule) : PendingImmediateStart()
}

// BUGFIX: ScheduleEditDialog's Save button, and ScheduleCard's off→on toggle,
// used to call addSchedule()/updateSchedule() immediately with zero
// confirmation. Because LockdownEngine.evaluate() treats "is the current
// moment inside this schedule's window" as "lockdown is active right now",
// saving or enabling a schedule that happens to cover this exact moment
// instantly started a live lockdown session — no warning, no easy way out.
// Both call sites below now check LockdownEngine.wouldBeActiveNow() FIRST
// and, only if it would, route through the confirmation dialog at the
// bottom of this composable instead of committing straight away. A schedule
// that doesn't cover the current moment still saves/toggles exactly as
// before — no new friction for the common case.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownSchedulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val schedules by BlockerRepository.schedules.collectAsState()
    // Scheduled lockdowns start themselves on time via an exact AlarmManager
    // wake-up (see LockdownScheduleAlarms) — the same "Alarms & reminders"
    // system permission Strict Alarms already needs (see AlarmScheduler).
    // Checked once per visit to this screen, same as StrictAlarmEditScreen
    // does for the identical permission.
    val canScheduleExact = remember { AlarmScheduler.canScheduleExact(context) }
    var showAddSchedule by remember { mutableStateOf<LockdownSchedule?>(null) }
    var pendingImmediateStart by remember { mutableStateOf<PendingImmediateStart?>(null) }

    // ── Swipe-to-delete reveal state ────────────────────────────────────────
    // Same rule as the Strict Alarms list: only one card's red delete action
    // can be open at a time — opening a new one snaps any previously-open
    // card shut.
    var revealedScheduleId by remember { mutableStateOf<String?>(null) }

    // ── Undo snackbar for a deleted schedule ────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun commitScheduleSave(saved: LockdownSchedule) {
        if (schedules.any { it.id == saved.id }) BlockerRepository.updateSchedule(saved)
        else BlockerRepository.addSchedule(saved)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgDarkest,
            snackbarHost = { SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData    = data,
                    containerColor  = CardSurface,
                    contentColor    = MaterialTheme.colorScheme.onBackground,
                    actionColor     = AccentTeal
                )
            } },
            topBar = {
                TopAppBar(
                    title = { Text("Schedules", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddSchedule = LockdownSchedule(id = BlockerRepository.newScheduleId()) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add schedule")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
                )
            }
        ) { pad ->
            Column(modifier = Modifier.padding(pad).fillMaxSize()) {
                // Without this permission, the exact wake-up alarm that
                // starts a scheduled lockdown on its own (see
                // LockdownScheduleAlarms) may fire late or not at all —
                // surfaced here, right where schedules are managed, so
                // it's never a silent, invisible failure.
                if (!canScheduleExact) {
                    SchedulePermissionNotice(
                        onGrant = { AlarmScheduler.requestExactAlarmPermission(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, top = 8.dp)
                    )
                }

                if (schedules.isEmpty()) {
                    EmptyHintCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        text     = "No schedules yet. Add one for things like \u201CLock every night 11pm\u20137am.\u201D"
                    )
                } else {
                    ReorderableColumn(
                        items     = schedules,
                        key       = { it.id },
                        onReorder = { newOrder -> BlockerRepository.reorderSchedules(newOrder) },
                        modifier  = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) { schedule, dragState ->
                        // Elevation and scale animate up while this card is the one
                        // being dragged — purely visual, on top of the reordering
                        // and sliding that ReorderableColumn already handles.
                        val elevation by animateDpAsState(
                            targetValue   = if (dragState.isDragging) 16.dp else 2.dp,
                            animationSpec = MotionSpecs.reorderPickup(),
                            label         = "scheduleCardElevation"
                        )
                        val scale by animateFloatAsState(
                            targetValue   = if (dragState.isDragging) 1.045f else 1f,
                            animationSpec = MotionSpecs.reorderPickup(),
                            label         = "scheduleCardScale"
                        )
                        ScheduleCard(
                            schedule     = schedule,
                            isDragging   = dragState.isDragging,
                            elevation    = elevation,
                            scale        = scale,
                            modifier     = dragState.itemModifier,
                            isRevealed   = revealedScheduleId == schedule.id,
                            onRevealChange = { open ->
                                revealedScheduleId = if (open) schedule.id else null
                            },
                            onToggle = { checked ->
                                when {
                                    !checked -> StrictModeGate.guard { BlockerRepository.updateSchedule(schedule.copy(enabled = checked)) }
                                    LockdownEngine.wouldBeActiveNow(schedule.copy(enabled = true)) ->
                                        pendingImmediateStart = PendingImmediateStart.Toggle(schedule)
                                    else -> BlockerRepository.updateSchedule(schedule.copy(enabled = checked))
                                }
                            },
                            onClick  = { showAddSchedule = schedule },
                            onDelete = {
                                // The reveal-then-confirm swipe already IS the
                                // confirmation — no dialog. Delete right away and
                                // give a few seconds of Undo via the snackbar host,
                                // the same safety net the Strict Alarms list uses.
                                StrictModeGate.guard {
                                    val removed = schedule
                                    if (revealedScheduleId == removed.id) revealedScheduleId = null
                                    BlockerRepository.removeSchedule(removed.id)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message           = "Schedule deleted",
                                            actionLabel       = "Undo",
                                            withDismissAction = false,
                                            duration          = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            BlockerRepository.addSchedule(removed)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    showAddSchedule?.let { editing ->
        ScheduleEditDialog(
            schedule  = editing,
            onDismiss = { showAddSchedule = null },
            onSave    = { saved ->
                // Checked "as if enabled = true" regardless of the schedule's
                // actual saved enabled state — see the kdoc on
                // LockdownEngine.wouldBeActiveNow() for why.
                if (LockdownEngine.wouldBeActiveNow(saved.copy(enabled = true))) {
                    pendingImmediateStart = PendingImmediateStart.Save(saved)
                } else {
                    commitScheduleSave(saved)
                    showAddSchedule = null
                }
            }
        )
    }

    // The confirmation dialog itself. Cancel leaves everything untouched —
    // for a Save, that means the ScheduleEditDialog above (still open,
    // since we never called showAddSchedule = null for this path) simply
    // reappears with nothing changed; for a Toggle, the switch just stays
    // off since updateSchedule() was never called.
    pendingImmediateStart?.let { pending ->
        val schedule = when (pending) {
            is PendingImmediateStart.Save   -> pending.schedule
            is PendingImmediateStart.Toggle -> pending.schedule
        }
        AlertDialog(
            onDismissRequest = { pendingImmediateStart = null },
            title = { Text("Starts lockdown now") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This schedule covers right now — saving it will start a lockdown immediately.")
                    if (schedule.label.isNotBlank()) {
                        Text(
                            "\u201C${schedule.label}\u201D \u00B7 ${BlockEngine.formatMinutes(schedule.startMinutes)} \u2013 ${BlockEngine.formatMinutes(schedule.endMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (pending) {
                        is PendingImmediateStart.Save -> {
                            commitScheduleSave(pending.schedule)
                            showAddSchedule = null
                        }
                        is PendingImmediateStart.Toggle -> {
                            BlockerRepository.updateSchedule(pending.schedule.copy(enabled = true))
                        }
                    }
                    pendingImmediateStart = null
                }) { Text("Start lockdown now") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImmediateStart = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SchedulePermissionNotice(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, AccentRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .pressable(onClick = onGrant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Permission needed",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Tap to allow exact alarms, or schedules could start late \u2014 or not at all.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun EmptyHintCard(modifier: Modifier = Modifier, text: String) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(containerColor = CardSurface),
            border   = BorderStroke(1.dp, TextMuted.copy(alpha = 0.14f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier.size(44.dp).clip(CircleShape).background(TextMuted.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = TextTertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: LockdownSchedule,
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
    // Matches the app's own shape token for "list item cards" — same one the
    // Strict Alarms cards use — instead of a one-off custom radius, so both
    // lists read as the same visual language.
    val cardShape = MaterialTheme.shapes.large

    // ── Swipe-to-REVEAL delete (not swipe-to-delete) ────────────────────────
    // Identical two-beat behaviour to the Strict Alarms list:
    //   1. Swipe left → the card slides over and STOPS, revealing a red
    //      delete button behind it. Nothing is deleted yet.
    //   2. Tap that button, or swipe left again (further, or a fast flick)
    //      → NOW it deletes.
    //   3. Swipe back right, tap the card itself, or open a different
    //      card's action → this one closes with nothing lost.
    // Hand-built with Modifier.draggable + Animatable, same as AlarmCard in
    // StrictAlarmListScreen.kt — see that file's comment for the full
    // reasoning on why SwipeToDismissBox wasn't used and why this doesn't
    // fight ReorderableColumn's long-press drag.
    val density   = LocalDensity.current
    val haptics   = rememberHaptics()
    val dragScope = rememberCoroutineScope()

    val revealWidthPx = with(density) { 78.dp.toPx() }
    val extraCommitPx = revealWidthPx * 0.9f
    val absoluteCommitPx = revealWidthPx + extraCommitPx
    val maxDragPx = revealWidthPx + extraCommitPx * 1.5f

    val offsetX = remember(schedule.id) { Animatable(0f) }
    var buzzedReveal by remember(schedule.id) { mutableStateOf(false) }
    var buzzedCommit by remember(schedule.id) { mutableStateOf(false) }
    // Captured once per gesture, at the moment a drag begins: was the card
    // already sitting fully revealed when this swipe started? Only that
    // second swipe is allowed to travel into "delete" territory.
    var dragStartedFromRevealed by remember(schedule.id) { mutableStateOf(false) }

    // A different card was swiped open, or the parent asked this one to
    // close — snap shut instead of ever leaving two cards open at once.
    LaunchedEffect(isRevealed) {
        if (!isRevealed && offsetX.value != 0f) {
            offsetX.animateTo(0f, MotionSpecs.tactile())
            buzzedReveal = false
            buzzedCommit = false
        }
    }

    suspend fun settleDrag(velocity: Float, startedFromRevealed: Boolean) {
        val current = offsetX.value

        if (!startedFromRevealed) {
            // FIRST swipe: deleting is never on the table here, by design.
            if (current <= -revealWidthPx * 0.5f) {
                offsetX.animateTo(-revealWidthPx, MotionSpecs.tactile())
                onRevealChange(true)
            } else {
                offsetX.animateTo(0f, MotionSpecs.tactile())
                onRevealChange(false)
                buzzedReveal = false
            }
            return
        }

        // SECOND swipe, starting from an already-revealed card: the only
        // gesture allowed to commit to delete.
        val flungFast = velocity < -700f
        when {
            current <= -absoluteCommitPx || (flungFast && current <= -revealWidthPx * 1.3f) -> {
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
                buzzedCommit = false
            }
        }
    }

    fun closeReveal() {
        dragScope.launch {
            offsetX.animateTo(0f, MotionSpecs.tactile())
            onRevealChange(false)
            buzzedReveal = false
            buzzedCommit = false
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // ── Red delete action, sitting behind the card ───────────────────
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
                    contentDescription = "Delete schedule",
                    tint     = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── Foreground card — slides left to reveal the action above ─────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX          = scale
                    scaleY          = scale
                    shadowElevation = if (isDragging) 24f else 0f
                    shape           = cardShape
                }
                .shadow(elevation, cardShape, clip = false)
                .background(CardSurface, cardShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragScope.launch {
                            val current = offsetX.value

                            if (!dragStartedFromRevealed) {
                                val next = (current + delta).coerceIn(-revealWidthPx, 0f)
                                offsetX.snapTo(next)
                                if (!buzzedReveal && next <= -revealWidthPx * 0.99f) {
                                    haptics.tap()
                                    buzzedReveal = true
                                } else if (next > -revealWidthPx * 0.5f) {
                                    buzzedReveal = false
                                }
                            } else {
                                val scaledDelta = if (current <= -revealWidthPx && delta < 0f) {
                                    val extra    = (-current - revealWidthPx).coerceAtLeast(0f)
                                    val maxExtra = maxDragPx - revealWidthPx
                                    val resistance = 1f - (extra / maxExtra).coerceIn(0f, 1f) * 0.72f
                                    delta * resistance
                                } else delta
                                val next = (current + scaledDelta).coerceIn(-maxDragPx, 0f)
                                offsetX.snapTo(next)

                                if (!buzzedReveal && next <= -revealWidthPx * 0.99f) {
                                    haptics.tap()
                                    buzzedReveal = true
                                } else if (next > -revealWidthPx * 0.5f) {
                                    buzzedReveal = false
                                }
                                if (!buzzedCommit && next <= -absoluteCommitPx) {
                                    haptics.tap()
                                    buzzedCommit = true
                                } else if (next > -absoluteCommitPx * 0.9f) {
                                    buzzedCommit = false
                                }
                            }
                        }
                    },
                    onDragStarted = {
                        dragStartedFromRevealed = offsetX.value <= -revealWidthPx * 0.99f
                    },
                    onDragStopped = { velocity -> settleDrag(velocity, dragStartedFromRevealed) }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // pressable gives the card a tactile 4% squeeze on tap,
                    // with no ripple — same feel as every other card in the
                    // app. While the delete action is showing, tapping the
                    // card just closes it back up instead of opening edit.
                    .pressable(onClick = {
                        if (offsetX.value != 0f) closeReveal() else onClick()
                    })
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    if (schedule.label.isNotBlank()) {
                        Text(
                            text       = schedule.label,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (schedule.enabled) TextPrimary else TextMuted
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text  = "${BlockEngine.formatMinutes(schedule.startMinutes)} \u2013 ${BlockEngine.formatMinutes(schedule.endMinutes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (schedule.enabled) TextTertiary else TextMuted
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = repeatSummary(schedule.daysOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Switch(
                    checked         = schedule.enabled,
                    onCheckedChange = { checked -> onToggle(checked) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentTeal
                    )
                )
            }
        }
    }
}

private fun repeatSummary(daysOfWeek: Set<Int>): String {
    if (daysOfWeek.isEmpty()) return "Never"
    if (daysOfWeek.size == 7) return "Every day"
    val weekdays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    val weekend  = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    if (daysOfWeek == weekdays) return "Weekdays"
    if (daysOfWeek == weekend)  return "Weekends"
    return daysOfWeek.sortedBy { DAY_ORDER.indexOf(it) }.mapNotNull { DAY_LABELS[it] }.joinToString(" \u00B7 ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditDialog(schedule: LockdownSchedule, onDismiss: () -> Unit, onSave: (LockdownSchedule) -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(schedule.label) }
    var start by remember { mutableStateOf(schedule.startMinutes) }
    var end   by remember { mutableStateOf(schedule.endMinutes) }
    var days  by remember { mutableStateOf(schedule.daysOfWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Lockdown schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (optional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pickTime(context, start) { start = it } }) { Text("From ${BlockEngine.formatMinutes(start)}") }
                    OutlinedButton(onClick = { pickTime(context, end)   { end   = it } }) { Text("To ${BlockEngine.formatMinutes(end)}") }
                }
                Text("Active on:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_ORDER.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick  = { days = if (day in days) days - day else days + day },
                            label    = { Text(DAY_LABELS[day] ?: "") }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(schedule.copy(label = label, startMinutes = start, endMinutes = end, daysOfWeek = days)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun pickTime(context: android.content.Context, currentMinutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, currentMinutes / 60, currentMinutes % 60, false).show()
}
