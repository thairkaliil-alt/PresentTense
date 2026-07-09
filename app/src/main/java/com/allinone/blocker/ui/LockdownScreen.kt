package com.allinone.blocker.ui

import android.app.Activity
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.allinone.blocker.R
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownDecision
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownSchedule
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionEasing
import com.allinone.blocker.ui.motion.MotionSpecs
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ════════════════════════════════════ Duration Presets ════════════════════════════════════
//
// Redesigned around how people actually use a lockdown app: some sessions
// are short focus sprints (minutes), others are "put the phone away for the
// weekend" style commitments (days). A flat row of "15m / 25m / 50m" pills
// simply had no room to express that second category, which is exactly the
// gap that prompted this rework — see [PickDurationCard] and
// [WheelDurationPicker] below for the custom-entry side of the same fix.
// Each preset now carries its own icon + short "why you'd pick this" label,
// closer to how Headspace/Calm/Opal present session-length choices as
// distinct *intents* rather than raw numbers.

private data class DurationPreset(
    val label  : String,
    val minutes: Int,
    val icon   : androidx.compose.ui.graphics.vector.ImageVector
)

private val DURATION_PRESETS = listOf(
    DurationPreset("Quick Focus",  15,    Icons.Filled.Timer),
    DurationPreset("Deep Work",    50,    Icons.Filled.Bolt),
    DurationPreset("Study Block",  90,    Icons.Filled.Schedule),
    DurationPreset("Half Day",     4 * 60, Icons.Filled.WbSunny),
    DurationPreset("Overnight",    8 * 60, Icons.Filled.Bedtime),
    DurationPreset("Full Day",     24 * 60, Icons.Filled.Lock),
    DurationPreset("Weekend",      2 * 24 * 60, Icons.Filled.Weekend),
    DurationPreset("Full Week",    7 * 24 * 60, Icons.Filled.CalendarMonth)
)

// Hard ceiling for a single armed session — 90 days. Purely a sanity bound
// (nothing about the underlying storage needs it; startManualLock() takes
// a plain Int number of minutes), so an accidental wheel-drag or preset
// tap can never arm something absurd like a 10-year lockdown.
private const val MAX_ARMED_MINUTES = 90 * 24 * 60

// ════════════════════════════════════ Screen root ════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LockdownScreen(
    onBack: () -> Unit,
    onManageWhitelist: () -> Unit = {},
    onManageSchedules: () -> Unit = {},
    onManageEmergencyBreaks: () -> Unit = {}
) {
    val context = LocalContext.current
    val manualUntil by BlockerRepository.manualLockUntil.collectAsState()
    val schedules   by BlockerRepository.schedules.collectAsState()
    val breakUntil  by BlockerRepository.breakUntil.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }

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

    // A stable State<Float> wrapper around voidProgress's live value.
    // Passing THIS object down the tree below (instead of voidProgress.value
    // as a plain Float) is the whole fix for the lag: a Compose State
    // object's IDENTITY never changes, only its .value does, so composables
    // that just forward it along — the scrollable list, the hero section —
    // don't need to recompose 60 times a second anymore just to keep a
    // number moving through them. Only the one spot that actually reads
    // .value (deep inside the orb, and the void overlay in MainActivity)
    // re-renders on every animation frame, exactly like it should.
    val voidProgressState = remember { derivedStateOf { voidProgress.value } }

    // Safety net: if this screen is ever torn down mid-hold (process death,
    // back navigation racing the gesture, etc.) don't leave the host
    // Activity stuck without its system bars.
    DisposableEffect(Unit) {
        onDispose {
            setImmersive(false)
            // Only wipe the shared overlay if a lockdown never actually
            // committed. If the hold DID finish (voidCommitted == true),
            // startManualLock()/goHome() are already mid-flight — clearing
            // the overlay here, right as this screen tears down for the
            // navigation, would yank the finished black screen away instead
            // of letting it hand off cleanly. Leaving it be is harmless:
            // the sessionRunning effect below cleans it up properly once
            // the new lockdown session is confirmed active.
            if (!voidCommitted) {
                LockdownVoidOverlayState.origin = null
            }
        }
    }

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

    // ── Mirror the void into the app-wide overlay ────────────────────────
    // VoidExpansion used to be drawn right here, in a Box wrapping this
    // screen's own Scaffold — but that Box only fills the content slot
    // MainActivity's outer Scaffold hands to the Lockdown tab, which stops
    // short of the bottom tab bar. That made the void look like it covered
    // "the lockdown screen" instead of "the whole phone", which is the
    // point of the animation. Instead, origin and a reference to the live
    // progress state are mirrored into [LockdownVoidOverlayState], a tiny
    // shared holder that AppRoot reads to paint the real VoidExpansion
    // above EVERYTHING, bottom bar included. See AppRoot in MainActivity.kt.
    //
    // Note this SideEffect only needs to fire when holdOrigin actually
    // changes (hold starts/ends) — not every animation frame. voidProgressState
    // is a stable object handed over once; AppRoot reads its .value directly
    // wherever it draws the void, so the live progress ticks flow straight
    // from this screen's Animatable to AppRoot's Canvas without needing
    // LockdownScreen itself to recompose 60 times a second in between.
    SideEffect {
        LockdownVoidOverlayState.origin        = holdOrigin
        LockdownVoidOverlayState.progressState = voidProgressState
    }

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
                    actions = {
                        IconButton(onClick = onManageSchedules) {
                            Icon(Icons.Filled.Schedule, contentDescription = "Manage schedules")
                        }
                        IconButton(onClick = onManageEmergencyBreaks) {
                            Icon(Icons.Filled.Bolt, contentDescription = "Manage emergency breaks")
                        }
                        IconButton(onClick = onManageWhitelist) {
                            Icon(Icons.Filled.Shield, contentDescription = "Manage whitelist")
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
                armedMinutes        = armedMinutes,
                onSelectPreset      = { mins -> armedMinutes = mins.coerceIn(1, MAX_ARMED_MINUTES) },
                voidProgress        = voidProgressState,
                onHoldStart         = { origin, mins -> holdOrigin = origin; holdArmedMinutes = mins; isHolding = true },
                onHoldEnd           = { isHolding = false },
                onEmergencyBreak    = { StrictModeGate.guard { BlockerRepository.startEmergencyBreak() } }
            )
        }
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
    progress    : State<Float>,
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
    // the full-screen void it feeds are always perfectly in sync. Reading
    // .value here, right where it's used, is what scopes the 60fps
    // recomposition to just this small composable instead of everything
    // that happens to forward the State object along the way.
    val progressValue = progress.value
    val chargeGlow = glowAlpha * (1f + progressValue * 0.7f)
    val pressEase  = 1f - (progressValue * 0.05f)
    val iconAlpha  = 1f - (progressValue * 0.85f)

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
fun VoidExpansion(origin: Offset, progress: Float) {
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
    voidProgress  : State<Float>,
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

        // The wheel is the one confident primary control; Quick Pick sits
        // underneath it, visually quieter and closed by default — see the
        // comments on [PickDurationCard] and [QuickPickSection] for why
        // these two used to be tabs in one card and no longer are.
        PickDurationCard(armedMinutes = armedMinutes, onSelectPreset = onSelectPreset)
        Spacer(Modifier.height(14.dp))
        QuickPickSection(armedMinutes = armedMinutes, onSelectPreset = onSelectPreset)
    }
}

// ════════════════════════════════════ Pick Duration card ════════════════════════════════════
//
// This card now does exactly one job: dial in a precise duration on the
// Days/Hours/Minutes wheel, full range up to [MAX_ARMED_MINUTES]. Named
// presets used to live in a second tab bolted onto this same card; they
// now live below it in their own collapsed-by-default [QuickPickSection]
// instead. Two principles drove that split:
//   • Hick's Law — decision time grows with the number of visible options,
//     so the moment someone opens this screen they should see ONE clear
//     way to set a duration, not two competing controls fighting for the
//     same 300dp of card.
//   • Progressive disclosure (a core Nielsen Norman heuristic, and the same
//     one behind why Headspace/Calm/Opal keep session-length shortcuts a
//     tap away rather than front-and-center) — secondary paths stay
//     reachable without competing for attention with the primary one.
// Both controls still write into the same [armedMinutes], so the live
// readout here and the "Hold the orb…" hint stay correct regardless of
// which one was used last.
@Composable
private fun PickDurationCard(
    armedMinutes  : Int,
    onSelectPreset: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurfaceAlt),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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

            // The live readout — always reflects exactly what's armed right
            // now, whether the wheel or a Quick Pick tap set it last.
            Text(
                formatDuration(armedMinutes),
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Spacer(Modifier.height(22.dp))

            WheelDurationPicker(totalMinutes = armedMinutes, onTotalMinutesChange = onSelectPreset)

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = TextMuted.copy(alpha = 0.12f))
            Spacer(Modifier.height(16.dp))

            // No "Start" button here on purpose — this card only arms a
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

// ── Quick Pick — collapsed-by-default shortcut drawer ───────────────────
// Deliberately built to read as LOWER in the visual hierarchy than the card
// above it, not just placed after it: a flat tinted surface instead of an
// elevated Card, a small label instead of a titleMedium headline, and
// closed on first render. That's on purpose — this is a shortcut for
// people who already know "Deep Work" means 50 minutes to them, not a
// second decision meant to compete with the wheel above for attention.
//
// The header still surfaces the current pick ("Deep Work · 50m") whenever
// armedMinutes happens to match a named preset, even while collapsed — so
// closing this drawer only ever hides the *grid of options*, never the
// *information* about what's currently armed (Nielsen's "visibility of
// system status" heuristic). Tapping a preset inside auto-collapses the
// drawer again, since its job is done the moment a choice is made.
@Composable
private fun QuickPickSection(armedMinutes: Int, onSelectPreset: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionSpecs.standard(),
        label       = "quick_pick_chevron"
    )
    val matchedPreset = remember(armedMinutes) { DURATION_PRESETS.firstOrNull { it.minutes == armedMinutes } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface.copy(alpha = 0.55f))
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(28.dp).clip(CircleShape).background(TextMuted.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Quick Pick", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Text(
                    text  = matchedPreset?.let { "${it.label} · ${formatDuration(it.minutes)}" } ?: "Named presets for common sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Icon(
                imageVector        = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse quick pick" else "Expand quick pick",
                tint               = TextMuted,
                modifier           = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(animationSpec = MotionSpecs.enter()) + fadeIn(animationSpec = MotionSpecs.enter()),
            exit    = shrinkVertically(animationSpec = MotionSpecs.exit()) + fadeOut(animationSpec = MotionSpecs.exit())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 18.dp, top = 2.dp)
            ) {
                PresetGrid(
                    armedMinutes   = armedMinutes,
                    onSelectPreset = { minutes -> onSelectPreset(minutes); expanded = false }
                )
            }
        }
    }
}

// ── Quick presets grid ───────────────────────────────────────────────────
// Two columns of named, intent-based cards rather than a single row of
// terse "15m / 25m / 50m" pills — closer to how Headspace/Calm/Opal present
// session lengths as distinct *choices* (Quick Focus vs. Overnight vs.
// Weekend) rather than raw numbers competing for the same handful of
// pixels. Icons give each card a recognizable silhouette at a glance, which
// matters more here than it did for the old pill row now that the range
// spans minutes through a full week.
@Composable
private fun PresetGrid(armedMinutes: Int, onSelectPreset: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        DURATION_PRESETS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { preset ->
                    PresetCard(
                        preset   = preset,
                        selected = armedMinutes == preset.minutes,
                        onClick  = { onSelectPreset(preset.minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Odd final row: pad with an invisible spacer so the last
                // card stays the same width as its siblings instead of
                // stretching to fill the row on its own.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset  : DurationPreset,
    selected: Boolean,
    onClick : () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor     = if (selected) AccentBlue.copy(alpha = 0.14f) else CardSurface
    val borderColor = if (selected) AccentBlue else TextMuted.copy(alpha = 0.14f)
    val iconTint    = if (selected) AccentBlue else TextSecondary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier         = Modifier.size(30.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(preset.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                preset.label,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = if (selected) TextPrimary else TextSecondary
            )
            Text(
                formatDuration(preset.minutes),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) AccentBlue else TextMuted
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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

// ════════════════════════════════════ Main lazy column ════════════════════════════════════

@Composable
private fun EmbeddedLockdownLazyColumn(
    modifier         : Modifier,
    sessionRunning   : Boolean,
    decision         : LockdownDecision,
    now              : Long,
    breaksRemaining  : Int,
    armedMinutes     : Int,
    onSelectPreset   : (Int) -> Unit,
    voidProgress     : State<Float>,
    onHoldStart      : (Offset, Int) -> Unit,
    onHoldEnd        : () -> Unit,
    onEmergencyBreak : () -> Unit
) {
    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {

        // ── Hero / Active panel ─────────────────────────────────────────────
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

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

// ════════════════════════════════════ Custom duration wheels ════════════════════════════════════
//
// Replaces the old single circular dial, which topped out at 4 hours (four
// laps around one clock face) and had no path to something like "3 days"
// short of dragging through 4,320 individual minutes. Three independent,
// snapping Days / Hours / Minutes columns are the same pattern iOS's
// date-and-time picker (and most alarm-clock apps) settled on for exactly
// this problem: each column stays legible and fast to scan no matter how
// large its own range is, instead of one control trying to cover minutes
// through weeks on a single axis. A shared highlight bar marks the selected
// row across all three columns at once, and a light haptic tick fires
// whenever a column settles on a new value while scrolling — the same
// "felt, not heard" cue the old dial gave per 5-minute crossing.

private val WHEEL_ITEM_HEIGHT = 44.dp
private const val WHEEL_VISIBLE_ITEMS = 5
private val WHEEL_MAX_DAYS = MAX_ARMED_MINUTES / (24 * 60)

@Composable
private fun WheelDurationPicker(
    totalMinutes         : Int,
    onTotalMinutesChange : (Int) -> Unit
) {
    val days    = totalMinutes / (24 * 60)
    val hours   = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    fun emit(d: Int, h: Int, m: Int) {
        onTotalMinutesChange((d * 24 * 60) + (h * 60) + m)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        // Shared selection highlight, drawn first (underneath) so the
        // scrolling numbers pass over it instead of it sitting on top and
        // hiding them.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .clip(RoundedCornerShape(14.dp))
                .background(CardSurface)
                .border(BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f)), RoundedCornerShape(14.dp))
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            WheelColumn(
                range          = 0..WHEEL_MAX_DAYS,
                value          = days,
                suffix         = "d",
                onValueSettled = { emit(it, hours, minutes) },
                modifier       = Modifier.weight(1f)
            )
            WheelColumn(
                range          = 0..23,
                value          = hours,
                suffix         = "h",
                onValueSettled = { emit(days, it, minutes) },
                modifier       = Modifier.weight(1f)
            )
            WheelColumn(
                range          = 0..59,
                value          = minutes,
                suffix         = "m",
                onValueSettled = { emit(days, hours, it) },
                modifier       = Modifier.weight(1f)
            )
        }
    }
}

// A single snapping wheel: the item nearest dead-center of [WHEEL_VISIBLE_ITEMS]
// rows is "selected". Centering is done the same way native wheel pickers do
// it — top/bottom content padding of half the visible height, so scrolling
// item N to the very top of the list's content area (offset 0) puts it
// exactly in the middle of the visible window — rather than a custom
// snap-position calculation.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    range         : IntRange,
    value         : Int,
    suffix        : String,
    onValueSettled: (Int) -> Unit,
    modifier      : Modifier = Modifier
) {
    val values     = remember(range) { range.toList() }
    val haptics    = rememberHaptics()
    val density    = LocalDensity.current
    val itemHeightPx = with(density) { WHEEL_ITEM_HEIGHT.toPx() }

    val startIndex   = remember(range) { (value - range.first).coerceIn(0, values.lastIndex) }
    val listState     = rememberLazyListState(startIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)

    // Recomputed continuously (not just once scrolling stops) so the
    // bold/highlighted number always matches what's actually centered,
    // including mid-drag and mid-fling.
    val centeredIndex by remember {
        derivedStateOf {
            val offsetItems = (listState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            (listState.firstVisibleItemIndex + offsetItems).coerceIn(0, values.lastIndex)
        }
    }

    var lastSettled by remember { mutableStateOf(startIndex) }
    LaunchedEffect(centeredIndex, listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && centeredIndex != lastSettled) {
            lastSettled = centeredIndex
            haptics.digitTick()
            onValueSettled(values[centeredIndex])
        }
    }

    // If the value changed from OUTSIDE this wheel — a preset tap, or one of
    // the *other* two wheels changing what this one now needs to show —
    // scroll to match instead of silently drifting out of sync.
    LaunchedEffect(value) {
        val target = (value - range.first).coerceIn(0, values.lastIndex)
        if (target != centeredIndex) {
            lastSettled = target
            listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state          = listState,
        flingBehavior  = flingBehavior,
        contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT * (WHEEL_VISIBLE_ITEMS / 2)),
        modifier       = modifier.height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS)
    ) {
        itemsIndexed(values) { index, v ->
            val distance   = kotlin.math.abs(index - centeredIndex)
            val isCentered = distance == 0
            val scale      = when (distance) { 0 -> 1f; 1 -> 0.82f; else -> 0.68f }
            val textAlpha  = when (distance) { 0 -> 1f; 1 -> 0.45f; 2 -> 0.22f; else -> 0.10f }
            Box(
                modifier         = Modifier.fillMaxWidth().height(WHEEL_ITEM_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "$v$suffix",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Medium,
                    color      = if (isCentered) TextPrimary else TextMuted,
                    modifier   = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = textAlpha }
                )
            }
        }
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val d = totalMinutes / (24 * 60)
    val h = (totalMinutes % (24 * 60)) / 60
    val m = totalMinutes % 60
    val parts = buildList {
        if (d > 0) add("${d}d")
        if (h > 0) add("${h}h")
        // Minutes are shown whenever there's no larger unit to anchor the
        // reading, or when they're the only nonzero part at all — so "3d"
        // stays clean but "0m" never gets dropped for a 30-second-rounding
        // edge case where every part would otherwise read as empty.
        if (m > 0 || isEmpty()) add("${m}m")
    }
    return parts.joinToString(" ")
}

// ════════════════════════════════════ Helpers ════════════════════════════════════

private fun formatCountdown(totalSec: Long): String {
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
