package com.allinone.blocker.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.ui.motion.AnimatedAppearance
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentPurpleSoft
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import java.util.Calendar
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// SLEEP SCIENCE CONSTANTS
//
// A full sleep cycle averages ~90 minutes. Waking at the end of a cycle (rather
// than mid-cycle) is what leaves you feeling refreshed instead of groggy. We add
// a 15-minute buffer for the time it takes the average person to actually fall
// asleep, and offer a 25-minute "power nap" as the shortest option.
// ─────────────────────────────────────────────────────────────────────────────

private const val CYCLE_MINUTES = 90
private const val FALL_ASLEEP_MINUTES = 15
private const val NAP_MINUTES = 25
/** Quick "bed now" alarm offset — 7 hours 40 minutes from tap time. */
private const val BED_NOW_ALARM_MINUTES = 7 * 60 + 40

private enum class CalcMode { WAKE_AT, SLEEP_AT }

/** A single recommended time the calculator produces. */
private data class SleepOption(
    val hour: Int,
    val minute: Int,
    val cycles: Int,          // 0 == nap
    val durationLabel: String,
    val recommended: Boolean,
    val isNap: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val alarm by BlockerRepository.strictAlarm.collectAsState()

    var mode by remember { mutableStateOf(CalcMode.WAKE_AT) }

    // Wake-at target — seeded from the user's current strict alarm time.
    var wakeHour by remember { mutableIntStateOf(alarm.hour) }
    var wakeMinute by remember { mutableIntStateOf(alarm.minute) }

    // Sleep-at base — seeded to "now".
    val nowCal = remember { Calendar.getInstance() }
    var bedHour by remember { mutableIntStateOf(nowCal.get(Calendar.HOUR_OF_DAY)) }
    var bedMinute by remember { mutableIntStateOf(nowCal.get(Calendar.MINUTE)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Calculator", style = MaterialTheme.typography.titleLarge) },
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
            AnimatedAppearance {
                ModeSwitch(mode = mode, onModeChange = { mode = it })
            }

            AnimatedAppearance(delayMs = 40) {
                if (mode == CalcMode.WAKE_AT) {
                    BedNowCard(
                        onNowClick = {
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.MINUTE, BED_NOW_ALARM_MINUTES)
                            }
                            val updated = alarm.copy(
                                enabled = true,
                                hour = cal.get(Calendar.HOUR_OF_DAY),
                                minute = cal.get(Calendar.MINUTE)
                            )
                            BlockerRepository.setStrictAlarm(updated)
                            AlarmScheduler.schedule(context, updated)
                            Toast.makeText(
                                context,
                                "Alarm set for ${formatClock(updated.hour, updated.minute)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    TimeSelectCard(
                        accent = AccentPurple,
                        icon = Icons.Filled.Bedtime,
                        caption = "When to go to bed",
                        hour = bedHour,
                        minute = bedMinute,
                        trailing = {
                            NowChip(onClick = {
                                val c = Calendar.getInstance()
                                bedHour = c.get(Calendar.HOUR_OF_DAY)
                                bedMinute = c.get(Calendar.MINUTE)
                            })
                        },
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> bedHour = h; bedMinute = m },
                                bedHour, bedMinute, false
                            ).show()
                        }
                    )
                }
            }

            val options = remember(mode, wakeHour, wakeMinute, bedHour, bedMinute) {
                if (mode == CalcMode.WAKE_AT) bedtimeOptions(wakeHour, wakeMinute)
                else wakeOptions(bedHour, bedMinute)
            }

            AnimatedAppearance(delayMs = 80) {
                Text(
                  text = if (mode == CalcMode.WAKE_AT)
                        "Best times to wake up" else "Best times to fall asleep",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedAppearance(delayMs = 100) {
                Text(
                    text = if (mode == CalcMode.WAKE_AT)
                        "Each time below lands at the end of a full cycle, so you wake up clear-headed."
                    else
                        "Pick a time that gives you complete sleep cycles. More cycles, more rest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEachIndexed { index, option ->
                if (index == 1) {
                    Spacer(Modifier.height(12.dp))
                }
                    AnimatedAppearance(delayMs = 120 + index * 45) {
                        OptionCard(
                            option = option,
                            isWakeResult = mode == CalcMode.SLEEP_AT,
                            onSetAlarm = {
                                val updated = alarm.copy(
                                    enabled = true,
                                    hour = option.hour,
                                    minute = option.minute
                                )
                                BlockerRepository.setStrictAlarm(updated)
                                AlarmScheduler.schedule(context, updated)
                                Toast.makeText(
                                    context,
                                    "Alarm set for ${formatClock(option.hour, option.minute)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            AnimatedAppearance(delayMs = 140) {
                Text(
                    "Based on 90-minute sleep cycles plus ~15 minutes to fall asleep. " +
                        "Highlighted times give you the 5–6 cycles most adults need.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE SWITCH (segmented control)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeSwitch(mode: CalcMode, onModeChange: (CalcMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgDarkest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      SegmentButton(
            label = "When to go to bed",
            icon = Icons.Filled.NightsStay,
            selected = mode == CalcMode.SLEEP_AT,
            accent = AccentPurple,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(CalcMode.SLEEP_AT) }
        )
        SegmentButton(
            label = "When to wake up",
            icon = Icons.Filled.LightMode,
            selected = mode == CalcMode.WAKE_AT,
            accent = AccentAmber,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(CalcMode.WAKE_AT) }
        )
    }
}

@Composable
private fun SegmentButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .pressable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BED NOW CARD (When to wake up tab)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BedNowCard(onNowClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AccentAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "I am going to bed now!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Set alarm after 7.5 hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            NowButton(onClick = onNowClick)
        }
    }
}

@Composable
private fun NowButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AccentAmber.copy(alpha = 0.2f))
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            "Now",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AccentAmber
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TIME SELECT CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimeSelectCard(
    accent: Color,
    icon: ImageVector,
    caption: String,
    hour: Int,
    minute: Int,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
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
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = formatClock(hour, minute),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "time"
                ) { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun NowChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AccentPurple.copy(alpha = 0.16f))
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "Now",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = AccentPurple
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OPTION CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OptionCard(
    option: SleepOption,
    isWakeResult: Boolean,
    onSetAlarm: () -> Unit
) {
    val accent = if (option.recommended) AccentPurple else TextSecondary
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (option.recommended) CardSurfaceAlt else CardSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatClock(option.hour, option.minute),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (option.recommended) {
                        Spacer(Modifier.size(8.dp))
                        RecommendBadge()
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(if (option.isNap) "Power nap" else "${option.cycles} cycles")
                        append(" • ")
                        append(option.durationLabel)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (option.recommended) AccentPurpleSoft else TextSecondary
                )
            }

            if (isWakeResult) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .pressable(onClick = onSetAlarm)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Set alarm",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (option.recommended) AccentPurple else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AccentPurple.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            "RECOMMENDED",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AccentPurple
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CALCULATION
// ─────────────────────────────────────────────────────────────────────────────

/** Wraps an hour/minute by [add] minutes, staying within a 24h clock. */
private fun addMinutes(hour: Int, minute: Int, add: Int): Pair<Int, Int> {
    val total = ((hour * 60 + minute + add) % 1440 + 1440) % 1440
    return total / 60 to total % 60
}

private fun cycleDurationLabel(cycles: Int): String {
    val hours = cycles * 1.5
    val text = if (hours % 1.0 == 0.0) "${hours.toInt()}" else "$hours"
    return "$text hrs of sleep"
}

/**
 * Given a target wake time, the recommended bedtimes — most cycles (earliest)
 * first. Cycles 5 and 6 are the sweet spot for most adults.
 */
private fun bedtimeOptions(wakeHour: Int, wakeMinute: Int): List<SleepOption> {
    val allCycles = (3..6).map { cycles ->
        val offset = cycles * CYCLE_MINUTES + FALL_ASLEEP_MINUTES
        val (h, m) = addMinutes(wakeHour, wakeMinute, -offset)
        SleepOption(
            hour = h,
            minute = m,
            cycles = cycles,
            durationLabel = cycleDurationLabel(cycles),
            recommended = cycles == 5,
            isNap = false
        )
    }
    val fiveCycle = allCycles.first { it.cycles == 5 }
    val rest = allCycles.filter { it.cycles != 5 }.sortedBy { it.cycles }
    return listOf(fiveCycle) + rest
}

/**
 * Given a bedtime, the times you'd wake at the end of each full cycle (plus a
 * short power nap). Fewest cycles (soonest) first.
 */
private fun wakeOptions(bedHour: Int, bedMinute: Int): List<SleepOption> {
    val nap = run {
        val (h, m) = addMinutes(bedHour, bedMinute, NAP_MINUTES)
        SleepOption(h, m, 0, "$NAP_MINUTES-min power nap", recommended = false, isNap = true)
    }
    val allCycles = (1..7).map { c ->
        val offset = FALL_ASLEEP_MINUTES + c * CYCLE_MINUTES
        val (h, m) = addMinutes(bedHour, bedMinute, offset)
        SleepOption(
            hour = h,
            minute = m,
            cycles = c,
            durationLabel = cycleDurationLabel(c),
            recommended = c == 5 || c == 6,
            isNap = false
        )
    }
    val fiveCycle = allCycles.first { it.cycles == 5 }
    val rest = allCycles.filter { it.cycles != 5 }.sortedBy { it.cycles } + listOf(nap)
    return listOf(fiveCycle) + rest
}

private fun formatClock(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}
