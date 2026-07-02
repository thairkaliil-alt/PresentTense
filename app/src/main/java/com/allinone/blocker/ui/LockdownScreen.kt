package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.allinone.blocker.R
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownDecision
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownSchedule
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val DAY_LABELS = mapOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)
private val DAY_ORDER = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

// ════════════════════════════════════ Duration Presets ════════════════════════════════════

private data class DurationPreset(val label: String, val minutes: Int)

private val DURATION_PRESETS = listOf(
    DurationPreset("15m",   15),
    DurationPreset("25m",   25),
    DurationPreset("50m",   50),
    DurationPreset("90m",   90),
    DurationPreset("2h",   120),
    DurationPreset("Custom", -1)
)

// ════════════════════════════════════ Screen root ════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LockdownScreen(onBack: () -> Unit, onManageWhitelist: () -> Unit = {}) {
    val context = LocalContext.current
    val manualUntil by BlockerRepository.manualLockUntil.collectAsState()
    val schedules   by BlockerRepository.schedules.collectAsState()
    val breakUntil  by BlockerRepository.breakUntil.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAddSchedule    by remember { mutableStateOf<LockdownSchedule?>(null) }
    var showCustomMinutes  by remember { mutableStateOf(false) }
    var customStartMinutes by remember { mutableStateOf(45) }

    val decisionPreview = remember(manualUntil, schedules, breakUntil, now) {
        LockdownEngine.evaluate(manualUntil, schedules, now, breakUntil)
    }
    LaunchedEffect(decisionPreview.active, decisionPreview.onBreak) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (decisionPreview.active || decisionPreview.onBreak) 1_000 else 30_000)
        }
    }

    val decision        = remember(manualUntil, schedules, breakUntil, now) {
        LockdownEngine.evaluate(manualUntil, schedules, now, breakUntil)
    }
    val breaksRemaining = BlockerRepository.breaksRemaining()
    val sessionRunning  = manualUntil > now || decision.active || decision.onBreak

    val goHome: () -> Unit = { LockdownLauncherActivity.launch(context) }

    // ── Ignition (press-and-hold-to-lock) state ─────────────────────────
    // Lives here, at the screen root, rather than down inside the hero
    // section, because the full-screen "liquid fill" has to paint OVER the
    // TopAppBar and everything else — something nested inside the Scaffold's
    // content can't do. The orb just reports where it is and which duration
    // is armed; this screen owns actually starting the lockdown once the
    // fill finishes.
    var ignitionOrigin  by remember { mutableStateOf<Offset?>(null) }
    var ignitionMinutes by remember { mutableStateOf<Int?>(null) }

    // ── Armed duration ───────────────────────────────────────────────────
    // Picking a preset chip or confirming the custom dial no longer starts
    // anything by itself — it only "arms" the orb with a chosen number of
    // minutes. The ONLY way a lockdown actually begins is holding the orb
    // down until its charge ring completes (see [LiquidGlassOrb]/[onIgnite]
    // above). armedIsCustom just tracks which chip should read as selected;
    // the orb itself only cares about armedMinutes.
    var armedMinutes by remember { mutableStateOf<Int?>(null) }
    var armedIsCustom by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier       = Modifier.fillMaxSize(),
            containerColor = BgDarkest,
            topBar = {
                TopAppBar(
                    title = { Text("Lockdown", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
                )
            }
        ) { pad ->
            EmbeddedLockdownLazyColumn(
                modifier            = Modifier.padding(pad),
                sessionRunning      = sessionRunning,
                decision            = decision,
                now                 = now,
                breaksRemaining     = breaksRemaining,
                schedules           = schedules,
                onManageWhitelist   = onManageWhitelist,
                armedMinutes        = armedMinutes,
                armedIsCustom       = armedIsCustom,
                onSelectPreset      = { mins -> armedMinutes = mins; armedIsCustom = false },
                onCustom            = { prefill -> customStartMinutes = prefill; showCustomMinutes = true },
                onIgnite            = { origin, mins -> ignitionOrigin = origin; ignitionMinutes = mins },
                onEmergencyBreak    = { StrictModeGate.guard { BlockerRepository.startEmergencyBreak() } },
                onAddSchedule       = { showAddSchedule = LockdownSchedule(id = BlockerRepository.newScheduleId()) },
                onToggleSchedule    = { s, v ->
                    if (!v) StrictModeGate.guard { BlockerRepository.updateSchedule(s.copy(enabled = v)) }
                    else BlockerRepository.updateSchedule(s.copy(enabled = v))
                },
                onDeleteSchedule    = { s -> StrictModeGate.guard { BlockerRepository.removeSchedule(s.id) } },
                onEditSchedule      = { showAddSchedule = it }
            )
        }

        // The hold-to-lock fill. Only present while an ignition is in
        // flight; it's the thing that actually starts the lockdown once the
        // screen is fully covered — see [LockdownIgnitionOverlay].
        val origin = ignitionOrigin
        val mins   = ignitionMinutes
        if (origin != null && mins != null) {
            LockdownIgnitionOverlay(
                origin     = origin,
                onComplete = {
                    BlockerRepository.startManualLock(mins)
                    goHome()
                    ignitionOrigin  = null
                    ignitionMinutes = null
                    armedMinutes    = null
                    armedIsCustom   = false
                }
            )
        }
    }

    showAddSchedule?.let { editing ->
        ScheduleEditDialog(
            schedule  = editing,
            onDismiss = { showAddSchedule = null },
            onSave    = { saved ->
                if (schedules.any { it.id == saved.id }) BlockerRepository.updateSchedule(saved)
                else BlockerRepository.addSchedule(saved)
                showAddSchedule = null
            }
        )
    }

    if (showCustomMinutes) {
        RadialTimerDialog(
            initialMinutes = customStartMinutes,
            onDismiss      = { showCustomMinutes = false },
            onConfirm      = { mins ->
                // Confirming the dial only arms the orb with this duration —
                // it does NOT start the lockdown. Holding the orb is what
                // actually triggers it.
                armedMinutes      = mins
                armedIsCustom     = true
                showCustomMinutes = false
            }
        )
    }
}

// ════════════════════════════════════ Liquid Glass Orb ════════════════════════════════════
//
// The orb is now the primary way to START a lockdown, not just decoration.
// Holding it down "charges" a ring around the glass — the same language as
// Snapchat's record button, Discord's push-to-talk, or iOS's hold-to-confirm
// toggles — so a lockdown (something with real teeth once Strict Mode is on)
// can never be triggered by a stray tap. Only once the ring completes does
// [onIgnite] fire; the screen-filling "liquid" reveal that follows lives in
// [LockdownIgnitionOverlay] below, since it needs to paint above the whole
// screen, not just this composable's own bounds.

/** How long a full press-and-hold takes to "charge" before it fires. */
private const val ORB_HOLD_MS = 650

@Composable
private fun LiquidGlassOrb(
    modifier    : Modifier = Modifier,
    armedMinutes: Int?     = null,
    onIgnite    : (Offset, Int) -> Unit = { _, _ -> }
) {
    val reducedMotion = LocalReducedMotion.current
    val haptics       = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "orb_breathe")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue  = 0.32f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Hold-to-charge state. holdProgress drives the ring; shakeOffset gives
    // a quick "no" wobble if someone holds without a duration picked yet.
    var orbCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    val holdProgress    = remember { Animatable(0f) }
    val shakeOffset      = remember { Animatable(0f) }
    var isHolding         by remember { mutableStateOf(false) }

    // While charging, the orb glows a little brighter and eases in very
    // slightly — a physical "gathering energy" read, not just a static ring.
    val chargeGlow = glowAlpha * (1f + holdProgress.value * 0.7f)
    val pressEase  = 1f - (holdProgress.value * 0.035f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(220.dp)
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                orbCenterInRoot = Offset(p.x + coords.size.width / 2f, p.y + coords.size.height / 2f)
            }
            .graphicsLayer { translationX = shakeOffset.value }
            .pointerInput(armedMinutes, reducedMotion) {
                coroutineScope {
                    while (true) {
                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                        isHolding = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        val chargeMs = if (reducedMotion) 120 else ORB_HOLD_MS
                        val chargeJob = launch {
                            holdProgress.animateTo(1f, tween(durationMillis = chargeMs, easing = LinearEasing))
                        }

                        awaitPointerEventScope { waitForUpOrCancellation() }
                        isHolding = false

                        if (chargeJob.isActive) {
                            // Released early — ease the ring back down instead
                            // of snapping, so letting go still feels deliberate.
                            chargeJob.cancel()
                            launch { holdProgress.animateTo(0f, MotionSpecs.standard()) }
                        } else {
                            val mins = armedMinutes
                            if (mins != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onIgnite(orbCenterInRoot, mins)
                            } else {
                                // Held long enough, but no duration is picked
                                // yet — reject with a small shake rather than
                                // silently doing nothing.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                launch {
                                    shakeOffset.animateTo(12f, tween(55))
                                    shakeOffset.animateTo(-12f, tween(85))
                                    shakeOffset.animateTo(6f, tween(70))
                                    shakeOffset.animateTo(0f, tween(70))
                                }
                            }
                            holdProgress.snapTo(0f)
                        }
                    }
                }
            }
    ) {
        // Outermost ambient glow halo
        Canvas(modifier = Modifier.size(220.dp).scale(breathScale * pressEase)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentBlue.copy(alpha = chargeGlow * 0.5f),
                        AccentTeal.copy(alpha = chargeGlow * 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f,
                center = center
            )
        }

        // Middle frosted glass ring
        Canvas(modifier = Modifier.size(190.dp).scale(breathScale * pressEase)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        AccentBlue.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                radius = radius,
                center = center
            )
            // Top specular highlight — the glass catch-light
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                ),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter  = false,
                style       = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            // Luminous rim
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        AccentBlue.copy(alpha = 0.15f),
                        AccentTeal.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.25f)
                    ),
                    center = center
                ),
                radius = radius - 1.dp.toPx(),
                center = center,
                style  = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Inner dark core — the logo lives here
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(148.dp)
                .scale(breathScale * pressEase)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A2535), Color(0xFF0E1520))
                    )
                )
        ) {
            Icon(
                painter            = painterResource(id = R.drawable.ic_leaf_brand),
                contentDescription = "Present Tense",
                tint               = Color.Unspecified,
                modifier           = Modifier.size(72.dp)
            )
        }

        // Charge ring — appears only while held, sweeping clockwise from the
        // top as it fills. This is the entire "am I holding long enough"
        // affordance, so it needs to read unmistakably: bright white, thick
        // enough to see at a glance, drawn just outside the glass.
        if (isHolding || holdProgress.value > 0f) {
            Canvas(modifier = Modifier.size(236.dp)) {
                val stroke = 4.dp.toPx()
                drawArc(
                    color      = Color.White.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress.value,
                    useCenter  = false,
                    style      = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// ════════════════════════════════════ Lockdown Ignition (full-screen fill) ════════════════════════════════════
//
// Plays once the orb's hold-to-lock ring completes. A circle grows out from
// the orb's exact on-screen position until it swallows the whole display,
// drifting in color from the orb's own blue/teal glass tones into the exact
// black the lockdown launcher opens on — so by the time [onComplete] fires
// and that screen actually appears, the color already matches and the cut
// underneath it is invisible.
//
// Timing deliberately runs past the ~400ms "Doherty threshold" this app's
// motion system otherwise holds to everywhere else (see Motion.kt) —
// everyday micro-interactions should be fast because speed reads as
// responsiveness, but this fires once a session at most and is meant to feel
// like a ritual, closer to iOS's "slide to power off" or a game's
// level-transition wipe, where a slightly longer, weightier motion reads as
// significant rather than slow.

private const val IGNITION_COLOR_DELAY_MS = 140L
private const val IGNITION_COLOR_MS       = 480

@Composable
private fun LockdownIgnitionOverlay(origin: Offset, onComplete: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val density       = LocalDensity.current
    val config        = LocalConfiguration.current

    // Screen diagonal, with headroom — guarantees full coverage no matter
    // where on screen the orb was, even if [origin] is slightly off due to
    // status-bar/inset rounding.
    val maxRadiusPx = remember(config) {
        with(density) {
            val wPx = config.screenWidthDp.dp.toPx()
            val hPx = config.screenHeightDp.dp.toPx()
            hypot(wPx, hPx) * 1.15f
        }
    }
    val startRadiusPx = with(density) { 100.dp.toPx() }

    val radius   = remember { Animatable(startRadiusPx) }
    val colorMix = remember { Animatable(0f) } // 0 = glass blue/teal · 1 = lockdown black

    // Canvas's draw block runs outside composition, so any color that reads
    // light/dark theme (like BgDarkest) has to be captured here first, in the
    // composable body, and then just referenced as a plain value below.
    val lockdownBlack = BgDarkest

    LaunchedEffect(origin) {
        if (reducedMotion) {
            // Respect the OS "remove animations" setting: cut straight to
            // black instead of playing the expand.
            radius.snapTo(maxRadiusPx)
            colorMix.snapTo(1f)
            delay(100)
            onComplete()
            return@LaunchedEffect
        }
        launch { radius.animateTo(maxRadiusPx, MotionSpecs.liquidExpand()) }
        delay(IGNITION_COLOR_DELAY_MS)
        colorMix.animateTo(1f, tween(durationMillis = IGNITION_COLOR_MS, easing = FastOutSlowInEasing))
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallow any stray touches while the fill is mid-flight so
            // nothing underneath can be double-tapped during the transition.
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillColor = lerp(AccentBlue, lockdownBlack, colorMix.value)
            val edgeColor = lerp(AccentTeal, lockdownBlack, colorMix.value)

            // Two faint trailing echoes just behind the leading edge — the
            // "surface tension" of a spreading liquid rather than a
            // hard-edged circle snapping outward.
            drawCircle(color = edgeColor.copy(alpha = 0.16f * (1f - colorMix.value)), radius = radius.value * 0.92f, center = origin)
            drawCircle(color = edgeColor.copy(alpha = 0.09f * (1f - colorMix.value)), radius = radius.value * 0.85f, center = origin)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(fillColor, lerp(fillColor, lockdownBlack, 0.35f)),
                    center = origin,
                    radius = (radius.value * 1.05f).coerceAtLeast(1f)
                ),
                radius = radius.value,
                center = origin
            )
        }
    }
}

// ════════════════════════════════════ Hero / Pick Duration ════════════════════════════════════

@Composable
private fun LockdownHeroSection(
    armedMinutes  : Int?,
    armedIsCustom : Boolean,
    onSelectPreset: (Int?) -> Unit,
    onCustom      : (Int) -> Unit,
    onIgnite      : (Offset, Int) -> Unit = { _, _ -> }
) {
    // Which chip should read as highlighted. A fixed preset chip is selected
    // when its minute value matches the armed value AND that value didn't
    // come from the custom dial; the Custom chip is selected purely off
    // armedIsCustom, since a custom time could coincidentally match a preset
    // number (e.g. dialing in exactly 25m).
    val selectedPreset = when {
        armedIsCustom        -> DURATION_PRESETS.last()
        armedMinutes != null -> DURATION_PRESETS.firstOrNull { it.minutes == armedMinutes }
        else                 -> null
    }

    Column(
        modifier              = Modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        LiquidGlassOrb(
            // The orb only ever fires from a hold-and-charge gesture — see
            // [LiquidGlassOrb] below. Picking a chip or confirming the custom
            // dial just sets armedMinutes; nothing else in this screen is
            // allowed to start a lockdown directly.
            armedMinutes = armedMinutes,
            onIgnite     = onIgnite
        )
        Spacer(Modifier.height(28.dp))

        Text(
            "Present Tense",
            style        = MaterialTheme.typography.titleMedium,
            fontWeight   = FontWeight.Medium,
            color        = TextMuted,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(containerColor = CardSurfaceAlt)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pick Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Everything except your whitelist will be blocked.", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))

                // 3-per-row chip grid
                DURATION_PRESETS.chunked(3).forEach { rowPresets ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowPresets.forEach { preset ->
                            DurationChip(
                                preset   = preset,
                                selected = selectedPreset == preset,
                                modifier = Modifier.weight(1f),
                                onClick  = {
                                    if (preset.minutes == -1) {
                                        // Custom has no fixed number — always
                                        // open the dial so a value can be
                                        // picked (or re-picked). Confirming
                                        // it is what actually arms the orb.
                                        onCustom(armedMinutes?.takeIf { armedIsCustom } ?: 45)
                                    } else if (selectedPreset == preset) {
                                        // Tapping the already-armed chip again disarms it.
                                        onSelectPreset(null)
                                    } else {
                                        onSelectPreset(preset.minutes)
                                    }
                                }
                            )
                        }
                        repeat((3 - rowPresets.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                selectedPreset?.let { preset ->
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.12f))
                    Spacer(Modifier.height(16.dp))

                    // No "Start" button here on purpose — the chips/dial only
                    // arm a duration. The orb above is the single trigger for
                    // actually starting a lockdown; this is just confirming
                    // what's armed and nudging people back up to it.
                    val armedLabel = if (armedIsCustom) formatDuration(armedMinutes ?: 0) else preset.label
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Text(
                            "Hold the orb above to start a $armedLabel lockdown",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary,
                            textAlign  = TextAlign.Center
                        )
                    }
                    if (armedIsCustom) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { onCustom(armedMinutes ?: 45) }) {
                            Text("Change time", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationChip(preset: DurationPreset, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bgColor     = if (selected) AccentBlue.copy(alpha = 0.18f) else CardSurface
    val borderColor = if (selected) AccentBlue else TextMuted.copy(alpha = 0.18f)
    val textColor   = if (selected) AccentBlue else TextPrimary
    val borderWidth = if (selected) 1.5.dp else 1.dp

    Box(
        modifier         = modifier.clip(RoundedCornerShape(12.dp)).background(bgColor).clickable(onClick = onClick),
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

// ════════════════════════════════════ Whitelist summary (entry point) ════════════════════════════════════
//
// One merged card replaces what used to be a header + search bar + full scrolling
// app list all living loose inside the lockdown page. The card always shows the
// count and a stacked preview of who's allowed; tapping it opens the full
// searchable manage-whitelist screen. This is the same "summary row → dedicated
// manager" pattern iOS Focus/Screen Time and apps like Opal use for allow-lists,
// so people aren't scrolling past hundreds of installed apps just to configure a
// lockdown duration.

@Composable
private fun WhitelistSummaryCard(
    count       : Int,
    previewApps : List<DeviceApp>,
    onClick     : () -> Unit
) {
    val pressScale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "whitelistCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        border   = BorderStroke(1.dp, TextMuted.copy(alpha = 0.10f))
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AccentTeal.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Whitelist", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (count == 0) "No apps allowed yet — tap to choose"
                    else "$count app${if (count == 1) "" else "s"} allowed during lockdown",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            if (previewApps.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                WhitelistAvatarStack(apps = previewApps)
                Spacer(Modifier.width(10.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Manage whitelist", tint = TextMuted)
        }
    }
}

// Overlapping "face pile" of the first few whitelisted apps — gives an instant
// preview of who's allowed without opening anything, same idea as the member
// avatar stacks in Slack/Notion.
@Composable
private fun WhitelistAvatarStack(apps: List<DeviceApp>) {
    val shown    = apps.take(3)
    val overflow = apps.size - shown.size

    Row {
        shown.forEachIndexed { index, app ->
            Box(
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .zIndex((shown.size - index).toFloat())
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .border(2.dp, CardSurface, CircleShape)
            ) {
                MiniAppAvatar(app = app, size = 24)
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (-8 * shown.size).dp)
                    .zIndex(0f)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CardSurfaceAlt)
                    .border(2.dp, CardSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+$overflow", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun MiniAppAvatar(app: DeviceApp, size: Int) {
    val icon = remember(app.packageName) { InstalledApps.iconFor(app.packageName) }
    Box(
        modifier         = Modifier.fillMaxSize().clip(CircleShape).background(CardSurfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size.dp).clip(CircleShape))
        } else {
            Text(
                app.label.take(1).uppercase(),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
        }
    }
}

// ════════════════════════════════════ Active lockdown panel ════════════════════════════════════

@Composable
private fun ActiveLockdownPanel(
    decision        : LockdownDecision,
    now             : Long,
    breaksRemaining : Int,
    onEmergencyBreak: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = if (decision.onBreak) MaterialTheme.colorScheme.tertiaryContainer
                             else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (decision.onBreak) Icons.Filled.Bolt else Icons.Filled.Lock,
                contentDescription = null,
                tint               = if (decision.onBreak) AccentTeal else AccentRed,
                modifier           = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (decision.onBreak) "Emergency Break active" else "Lockdown is active",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(decision.reason, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            Spacer(Modifier.height(20.dp))

            val targetMillis = if (decision.onBreak) decision.breakEndsAtMillis else decision.endsAtMillis
            if (targetMillis in 1 until Long.MAX_VALUE) {
                val remainingSec = ((targetMillis - now) / 1000L).coerceAtLeast(0)
                Text(formatCountdown(remainingSec), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(if (decision.onBreak) "until lockdown resumes" else "remaining", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                if (decision.onBreak) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress      = { (remainingSec / BlockerRepository.breakDurationSeconds().toFloat()).coerceIn(0f, 1f) },
                        modifier      = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color         = AccentTeal,
                        trackColor    = AccentTeal.copy(alpha = 0.2f)
                    )
                }
            } else {
                Text("∞", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("indefinite lockdown", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }

            Spacer(Modifier.height(24.dp))

            if (!decision.onBreak && breaksRemaining > 0) {
                OutlinedButton(
                    onClick          = onEmergencyBreak,
                    modifier         = Modifier.fillMaxWidth(),
                    shape            = MaterialTheme.shapes.large,
                    border           = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.6f)),
                    colors           = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                    contentPadding   = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Emergency Break ($breaksRemaining left)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ════════════════════════════════════ Section header + schedule cards ════════════════════════════════════

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = AccentBlue, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun EmptyHintCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = CardSurface)) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = TextTertiary)
    }
}

@Composable
private fun ScheduleCard(schedule: LockdownSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = CardSurface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (schedule.label.isNotBlank()) Text(schedule.label, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${BlockEngine.formatMinutes(schedule.startMinutes)} – ${BlockEngine.formatMinutes(schedule.endMinutes)}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(
                    schedule.daysOfWeek.sortedBy { DAY_ORDER.indexOf(it) }.mapNotNull { DAY_LABELS[it] }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
            }
            Switch(
                checked         = schedule.enabled,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentTeal)
            )
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Shield, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
        }
    }
}

// ════════════════════════════════════ Main lazy column ════════════════════════════════════

@Composable
private fun EmbeddedLockdownLazyColumn(
    modifier         : Modifier,
    sessionRunning   : Boolean,
    decision         : LockdownDecision,
    now              : Long,
    breaksRemaining  : Int,
    schedules        : List<LockdownSchedule>,
    onManageWhitelist: () -> Unit,
    armedMinutes     : Int?,
    armedIsCustom    : Boolean,
    onSelectPreset   : (Int?) -> Unit,
    onCustom         : (Int) -> Unit,
    onIgnite         : (Offset, Int) -> Unit,
    onEmergencyBreak : () -> Unit,
    onAddSchedule    : () -> Unit,
    onToggleSchedule : (LockdownSchedule, Boolean) -> Unit,
    onDeleteSchedule : (LockdownSchedule) -> Unit,
    onEditSchedule   : (LockdownSchedule) -> Unit
) {
    val context   = LocalContext.current
    val whitelist by BlockerRepository.whitelist.collectAsState()
    val all       by InstalledApps.apps.collectAsState()

    LaunchedEffect(Unit) { if (all.isEmpty()) InstalledApps.refresh(context) }

    val whitelistedApps = remember(all, whitelist) {
        all.filter { it.packageName in whitelist }
    }

    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {

        // ── 1. Hero / Active panel ──────────────────────────────────────────
        item(key = "session_header") {
            if (!sessionRunning) LockdownHeroSection(
                armedMinutes   = armedMinutes,
                armedIsCustom  = armedIsCustom,
                onSelectPreset = onSelectPreset,
                onCustom       = onCustom,
                onIgnite       = onIgnite
            )
            else ActiveLockdownPanel(
                decision         = decision,
                now              = now,
                breaksRemaining  = breaksRemaining,
                onEmergencyBreak = onEmergencyBreak
            )
        }

        // ── 2. Whitelist — one merged card: count + preview + tap to manage ──
        item(key = "whitelist_summary_card") {
            WhitelistSummaryCard(
                count       = whitelist.size,
                previewApps = whitelistedApps,
                onClick     = onManageWhitelist
            )
        }

        // Divider before the rest of the settings
        item(key = "divider_after_whitelist") {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = TextMuted.copy(alpha = 0.10f))
            Spacer(Modifier.height(4.dp))
        }

        // ── 3. Emergency breaks ────────────────────────────────────────────
        item(key = "emergency_breaks_header") { SectionHeader(title = "Emergency breaks") }

        item(key = "emergency_breaks_card") {
            val breakConfig by BlockerRepository.strictMode.collectAsState()
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("When lockdown is active, you can request a short break. Configure how many and how long each one lasts.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Breaks per session", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontWeight = FontWeight.SemiBold)
                            EmergencyBreakPill(
                                label = if (breakConfig.maxBreaksPerSession == 0) "None" else "${breakConfig.maxBreaksPerSession}",
                                color = if (breakConfig.maxBreaksPerSession == 0) AccentRed else AccentTeal
                            )
                        }
                        Slider(
                            value         = breakConfig.maxBreaksPerSession.toFloat(),
                            onValueChange = { BlockerRepository.setStrictMode(breakConfig.copy(maxBreaksPerSession = it.toInt())) },
                            valueRange    = 0f..5f,
                            steps         = 4,
                            colors        = SliderDefaults.colors(thumbColor = AccentTeal, activeTrackColor = AccentTeal, inactiveTrackColor = AccentTeal.copy(alpha = 0.2f))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0 (none)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text("5", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        if (breakConfig.maxBreaksPerSession == 0) {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentRed.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("No breaks allowed. Once lockdown starts, it runs until it ends.", style = MaterialTheme.typography.bodySmall, color = AccentRed)
                            }
                        }
                    }

                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.10f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Break duration", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontWeight = FontWeight.SemiBold)
                            EmergencyBreakPill(label = "${breakConfig.breakDurationMinutes} min", color = AccentTeal)
                        }
                        Slider(
                            value         = breakConfig.breakDurationMinutes.toFloat(),
                            onValueChange = { BlockerRepository.setStrictMode(breakConfig.copy(breakDurationMinutes = it.toInt())) },
                            valueRange    = 1f..30f,
                            steps         = 28,
                            colors        = SliderDefaults.colors(thumbColor = AccentTeal, activeTrackColor = AccentTeal, inactiveTrackColor = AccentTeal.copy(alpha = 0.2f))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1 min", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text("30 min", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }
        }

        // ── 4. Daily schedules ─────────────────────────────────────────────
        item(key = "schedules_header") {
            SectionHeader(title = "Daily schedules", action = "+ Add", onAction = onAddSchedule)
        }

        if (schedules.isEmpty()) {
            item(key = "schedules_empty") {
                EmptyHintCard("No schedules yet. Add one for things like \u201CLock every night 11pm\u20137am.\u201D")
            }
        } else {
            items(schedules, key = { "schedule_${it.id}" }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onToggle = { onToggleSchedule(schedule, it) },
                    onDelete = { onDeleteSchedule(schedule) },
                    onEdit   = { onEditSchedule(schedule) }
                )
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

// ════════════════════════════════════ Radial timer dialog ════════════════════════════════════

@Composable
private fun RadialTimerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(initialMinutes.coerceIn(1, 240)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CardSurfaceAlt,
        title            = { Text("Custom Duration", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Drag the dial to set your focus time", style = MaterialTheme.typography.bodySmall, color = TextTertiary, textAlign = TextAlign.Center)
                RadialDial(minutes = minutes, onMinutesChange = { minutes = it })
                Text(formatDuration(minutes), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NudgeButton("−15m") { minutes = (minutes - 15).coerceAtLeast(1) }
                    NudgeButton("−5m")  { minutes = (minutes - 5).coerceAtLeast(1) }
                    NudgeButton("+5m")  { minutes = (minutes + 5).coerceAtMost(240) }
                    NudgeButton("+15m") { minutes = (minutes + 15).coerceAtMost(240) }
                }
                // This only arms the orb — it doesn't start anything. Said
                // explicitly here so it's clear the lockdown still needs the
                // hold gesture back on the main screen.
                Text("This sets the time — you'll still hold the orb to start", style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(minutes) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Use ${formatDuration(minutes)}", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun RadialDial(minutes: Int, onMinutesChange: (Int) -> Unit) {
    var centerX by remember { mutableStateOf(0f) }
    var centerY by remember { mutableStateOf(0f) }
    val tickColor      = TextPrimary
    val dialTrackColor = CardSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp).pointerInput(Unit) {
            detectDragGestures { change, _ ->
                change.consume()
                val dx = change.position.x - centerX
                val dy = change.position.y - centerY
                val angleDeg = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()).coerceIn(-Math.PI, Math.PI)) + 360) % 360
                val minsFromAngle = (angleDeg / 360.0 * 60.0).roundToInt().coerceIn(0, 60)
                val fullLaps = (minutes / 60).coerceIn(0, 3)
                val candidate = fullLaps * 60 + minsFromAngle
                val adjusted = when {
                    minutes % 60 > 50 && minsFromAngle < 10 && fullLaps < 3 -> (fullLaps + 1) * 60 + minsFromAngle
                    minutes % 60 < 10 && minsFromAngle > 50 && fullLaps > 0  -> (fullLaps - 1) * 60 + minsFromAngle
                    else -> candidate
                }
                onMinutesChange(adjusted.coerceIn(1, 240))
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            centerX = size.width / 2f
            centerY = size.height / 2f
            val radius = size.minDimension / 2f

            drawCircle(color = dialTrackColor, radius = radius, center = Offset(centerX, centerY))

            val sweepAngle = ((minutes % 60) / 60f) * 360f
            drawArc(color = AccentBlue, startAngle = -90f, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round))

            for (i in 0 until 12) {
                val tickAngle = Math.toRadians((i * 30.0) - 90.0)
                val isLarge   = i % 3 == 0
                val innerR    = radius - (if (isLarge) 28.dp.toPx() else 20.dp.toPx())
                val outerR    = radius - 10.dp.toPx()
                drawLine(
                    color       = if (isLarge) tickColor.copy(alpha = 0.4f) else tickColor.copy(alpha = 0.15f),
                    start       = Offset(centerX + (innerR * cos(tickAngle)).toFloat(), centerY + (innerR * sin(tickAngle)).toFloat()),
                    end         = Offset(centerX + (outerR * cos(tickAngle)).toFloat(), centerY + (outerR * sin(tickAngle)).toFloat()),
                    strokeWidth = if (isLarge) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            val handleAngle = Math.toRadians((sweepAngle - 90.0))
            drawCircle(color = AccentBlue, radius = 10.dp.toPx(), center = Offset(centerX + (radius * cos(handleAngle)).toFloat(), centerY + (radius * sin(handleAngle)).toFloat()))

            val fullLaps  = (minutes / 60).coerceIn(0, 4)
            val dotSpacing = 16.dp.toPx()
            val dotStartX  = centerX - ((fullLaps - 1) * dotSpacing) / 2f
            for (lap in 0 until fullLaps) {
                drawCircle(color = AccentBlue.copy(alpha = 0.7f), radius = 5.dp.toPx(), center = Offset(dotStartX + lap * dotSpacing, centerY + 28.dp.toPx()))
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick          = onClick,
        shape            = MaterialTheme.shapes.small,
        border           = BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f)),
        colors           = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        contentPadding   = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return when { h == 0 -> "${m}m"; m == 0 -> "${h}h"; else -> "${h}h ${m}m" }
}

// ════════════════════════════════════ Schedule edit dialog ════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditDialog(schedule: LockdownSchedule, onDismiss: () -> Unit, onSave: (LockdownSchedule) -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(schedule.label) }
    var start by remember { mutableStateOf(schedule.startMinutes) }
    var end   by remember { mutableStateOf(schedule.endMinutes) }
    var days  by remember { mutableStateOf(schedule.daysOfWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Lockdown schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (optional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pickTime(context, start) { start = it } }) { Text("From ${BlockEngine.formatMinutes(start)}") }
                    OutlinedButton(onClick = { pickTime(context, end)   { end   = it } }) { Text("To ${BlockEngine.formatMinutes(end)}") }
                }
                Text("Active on:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_ORDER.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick  = { days = if (day in days) days - day else days + day },
                            label    = { Text(DAY_LABELS[day] ?: "") }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(schedule.copy(label = label, startMinutes = start, endMinutes = end, daysOfWeek = days)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ════════════════════════════════════ Helpers ════════════════════════════════════

private fun pickTime(context: android.content.Context, currentMinutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, currentMinutes / 60, currentMinutes % 60, false).show()
}

private fun formatCountdown(totalSec: Long): String {
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun EmergencyBreakPill(label: String, color: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
