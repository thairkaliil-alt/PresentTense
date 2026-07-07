package com.allinone.blocker.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.allinone.blocker.R
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownDecision
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownSchedule
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionEasing
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
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

// No "Custom" entry anymore — the dial in the Pick Duration card can reach
// any value directly, so these are now pure quick-jump shortcuts rather than
// the only way to pick a duration.
private val DURATION_PRESETS = listOf(
    DurationPreset("15m",   15),
    DurationPreset("25m",   25),
    DurationPreset("50m",   50),
    DurationPreset("90m",   90),
    DurationPreset("2h",   120)
)

// Quick-pick presets for a single emergency break's length. Reuses the same
// DurationChip look as the lockdown-length picker above so the two "pick a
// duration" moments in this screen feel like one consistent design language
// instead of two different controls (chips vs. a slider).
private val BREAK_DURATION_PRESETS = listOf(
    DurationPreset("5m",  5),
    DurationPreset("10m", 10),
    DurationPreset("15m", 15),
    DurationPreset("20m", 20),
    DurationPreset("30m", 30)
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
    val reducedMotion = LocalReducedMotion.current

    // ── True edge-to-edge for the void ────────────────────────────────────
    // fillMaxSize() alone only fills the app's *content* area — Android
    // reserves a strip at the bottom for the gesture/nav bar ("the ribbon")
    // and won't let a normal composable paint under it. The same trick
    // LockdownLauncherActivity already uses (immersive, transient-by-swipe)
    // is applied here for the few hundred milliseconds of the hold, so the
    // void genuinely eats the whole physical screen instead of stopping
    // short at that strip. It's restored immediately if the hold is
    // released early; if it's committed, LockdownLauncherActivity takes
    // over the window right after and sets its own immersive flags anyway.
    val hostActivity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        ctx as? Activity
    }
    fun setImmersive(hidden: Boolean) {
        val window = hostActivity?.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, !hidden)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (hidden) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    // Safety net: if this screen is ever torn down mid-hold (process death,
    // back navigation racing the gesture, etc.) don't leave the host
    // Activity stuck without its system bars.
    DisposableEffect(Unit) {
        onDispose { setImmersive(false) }
    }

    // ── Armed duration ───────────────────────────────────────────────────
    // Dragging the dial (or tapping a quick-jump pill) never starts
    // anything by itself — it only "arms" the orb with a chosen number of
    // minutes. The ONLY way a lockdown actually begins is holding the orb
    // down until the void below finishes swallowing the screen. Starts
    // pre-loaded at 25 minutes (a classic single focus-block length) so
    // there's always a sensible value ready the first time someone opens
    // this screen, rather than forcing a choice before the orb does anything.
    var armedMinutes by remember { mutableStateOf(25) }

    // ── Hold-to-void ignition state ──────────────────────────────────────
    // Lives here, at the screen root, rather than down inside the hero
    // section, because the growing void has to paint OVER the TopAppBar and
    // everything else — something nested inside the Scaffold's content
    // can't do. Unlike a "charge, then release, then play an animation"
    // design, [voidProgress] is driven live for the entire duration of the
    // hold: the void's radius on screen at any instant IS how long the
    // press has lasted. Reaching 1f *while still held* is what actually
    // starts the lockdown; letting go earlier reverses it. See
    // [VoidExpansion] and [LiquidGlassOrb] below for the visuals.
    var holdOrigin       by remember { mutableStateOf<Offset?>(null) }
    var holdArmedMinutes by remember { mutableStateOf<Int?>(null) }
    var isHolding         by remember { mutableStateOf(false) }
    var voidCommitted     by remember { mutableStateOf(false) }
    val voidProgress      = remember { Animatable(0f) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            voidCommitted = false
            // Go edge-to-edge the instant the hold begins, so the void's
            // very first frame already paints under the nav-bar ribbon
            // instead of popping late once it catches up.
            setImmersive(true)
            val chargeMs = if (reducedMotion) 160 else VOID_HOLD_MS
            // Accelerate easing (slow start, fast finish) so the void reads
            // like something gathering pull before rapidly swallowing the
            // screen, rather than a mechanical linear wipe.
            voidProgress.animateTo(1f, tween(durationMillis = chargeMs, easing = MotionEasing.Accelerate))
            // Only reached if the hold was sustained all the way through —
            // animateTo above gets cancelled (and this line never runs) the
            // instant isHolding flips back to false from an early release.
            voidCommitted = true
            val mins = holdArmedMinutes
            if (mins != null) {
                if (!reducedMotion) delay(90) // let the black fully read before the cut
                BlockerRepository.startManualLock(mins)
                goHome()
            }
        } else if (!voidCommitted) {
            // Released early — ease back down instead of snapping, so
            // letting go still feels deliberate rather than broken.
            if (reducedMotion) voidProgress.snapTo(0f)
            else voidProgress.animateTo(0f, MotionSpecs.exit(MotionDurations.Emphasized))
            holdOrigin = null
            setImmersive(false)
        }
    }

    // Once a lockdown session is actually confirmed active, make sure
    // nothing is left mid-flight — covers the case where this composable
    // stays alive across the goHome() navigation.
    LaunchedEffect(sessionRunning) {
        if (sessionRunning) {
            isHolding     = false
            voidCommitted = false
            holdOrigin    = null
            voidProgress.snapTo(0f)
        }
    }

    // Subtle scroll-aware top bar: stays flat/transparent-feeling while the
    // list is at rest, and picks up a faint tone shift once content actually
    // scrolls underneath it — the same small "the surface has depth" cue
    // Gmail/Notion/Slack use, instead of a bar that looks identical whether
    // or not anything is happening beneath it.
    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier       = Modifier.fillMaxSize().nestedScroll(topBarScroll.nestedScrollConnection),
            containerColor = BgDarkest,
            topBar = {
                TopAppBar(
                    title = { Text("Lockdown", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor         = BgDarkest,
                        scrolledContainerColor = CardSurface
                    ),
                    scrollBehavior = topBarScroll
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
                onSelectPreset      = { mins -> armedMinutes = mins },
                voidProgress        = voidProgress.value,
                onHoldStart         = { origin, mins -> holdOrigin = origin; holdArmedMinutes = mins; isHolding = true },
                onHoldEnd           = { isHolding = false },
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

        // The hold-driven void. Only present while a hold has ever started
        // this cycle (holdOrigin != null); its radius tracks voidProgress
        // continuously, whether growing (still held), shrinking (released
        // early) or pinned at 1f (committed, waiting on goHome()).
        holdOrigin?.let { origin ->
            VoidExpansion(origin = origin, progress = voidProgress.value)
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
}

// ════════════════════════════════════ Liquid Glass Orb ════════════════════════════════════
//
// The orb is the ONLY way to start a lockdown. Holding it down doesn't just
// charge a little ring anymore — it directly drives [VoidExpansion] below,
// a full-screen void that grows from the orb's own position in lockstep
// with how long the press has lasted. There's no separate "charge, then
// release, then play an animation" step: the void's radius at any instant
// IS the hold's progress, so letting go early visibly reverses it, and
// reaching full screen while still held is itself what starts the
// lockdown. Same trigger language as Snapchat's record button or iOS's
// hold-to-confirm toggles, but the payoff (the void) and the feedback (the
// void's growth) are now the same object instead of two.

@Composable
private fun LiquidGlassOrb(
    modifier    : Modifier = Modifier,
    armedMinutes: Int      = 25,
    progress    : Float    = 0f,
    onHoldStart : (Offset, Int) -> Unit = { _, _ -> },
    onHoldEnd   : () -> Unit = {}
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

    var orbCenterInRoot by remember { mutableStateOf(Offset.Zero) }

    // As the hold progresses, the glass glows brighter and eases in very
    // slightly — a physical "gathering energy" read — and the inner icon
    // fades out, since the growing void is about to visually swallow it
    // anyway. progress comes from the root's voidProgress, so this orb and
    // the full-screen void it feeds are always perfectly in sync.
    val chargeGlow = glowAlpha * (1f + progress * 0.7f)
    val pressEase  = 1f - (progress * 0.05f)
    val iconAlpha  = 1f - (progress * 0.85f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(220.dp)
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                orbCenterInRoot = Offset(p.x + coords.size.width / 2f, p.y + coords.size.height / 2f)
            }
            .pointerInput(armedMinutes, reducedMotion) {
                coroutineScope {
                    while (true) {
                        awaitPointerEventScope { awaitFirstDown(requireUnconsumed = false) }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHoldStart(orbCenterInRoot, armedMinutes)
                        awaitPointerEventScope { waitForUpOrCancellation() }
                        onHoldEnd()
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

        // Inner dark core — the logo lives here. Fades out as progress
        // climbs, since [VoidExpansion] starts at exactly this orb's own
        // radius and will already be growing past/over it by then.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(148.dp)
                .scale(breathScale * pressEase)
                .graphicsLayer { alpha = iconAlpha }
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
    }
}

// ════════════════════════════════════ Void Expansion (the hold gesture itself) ════════════════════════════════════
//
// This is not a flourish that plays after the hold finishes — it IS the
// hold. As long as a finger stays down on the orb with a duration armed,
// this circle's radius tracks [progress] frame for frame: a literal void
// eating the screen from wherever the orb sits, live, the whole time it's
// held. It starts at exactly the orb's own outer glow radius (110.dp) so
// there's no seam between "orb" and "void starting to grow" — the glass
// itself is the void's origin point. Reaching full coverage while still
// held is what starts the lockdown (driven by the isHolding LaunchedEffect
// in [LockdownScreen]); releasing early lets this same circle contract
// back down instead of just vanishing.
//
// Motion references: Material 3 Expressive's "hero moment" guidance — brief,
// physics-driven beats reserved for the one or two interactions per app that
// deserve to feel alive rather than efficient — applied to a shape that grows
// from a touch point and decelerate as it commits; the hold-to-confirm
// gestures in iOS's "Hold to power off" and Instagram's record button, where
// the progress indicator and the payoff are the same visual object rather
// than two separate animations chained together; and the "energy absorption"
// language mobile games use for consuming/collecting moments (loot vacuuming
// into a reward chest, a portal pulling in matter) — implemented here as
// particles spiraling into the void rather than a flat circular wipe, which
// is what makes a progress reveal feel gamified instead of mechanical.
// Timing deliberately runs past the ~400ms Doherty threshold this app's
// motion system otherwise holds to everywhere else (see Motion.kt) —
// everyday micro-interactions should be fast, but this is a once-a-session
// ritual, closer to a game's level-transition wipe than a button press.

// 2200ms — deliberately slower than a typical hold-to-confirm (iOS's "Hold
// to power off" runs ~2s; Instagram's record button has no fixed ceiling but
// reads similarly unhurried past the first second). A once-a-session ritual
// like starting a lockdown benefits from feeling weighty rather than snappy —
// long enough that letting go early is a real, felt choice, not a twitch.
private const val VOID_HOLD_MS = 2200

@Composable
private fun VoidExpansion(origin: Offset, progress: Float) {
    if (progress <= 0f) return

    val density = LocalDensity.current
    val config  = LocalConfiguration.current

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
    val startRadiusPx = with(density) { 110.dp.toPx() }

    // Canvas's draw block runs outside composition, so any color that reads
    // light/dark theme (like BgDarkest) has to be captured here first, in
    // the composable body, and then just referenced as a plain value below.
    val lockdownBlack = BgDarkest

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallow any stray touches while the void is mid-flight so
            // nothing underneath can be double-tapped during the hold.
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = startRadiusPx + (maxRadiusPx - startRadiusPx) * progress

            // Color stays glassy blue/teal through the first ~45% of the
            // hold, then rapidly saturates to lockdown black as it nears
            // full coverage — the "swallowed by darkness" beat lands right
            // at the end instead of dragging across the whole gesture.
            val colorMix  = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val fillColor = lerp(AccentBlue, lockdownBlack, colorMix)
            val edgeColor = lerp(AccentTeal, lockdownBlack, colorMix)

            // Concentric echo rings continuously emanate from the edge while
            // there's still energy left (before it's gone fully black) — a
            // "portal pulse" stack. Bumped from 3 to 7, tighter-spaced, so
            // the edge reads as a dense, layered pulse instead of a few
            // isolated ripples — entirely driven by progress so it never
            // needs its own animation clock.
            val ringCount = 7
            if (colorMix < 1f) {
                for (i in 0 until ringCount) {
                    val phase = ((progress * 2.6f) + i.toFloat() / ringCount) % 1f
                    val ringRadius = startRadiusPx + (maxRadiusPx - startRadiusPx) * phase
                    val ringAlpha  = (1f - phase) * 0.20f * (1f - colorMix)
                    if (ringAlpha > 0.01f) {
                        drawCircle(
                            color  = edgeColor.copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = origin,
                            style  = Stroke(width = (1.5f + (i % 3)).dp.toPx())
                        )
                    }
                }
            }

            // Consumption particles — a wide halo of motes swirling and
            // spiraling inward toward the growing edge, like matter being
            // pulled into a portal/vacuum (a common "collect" or "absorb"
            // beat in mobile games, e.g. loot flying into a reward chest).
            // Positions are pure functions of progress + index, so this
            // needs no particle system or extra animation clock — the same
            // "state IS the visual" approach as the void's radius itself.
            // Golden-angle spacing (137.5°) gives an even, non-repeating
            // spread instead of particles clumping into visible rows.
            if (colorMix < 1f) {
                val particleCount = 40
                val spinDeg = progress * 300f
                for (i in 0 until particleCount) {
                    val seedAngle  = (i * 137.5f) % 360f
                    val angleRad   = Math.toRadians((seedAngle + spinDeg).toDouble())
                    val laneJitter = (i % 5) / 5f
                    // Orbit sits just outside the growing edge and is pulled
                    // in closer as progress climbs — the spiral-inward read.
                    val orbitRadius = radius * (1.08f + laneJitter * 0.35f) * (1f - progress * 0.4f)
                    val px = origin.x + (orbitRadius * cos(angleRad)).toFloat()
                    val py = origin.y + (orbitRadius * sin(angleRad)).toFloat()
                    val particleAlpha = 0.55f * (1f - colorMix) * (0.35f + 0.65f * laneJitter)
                    val particleRadius = (1f + (i % 3)).dp.toPx()
                    drawCircle(
                        color  = edgeColor.copy(alpha = particleAlpha),
                        radius = particleRadius,
                        center = Offset(px, py)
                    )
                }
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(fillColor, lerp(fillColor, lockdownBlack, 0.35f)),
                    center = origin,
                    radius = (radius * 1.05f).coerceAtLeast(1f)
                ),
                radius = radius,
                center = origin
            )

            // Two-layer rim right at the growing edge — a soft outer bloom
            // plus a crisp inner line — the "am I still charging" read at a
            // glance, denser than a single stroke, fading out as the color
            // finishes saturating to black.
            if (colorMix < 1f) {
                drawCircle(
                    color  = edgeColor.copy(alpha = 0.20f * (1f - colorMix)),
                    radius = radius,
                    center = origin,
                    style  = Stroke(width = 10.dp.toPx())
                )
                drawCircle(
                    color  = Color.White.copy(alpha = 0.35f * (1f - colorMix)),
                    radius = radius,
                    center = origin,
                    style  = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

// ════════════════════════════════════ Hero / Pick Duration ════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockdownHeroSection(
    armedMinutes  : Int,
    voidProgress  : Float,
    onSelectPreset: (Int) -> Unit,
    onHoldStart   : (Offset, Int) -> Unit = { _, _ -> },
    onHoldEnd     : () -> Unit = {}
) {
    Column(
        modifier              = Modifier.fillMaxWidth(),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        LiquidGlassOrb(
            // The orb only ever fires from a hold gesture — see
            // [LiquidGlassOrb] below. Dragging the dial or tapping a
            // quick-jump pill just sets armedMinutes; nothing else in this
            // screen is allowed to start a lockdown directly.
            armedMinutes = armedMinutes,
            progress     = voidProgress,
            onHoldStart  = onHoldStart,
            onHoldEnd    = onHoldEnd
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
            shape    = RoundedCornerShape(28.dp),
            colors   = CardDefaults.cardColors(containerColor = CardSurfaceAlt)
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    Text("Pick Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text("Everything except your whitelist will be blocked.", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))

                // The live readout IS the primary feedback for this control —
                // big enough to read at a glance while dragging, so the dial
                // never needs a separate "confirm" step to know what's armed.
                Text(
                    formatDuration(armedMinutes),
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
                Spacer(Modifier.height(12.dp))

                // The dial itself is the primary control — drag anywhere on
                // the ring to set any minute value directly, rather than
                // picking from a fixed menu of options. See [RadialDial].
                RadialDial(minutes = armedMinutes, onMinutesChange = onSelectPreset)

                Spacer(Modifier.height(20.dp))

                // Quick-jump pills — shortcuts onto the dial for the most
                // common lengths, not the primary way to choose a duration
                // anymore. Tapping one snaps the dial straight there; you can
                // still drag from that point to fine-tune.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DURATION_PRESETS.forEach { preset ->
                        DurationJumpPill(
                            label    = preset.label,
                            selected = armedMinutes == preset.minutes,
                            onClick  = { onSelectPreset(preset.minutes) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))

                // No "Start" button here on purpose — the dial only arms a
                // duration. The orb above is the single trigger for actually
                // starting a lockdown; this is just confirming what's armed
                // and nudging people back up to it.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Text(
                        "Hold the orb above to start a ${formatDuration(armedMinutes)} lockdown",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Small pill used only as a quick-jump shortcut under the dial — visually
// lighter than [DurationChip] on purpose, since it's now a secondary shortcut
// rather than the primary way to pick a duration.
@Composable
private fun DurationJumpPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor     = if (selected) AccentBlue.copy(alpha = 0.18f) else CardSurface
    val borderColor = if (selected) AccentBlue else TextMuted.copy(alpha = 0.18f)
    val textColor   = if (selected) AccentBlue else TextSecondary

    Box(
        modifier         = Modifier.clip(RoundedCornerShape(999.dp)).background(bgColor).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(color = borderColor, cornerRadius = CornerRadius(999.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
        }
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color      = textColor,
            modifier   = Modifier.padding(vertical = 8.dp, horizontal = 14.dp),
            textAlign  = TextAlign.Center
        )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .pressable(onClick = onClick),
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
            val badgeTint = if (decision.onBreak) AccentTeal else AccentRed
            Box(
                modifier         = Modifier.size(56.dp).clip(CircleShape).background(badgeTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (decision.onBreak) Icons.Filled.Bolt else Icons.Filled.Lock,
                    contentDescription = null,
                    tint               = badgeTint,
                    modifier           = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
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

// ════════════════════════════════════ Emergency breaks (expandable card) ════════════════════════════════════
//
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
//    the exact same DurationChip look as the lockdown-length picker at the
//    top of this screen, so the two duration choices in this screen feel
//    like one design instead of two.

@Composable
private fun EmergencyBreaksCard() {
    val breakConfig by BlockerRepository.strictMode.collectAsState()
    var expanded by remember { mutableStateOf(false) }

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
        border    = BorderStroke(1.dp, TextMuted.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(0.dp)
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

                    // Breaks per session — stepper
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Breaks per session", style = MaterialTheme.typography.labelLarge, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        BreakCountStepper(
                            value         = breakConfig.maxBreaksPerSession,
                            range         = 0..5,
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

// A small precise range (0–5) reads and adjusts far more reliably as a
// stepper than as a slider: every tap is exactly ±1, there's no risk of
// dragging past the number you meant to land on, and "None" is spelled out
// in words rather than just showing "0", which is easy to misread as "min".
@Composable
private fun BreakCountStepper(value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
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
            enabled = value > range.first,
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
            enabled = value < range.last,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        border   = BorderStroke(1.dp, TextMuted.copy(alpha = 0.10f))
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier         = Modifier.size(44.dp).clip(CircleShape).background(TextMuted.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextTertiary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ScheduleCard(schedule: LockdownSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        onClick  = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        border   = BorderStroke(1.dp, TextMuted.copy(alpha = 0.10f))
    ) {
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
            Spacer(Modifier.width(4.dp))
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
    armedMinutes     : Int,
    onSelectPreset   : (Int) -> Unit,
    voidProgress     : Float,
    onHoldStart      : (Offset, Int) -> Unit,
    onHoldEnd        : () -> Unit,
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
                voidProgress   = voidProgress,
                onSelectPreset = onSelectPreset,
                onHoldStart    = onHoldStart,
                onHoldEnd      = onHoldEnd
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

        // ── 3. Emergency breaks (expandable — see EmergencyBreaksCard) ──────
        item(key = "emergency_breaks_card") {
            EmergencyBreaksCard()
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

// ════════════════════════════════════ Radial dial ════════════════════════════════════
//
// This used to only exist inside a separate "Custom Duration" AlertDialog,
// reachable by tapping a 6th "Custom" chip. It's now the primary control
// living directly inside the Pick Duration card (see [LockdownHeroSection]) —
// any value from 1 minute to 4 hours is reachable by dragging, with the small
// pills below it as shortcuts rather than the only way in. Bumped from 200dp
// to 240dp now that it's the card's main content instead of a secondary
// dialog, and a soft haptic tick fires every 5 minutes crossed while
// dragging — the same tactile-precision cue iOS/Android's native pickers use
// so a continuous drag reads as deliberately steppable, not a flat slider.

@Composable
private fun RadialDial(minutes: Int, onMinutesChange: (Int) -> Unit) {
    var centerX by remember { mutableStateOf(0f) }
    var centerY by remember { mutableStateOf(0f) }
    val tickColor      = TextPrimary
    val dialTrackColor = CardSurface
    val haptics        = rememberHaptics()
    var lastHapticMinute by remember { mutableStateOf(minutes) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp).pointerInput(Unit) {
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
                val clamped = adjusted.coerceIn(1, 240)
                if (kotlin.math.abs(clamped - lastHapticMinute) >= 5) {
                    haptics.digitTick()
                    lastHapticMinute = clamped
                }
                onMinutesChange(clamped)
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
