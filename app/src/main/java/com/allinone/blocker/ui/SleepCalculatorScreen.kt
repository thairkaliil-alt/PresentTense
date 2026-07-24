package com.allinone.blocker.ui

import android.app.TimePickerDialog
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentPurpleSoft
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// SLEEP SCIENCE CONSTANTS
//
// A full sleep cycle averages ~90 minutes. Waking at the end of a cycle (rather
// than mid-cycle) is what leaves you feeling refreshed instead of groggy. We add
// a 15-minute buffer for the time it takes the average person to actually fall
// asleep, and offer a 25-minute "power nap" as a separate, secondary option.
//
// We only show 3-6 cycles (4.5-9 hours) in the list, and only ever badge ONE
// option "Recommended" (5 cycles / 7.5 hours). The old version showed up to 8
// options with two different "recommended" badges — more choices make people
// slower and less confident to decide (this is "Hick's Law" in UX research),
// so the list is deliberately short and has one clear best answer.
// ─────────────────────────────────────────────────────────────────────────────

private const val CYCLE_MINUTES = 90
private const val FALL_ASLEEP_MINUTES = 15
private const val NAP_MINUTES = 25
private const val MIN_CYCLES = 3
private const val MAX_CYCLES = 6
private const val RECOMMENDED_CYCLES = 5

// ─────────────────────────────────────────────────────────────────────────────
// WHICH TIME THE USER IS PROVIDING
//
// Named after the INPUT, not the result — mixing those two up (labeling a mode
// by its answer instead of its question) is exactly what made the old version
// show correct numbers under swapped headings. Keep this pairing in mind when
// editing anything below:
//
//   SleepInput.BEDTIME       "I'm going to bed at ___ (or right now)."
//                             -> shows RECOMMENDED WAKE-UP TIMES, each with a
//                                "Set alarm" button, since these map to a
//                                real alarm.
//
//   SleepInput.WAKE_UP_TIME  "I need to wake up at ___."
//                             -> shows RECOMMENDED BEDTIMES (no alarm button
//                                on those — a bedtime isn't an alarm), but
//                                the INPUT card itself gets a "Set alarm"
//                                button, because the wake-up time you just
//                                typed in IS a real alarm time already.
//
// The input you collect always produces the OTHER kind of time as the result.
// Every "Set alarm" button on this screen — whichever mode it's in — goes
// through the one setAlarmFor() function below, so it always behaves the
// same way: set immediately, confirmed with an undoable Snackbar.
// ─────────────────────────────────────────────────────────────────────────────
private enum class SleepInput { BEDTIME, WAKE_UP_TIME }

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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val allAlarms by BlockerRepository.strictAlarms.collectAsState()

    // "The" alarm this screen seeds from / writes to: the soonest-firing
    // enabled entry, or just the first entry if none are enabled, or a
    // fresh blank one if there are no alarms at all yet. This keeps the
    // "set my wake-up alarm" shortcut feeling like it has one obvious
    // target, the same way it did back when there was only ever one alarm.
    val seedAlarm = remember(allAlarms) {
        allAlarms.filter { it.enabled }.minByOrNull { it.hour * 60 + it.minute }
            ?: allAlarms.firstOrNull()
            ?: com.allinone.blocker.data.StrictAlarmEntry.newDefault(BlockerRepository.nextAlarmRequestCode())
    }

    fun saveAlarm(updated: com.allinone.blocker.data.StrictAlarmEntry) {
        if (allAlarms.any { it.id == updated.id }) {
            BlockerRepository.updateStrictAlarmEntry(updated)
        } else {
            BlockerRepository.addStrictAlarmEntry(updated)
        }
        AlarmScheduler.schedule(context, updated)
    }

    // Sets (or moves) the alarm to hour:minute IMMEDIATELY — no extra screen,
    // no extra confirmation dialog — then shows a Snackbar with "Undo" so a
    // mis-tap is never permanent. This is used both by the results list below
    // and by the "Wake-up time" input card's own Set Alarm button, so setting
    // an alarm behaves exactly the same way everywhere on this screen.
    fun setAlarmFor(hour: Int, minute: Int) {
        val previousState = allAlarms.firstOrNull { it.id == seedAlarm.id }
        val updated = seedAlarm.copy(enabled = true, hour = hour, minute = minute)
        saveAlarm(updated)

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Alarm set for ${formatClock(hour, minute)}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (previousState == null) {
                    // It did not exist before this tap — undo means removing
                    // it again, not "restoring" it to some earlier time.
                    BlockerRepository.removeStrictAlarmEntry(updated.id)
                    AlarmScheduler.cancel(context, updated.id)
                } else {
                    saveAlarm(previousState)
                }
            }
        }
    }

    // Default to BEDTIME: opening the calculator immediately shows wake-up
    // suggestions for "right now" — the single most common reason someone
    // opens a sleep calculator — with zero typing needed to get an answer.
    var sleepInput by remember { mutableStateOf(SleepInput.BEDTIME) }

    // Wake-up-time input — seeded from the seed alarm's time.
    var wakeHour by remember { mutableIntStateOf(seedAlarm.hour) }
    var wakeMinute by remember { mutableIntStateOf(seedAlarm.minute) }

    // Bedtime input — seeded to "now".
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                ModeSwitch(sleepInput = sleepInput, onChange = { sleepInput = it })
            }

            AnimatedAppearance(delayMs = 40) {
                when (sleepInput) {
                    SleepInput.BEDTIME -> TimeInputCard(
                        accent = AccentPurple,
                        icon = Icons.Filled.Bedtime,
                        caption = "I'm going to bed at",
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
                    SleepInput.WAKE_UP_TIME -> TimeInputCard(
                        accent = AccentAmber,
                        icon = Icons.Filled.LightMode,
                        caption = "I need to wake up at",
                        hour = wakeHour,
                        minute = wakeMinute,
                        trailing = {
                            SetAlarmChip(onClick = { setAlarmFor(wakeHour, wakeMinute) })
                        },
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> wakeHour = h; wakeMinute = m },
                                wakeHour, wakeMinute, false
                            ).show()
                        }
                    )
                }
            }

            val options = remember(sleepInput, wakeHour, wakeMinute, bedHour, bedMinute) {
                if (sleepInput == SleepInput.BEDTIME) wakeUpOptions(bedHour, bedMinute)
                else bedtimeOptions(wakeHour, wakeMinute)
            }

            AnimatedAppearance(delayMs = 80) {
                Text(
                    text = if (sleepInput == SleepInput.BEDTIME)
                        "Recommended wake-up times" else "Recommended bedtimes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedAppearance(delayMs = 100) {
                Text(
                    text = if (sleepInput == SleepInput.BEDTIME)
                        "Each one lands at the end of a full cycle, so you wake up clear-headed instead of groggy."
                    else
                        "Go to sleep at one of these times to get complete cycles before you wake up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEachIndexed { index, option ->
                    if (index == 1) {
                        Spacer(Modifier.height(12.dp))
                    }
                    if (option.isNap) {
                        Text(
                            "Just need a nap?",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                    AnimatedAppearance(delayMs = 120 + index * 45) {
                        OptionCard(
                            option = option,
                            isWakeResult = sleepInput == SleepInput.BEDTIME,
                            onSetAlarm = { setAlarmFor(option.hour, option.minute) }
                        )
                    }
                }
            }

            AnimatedAppearance(delayMs = 140) {
                Text(
                    "Based on 90-minute sleep cycles plus about 15 minutes to fall asleep. " +
                        "The highlighted time gives you 5 full cycles (7.5 hours) — the sweet spot most adults need.",
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
private fun ModeSwitch(sleepInput: SleepInput, onChange: (SleepInput) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgDarkest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentButton(
            label = "Bedtime",
            icon = Icons.Filled.NightsStay,
            selected = sleepInput == SleepInput.BEDTIME,
            accent = AccentPurple,
            modifier = Modifier.weight(1f),
            onClick = { onChange(SleepInput.BEDTIME) }
        )
        SegmentButton(
            label = "Wake-up time",
            icon = Icons.Filled.LightMode,
            selected = sleepInput == SleepInput.WAKE_UP_TIME,
            accent = AccentAmber,
            modifier = Modifier.weight(1f),
            onClick = { onChange(SleepInput.WAKE_UP_TIME) }
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
// TIME INPUT CARD — shared by both modes. Tap it to open a time picker.
// The trailing slot holds whichever action makes sense for that mode: a
// "Now" chip in Bedtime mode, a "Set alarm" chip in Wake-up-time mode.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimeInputCard(
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

/**
 * The one "Set alarm" button used everywhere on this screen — on the
 * wake-up-time input card and on every wake-up result card below. Same
 * look, same behaviour, wherever it appears.
 */
@Composable
private fun SetAlarmChip(
    accent: Color = AccentPurple,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.16f))
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "Set alarm",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent
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
                SetAlarmChip(
                    accent = if (option.recommended) AccentPurple else TextSecondary,
                    onClick = onSetAlarm
                )
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
 * The shared math behind both modes: MIN_CYCLES..MAX_CYCLES full cycles away
 * from [baseHour]:[baseMinute], counted backwards (an earlier time) if
 * [subtractFromBase] is true, or forwards (a later time) otherwise. The
 * RECOMMENDED_CYCLES option is always returned first.
 */
private fun cycleOptions(baseHour: Int, baseMinute: Int, subtractFromBase: Boolean): List<SleepOption> {
    val allCycles = (MIN_CYCLES..MAX_CYCLES).map { cycles ->
        val offset = cycles * CYCLE_MINUTES + FALL_ASLEEP_MINUTES
        val (h, m) = addMinutes(baseHour, baseMinute, if (subtractFromBase) -offset else offset)
        SleepOption(
            hour = h,
            minute = m,
            cycles = cycles,
            durationLabel = cycleDurationLabel(cycles),
            recommended = cycles == RECOMMENDED_CYCLES,
            isNap = false
        )
    }
    val recommended = allCycles.first { it.cycles == RECOMMENDED_CYCLES }
    val rest = allCycles.filter { it.cycles != RECOMMENDED_CYCLES }.sortedBy { it.cycles }
    return listOf(recommended) + rest
}

/** Given a target WAKE-UP time, the recommended BEDTIMES that reach it. */
private fun bedtimeOptions(wakeHour: Int, wakeMinute: Int): List<SleepOption> =
    cycleOptions(wakeHour, wakeMinute, subtractFromBase = true)

/**
 * Given a BEDTIME, the recommended WAKE-UP times — plus a short power nap,
 * kept separate at the very end since it isn't a full-cycle recommendation.
 */
private fun wakeUpOptions(bedHour: Int, bedMinute: Int): List<SleepOption> {
    val nap = run {
        val (h, m) = addMinutes(bedHour, bedMinute, NAP_MINUTES)
        SleepOption(h, m, 0, "$NAP_MINUTES-min power nap", recommended = false, isNap = true)
    }
    return cycleOptions(bedHour, bedMinute, subtractFromBase = false) + listOf(nap)
}

private fun formatClock(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}
