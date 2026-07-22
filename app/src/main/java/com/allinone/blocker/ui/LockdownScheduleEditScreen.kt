package com.allinone.blocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.LockdownSchedule
import com.allinone.blocker.ui.motion.AnimatedAppearance
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import java.util.Calendar

// ════════════════════════════════════════════════════════════════════════════
// The redesigned "pick a lockdown schedule" screen.
//
// WHY THIS EXISTS: the old version was a plain AlertDialog — a small boxed
// popup with a name field, two buttons that opened Android's stock
// black-and-white TimePickerDialog (which ignores this app's theme
// completely), and a row of boxy checkbox-style chips for days. It worked,
// but it looked and felt bolted on next to the rest of the app.
//
// THE APPROACH — a few borrowed, well-tested ideas rather than novelty for
// its own sake:
//   • A dedicated full screen, not a popup. A schedule is a real decision
//     ("I will not be able to use my phone for 8 hours") — Fitts's Law and
//     the Doherty threshold both argue for giving that decision room and
//     immediate visual feedback, not squeezing it into a 300dp box.
//   • A duration "hero" number up top. This is the single fact a person
//     actually cares about in the moment ("how long am I locking myself
//     out for?"), shown before the raw start/end clock times — the same
//     instinct behind Apple Health's Bedtime screen leading with "8 hr 15
//     min" rather than raw times.
//   • Quick-pick presets (Every day / Weekdays / Weekends) above the
//     individual day circles. Most people want one of those three;
//     Hick's Law says fewer, clearer choices for the common case beats
//     making everyone hand-pick 7 checkboxes every time. Full custom
//     control is still one tap away right below.
//   • The system time picker is now Material3's own TimePicker, shown
//     inside a card that matches the app's own colors and shapes, instead
//     of the stock OS dialog — same familiar dial interaction (Jakob's
//     Law: don't reinvent how people already set a time), just no longer
//     visually foreign to the rest of the app.
//   • A plain-English summary line always visible at the bottom
//     ("Weekdays · 11:00 PM – 7:00 AM") so what you're about to save is
//     never in question before you tap Save — visibility of system status.
//
// Nothing about *what* a schedule can do changed — same fields
// (label/startMinutes/endMinutes/daysOfWeek/strictModeProtected), same
// StrictModeProtectionToggle, same "would this start a lockdown right now"
// confirmation owned by LockdownSchedulesScreen. Only how you set those
// fields changed.
// ════════════════════════════════════════════════════════════════════════════

private enum class SchedulePickerTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    schedule: LockdownSchedule,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (LockdownSchedule) -> Unit
) {
    // System/gesture back closes the editor exactly like the X button —
    // discards the draft, same as before (nothing is written until Save).
    BackHandler(enabled = true) { onDismiss() }

    // Keyed on schedule.id so if this screen is ever reused for a different
    // schedule without being fully torn down, the draft resets correctly
    // instead of leaking the previous schedule's edits.
    var label by remember(schedule.id) { mutableStateOf(schedule.label) }
    var start by remember(schedule.id) { mutableStateOf(schedule.startMinutes) }
    var end by remember(schedule.id) { mutableStateOf(schedule.endMinutes) }
    var days by remember(schedule.id) { mutableStateOf(schedule.daysOfWeek) }
    var strictModeProtected by remember(schedule.id) { mutableStateOf(schedule.strictModeProtected) }

    var pickerTarget by remember { mutableStateOf<SchedulePickerTarget?>(null) }

    val durationText = remember(start, end) { formatDuration(scheduleDurationMinutes(start, end)) }
    val summaryText = remember(start, end, days) {
        "${repeatSummary(days)} \u00B7 ${BlockEngine.formatMinutes(start)} \u2013 ${BlockEngine.formatMinutes(end)}"
    }

    // The three "common case" presets from Hick's Law reasoning above.
    // Compared by value against the current `days` set so a preset pill
    // lights up automatically if it happens to match — no separate
    // "which mode am I in" state to keep in sync.
    val weekdaySet = remember {
        setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    }
    val weekendSet = remember { setOf(Calendar.SATURDAY, Calendar.SUNDAY) }
    val everyDaySet = remember { DAY_ORDER.toSet() }

    Scaffold(
        containerColor = BgScreen,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "New schedule" else "Edit schedule",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen)
            )
        },
        floatingActionButton = {
            // Static Idle state on purpose: whether this actually closes the
            // screen depends on LockdownSchedulesScreen's own "would this
            // start a lockdown right now?" confirmation, which may pop up
            // on top instead of an immediate save — so we don't fake a
            // Saving/Saved animation for a commit that might not happen yet.
            SaveButton(
                state = SaveState.Idle,
                onClick = {
                    onSave(
                        schedule.copy(
                            label = label,
                            startMinutes = start,
                            endMinutes = end,
                            daysOfWeek = days,
                            strictModeProtected = strictModeProtected
                        )
                    )
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))

            AnimatedAppearance {
                ScheduleNameField(value = label, onValueChange = { label = it })
            }

            Spacer(Modifier.height(24.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs) {
                DurationHero(durationText = durationText)
            }

            Spacer(Modifier.height(28.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Bedtime,
                        label = "Starts",
                        timeText = BlockEngine.formatMinutes(start),
                        onClick = { pickerTarget = SchedulePickerTarget.START }
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    TimeCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.WbSunny,
                        label = "Ends",
                        timeText = BlockEngine.formatMinutes(end),
                        onClick = { pickerTarget = SchedulePickerTarget.END }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 3) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "REPEAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetPill(
                            label = "Every day",
                            selected = days == everyDaySet,
                            onClick = { days = everyDaySet }
                        )
                        PresetPill(
                            label = "Weekdays",
                            selected = days == weekdaySet,
                            onClick = { days = weekdaySet }
                        )
                        PresetPill(
                            label = "Weekends",
                            selected = days == weekendSet,
                            onClick = { days = weekendSet }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DAY_ORDER.forEach { day ->
                            DayCircle(
                                label = DAY_LABELS[day]?.take(1) ?: "",
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 4) {
                LiveSummaryCard(text = summaryText)
            }

            Spacer(Modifier.height(20.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 5) {
                StrictModeProtectionToggle(
                    checked = strictModeProtected,
                    onCheckedChange = { strictModeProtected = it }
                )
            }

            // Clears the floating Save button so the last section is never
            // hidden behind it.
            Spacer(Modifier.height(96.dp))
        }
    }

    pickerTarget?.let { target ->
        PremiumTimePickerDialog(
            title = if (target == SchedulePickerTarget.START) "Starts" else "Ends",
            initialMinutes = if (target == SchedulePickerTarget.START) start else end,
            onDismiss = { pickerTarget = null },
            onConfirm = { minutes ->
                if (target == SchedulePickerTarget.START) start = minutes else end = minutes
                pickerTarget = null
            }
        )
    }
}

// ── Duration hero ───────────────────────────────────────────────────────────
// The single number people actually care about in the moment, shown before
// the raw clock times below it — mirrors Apple Health's Bedtime screen
// leading with the total sleep duration rather than the raw times.
@Composable
private fun DurationHero(durationText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccentBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Locks your phone for",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            durationText,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

// ── Start / End time card ──────────────────────────────────────────────────
@Composable
private fun TimeCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    timeText: String,
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(CardSurface)
            .border(1.dp, TextMuted.copy(alpha = 0.14f), MaterialTheme.shapes.large)
            .pressable(onClick = {
                haptics.tap()
                onClick()
            })
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            timeText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

// ── Repeat presets & day circles ───────────────────────────────────────────
// AccentTeal on purpose — the same color the schedules list already uses for
// a schedule's own enabled/disabled switch (see ScheduleCard in
// LockdownSchedulesScreen.kt), so "which days this is active" reads as one
// consistent color language across the list and the editor.
@Composable
private fun PresetPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentTeal else CardSurface)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else TextMuted.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50)
            )
            .pressable(onClick = {
                haptics.tap()
                onClick()
            })
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun DayCircle(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) AccentTeal else CardSurface)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else TextMuted.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .pressable(onClick = {
                haptics.tap()
                onClick()
            }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

// ── Live plain-English summary ─────────────────────────────────────────────
// Nielsen's "visibility of system status" — restates exactly what will be
// saved, in the same wording the schedules list itself uses (repeatSummary),
// so there's never a surprise between the editor and the list afterwards.
@Composable
private fun LiveSummaryCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, TextMuted.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ── Name field ──────────────────────────────────────────────────────────────
// Underline-only, transparent background, no boxed border — quieter than a
// full OutlinedTextField so the name reads as an optional label rather than
// the first thing demanding attention (that's the duration hero below it).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleNameField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                "Name this schedule (optional)",
                style = MaterialTheme.typography.titleMedium,
                color = TextMuted
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = AccentBlue,
            unfocusedIndicatorColor = TextMuted.copy(alpha = 0.25f),
            cursorColor = AccentBlue,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── Themed time picker ─────────────────────────────────────────────────────
// Material3's own TimePicker (a clock-face dial — the same familiar
// interaction as the stock OS picker per Jakob's Law) shown inside a card
// styled with this app's own shapes/colors instead of Android's unthemed
// black-and-white TimePickerDialog. Always 12-hour with AM/PM to match
// BlockEngine.formatMinutes(), which is what every other time in the app
// (including this screen's own time cards) is displayed with.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60) % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Set", fontWeight = FontWeight.SemiBold, color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextTertiary) }
        }
    )
}

// ── Small pure helpers ─────────────────────────────────────────────────────

/**
 * Minutes between [start] and [end] on a 24-hour clock, wrapping past
 * midnight when [end] is earlier than [start] (e.g. 11:00 PM \u2192 7:00 AM
 * is 480 minutes, not negative). Mirrors how LockdownEngine itself treats an
 * overnight window.
 */
private fun scheduleDurationMinutes(start: Int, end: Int): Int =
    if (end > start) end - start else (24 * 60 - start) + end

/** "8h 30m" / "8h" / "45m" — never shows a redundant "0h" or "0m" half. */
private fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 && minutes == 0 -> "0m"
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}
