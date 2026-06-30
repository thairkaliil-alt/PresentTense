package com.allinone.blocker.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.STRICT_ALARM_INTERVAL_MINUTES
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmListScreen(
    onBack: () -> Unit,
    onAddAlarm: () -> Unit = {},
    onQuickAdd: () -> Unit = {},
    onEditAlarm: (String) -> Unit = {},
    onOpenSleepCalculator: () -> Unit = {}
) {
    val context = LocalContext.current
    val alarms by BlockerRepository.strictAlarms.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // ── Drag state ────────────────────────────────────────────────────────────
    // draggingIndex  = which card is currently being dragged (-1 = none)
    // dragOffsetY    = how many pixels the card has moved from its original spot
    // hoverIndex     = which slot the dragged card is currently hovering over
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY   by remember { mutableFloatStateOf(0f) }
    var hoverIndex    by remember { mutableIntStateOf(-1) }

    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    val density    = LocalDensity.current

    // Delete dialog
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete alarm?") },
            text  = { Text("This alarm will be removed and cancelled.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDeleteId!!
                    val toDelete = alarms.firstOrNull { it.id == id }
                    BlockerRepository.removeStrictAlarmEntry(id)
                    if (toDelete != null) AlarmScheduler.cancel(context, toDelete)
                    else AlarmScheduler.cancel(context, id)
                    pendingDeleteId = null
                }) { Text("Delete", color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
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

        // We keep a local mutable copy while dragging so the list animates
        // in real-time; we only commit to the repository when the finger lifts.
        var orderedAlarms by remember(alarms) { mutableStateOf(alarms) }

        LazyColumn(
            state   = listState,
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // ── Drag gesture — long-press then drag ───────────────────
                .pointerInput(orderedAlarms) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // Figure out which card the finger is over
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

                            // Work out which slot the card is hovering over now
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
                                    // OTHER (non-dragged) cards slide into their new
                                    // slots, via animateItem() on each AlarmCard below.
                                    val mutable = orderedAlarms.toMutableList()
                                    val fromIdx = hoverIndex.coerceIn(0, mutable.lastIndex)
                                    val toIdx   = newHover.coerceIn(0, mutable.lastIndex)
                                    val item    = mutable.removeAt(fromIdx)
                                    mutable.add(toIdx, item)
                                    orderedAlarms = mutable

                                    // The card being dragged now sits at a NEW index,
                                    // so Compose will lay it out at a different base
                                    // position next frame. Correct dragOffsetY by that
                                    // same amount so the card visually stays glued
                                    // under the finger instead of jumping — this is
                                    // separate from animateItem (which is deliberately
                                    // skipped on the dragged card; see itemModifier
                                    // below) and only concerns the one card under touch.
                                    dragOffsetY  += (draggingItem.offset - (itemInfo.firstOrNull { it.index == newHover }?.offset ?: draggingItem.offset))
                                    hoverIndex    = newHover
                                }
                            }

                            // Auto-scroll near edges
                            val viewportHeight = listState.layoutInfo.viewportEndOffset.toFloat()
                            val edgeZone = with(density) { 60.dp.toPx() }
                            val finger = change.position.y
                            scope.launch {
                                when {
                                    finger < edgeZone           -> listState.scrollBy(-12f)
                                    finger > viewportHeight - edgeZone -> listState.scrollBy(12f)
                                }
                            }
                        },
                        onDragEnd = {
                            // Commit the new order to the repository
                            BlockerRepository.reorderStrictAlarms(orderedAlarms)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(orderedAlarms, key = { _, entry -> entry.id }) { index, entry ->
                val isDragging = index == draggingIndex

                // Elevation and scale animate up when dragging this card
                val elevation by animateDpAsState(
                    targetValue    = if (isDragging) 12.dp else 2.dp,
                    animationSpec  = tween(150, easing = FastOutSlowInEasing),
                    label          = "cardElevation"
                )
                val scale by animateFloatAsState(
                    targetValue   = if (isDragging) 1.03f else 1f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label         = "cardScale"
                )

                // ── The actual "smooth reorder" fix ─────────────────────────
                // Modifier.animateItem() is Compose's own built-in reorder
                // animation: whenever an item's position in the list changes
                // (because we just swapped `orderedAlarms` mid-drag), every
                // OTHER card that got pushed up or down smoothly SLIDES into
                // its new slot instead of snapping there on the next frame.
                // Skipped on the card actually being dragged — that one is
                // already following the finger directly via dragOffsetY, so
                // animating it too would fight the live drag and feel laggy.
                // Also skipped entirely if the user has "reduce motion" on.
                val reducedMotion = LocalReducedMotion.current
                val itemModifier = if (isDragging || reducedMotion) {
                    Modifier
                } else {
                    // animateItem() needs a FiniteAnimationSpec<IntOffset>.
                    // StiffnessMedium (not the very stiff tap-feedback spring
                    // used elsewhere) so the slide is actually visible as the
                    // card travels several rows — a touch of bounce on
                    // landing reads as "alive" without feeling floaty.
                    Modifier.animateItem(
                        placementSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness    = androidx.compose.animation.core.Spring.StiffnessMedium,
                            visibilityThreshold = IntOffset.VisibilityThreshold                        )
                    )
                }

                AlarmCard(
                    entry      = entry,
                    isDragging = isDragging,
                    elevation  = elevation,
                    scale      = scale,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    modifier   = itemModifier,
                    onToggle   = { checked ->
                        BlockerRepository.setStrictAlarmEntryEnabled(entry.id, checked)
                        AlarmScheduler.schedule(context, entry.copy(enabled = checked))
                    },
                    onClick    = { onEditAlarm(entry.id) },
                    onDelete   = { pendingDeleteId = entry.id }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AlarmCard(
    entry: StrictAlarmEntry,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    scale: Float,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val slotCount = entry.effectiveAlarmCount()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX          = scale
                scaleY          = scale
                translationY    = dragOffsetY
                shadowElevation = if (isDragging) 24f else 0f
            }
            .shadow(elevation, RoundedCornerShape(20.dp), clip = false)
            .background(CardSurface, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // pressable gives the card a tactile 4% squeeze on tap,
                // with no ripple — same feel as every other card in the app.
                // It handles the click so we no longer need a pointerInput block.
                .pressable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Top row: time + drag handle ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text      = formatAlarmTime(entry.hour, entry.minute),
                    style     = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color     = if (entry.enabled) MaterialTheme.colorScheme.onBackground else TextMuted,
                    modifier  = Modifier.weight(1f)
                )

                // Drag handle — the visual cue that this card is draggable
                Icon(
                    imageVector        = Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint               = TextMuted,
                    modifier           = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Repeat-days summary ───────────────────────────────────────
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

            Spacer(Modifier.height(14.dp))

            // ── Bottom row: label (if any) + delete + toggle ──────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.label.isNotBlank()) {
                    Text(
                        text   = entry.label,
                        style  = MaterialTheme.typography.labelMedium,
                        color  = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // Delete icon
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete alarm",
                        tint               = AccentRed.copy(alpha = 0.7f),
                        modifier           = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // On/off toggle
                Switch(
                    checked       = entry.enabled,
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
