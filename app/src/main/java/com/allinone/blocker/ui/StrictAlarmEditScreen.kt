package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmChallengeDifficulty
import com.allinone.blocker.data.AlarmChallengeType
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.data.effectiveChallengeRounds
import com.allinone.blocker.data.effectiveTypingPhrase
import com.allinone.blocker.data.nextTriggerMillis
import com.allinone.blocker.ui.motion.Haptics
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

/**
 * Editor for ONE [StrictAlarmEntry].
 *
 * Changes are held locally until the user taps Save — nothing is written to
 * the repository or the alarm scheduler until that moment. The Save button
 * cycles through Idle → Loading → Done, then auto-resets.
 *
 * The on/off toggle at the top is the only thing that still saves immediately,
 * because toggling an alarm on/off is a deliberate one-tap action that should
 * take effect right away (same pattern as the list screen).
 *
 * @param isNew  true when opened from the + button — means the entry does NOT
 *               exist in the repository yet. Back without saving discards
 *               the draft silently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmEditScreen(
    alarmId: String,
    isNew: Boolean = false,
    onBack: () -> Unit,
    onSaved: (StrictAlarmEntry) -> Unit = {},
    onDelete: (String) -> Unit = {}
) {
    val context   = LocalContext.current
    val haptics   = rememberHaptics()
    val allAlarms by BlockerRepository.strictAlarms.collectAsState()

    // ── Source of truth from the repository ──────────────────────────────
    val savedAlarm = remember(allAlarms, alarmId) {
        allAlarms.firstOrNull { it.id == alarmId }
            ?: StrictAlarmEntry(id = alarmId, requestCode = BlockerRepository.nextAlarmRequestCode())
    }

    // ── Local draft — only written to repo when Save is tapped ───────────
    var draft by remember(savedAlarm) { mutableStateOf(savedAlarm) }

    // Keep draft in sync if the alarm changes externally (e.g. another screen)
    // but only when we have no unsaved edits (draft == savedAlarm means untouched)
    LaunchedEffect(savedAlarm) {
        if (draft == savedAlarm) draft = savedAlarm
    }

    val canScheduleExact = remember { AlarmScheduler.canScheduleExact(context) }

    // ── Save button state ─────────────────────────────────────────────────
    var saveState by remember { mutableStateOf(SaveState.Idle) }

    // ── The actual persist function — only called on Save tap ─────────────
    fun commitSave() {
        val updated = draft
        if (allAlarms.any { it.id == updated.id }) {
            BlockerRepository.updateStrictAlarmEntry(updated)
        } else {
            BlockerRepository.addStrictAlarmEntry(updated)
        }
        AlarmScheduler.schedule(context, updated)
    }

    // ── Immediate save for the enabled toggle — only for existing alarms ──
    fun saveEnabled(checked: Boolean) {
        val updated = draft.copy(enabled = checked)
        draft = updated
        if (!isNew || allAlarms.any { it.id == updated.id }) {
            BlockerRepository.updateStrictAlarmEntry(updated)
            AlarmScheduler.schedule(context, updated)
        }
    }

   Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onDelete(draft.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete alarm", tint = AccentRed)
                    }
                }
            )
        },
        floatingActionButton = {
            SaveButton(
                state   = saveState,
                onClick = {
                    saveState = SaveState.Loading
                    commitSave()
                    saveState = SaveState.Done
                },
                // Fires automatically a moment after "Saved" — hands the
                // freshly-saved alarm back to the caller, which navigates
                // to the list AND (via onSaved) triggers a brief "Alarm
                // set for X from now" message there — same beat as stock
                // Android/Samsung's Clock app.
                onReset = { onSaved(draft) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── On/off toggle — saves immediately ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Alarm on",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { checked -> saveEnabled(checked) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue
                    )
                )
            }

            if (!canScheduleExact) {
                Spacer(Modifier.height(20.dp))
                PermissionNotice(onGrant = { AlarmScheduler.requestExactAlarmPermission(context) })
            }

            Spacer(Modifier.height(28.dp))

            // ── Big time display — tap to change ──────────────────────────
            Text(
                text = formatEditTime(draft.hour, draft.minute),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> draft = draft.copy(hour = hour, minute = minute) },
                        draft.hour,
                        draft.minute,
                        false
                    ).show()
                }
            )

            Spacer(Modifier.height(36.dp))

            // ── Repeat days ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "Repeat on",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                // Live summary of the current repeat state — "Once" is the
                // default for a brand-new alarm (no days picked yet), the
                // same wording and the same "doesn't repeat" default every
                // stock alarm app uses. Picking any day chip below turns
                // this into "Every day", "Weekdays", or the specific days.
                Text(
                    text  = repeatCaption(draft.daysOfWeek),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DAY_LABELS.forEach { (dayConst, label) ->
                    val selected = dayConst in draft.daysOfWeek
                    DayChip(
                        label    = label,
                        selected = selected,
                        onClick  = {
                            val newDays = draft.daysOfWeek.toMutableSet().apply {
                                if (selected) remove(dayConst) else add(dayConst)
                            }
                            draft = draft.copy(daysOfWeek = newDays)
                        }
                    )
                }
            }

            if (draft.enabled) {
                Spacer(Modifier.height(32.dp))
                NextRingNotice(draft)
            }

            Spacer(Modifier.height(32.dp))

            // ── Snooze — a normal alarm-level setting, independent of
            // Strict Mode below. Every stock alarm clock has this. ────────
            SnoozeSection(
                draft = draft,
                onChange = { updated -> draft = updated }
            )

            Spacer(Modifier.height(24.dp))

            // ── Strict Mode — OFF by default. This entire section is the
            // "optional addition" — turning it on is the only thing that
            // makes this alarm behave any differently from a normal one. ──
            StrictModeSection(
                draft = draft,
                haptics = haptics,
                onChange = { updated -> draft = updated }
            )

            // ── Bottom padding so content clears the FAB ──────────────────
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = if (selected) 0.dp else 3.dp,
                shape     = RoundedCornerShape(10.dp),
                clip      = false
            )
            .background(
                color  = if (selected) AccentBlue else CardSurface,
                shape  = RoundedCornerShape(10.dp)
            )
            .pressable(pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else TextSecondary,
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionNotice(onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                "Tap to allow exact alarms, or this could ring late.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun NextRingNotice(alarm: StrictAlarmEntry) {
    val next = remember(alarm) { alarm.nextTriggerMillis() }
    Text(
        text = if (next != null)
            "Next alarm: ${formatFullDate(next)}"
        else
            "No upcoming alarm — pick at least one day",
        style      = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color      = TextSecondary,
        modifier   = Modifier.fillMaxWidth()
    )
}

private val DAY_LABELS = listOf(
    Calendar.MONDAY    to "M", Calendar.TUESDAY   to "T", Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY  to "T", Calendar.FRIDAY    to "F", Calendar.SATURDAY  to "S",
    Calendar.SUNDAY    to "S"
)

/** "Once", "Every day", "Weekdays", "Weekends", or a specific-days list. */
private fun repeatCaption(daysOfWeek: Set<Int>): String {
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

private fun formatEditTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}

private fun formatFullDate(millis: Long): String {
    return java.text.SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        .format(java.util.Date(millis))
}

// ─────────────────────────────────────────────────────────────────────────────
// SNOOZE — a normal, always-visible alarm setting (independent of Strict
// Mode below it). Every stock alarm clock has this; this app didn't before.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SnoozeSection(draft: StrictAlarmEntry, onChange: (StrictAlarmEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Snooze", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    if (draft.snoozeEnabled) "Adds a Snooze button, ${draft.snoozeMinutes} min at a time"
                    else "No Snooze button on the ring screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = draft.snoozeEnabled,
                onCheckedChange = { onChange(draft.copy(snoozeEnabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBlue)
            )
        }
        if (draft.snoozeEnabled) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 9, 10, 15).forEach { minutes ->
                    SnoozeMinuteChip(
                        minutes  = minutes,
                        selected = draft.snoozeMinutes == minutes,
                        onClick  = { onChange(draft.copy(snoozeMinutes = minutes)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SnoozeMinuteChip(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentBlue else CardSurface)
            .pressable(pressedScale = 0.92f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            "${minutes}m",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else TextSecondary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STRICT MODE — OFF by default (see StrictAlarmEntry.strictModeEnabled).
// This whole card is the "optional addition" — every field inside it is
// inert until the switch at the top is turned on.
// ─────────────────────────────────────────────────────────────────────────────

private data class ChallengeTypeInfo(val type: AlarmChallengeType, val emoji: String, val label: String)

private val CHALLENGE_TYPES = listOf(
    ChallengeTypeInfo(AlarmChallengeType.MATH, "🧮", "Math"),
    ChallengeTypeInfo(AlarmChallengeType.TYPING, "⌨️", "Typing"),
    ChallengeTypeInfo(AlarmChallengeType.SHAKE, "📳", "Shake")
)

private val DIFFICULTY_LABELS = listOf(
    AlarmChallengeDifficulty.EASY   to "Easy",
    AlarmChallengeDifficulty.MEDIUM to "Medium",
    AlarmChallengeDifficulty.HARD   to "Hard"
)

@Composable
private fun StrictModeSection(
    draft: StrictAlarmEntry,
    haptics: Haptics,
    onChange: (StrictAlarmEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(CardSurfaceAlt)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint     = AccentPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Strict Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    if (draft.strictModeEnabled) "Solve a challenge before it'll stop ringing"
                    else "Off — a normal alarm, one tap to dismiss",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = draft.strictModeEnabled,
                onCheckedChange = { checked ->
                    haptics.toggleTick()
                    onChange(draft.copy(strictModeEnabled = checked))
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBlue)
            )
        }

        AnimatedVisibility(
            visible = draft.strictModeEnabled,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 22.dp)) {

                Text("Challenge", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CHALLENGE_TYPES.forEach { info ->
                        ChallengeTypeOption(
                            emoji    = info.emoji,
                            label    = info.label,
                            selected = draft.challengeType == info.type,
                            onClick  = {
                                haptics.toggleTick()
                                onChange(draft.copy(challengeType = info.type))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                if (draft.challengeType == AlarmChallengeType.TYPING) {
                    Text("Phrase to type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value          = draft.typingPhrase,
                        onValueChange  = { onChange(draft.copy(typingPhrase = it)) },
                        placeholder    = { Text(draft.effectiveTypingPhrase, color = TextSecondary) },
                        singleLine     = true,
                        shape          = RoundedCornerShape(12.dp),
                        modifier       = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple,
                            cursorColor         = AccentPurple
                        )
                    )
                } else {
                    Text("Difficulty", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DIFFICULTY_LABELS.forEach { (level, label) ->
                            DifficultySegment(
                                label    = label,
                                selected = draft.challengeDifficulty == level,
                                onClick  = {
                                    haptics.toggleTick()
                                    onChange(draft.copy(challengeDifficulty = level))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Rounds", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("How many times in a row", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    RoundsStepper(
                        value    = draft.effectiveChallengeRounds,
                        haptics  = haptics,
                        onChange = { onChange(draft.copy(challengeRounds = it)) }
                    )
                }

                if (draft.snoozeEnabled) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Tip: turn off Snooze above for zero escape hatch",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeTypeOption(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentPurple.copy(alpha = 0.18f) else CardSurface)
            .border(
                width = 1.5.dp,
                color = if (selected) AccentPurple else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) AccentPurple else TextSecondary
        )
    }
}

@Composable
private fun DifficultySegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentPurple else CardSurface)
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun RoundsStepper(
    value: Int,
    haptics: Haptics,
    onChange: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick  = { haptics.tap(); onChange((value - 1).coerceIn(1, 5)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Filled.Remove, null, tint = TextSecondary)
        }
        Text(
            "$value",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.width(24.dp)
        )
        IconButton(
            onClick  = { haptics.tap(); onChange((value + 1).coerceIn(1, 5)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Filled.Add, null, tint = TextSecondary)
        }
    }
}
