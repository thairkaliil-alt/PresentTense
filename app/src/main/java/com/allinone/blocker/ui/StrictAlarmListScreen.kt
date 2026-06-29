package com.allinone.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.STRICT_ALARM_INTERVAL_MINUTES
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.ui.motion.StaggeredColumn
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

/**
 * The "all alarms" list screen. Each StrictAlarmEntry appears as one or more
 * rows depending on its multi-alarm count — so if you set 3 alarms at 7:00 AM,
 * you see three rows: 7:00, 7:03, 7:06, all grouped under the same entry.
 * Each row has its own toggle. Long-pressing any row shows a delete dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmListScreen(
    onBack: () -> Unit,
    onAddAlarm: () -> Unit = {},
    onEditAlarm: (String) -> Unit = {},
    onOpenSleepCalculator: () -> Unit = {}
) {
    val context = LocalContext.current
    val alarms by BlockerRepository.strictAlarms.collectAsState()

    // Which alarm entry the user long-pressed (null = no dialog showing)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete alarm?") },
            text = { Text("This alarm will be removed and cancelled.") },
            confirmButton = {
    TextButton(onClick = {
        val id = pendingDeleteId!!
        val alarmToDelete = alarms.firstOrNull { it.id == id }
        BlockerRepository.removeStrictAlarmEntry(id)
        if (alarmToDelete != null) {
            AlarmScheduler.cancel(context, alarmToDelete)
        } else {
            AlarmScheduler.cancel(context, id)
        }
        pendingDeleteId = null
    }) {
        Text("Delete", color = AccentRed)
    }
},
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
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
                    IconButton(onClick = onAddAlarm) {
                        Icon(Icons.Filled.Add, contentDescription = "Add alarm")
                    }
                }
            )
        }
    ) { pad ->
        if (alarms.isEmpty()) {
            EmptyAlarmsState(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                onAddAlarm = onAddAlarm
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val sorted = remember(alarms) {
                alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
            }

            // Each entry expands into one row per burst slot
            sorted.forEach { entry ->
                val slotCount = entry.effectiveAlarmCount()
                for (slotIndex in 0 until slotCount) {
                    val slotMinuteOffset = slotIndex * STRICT_ALARM_INTERVAL_MINUTES
                    val totalMinutes = entry.hour * 60 + entry.minute + slotMinuteOffset
                    val displayHour = (totalMinutes / 60) % 24
                    val displayMinute = totalMinutes % 60

                    // For burst rows after the first, show a subtle "part of N" label
                    val subLabel = when {
                        slotCount == 1 -> repeatSummary(entry.daysOfWeek)
                        slotIndex == 0 -> repeatSummary(entry.daysOfWeek) + "  ·  ${slotCount}× burst"
                        else -> "+${slotMinuteOffset} min"
                    }

                    AlarmRow(
                        hour = displayHour,
                        minute = displayMinute,
                        subLabel = subLabel,
                        enabled = entry.enabled,
                        // Only the first slot row shows the toggle (controls the whole entry)
                        showToggle = slotIndex == 0,
                        onToggle = { checked ->
                            BlockerRepository.setStrictAlarmEntryEnabled(entry.id, checked)
                            val updated = entry.copy(enabled = checked)
                            AlarmScheduler.schedule(context, updated)
                        },
                        onClick = { onEditAlarm(entry.id) },
                        onLongPress = { pendingDeleteId = entry.id }
                    )
                }

                // Thin divider between different alarm entries
                if (entry != sorted.last()) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 4.dp)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlarmRow(
    hour: Int,
    minute: Int,
    subLabel: String,
    enabled: Boolean,
    showToggle: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onClick, onLongPress) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatTime(hour, minute),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else TextMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) TextSecondary else TextMuted
            )
        }
        if (showToggle) {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentBlue
                )
            )
        } else {
            // Placeholder so the row width stays consistent
            Spacer(Modifier.size(52.dp))
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
                tint = TextMuted,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No alarms yet",
                style = MaterialTheme.typography.titleMedium,
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
                        detectTapGestures(onTap = { onAddAlarm() })
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

/** "Every day" / "Weekdays" / "Weekends" / "Tue, Thu" / "Once" style summary. */
private fun repeatSummary(daysOfWeek: Set<Int>): String {
    if (daysOfWeek.isEmpty()) return "Once"
    if (daysOfWeek.size == 7) return "Every day"

    val weekdays = setOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY
    )
    val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    if (daysOfWeek == weekdays) return "Weekdays"
    if (daysOfWeek == weekend) return "Weekends"

    val order = listOf(
        Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun"
    )
    return order.filter { it.first in daysOfWeek }.joinToString(", ") { it.second }
}

private fun formatTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}
