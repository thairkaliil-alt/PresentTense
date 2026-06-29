package com.allinone.blocker.ui

import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.STRICT_ALARM_INTERVAL_MINUTES
import com.allinone.blocker.data.STRICT_ALARM_MAX_COUNT
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.data.allNextTriggerMillis
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

/**
 * Card-less editor for ONE [StrictAlarmEntry] — a big clean time display
 * you tap to change, a row of small squarish "Repeat on" day chips (white,
 * soft shadow, pressable, fill with the accent color when selected), and a
 * toggle for the multi-alarm burst. No boxed Card backgrounds; content
 * floats directly on the screen background, sectioned by spacing only.
 *
 * @param alarmId          which entry to edit. If it's not found in
 *                         BlockerRepository.strictAlarms (e.g. the "+" button
 *                         was tapped before the new entry was saved), a fresh
 *                         default StrictAlarmEntry with this id is edited
 *                         instead, and saving will add it as new.
 * @param onBack           back / done button.
 * @param onDelete         delete button tapped — caller should remove this
 *                         entry and navigate back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmEditScreen(
    alarmId: String,
    onBack: () -> Unit,
    onDelete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val allAlarms by BlockerRepository.strictAlarms.collectAsState()
    val alarm = remember(allAlarms, alarmId) {
       allAlarms.firstOrNull { it.id == alarmId } ?: StrictAlarmEntry(id = alarmId, requestCode = BlockerRepository.nextAlarmRequestCode())
    }
    val canScheduleExact = remember { AlarmScheduler.canScheduleExact(context) }

    fun save(updated: StrictAlarmEntry) {
        // The alarm is always pre-saved to the list before this screen opens
        // (onAddAlarm in MainActivity calls addStrictAlarmEntry first).
        // So we always update — never add — which prevents duplicate entries.
        if (allAlarms.any { it.id == updated.id }) {
            BlockerRepository.updateStrictAlarmEntry(updated)
        } else {
            // Fallback: shouldn't happen normally, but handle it gracefully
            BlockerRepository.addStrictAlarmEntry(updated)
        }
        AlarmScheduler.schedule(context, updated)
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
                    IconButton(onClick = { onDelete(alarm.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete alarm", tint = AccentRed)
                    }
                }
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
            // ── On/off toggle, top of the editor ───────────────────────
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
                    checked = alarm.enabled,
                    onCheckedChange = { checked -> save(alarm.copy(enabled = checked)) },
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

            // ── Big clean time display — tap to change ─────────────────
            Text(
                text = formatTime(alarm.hour, alarm.minute),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> save(alarm.copy(hour = hour, minute = minute)) },
                        alarm.hour,
                        alarm.minute,
                        false
                    ).show()
                }
            )

            Spacer(Modifier.height(36.dp))

            // ── Repeat on — small square pressable day chips ───────────
            Text(
                "Repeat on",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DAY_LABELS.forEach { (dayConst, label) ->
                    val selected = dayConst in alarm.daysOfWeek
                    DayChip(
                        label = label,
                        selected = selected,
                        onClick = {
                            val newDays = alarm.daysOfWeek.toMutableSet().apply {
                                if (selected) remove(dayConst) else add(dayConst)
                            }
                            save(alarm.copy(daysOfWeek = newDays))
                        }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Multi-alarm burst ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Snooze burst",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "This alarm re-rings every $STRICT_ALARM_INTERVAL_MINUTES min. To add a separate alarm at a different time, tap + on the alarms list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = alarm.multiAlarmEnabled,
                    onCheckedChange = { checked -> save(alarm.copy(multiAlarmEnabled = checked)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue
                    )
                )
            }

            if (alarm.multiAlarmEnabled) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Number of alarms",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${alarm.alarmCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                }
                Slider(
                    value = alarm.alarmCount.toFloat(),
                    onValueChange = { value ->
                        val count = value.toInt().coerceIn(1, STRICT_ALARM_MAX_COUNT)
                        save(alarm.copy(alarmCount = count))
                    },
                    valueRange = 1f..STRICT_ALARM_MAX_COUNT.toFloat(),
                    steps = STRICT_ALARM_MAX_COUNT - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = AccentBlue.copy(alpha = 0.2f)
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("$STRICT_ALARM_MAX_COUNT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                if (alarm.alarmCount > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildMultiAlarmPreview(alarm),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (alarm.enabled) {
                Spacer(Modifier.height(32.dp))
                NextRingNotice(alarm)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One small square day chip — white, soft shadow, rounded corners, scales
 * down slightly on press (via [pressable]), and fills with the accent
 * color + white text when selected. This is the "clean, pressable, changes
 * color when selected" look from the reference design.
 */
@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = if (selected) 0.dp else 3.dp,
                shape = RoundedCornerShape(10.dp),
                clip = false
            )
            .background(
                color = if (selected) AccentBlue else CardSurface,
                shape = RoundedCornerShape(10.dp)
            )
            .pressable(pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary,
            textAlign = TextAlign.Center
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
                style = MaterialTheme.typography.titleSmall,
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
    val upcoming = remember(alarm) { alarm.allNextTriggerMillis() }
    Column(Modifier.fillMaxWidth()) {
        Text(
            when {
                upcoming.isEmpty() -> "No upcoming alarm — pick at least one day"
                upcoming.size == 1 -> "Next alarm: ${formatFullDate(upcoming.first())}"
                else -> "Next ${upcoming.size} alarms"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        if (upcoming.size > 1) {
            Spacer(Modifier.height(4.dp))
            upcoming.forEachIndexed { index, millis ->
                Text(
                    "${index + 1}. ${formatFullDate(millis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

private val DAY_LABELS = listOf(
    Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "T", Calendar.FRIDAY to "F", Calendar.SATURDAY to "S",
    Calendar.SUNDAY to "S"
)

private fun buildMultiAlarmPreview(alarm: StrictAlarmEntry): String {
    val times = (0 until alarm.alarmCount).map { index ->
        val totalMinutes = alarm.hour * 60 + alarm.minute + index * STRICT_ALARM_INTERVAL_MINUTES
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        formatTime(h, m)
    }
    return times.joinToString("  ·  ")
}

private fun formatTime(hour: Int, minute: Int): String {
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
