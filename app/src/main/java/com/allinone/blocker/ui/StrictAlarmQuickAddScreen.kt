package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.MULTI_ALARM_INTERVAL_MINUTES
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val MAX_ALARMS = 10
private const val MIN_ALARMS = 1

/**
 * The three repeat presets offered on the multi-add screen. The chosen
 * option's [days] set is applied to every alarm created in that batch —
 * the same [java.util.Calendar] day constants used everywhere else
 * ([StrictAlarmEntry.daysOfWeek]).
 */
private enum class MultiAddRepeatOption(val label: String, val days: Set<Int>) {
    EVERY_DAY("Every day", (1..7).toSet()),
    WEEKDAYS(
        "Weekdays",
        setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
    ),
    WEEKENDS("Weekends", setOf(Calendar.SATURDAY, Calendar.SUNDAY))
}

/**
 * "Add multiple alarms" screen.
 *
 * The user picks a START TIME and a COUNT (1–10). The app then creates that
 * many fully independent [StrictAlarmEntry] items, each [MULTI_ALARM_INTERVAL_MINUTES]
 * minutes apart, starting from the chosen time. Every alarm becomes its own card
 * on the alarm list — its own on/off switch, its own repeat days, deletable
 * independently — exactly like if the user had tapped "+" and saved that many
 * times in a row.
 *
 * The interval is currently fixed at [MULTI_ALARM_INTERVAL_MINUTES] (3 min).
 * When you're ready to expose this as a user setting, replace that constant
 * with a value read from SharedPreferences and add the slider to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmQuickAddScreen(
    onBack: () -> Unit,
    onDone: (List<StrictAlarmEntry>) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── State ─────────────────────────────────────────────────────────────
    // Start time — defaults to the current time rounded to the next minute
    val nowCal = remember {
        Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
    }
    var startHour   by remember { mutableIntStateOf(nowCal.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableIntStateOf(nowCal.get(Calendar.MINUTE)) }
    var alarmCount  by remember { mutableIntStateOf(3) } // sensible default
    var repeatOption by remember { mutableStateOf(MultiAddRepeatOption.EVERY_DAY) }
    var isCreating  by remember { mutableStateOf(false) }

    // ── Derived preview times ─────────────────────────────────────────────
    val previewTimes = remember(startHour, startMinute, alarmCount) {
        buildPreviewTimes(startHour, startMinute, alarmCount)
    }

    // ── Create all alarms ─────────────────────────────────────────────────
    fun createAll() {
        if (isCreating) return
        isCreating = true

        // Build every entry first so each one gets its own unique, stable
        // requestCode (nextAlarmRequestCode looks at what is already in the
        // repository, so we account for entries we are about to add).
        val newEntries = mutableListOf<StrictAlarmEntry>()
        var nextCode = BlockerRepository.nextAlarmRequestCode()
        for (i in 0 until alarmCount) {
            val totalMinutes = startHour * 60 + startMinute + i * MULTI_ALARM_INTERVAL_MINUTES
            val h = (totalMinutes / 60) % 24
            val m = totalMinutes % 60
            newEntries.add(
                StrictAlarmEntry(
                    id          = BlockerRepository.newStrictAlarmId(),
                    requestCode = nextCode,
                    hour        = h,
                    minute      = m,
                    daysOfWeek  = repeatOption.days,
                    label       = if (alarmCount > 1) "Wake up ${i + 1}/$alarmCount" else "Wake up"
                )
            )
            nextCode += 1
        }

        BlockerRepository.addStrictAlarmEntries(newEntries)

        scope.launch {
            // Space the Android scheduling calls slightly so we don't hammer
            // AlarmManager with a burst of exact-alarm registrations at once.
            AlarmScheduler.scheduleAllSpaced(context, newEntries, spacingMillis = 300L)
            isCreating = false
            onDone(newEntries)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add multiple alarms",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            SaveButton(
                state   = if (isCreating) SaveState.Loading else SaveState.Idle,
                onClick = { createAll() },
                onReset = {}
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Creates several independent alarms a few minutes apart — " +
                        "each one its own card you can edit or delete separately.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(28.dp))

            // ── Start time picker ─────────────────────────────────────────
            SectionLabel("First alarm at")
            Spacer(Modifier.height(12.dp))
            StartTimePicker(
                hour   = startHour,
                minute = startMinute,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            startHour   = h
                            startMinute = m
                        },
                        startHour,
                        startMinute,
                        false
                    ).show()
                }
            )

            Spacer(Modifier.height(32.dp))

            // ── Count picker ──────────────────────────────────────────────
            SectionLabel("Number of alarms")
            Spacer(Modifier.height(16.dp))
            CountPicker(
                count     = alarmCount,
                onMinus   = { if (alarmCount > MIN_ALARMS) alarmCount-- },
                onPlus    = { if (alarmCount < MAX_ALARMS) alarmCount++ }
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text  = "${MULTI_ALARM_INTERVAL_MINUTES} min apart",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Repeat selector ──────────────────────────────────────────
            SectionLabel("Repeat")
            Spacer(Modifier.height(12.dp))
            RepeatSelector(
                selected = repeatOption,
                onSelect = { repeatOption = it }
            )

            Spacer(Modifier.height(32.dp))

            // ── Live preview ──────────────────────────────────────────────
            SectionLabel("Your alarms")
            Spacer(Modifier.height(12.dp))
            PreviewList(times = previewTimes)

           Spacer(Modifier.height(36.dp))

            // ── Bottom padding so content clears the FAB ──────────────────
            Spacer(Modifier.height(88.dp))
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        color    = TextSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StartTimePicker(hour: Int, minute: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(20.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = formatMultiAddTime(hour, minute),
            style      = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color      = AccentBlue
        )
    }
}

@Composable
private fun CountPicker(count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minus button
        CountButton(icon = Icons.Filled.Remove, enabled = count > MIN_ALARMS, onClick = onMinus)

        Spacer(Modifier.width(32.dp))

        // Count display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "$count",
                fontSize   = 52.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
                lineHeight = 56.sp
            )
            Text(
                text  = if (count == 1) "alarm" else "alarms",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(Modifier.width(32.dp))

        // Plus button
        CountButton(icon = Icons.Filled.Add, enabled = count < MAX_ALARMS, onClick = onPlus)
    }
}

@Composable
private fun CountButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(
                color  = if (enabled) AccentBlue else AccentBlue.copy(alpha = 0.15f),
                shape  = CircleShape
            )
            .pressable(onClick = { if (enabled) onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = if (enabled) Color.White else AccentBlue.copy(alpha = 0.4f),
            modifier           = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun RepeatSelector(selected: MultiAddRepeatOption, onSelect: (MultiAddRepeatOption) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MultiAddRepeatOption.entries.forEach { option ->
            RepeatChip(
                label    = option.label,
                selected = option == selected,
                onClick  = { onSelect(option) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RepeatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) AccentBlue else CardSurface,
                shape = RoundedCornerShape(14.dp)
            )
            .pressable(pressedScale = 0.96f, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color      = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun PreviewList(times: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        times.forEachIndexed { index, time ->
            PreviewRow(index = index + 1, time = time)
        }
    }
}

@Composable
private fun PreviewRow(index: Int, time: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Numbered badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(AccentBlue.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "$index",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = AccentBlue
            )
        }

        Spacer(Modifier.width(12.dp))

        Icon(
            imageVector        = Icons.Filled.Alarm,
            contentDescription = null,
            tint               = TextMuted,
            modifier           = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text       = time,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildPreviewTimes(startHour: Int, startMinute: Int, count: Int): List<String> =
    (0 until count).map { i ->
        val total = startHour * 60 + startMinute + i * MULTI_ALARM_INTERVAL_MINUTES
        val h = (total / 60) % 24
        val m = total % 60
        formatMultiAddTime(h, m)
    }

private fun formatMultiAddTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}
