package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictAlarm
import com.allinone.blocker.data.STRICT_ALARM_INTERVAL_MINUTES
import com.allinone.blocker.data.STRICT_ALARM_MAX_COUNT
import com.allinone.blocker.data.allNextTriggerMillis
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

private val DAY_LABELS = listOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmScreen(onBack: () -> Unit, onOpenSleepCalculator: () -> Unit = {}) {
    val context = LocalContext.current
    val alarm by BlockerRepository.strictAlarm.collectAsState()
    val canScheduleExact = remember { AlarmScheduler.canScheduleExact(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Strict Alarm", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!canScheduleExact) {
                PermissionWarningCard(
                    onGrant = { AlarmScheduler.requestExactAlarmPermission(context) }
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Strict alarm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "You'll need to solve a puzzle to turn it off — no snooze.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = alarm.enabled,
                            onCheckedChange = { checked ->
                                val updated = alarm.copy(enabled = checked)
                                BlockerRepository.setStrictAlarm(updated)
                                if (checked) {
                                    AlarmScheduler.schedule(context, updated)
                                } else {
                                    AlarmScheduler.cancel(context)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Time display — tap to open the time picker.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formatTime(alarm.hour, alarm.minute),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue,
                            modifier = Modifier.clickable {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val updated = alarm.copy(hour = hour, minute = minute)
                                        BlockerRepository.setStrictAlarm(updated)
                                        if (updated.enabled) AlarmScheduler.schedule(context, updated)
                                    },
                                    alarm.hour,
                                    alarm.minute,
                                    false
                                ).show()
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Repeat on",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DAY_LABELS.forEach { (dayConst, label) ->
                            val selected = dayConst in alarm.daysOfWeek
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val newDays = alarm.daysOfWeek.toMutableSet().apply {
                                        if (selected) remove(dayConst) else add(dayConst)
                                    }
                                    val updated = alarm.copy(daysOfWeek = newDays)
                                    BlockerRepository.setStrictAlarm(updated)
                                    if (updated.enabled) AlarmScheduler.schedule(context, updated)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

               }
            }

            // ── Multi-alarm card ─────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Set multiple alarms at once",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Each alarm rings $STRICT_ALARM_INTERVAL_MINUTES minutes after the last.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = alarm.multiAlarmEnabled,
                            onCheckedChange = { checked ->
                                val updated = alarm.copy(multiAlarmEnabled = checked)
                                BlockerRepository.setStrictAlarm(updated)
                                if (updated.enabled) AlarmScheduler.schedule(context, updated)
                            }
                        )
                    }

                    if (alarm.multiAlarmEnabled) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                val updated = alarm.copy(alarmCount = count)
                                BlockerRepository.setStrictAlarm(updated)
                                if (updated.enabled) AlarmScheduler.schedule(context, updated)
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
                            Spacer(Modifier.height(12.dp))
                            Text(
                                buildMultiAlarmPreview(alarm),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (alarm.enabled) {
                NextRingCard(alarm)
            }

            SleepCalculatorEntryCard(onClick = onOpenSleepCalculator)

            Text(
                "Heads up: dismissing this alarm currently uses a basic math puzzle. " +
                    "Hooking it up to your full Strict Mode challenges (PIN, pledge, cooldown) is a planned next step.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PermissionWarningCard(onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardSurface)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AccentAmber)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Permission needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Android needs your permission to wake the phone at an exact time. " +
                    "Without it, this alarm could ring late or not at all.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onGrant) {
                Text("Allow exact alarms")
            }
        }
    }
}

@Composable
private fun SleepCalculatorEntryCard(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = AccentPurple)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Sleep calculator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Find the best time to sleep or wake using full sleep cycles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun NextRingCard(alarm: StrictAlarm) {
    val upcoming = remember(alarm) { alarm.allNextTriggerMillis() }
    Card(colors = CardDefaults.cardColors(containerColor = CardSurface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AlarmOn, contentDescription = null, tint = AccentBlue)
                Spacer(Modifier.size(12.dp))
                Text(
                    when {
                        upcoming.isEmpty() -> "No upcoming alarm — pick at least one day"
                        upcoming.size == 1 -> "Next alarm: ${formatFullDate(upcoming.first())}"
                        else -> "Next ${upcoming.size} alarms"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (upcoming.size > 1) {
                upcoming.forEachIndexed { index, millis ->
                    Text(
                        "${index + 1}. ${formatFullDate(millis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
            }
        }
    }
}

private fun buildMultiAlarmPreview(alarm: StrictAlarm): String {
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
