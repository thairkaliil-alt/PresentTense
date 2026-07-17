package com.allinone.blocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownGracePeriod
import com.allinone.blocker.service.LockdownOverlay
import com.allinone.blocker.ui.motion.AnimatedAppearance
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionEasing
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * The full-phone lockdown "home screen" — a black launcher showing the
 * whitelisted apps as a grid. Tapping one launches it normally; pressing back
 * does nothing; and the [AppBlockerAccessibilityService] bounces the user
 * straight back here the moment they try to open anything that isn't
 * whitelisted. Together they turn the device into a single inescapable screen —
 * the Digital-Detox effect — without needing Device Owner / ADB.
 *
 * WHERE THE SCREEN ACTUALLY LIVES: the visible UI ([LockdownLauncherScreen]) is
 * no longer hosted by this Activity. It's drawn by [LockdownOverlay] as a
 * system overlay WINDOW that stays painted on top even when Home is pressed —
 * which is what removed the old "press Home and the screen rolls away for a
 * split second" flash. An Activity is a task the OS can switch away from before
 * we can react; an overlay window is not, so there's no switch to flash past.
 * The earlier Screen Pinning (Activity.startLockTask()) approach was dropped:
 * it couldn't even run here (this Activity is excludeFromRecents + a HOME
 * launcher, which Android refuses to pin) and required a manual system toggle.
 *
 * This Activity now only survives as the HOME-intent fallback registered in the
 * manifest: if it's ever launched it makes sure the overlay is up for a live
 * session and immediately hands off to [MainActivity].
 */
class LockdownLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BlockerRepository.isInitialized) BlockerRepository.init(applicationContext)
        if (!LockdownCompletionRepository.isInitialized) LockdownCompletionRepository.init(applicationContext)

        // The lockdown screen itself is no longer an Activity — it's a system
        // overlay window (see [LockdownOverlay]) that stays on top even when
        // Home is pressed, which is what removes the old "it goes away and
        // comes back" flash. This Activity now only exists as the HOME-intent
        // fallback registered in the manifest: if it's ever launched (e.g. the
        // user pressed Home and Present Tense happens to be their default home
        // app), make sure the overlay is up when a session is live, then get
        // out of the way to the normal app underneath.
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        if (decision.active || decision.onBreak) {
            LockdownOverlay.show(this)
        }

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    companion object {
        /**
         * Brings the lockdown screen up. Kept as the single entrypoint every
         * caller already uses (accessibility corral, watchdog, MainActivity,
         * LockdownScreen) — it now raises the overlay window rather than
         * starting this Activity, so none of those call sites had to change.
         */
        fun launch(context: Context) {
            LockdownOverlay.show(context)
        }
    }
}

/** An app shown on the lockdown launcher grid. */
private data class LauncherApp(val packageName: String, val label: String)

@Composable
internal fun LockdownLauncherScreen(
    onLaunchApp: (String) -> Unit,
    onExitToApp: () -> Unit,
    onSessionComplete: () -> Unit
) {
    val context = LocalContext.current
    val whitelist by BlockerRepository.whitelist.collectAsState()
    val manualUntil by BlockerRepository.manualLockUntil.collectAsState()
    val schedules by BlockerRepository.schedules.collectAsState()
    val breakUntil by BlockerRepository.breakUntil.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedTicker { now = it }

    val decision = remember(manualUntil, schedules, breakUntil, now) {
        LockdownEngine.evaluate(manualUntil, schedules, now, breakUntil)
    }

    // Option C: a short, no-friction window right at the start of a session
    // to undo a mistake before it's had any real effect — see
    // LockdownGracePeriod's header comment. Recomputed on the same 1-second
    // ticker as everything else on this screen so the countdown moves and
    // the banner disappears on its own the instant the window closes.
    val graceRemainingMs = remember(now) { LockdownGracePeriod.remainingMs(now) }
    val showGraceCancel = graceRemainingMs > 0L && !decision.onBreak

    // Anchor for anything that needs to know when THIS session actually
    // began (not just when it ends) — the reflective line's rotation and,
    // below, the progress ring's elapsed/total math both key off this.
    val sessionStartedAtMillis = remember(now) { LockdownCompletionRepository.currentSessionStartedAtMillis() }

    // ENTRY RITUAL: should this composition play the "locking in" moment?
    // Deliberately a plain, unkeyed `remember` — it evaluates exactly ONCE,
    // the first time this composable enters composition, and then holds
    // that answer for as long as this screen's Activity instance lives.
    //
    // Why that's the right signal: LockdownLauncherActivity is
    // launchMode="singleTask", so when the accessibility service bounces
    // the user back mid-session it reuses this same instance (onResume
    // only) — it does NOT call onCreate again, so this composable is never
    // re-entered and this `remember` never re-evaluates. A genuinely new
    // session (this screen finished and relaunched fresh, or the process
    // was recreated) DOES re-run this composable from scratch.
    //
    // That second case — process recreation — is exactly why this isn't
    // just "true on first composition": Android can and does kill a
    // long-backgrounded process (this Activity is stateNotNeeded=true,
    // i.e. the app already expects this), and a multi-hour "Sleep" session
    // being recreated eight hours in must NOT replay the ritual as if it
    // just began. So the actual check is proximity: does the session's
    // OWN recorded start time (not the wall clock) sit within the last
    // minute? That's generous enough to absorb real delivery paths (the
    // accessibility service's ~3s live tick, or the watchdog alarm's ~45s
    // worst case after a process kill) while still clearly excluding
    // "this session has been running for a while and we're just being
    // recreated mid-way through it".
    val isGenuineSessionStart = remember {
        val startedAt = sessionStartedAtMillis
        startedAt != null && (now - startedAt) in 0..ENTRY_RITUAL_FRESHNESS_WINDOW_MS
    }

    // The screen's one line of "why", not just "how long" — quiet, rotates
    // slowly, keyed off the session's own start time (not the wall clock) so
    // the rotation cadence is clean from wherever the session began. See
    // LockdownReflections' header comment for the design intent.
    val reflectionLine = remember(now, decision.reason) {
        LockdownReflections.currentLine(
            reason = decision.reason,
            sessionStartedAtMillis = sessionStartedAtMillis,
            nowMillis = now
        )
    }

    // The screen's color mood — tied to the SAME keyword match that picks
    // the reflective line above (LockdownReflections.moodFor), so the copy
    // and the ambient glow's color can never say different things about
    // what this session is for. See AmbientGlow's header comment for why
    // these particular accent tokens.
    val moodColor = remember(decision.reason) {
        when (LockdownReflections.moodFor(decision.reason)) {
            LockdownMood.SLEEP -> AccentBlue
            LockdownMood.FAMILY -> AccentAmber
            LockdownMood.FOCUS -> AccentTeal
            LockdownMood.GENERIC -> AccentBlue
        }
    }

    // A real, honest progress fraction — ONLY when both ends of the session
    // are genuinely known (a fixed end time AND a recorded start time). This
    // is deliberately separate from LockdownFocusRing's breathing pulse: an
    // indefinite manual lock ("until turned off") has no total duration to
    // measure against, so faking a progress arc for it would be exactly the
    // misleading indicator Calm Technology practice says to avoid — see
    // LockdownFocusRing's header comment. Null here means "unknown", not
    // "zero" — LockdownFocusRing falls back to the breathing pulse whenever
    // this is null, whatever the reason.
    val target = decision.endsAtMillis
    val sessionProgress = remember(target, sessionStartedAtMillis, now) {
        val startedAt = sessionStartedAtMillis
        if (target in 1 until Long.MAX_VALUE && startedAt != null && target > startedAt) {
            ((now - startedAt).toFloat() / (target - startedAt).toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
    }

    // Peak-End Rule: the final stretch of a KNOWN-duration session gets a
    // warmer visual treatment, so the ending is memorable in a good way
    // rather than just... stopping. Only possible when there's a total
    // duration to measure "final stretch" against in the first place — same
    // "known start AND known end" requirement as sessionProgress above, so
    // an indefinite manual lock never has an ending to make warm (there's
    // nothing to be "almost done" with).
    //
    // The window itself is 5% of the session's total length, clamped to
    // 15s–2min: short enough that a 10-minute session doesn't spend a third
    // of itself "almost done", long enough that even a 5-minute session
    // gets a few visible seconds of it.
    val isAlmostDone = remember(target, sessionStartedAtMillis, now) {
        val startedAt = sessionStartedAtMillis
        if (target in 1 until Long.MAX_VALUE && startedAt != null && target > startedAt) {
            val totalMs = target - startedAt
            val windowMs = (totalMs / 20).coerceIn(15_000L, 120_000L)
            (target - now) in 0..windowMs
        } else {
            false
        }
    }

    // Both the ring and the ambient glow read from this one value, so the
    // "warming up" cue is consistent across the whole screen rather than
    // just one element changing. AccentAmber rather than a new color —
    // same "reuse the existing tokens" rule as everywhere else on this
    // screen. Each composable below already smooths color changes with its
    // own animateColorAsState, so this doesn't need its own cross-fade here.
    val displayMoodColor = if (isAlmostDone) AccentAmber else moodColor

    // THE fix for "nothing happens when the countdown hits zero": this
    // screen is, by design, the only thing the user can see for the entire
    // duration of a session — they never leave it, so the Activity is never
    // paused-and-resumed, so onResume() (where this used to be the ONLY
    // place session-end was detected in the foreground) never runs again on
    // its own. The 1-second ticker above already recomputes `decision`
    // every tick; this is what actually acts on it the moment it flips from
    // live to over, instead of the countdown just sitting at 0:00 forever.
    LaunchedEffect(decision.active, decision.onBreak) {
        if (!decision.active) {
            if (decision.onBreak) onExitToApp() else onSessionComplete()
        }
    }

    // Build the visible app list: phone + messages (always exempt) followed by
    // the user's whitelist, de-duplicated and labelled.
    val apps = remember(whitelist) { buildLauncherApps(context, whitelist) }
    val breaksRemaining = BlockerRepository.breaksRemaining()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarkest)
    ) {
        // Depth layer: a slow, subtle wash of color behind the focus ring —
        // drawn first so everything else sits on top of it. See AmbientGlow's
        // header comment for the design intent.
        AmbientGlow(color = displayMoodColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            LockdownFocusRing(progress = sessionProgress, color = displayMoodColor)
            Spacer(Modifier.height(24.dp))

            // Countdown / status — generous size, minimal chrome. The digits
            // themselves now carry more weight (displayLarge, Medium instead
            // of Light) and roll from one value to the next via AnimatedContent
            // instead of snapping — the same pattern StreaksScreen.kt already
            // uses for its counting number, reused here for consistency.
            if (target in 1 until Long.MAX_VALUE) {
                val remainingSec = ((target - now) / 1000L).coerceAtLeast(0)
                AnimatedContent(
                    targetState = formatLockCountdown(remainingSec),
                    transitionSpec = {
                        (slideInVertically { h -> h / 4 } + fadeIn(tween(160)))
                            .togetherWith(slideOutVertically { h -> -h / 4 } + fadeOut(tween(160)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "lockdownCountdown"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "REMAINING",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    decision.reason.ifBlank { "Locked down" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(14.dp))
            AnimatedAppearance(delayMs = 260) {
                Crossfade(
                    targetState = reflectionLine,
                    animationSpec = MotionSpecs.standard(MotionDurations.Slow),
                    label = "reflectionLine"
                ) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            if (showGraceCancel) {
                Spacer(Modifier.height(20.dp))
                GraceCancelBanner(
                    remainingMs = graceRemainingMs,
                    onCancel = {
                        if (LockdownGracePeriod.cancelCurrentSession(now)) onExitToApp()
                    }
                )
            }

            Spacer(Modifier.height(56.dp))

            if (apps.isEmpty()) {
                Text(
                    "No apps whitelisted.\nPhone and Messages still work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 40.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        LauncherAppIcon(app = app, moodColor = moodColor, onClick = { onLaunchApp(app.packageName) })
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        // Emergency break — deliberately small and tucked away, not a headline
        // action. A single tap only reveals it; getting out requires holding
        // it down. The friction is the point: this isn't meant to be the easy
        // button, it's the "only if you really need it" button.
        if (!decision.onBreak && breaksRemaining > 0) {
            EmergencyBreakControl(
                breaksRemaining = breaksRemaining,
                onConfirmed = {
                    if (BlockerRepository.startEmergencyBreak()) onExitToApp()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
            )
        }

        // The "locking in" moment — see the isGenuineSessionStart comment
        // above for exactly when this does and doesn't play. Rendered last
        // so it's on top of everything else, fading away to reveal a screen
        // that's already fully in place underneath it.
        LockdownEntryRitual(play = isGenuineSessionStart)
    }
}

/**
 * One tile in the whitelist grid. This is the one "permission-giving"
 * positive moment on the whole screen — everything else here is either
 * neutral status or a restriction — so it gets a soft tint of the screen's
 * mood color (same [moodColor] the ambient glow and ring use) instead of a
 * flat, anonymous translucent square, tying it visually to "this is allowed,
 * on purpose" rather than looking like every other locked-out surface.
 */
@Composable
private fun LauncherAppIcon(app: LauncherApp, moodColor: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .pressable(pressedScale = MotionTokens.PressScaleSmall, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(moodColor.copy(alpha = 0.14f), moodColor.copy(alpha = 0.05f))
                    )
                )
                .border(1.dp, moodColor.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            AppIconOrLetter(packageName = app.packageName, label = app.label)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** How long the entry ritual's black scrim takes to fully dissolve. Deliberately
 *  longer than every other duration in [MotionDurations] — those are tuned to
 *  the Doherty threshold for everyday UI feedback, but this is a one-time,
 *  intentionally weighty moment, not a state change the user is waiting on. */
private const val ENTRY_RITUAL_DURATION_MS = 1400

/** How fresh [LockdownCompletionRepository.currentSessionStartedAtMillis] must
 *  be for this to count as a genuine session start rather than a process
 *  recreation mid-session — see the isGenuineSessionStart comment in
 *  [LockdownLauncherScreen] for the full reasoning. */
private const val ENTRY_RITUAL_FRESHNESS_WINDOW_MS = 60_000L

/**
 * The "locking in" moment — a brief, calm fade-from-black the very first
 * time this screen appears for a new session, giving that moment some
 * weight instead of the lockdown just snapping into view. Same
 * psychological function as Brick's physical tap-to-lock ritual, achieved
 * with a software transition instead of hardware (see the backlog item
 * this implements).
 *
 * Everything underneath (the ring, the countdown, the grid) is already
 * fully rendered and correctly positioned the instant this composes — this
 * is purely a scrim on top that dissolves, not a choreographed entrance for
 * the content itself. That keeps this additive and low-risk: nothing about
 * how the rest of the screen renders has to change for this to work.
 *
 * Respects [LocalReducedMotion]: if the user has the OS "remove animations"
 * setting on, this renders nothing at all rather than holding a black frame
 * — same convention [AnimatedAppearance] already follows.
 */
@Composable
private fun LockdownEntryRitual(play: Boolean) {
    if (!play) return
    if (LocalReducedMotion.current) return

    val haptics = rememberHaptics()
    val scrimAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        haptics.lockIn()
        scrimAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = ENTRY_RITUAL_DURATION_MS,
                easing = MotionEasing.Decelerate
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarkest.copy(alpha = scrimAlpha.value))
    )
}

/**
 * A slow, soft wash of color behind the focus ring — the screen's answer to
 * the "flat background, no depth" note in the visual-depth backlog item.
 *
 * rather than ambient:
 *   - Low peak alpha (never above ~0.2) — a hint of color, not a spotlight.
 *   - Slow breathing (9s) and an even slower horizontal drift (14s), both
 *     using the same small-displacement philosophy as the rest of the app's
 *     motion (see Motion.kt's header) — felt more than seen.
 *   - The color itself cross-fades over ~1.2s when it changes (e.g. one
 *     scheduled session ends and a differently-labeled one begins) instead
 *     of cutting, so a mood change is never a jolt.
 *
 * [color] comes from [LockdownReflections.moodFor] via the same accent
 * tokens the rest of the app already uses semantically — AccentBlue for a
 * cooler/calmer mood (sleep, and the generic fallback), AccentAmber for the
 * warm family mood, AccentTeal for focus/work — never a new raw hex color.
 */
@Composable
private fun AmbientGlow(color: Color) {
    val infinite = rememberInfiniteTransition(label = "ambientGlow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(9_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val driftX by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowDriftX"
    )
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(1_200),
        label = "glowColor"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val driftPx = driftX * 24.dp.toPx()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(animatedColor.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(size.width / 2f + driftPx, size.height * 0.22f),
                radius = size.minDimension * 0.9f
            )
        )
    }
}

/**
 * The breathing/progress ring drawn around the countdown during an active
 * lockdown session.
 *
 * TWO MODES, chosen by [progress]:
 *   - [progress] == null (indefinite locks, e.g. a manual "until turned off"
 *     session, or the brief window before the session tracker has recorded a
 *     start time): a slow, subtle breathing pulse — the same "still alive,
 *     still holding" cue premium focus apps use (Opal's orb, Endel's
 *     breathing visuals) instead of a hard mechanical timer. Deliberately
 *     NOT a literal progress ring here, because the app genuinely doesn't
 *     know a total duration to measure against — faking one would be
 *     exactly the misleading indicator Calm Technology practice warns
 *     against. Paired with a single barely-there haptic pulse once per
 *     breath cycle — reinforcing the same "still alive, still holding"
 *     idea through touch, not just sight. Only for this mode: a session
 *     with a real progress arc already has an unambiguous, purely visual
 *     "still going" signal, so doubling it with a repeating haptic there
 *     would just be noise.
 *   - [progress] in 0f..1f (any session with a known start AND a known end —
 *     a fixed-length schedule, or a manual lock with a set duration): a
 *     real, honest elapsed/total arc. This is intentionally a DIFFERENT
 *     visual — a filling arc, not a pulse — so it's never confused with the
 *     ambient "alive" cue above; it's making an actual claim about how much
 *     of the session is left, so it only ever appears when that claim is true.
 *
 * [color] is the same mood color driving [AmbientGlow] (see that composable's
 * header comment) — cross-fades over ~1.2s on change, same as the glow, so
 * the ring and the ambient wash always agree with each other.
 */
@Composable
private fun LockdownFocusRing(progress: Float? = null, color: Color = AccentBlue) {
    val infinite = rememberInfiniteTransition(label = "focusBreath")
    val breath by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(1_200),
        label = "ringColor"
    )

    // Smooths the once-a-second jump from the caller's tick into a gentle
    // glide, rather than the arc visibly snapping forward every second.
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "lockdownProgress"
    )

    // The breathing haptic — see the header comment above for why this is
    // scoped to indefinite locks only. Fires once per full breath cycle
    // (the ring's tween is 3.2s out, 3.2s back — a 6.4s round trip), timed
    // to the moment the ring is fullest, matching what's on screen.
    if (progress == null) {
        val haptics = rememberHaptics()
        LaunchedEffect(Unit) {
            while (true) {
                delay(3_200L)
                haptics.breathPulse()
                delay(3_200L)
            }
        }
    }

    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // static outer ring — faint, structural
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 1.dp.toPx())
            )

            if (progress != null) {
                // Real, honest progress arc — elapsed/total, nothing guessed.
                val strokeWidthPx = 3.dp.toPx()
                val inset = strokeWidthPx / 2f
                drawArc(
                    color = animatedColor.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            } else {
                // breathing inner ring — the "alive" cue, for indefinite locks
                drawCircle(
                    color = animatedColor.copy(alpha = 0.4f * breath),
                    radius = size.minDimension / 2f - 10.dp.toPx(),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = TextPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Option C's cancel affordance: a plain, clearly visible "Cancel lockdown"
 * pill shown only for the first [LockdownGracePeriod.GRACE_PERIOD_MS] of a
 * session, with a small live countdown so it's obvious this option is
 * temporary. Deliberately the opposite of [EmergencyBreakControl] below —
 * no reveal-then-hold friction — because a grace-period cancel isn't the
 * "only if you really need it" escape valve, it's undoing a mistake made
 * before the session had any real effect. Calm and matter-of-fact, not
 * gamified, per the tone the rest of this app's completion/celebration
 * surfaces already keep (see the header comment on LockdownCompletionRepository.kt).
 */
@Composable
private fun GraceCancelBanner(remainingMs: Long, onCancel: () -> Unit) {
    val remainingSec = (remainingMs / 1000L).coerceAtLeast(0L)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pressable(pressedScale = MotionTokens.PressScaleSmall, onClick = onCancel)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            "Cancel lockdown",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary.copy(alpha = 0.85f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${remainingSec}s left",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
    }
}

/**
 * The emergency-break control. Intentionally tiny and unlabeled by default —
 * a single tap only reveals what it is; actually using it requires holding
 * it down for a beat. That's a deliberate choice, not an oversight: the
 * point of this screen is to NOT make leaving easy, so the one sanctioned
 * escape valve gets exactly enough visibility to be found in a genuine
 * emergency, and exactly enough friction that it's never the reflexive tap.
 */
@Composable
private fun EmergencyBreakControl(
    breaksRemaining: Int,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(holding) {
        if (holding) {
            val totalMs = 900
            var elapsed = 0
            while (holding && elapsed < totalMs) {
                delay(16L)
                elapsed += 16
                holdProgress = (elapsed / totalMs.toFloat()).coerceIn(0f, 1f)
            }
            if (holding && holdProgress >= 1f) {
                haptics.confirm()
                onConfirmed()
            }
        } else {
            holdProgress = 0f
        }
    }

    // Auto-hide the label again if the user reveals it but doesn't act.
    LaunchedEffect(revealed, holding) {
        if (revealed && !holding) {
            delay(4000)
            revealed = false
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = revealed) {
            Text(
                "hold for a break \u00B7 $breaksRemaining left",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(end = 10.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .pointerInput(revealed) {
                    detectTapGestures(
                        onPress = {
                            if (!revealed) {
                                revealed = true
                                haptics.tap()
                            } else {
                                holding = true
                                tryAwaitRelease()
                                holding = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                if (holdProgress > 0f) {
                    drawArc(
                        color = AccentTeal,
                        startAngle = -90f,
                        sweepAngle = 360f * holdProgress,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Icon(
                Icons.Filled.Bolt,
                contentDescription = "Emergency break",
                tint = TextMuted.copy(alpha = if (revealed) 0.9f else 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Drives a 1-second clock for the countdown without leaking a coroutine; the
 * caller just receives the latest millis.
 */
@Composable
private fun LaunchedTicker(onTick: (Long) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            onTick(System.currentTimeMillis())
            delay(1_000)
        }
    }
}

private fun buildLauncherApps(context: Context, whitelist: Set<String>): List<LauncherApp> {
    val pm = context.packageManager
    val ordered = LinkedHashSet<String>()

    // Always-available comms first.
    runCatching {
        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    }.getOrNull()?.let { ordered.add(it) }
    runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { ordered.add(it) }

    ordered.addAll(whitelist)

    return ordered
        // Only keep things that can actually be launched.
        .filter { pm.getLaunchIntentForPackage(it) != null }
        .map { LauncherApp(it, InstalledApps.labelFor(context, it)) }
}

private fun formatLockCountdown(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
