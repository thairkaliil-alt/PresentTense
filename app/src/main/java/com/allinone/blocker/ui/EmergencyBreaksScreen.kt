package com.allinone.blocker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════════════════════
// The dedicated emergency-breaks manager — reached via the bolt icon in the
// Lockdown screen's top bar. Relocated from the old decluttered Lockdown
// screen without any behavior changes: same expandable card, same stepper +
// duration-chip controls, same lock-while-a-session-is-running friction.
// ════════════════════════════════════════════════════════════════════════════

private data class DurationPreset(val label: String, val minutes: Int)

// Quick-pick presets for a single emergency break's length.
private val BREAK_DURATION_PRESETS = listOf(
    DurationPreset("5m",  5),
    DurationPreset("10m", 10),
    DurationPreset("15m", 15),
    DurationPreset("20m", 20),
    DurationPreset("30m", 30)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyBreaksScreen(onBack: () -> Unit) {
    val manualUntil by BlockerRepository.manualLockUntil.collectAsState()
    val schedules   by BlockerRepository.schedules.collectAsState()
    val breakUntil  by BlockerRepository.breakUntil.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val decision = remember(manualUntil, schedules, breakUntil, now) {
        LockdownEngine.evaluate(manualUntil, schedules, now, breakUntil)
    }
    LaunchedEffect(decision.active, decision.onBreak) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (decision.active || decision.onBreak) 1_000 else 30_000)
        }
    }
    val sessionRunning = manualUntil > now || decision.active || decision.onBreak

    Scaffold(
        containerColor = BgDarkest,
        topBar = {
            TopAppBar(
                title = { Text("Emergency Breaks", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            EmergencyBreaksCard(sessionRunning = sessionRunning)
        }
    }
}

// This used to be a section header plus a card that was always fully open —
// two sliders and their labels sitting on the screen at all times, whether
// or not anyone needed to touch them. That's now collapsed into a single
// expandable card, the same "collapsed summary → tap to reveal" pattern
// used throughout iOS Settings and Android's own Settings app: the header
// always shows the current configuration at a glance (e.g. "2 breaks · 10
// min each"), and the actual controls only appear once someone taps it.
//
// The two controls themselves were also upgraded:
//  - "Breaks per session" (a small, precise range of 0–5) is now a stepper
//    with +/- buttons instead of a slider — per Nielsen Norman Group's
//    guidance, steppers give users exact, error-free control over small
//    numeric ranges, where a slider's imprecise drag makes it easy to
//    overshoot the number you meant to land on.
//  - "Break duration" is now quick-pick chips (5/10/15/20/30 min), reusing
//    the same DurationChip look the lockdown-length picker uses, so the two
//    duration choices in the app feel like one design instead of two.
@Composable
private fun EmergencyBreaksCard(sessionRunning: Boolean) {
    val breakConfig by BlockerRepository.strictMode.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    // Locked the moment a lockdown session (or a break inside one) is live —
    // otherwise "2 breaks of 5 minutes" is just a suggestion, since anyone
    // could open this same card mid-break and dial it up to "10 breaks of
    // 60 minutes" right before their current break runs out. Change these
    // BEFORE you start your next lockdown instead. BlockerRepository.setStrictMode
    // enforces this too (belt-and-suspenders), so this is UI-level only.
    val locked = sessionRunning

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label       = "emergencyBreaksChevron"
    )

    val noBreaks = breakConfig.maxBreaksPerSession == 0
    val summary = if (noBreaks) {
        "No breaks allowed"
    } else {
        val breakWord = if (breakConfig.maxBreaksPerSession == 1) "break" else "breaks"
        "${breakConfig.maxBreaksPerSession} $breakWord · ${breakConfig.breakDurationMinutes} min each"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {

            // Header — always visible. The whole row is the tap target, and
            // the subtitle doubles as a live summary so the setting is
            // scannable even while collapsed.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AccentTeal.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Emergency breaks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = if (noBreaks) AccentRed.copy(alpha = 0.85f) else TextTertiary)
                }
                if (locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked while a lockdown session is running",
                        tint     = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint     = TextMuted,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                exit    = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.10f))

                    Text(
                        "When lockdown is active, you can request a short break. Configure how many and how long each one lasts.",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )

                    if (locked) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TextMuted.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Locked while this lockdown session is running — including during a break. Change these before your next session starts.",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                                )
                            }
                        }
                    }

                    // Breaks per session — stepper
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Breaks per session", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        BreakCountStepper(
                            value         = breakConfig.maxBreaksPerSession,
                            range         = 0..5,
                            locked        = locked,
                            onValueChange = { BlockerRepository.setStrictMode(breakConfig.copy(maxBreaksPerSession = it)) }
                        )
                        if (noBreaks) {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentRed.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("No breaks allowed. Once lockdown starts, it runs until it ends.", style = MaterialTheme.typography.bodySmall, color = AccentRed)
                            }
                        }
                    }

                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.10f))

                    // Break duration — quick-pick chips
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Break duration", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BREAK_DURATION_PRESETS.forEach { preset ->
                                DurationChip(
                                    preset   = preset,
                                    selected = breakConfig.breakDurationMinutes == preset.minutes,
                                    modifier = Modifier.weight(1f),
                                    enabled  = !locked,
                                    onClick  = { BlockerRepository.setStrictMode(breakConfig.copy(breakDurationMinutes = preset.minutes)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationChip(preset: DurationPreset, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bgColor     = if (selected) AccentBlue.copy(alpha = 0.18f) else CardSurface
    val borderColor = if (selected) AccentBlue else TextMuted.copy(alpha = 0.18f)
    val textColor   = if (selected) AccentBlue else TextPrimary
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val contentAlpha = if (enabled) 1f else 0.4f

    Box(
        modifier         = modifier
            .graphicsLayer { alpha = contentAlpha }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(color = borderColor, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(width = borderWidth.toPx()))
        }
        Text(
            text      = preset.label,
            style     = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color     = textColor,
            modifier  = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

// A small precise range (0–5) reads and adjusts far more reliably as a
// stepper than as a slider: every tap is exactly ±1, there's no risk of
// dragging past the number you meant to land on, and "None" is spelled out
// in words rather than just showing "0", which is easy to misread as "min".
@Composable
private fun BreakCountStepper(value: Int, range: IntRange, locked: Boolean = false, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TextTertiary.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepperIconButton(
            icon    = Icons.Filled.Remove,
            enabled = !locked && value > range.first,
            onClick = { onValueChange((value - 1).coerceIn(range)) }
        )

        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (value == 0) "None" else "$value",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = if (value == 0) AccentRed else TextPrimary
            )
            Text(
                if (value == 0) "no breaks" else if (value == 1) "break" else "breaks",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        StepperIconButton(
            icon    = Icons.Filled.Add,
            enabled = !locked && value < range.last,
            onClick = { onValueChange((value + 1).coerceIn(range)) }
        )
    }
}

// Shared +/- button for the stepper above. Disabled (rather than hidden) at
// the ends of the range, per standard stepper accessibility guidance, so the
// control never visually jumps around as the value nears its limits.
@Composable
private fun StepperIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val bg   = if (enabled) AccentTeal.copy(alpha = 0.16f) else TextTertiary.copy(alpha = 0.06f)
    val tint = if (enabled) AccentTeal else TextMuted.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
