package com.allinone.blocker.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.ui.motion.StaggeredColumn
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

/**
 * The "all alarms" list screen — shows every [StrictAlarmEntry] as a clean,
 * card-less row (big time, short repeat-days summary, a toggle on the
 * right), closest in spirit to a normal phone clock app's alarm list.
 * Tapping a row opens the editor for that one alarm; tapping "+" creates a
 * new one and opens the editor for it.
 *
 * @param onBack            back arrow in the top bar.
 * @param onAddAlarm        "+" button tapped — caller should create a new
 *                          entry (e.g. via BlockerRepository.newStrictAlarmId()
 *                          + StrictAlarmEntry.newDefault()) and navigate to
 *                          an editor for it.
 * @param onEditAlarm       a row was tapped — caller should navigate to an
 *                          editor for this entry's id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmListScreen(
    onBack: () -> Unit,
    onAddAlarm: () -> Unit = {},
    onEditAlarm: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val alarms by BlockerRepository.strictAlarms.collectAsState()

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
            StaggeredColumn(items = sorted, spacing = 0.dp) { entry ->
                AlarmRow(
                    entry = entry,
                    onToggle = { checked ->
                        BlockerRepository.setStrictAlarmEntryEnabled(entry.id, checked)
                        val updated = entry.copy(enabled = checked)
                        AlarmScheduler.schedule(context, updated)
                    },
                    onClick = { onEditAlarm(entry.id) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlarmRow(
    entry: StrictAlarmEntry,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatTime(entry.hour, entry.minute),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (entry.enabled) MaterialTheme.colorScheme.onBackground else TextMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = repeatSummary(entry.daysOfWeek),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.enabled) TextSecondary else TextMuted
            )
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue
            )
        )
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
                    .pressable(onClick = onAddAlarm)
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
