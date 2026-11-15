package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroCompleteOverlay.kt  —  "Session finished, what's next?" overlay
//
// WHAT IT DOES
//   When a Pomodoro focus session or break ends, this overlay slides over
//   WHATEVER screen you are on (Home, Lockdown, Stats...). It offers:
//
//     1. ONE big button that starts the next phase (focus -> break, or
//        break -> focus). No more: open app -> find Pomodoro -> press Start.
//     2. A smaller "Stop for now" button that ends the chain: nothing else
//        starts, and the timer goes back to a calm "ready to focus" state.
//     3. A small X (and the phone's Back button) that simply closes the
//        overlay. You are never stuck on it.
//
// HOW IT FITS IN
//   • It reads the timer's state from PomodoroViewModel. It shows itself
//     whenever the timer says "a session just finished and the next one is
//     waiting" (state.handoffFrom != null). It does not run any timer logic.
//   • MainActivity wraps the whole app in PomodoroCompleteLayer (below), so
//     the overlay is drawn ON TOP of the current screen instead of replacing
//     it. That way, if you were in the middle of something (like editing an
//     alarm), it is still exactly as you left it when you close the overlay.
//   • Ultra Focus sessions used to show the old "lockdown complete" screen.
//     This file also tells MainActivity to skip that screen for sessions
//     that belong to the Pomodoro timer, so you only ever see ONE screen.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentTeal
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────
// LOOK  (same colours as the timer screen, so the overlay feels like part of it)
// ─────────────────────────────────────────────────────────────────────────

private val OnOverlay = Color.White
private val OnOverlaySoft = Color.White.copy(alpha = 0.78f)

/** The overlay wears the colour of the phase that is coming NEXT. */
private fun overlayAccent(next: PomodoroMode): Color = when (next) {
    PomodoroMode.FOCUS -> AccentBlue
    PomodoroMode.SHORT_BREAK -> AccentTeal
    PomodoroMode.LONG_BREAK -> AccentAmber
}

/** The accents are light, so darken them a little to keep white text readable. */
private fun overlayBackdrop(next: PomodoroMode): Color =
    lerp(overlayAccent(next), Color.Black, 0.40f)

// ─────────────────────────────────────────────────────────────────────────
// WHAT THE OVERLAY SHOWS  (a small snapshot of the timer state)
// ─────────────────────────────────────────────────────────────────────────

private data class OverlayInfo(
    val ended: PomodoroMode,        // what just finished
    val next: PomodoroMode,         // what is waiting
    val nextMinutes: Int,
    val locks: Boolean,             // next one is a locked (Ultra Focus) session
    val roundFilled: Int,           // dots: sessions done in this round
    val roundTotal: Int,
    val autoStartInSeconds: Int     // > 0 while "starting automatically..." counts down
)

private fun overlayInfoFrom(state: PomodoroState): OverlayInfo? {
    val ended = state.handoffFrom ?: return null
    val perCycle = state.settings.sessionsUntilLongBreak.coerceAtLeast(1)
    val inCycle = state.cycleCount % perCycle
    // A just-finished full round counts as completely filled (same rule as the timer screen).
    val filled = if (inCycle == 0 && state.cycleCount > 0) perCycle else inCycle
    return OverlayInfo(
        ended = ended,
        next = state.mode,
        nextMinutes = state.totalTime / 60,
        locks = state.mode == PomodoroMode.FOCUS && state.settings.ultraFocusEnabled,
        roundFilled = filled,
        roundTotal = perCycle,
        autoStartInSeconds = state.autoStartInSeconds
    )
}

private fun headlineFor(info: OverlayInfo): String =
    if (info.ended == PomodoroMode.FOCUS) "Focus complete" else "Break's over"

private fun sublineFor(info: OverlayInfo): String = when {
    info.ended == PomodoroMode.FOCUS && info.next == PomodoroMode.LONG_BREAK ->
        "Great work. You've earned a long break."
    info.ended == PomodoroMode.FOCUS -> "Nice work. Take a short break."
    else -> "Ready for the next round?"
}

private fun primaryLabelFor(info: OverlayInfo): String = when (info.next) {
    PomodoroMode.FOCUS -> if (info.locks) "Start locked focus" else "Start focus"
    PomodoroMode.SHORT_BREAK -> "Start short break"
    PomodoroMode.LONG_BREAK -> "Start long break"
}

// ─────────────────────────────────────────────────────────────────────────
// THE LAYER  (MainActivity wraps the whole app in this)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Draws the app ([content]) and, on top of it, the "session finished"
 * overlay whenever one is due.
 *
 * @param overlayAllowed  false while something more important owns the
 *                        screen (e.g. the first-run walkthrough).
 * @param content         the app. It is told whether it should HIDE the old
 *                        lockdown-complete screen — true when that finished
 *                        lockdown was really a Pomodoro Ultra Focus session
 *                        (this overlay takes its place).
 */
@Composable
fun PomodoroCompleteLayer(
    overlayAllowed: Boolean,
    viewModel: PomodoroViewModel = viewModel(),
    content: @Composable (suppressLockdownCelebration: Boolean) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val unlockPending by StrictModeGate.pendingAction.collectAsState()
    val lockdownPending by LockdownCompletionRepository.pendingCelebration.collectAsState()
    val handoffNote by PomodoroHandoff.pending.collectAsState()

    // ── Hand-over from the old lockdown-complete screen ──────────────────
    // An Ultra Focus session starts a manual lockdown whose end time is
    // exactly state.ultraEndsAtMillis. A finished lockdown whose
    // (start + length) equals that end time is therefore OURS. We remember
    // that end time as soon as we see it, because the timer forgets it the
    // moment the session ends. The second check covers an app restart: the
    // timer pins a "what's next" note for a locked session that finished
    // while the app was closed.
    var latchedUltraEnd by remember { mutableStateOf(0L) }
    LaunchedEffect(state.ultraEndsAtMillis) {
        if (state.ultraEndsAtMillis > 0L) latchedUltraEnd = state.ultraEndsAtMillis
    }
    val finishedLock = lockdownPending
    val note = handoffNote
    val lockBelongsToPomodoro = finishedLock != null && (
        (finishedLock.plannedMinutes > 0 &&
            abs(finishedLock.startedAtMillis + finishedLock.plannedMinutes * 60_000L - latchedUltraEnd) < 2_000L) ||
            (note != null &&
                note.endedMode == PomodoroMode.FOCUS &&
                note.createdAtMillis >= finishedLock.startedAtMillis)
        )
    // The lockdown's totals are already saved; this only clears the "show a
    // celebration" flag so the old screen never appears for this session.
    LaunchedEffect(lockBelongsToPomodoro) {
        if (lockBelongsToPomodoro) LockdownCompletionRepository.consumePendingCelebration()
    }

    // ── Should the overlay be on screen right now? ───────────────────────
    // "dismissed" remembers that you closed it with the X / Back button, so
    // it does not pop right back up. It resets by itself once the timer
    // moves on (handoffFrom becomes null when any next session starts).
    var dismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.handoffFrom) {
        if (state.handoffFrom == null) dismissed = false
    }

    val wanted = overlayAllowed &&
        unlockPending == null &&
        !dismissed &&
        state.handoffFrom != null

    // Keep the last content around so the fade-out still has words to show
    // (the timer clears its state the instant the next session starts).
    val current = if (wanted) overlayInfoFrom(state) else null
    var lastInfo by remember { mutableStateOf<OverlayInfo?>(null) }
    LaunchedEffect(current) {
        if (current != null) lastInfo = current
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(lockBelongsToPomodoro)

        AnimatedVisibility(
            visible = wanted,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(MotionSpecs.enter(MotionDurations.Slow)),
            exit = fadeOut(MotionSpecs.exit())
        ) {
            val info = current ?: lastInfo
            if (info != null) {
                PomodoroCompleteContent(
                    info = info,
                    onStart = { viewModel.startTimer() },
                    onStop = { viewModel.stopAfterSession() },
                    onClose = {
                        // Closing must never leave a hidden countdown that
                        // starts a (possibly locked) session behind your back.
                        viewModel.cancelAutoStart()
                        dismissed = true
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// THE SCREEN ITSELF
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PomodoroCompleteContent(
    info: OverlayInfo,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val haptics = rememberHaptics()
    val backdrop = overlayBackdrop(info.next)
    val backdropDeep = lerp(backdrop, Color.Black, 0.28f)

    // Back button = close. Registered after the activity's own handler, so
    // it wins while the overlay is showing.
    BackHandler(onBack = onClose)

    // Confetti only for a finished FOCUS session: it is an earned moment.
    // Finishing a break is not, so that stays quiet.
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showConfetti = info.ended == PomodoroMode.FOCUS
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(backdrop, backdropDeep)))
            // Swallow taps so nothing underneath can be pressed by accident.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Small round badge: a check when you earned it, a play arrow otherwise.
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (info.ended == PomodoroMode.FOCUS) Icons.Filled.Check else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = OnOverlay,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = headlineFor(info),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnOverlay,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = sublineFor(info),
                style = MaterialTheme.typography.bodyLarge,
                color = OnOverlaySoft,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
            CompleteRoundDots(filled = info.roundFilled, total = info.roundTotal)

            Spacer(Modifier.weight(1.2f))

            // ── The two quick buttons ────────────────────────────────────
            NextPhasePill(
                label = primaryLabelFor(info),
                minutes = info.nextMinutes,
                icon = if (info.locks) Icons.Filled.Lock else Icons.Filled.PlayArrow,
                textColor = backdrop,
                autoStartInSeconds = info.autoStartInSeconds,
                onClick = {
                    haptics.confirm()
                    onStart()
                }
            )

            // Fixed-height line so nothing jumps when the auto-start countdown appears.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (info.autoStartInSeconds > 0) {
                    Text(
                        text = "Starting in ${info.autoStartInSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnOverlaySoft
                    )
                } else if (info.locks) {
                    Text(
                        text = "Locks your phone for ${info.nextMinutes} min. Breaks stay free.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnOverlaySoft,
                        textAlign = TextAlign.Center
                    )
                }
            }

            StopPill(
                onClick = {
                    haptics.tap()
                    onStop()
                }
            )

            Spacer(Modifier.height(28.dp))
        }

        // Close (X), top-left.
        IconButton(
            onClick = {
                haptics.tap()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = OnOverlaySoft
            )
        }

        ConfettiOverlay(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
            pieceCount = 70
        )
    }
}

/** Little dots showing how far you are in the current round. */
@Composable
private fun CompleteRoundDots(filled: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total.coerceIn(1, 12)) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index < filled) 0.95f else 0.28f))
            )
        }
    }
}

/**
 * The big white pill that starts the next phase. While auto-start counts
 * down it slowly fills up, exactly like the one on the timer screen.
 */
@Composable
private fun NextPhasePill(
    label: String,
    minutes: Int,
    icon: ImageVector,
    textColor: Color,
    autoStartInSeconds: Int,
    onClick: () -> Unit
) {
    val counting = autoStartInSeconds > 0
    val fillTarget = if (counting) {
        (PomodoroViewModel.AUTO_START_SECONDS - autoStartInSeconds + 1).toFloat() /
            PomodoroViewModel.AUTO_START_SECONDS
    } else {
        0f
    }
    val fill by animateFloatAsState(
        targetValue = fillTarget.coerceIn(0f, 1f),
        animationSpec = if (counting) tween(durationMillis = 1000, easing = LinearEasing) else snap(),
        label = "completeAutoStartFill"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (fill > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .background(textColor.copy(alpha = 0.16f))
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)) {
                        append(label)
                    }
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            color = textColor.copy(alpha = 0.65f)
                        )
                    ) {
                        append("  ·  $minutes min")
                    }
                },
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The smaller, quieter button: end the chain here. */
@Composable
private fun StopPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.40f), shape)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Stop for now",
            color = OnOverlay.copy(alpha = 0.92f),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}
