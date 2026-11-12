package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroScreen.kt  —  Focus timer
//
// Reference points for this pass: Forest (session rings + a hero "time
// invested" stat), Flow / Focus Keeper (session-progress dots, a working
// skip), Duolingo (honest, earned celebration — confetti only on a REAL
// completion, never a fake nudge), and the app's own Motion.kt / Haptics.kt
// system so this screen finally moves and buzzes the same way every other
// screen already does (StreaksScreen, UnlockChallengeScreen, etc.).
//
// What changed vs. the old version, and why:
//   • Every control now has the shared press-scale + a matching haptic tick,
//     via Modifier.pressScale from motion/Pressable.kt — previously this
//     screen was the one place in the app with zero tactile feedback.
//   • The ring's progress and mode colour both animate smoothly (a spring /
//     tween, not a per-second jump), and there's a barely-there "breathing"
//     pulse while running — reads as alive without being distracting.
//   • Session-progress dots + a "Session X of N" caption, so a glance tells
//     you where you are in the cycle (the thing Forest/Flow both nail and
//     the old screen was completely missing).
//   • Skip is real now instead of a permanently-disabled placeholder.
//   • Reset asks for confirmation ONLY when it would actually throw away
//     progress — mirrors Forest's "give up this tree?" guardrail against a
//     mis-tap costing you a real focus session.
//   • Finishing a focus session fires the app's existing confetti + a
//     "confirm" haptic — honest, earned dopamine for something that really
//     happened, never a manufactured urgency cue.
//   • StatsCard leads with a hero "Focused Today" total (the single number
//     every good focus app puts front and centre) with the two supporting
//     counts underneath, and every number rolls with AnimatedCount instead
//     of snapping.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import com.allinone.blocker.ui.theme.*
import com.allinone.blocker.ui.motion.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    onBack: () -> Unit,
    viewModel: PomodoroViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val modeColor by animateColorAsState(
        targetValue = when (state.mode) {
            PomodoroMode.FOCUS -> AccentBlue
            PomodoroMode.SHORT_BREAK -> AccentTeal
            PomodoroMode.LONG_BREAK -> AccentAmber
        },
        animationSpec = MotionSpecs.standard(),
        label = "modeColor"
    )

    // Fires exactly once per real focus-session completion (celebrationTick
    // is a monotonically increasing counter, not a Boolean, so back-to-back
    // completions each get their own beat). Guarded at 0 so nothing fires
    // just from opening the screen.
    //
    // seenCelebrationTick starts at whatever the count already is when this
    // screen opens, so a session that finished while you were elsewhere does
    // NOT replay its confetti the next time you come in (the end-of-session
    // chime and the hand-off card already covered that moment).
    var seenCelebrationTick by remember { mutableStateOf(state.celebrationTick) }
    LaunchedEffect(state.celebrationTick) {
        if (state.celebrationTick > seenCelebrationTick) {
            seenCelebrationTick = state.celebrationTick
            haptics.confirm()
            showConfetti = true
        }
    }

    // Keeps the hand-off card's text on screen while it fades out, after the
    // ViewModel has already cleared handoffFrom.
    val lastHandoffFrom = remember { arrayOfNulls<PomodoroMode>(1) }
    if (state.handoffFrom != null) lastHandoffFrom[0] = state.handoffFrom

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen)
    ) {
        // Faint ambient glow that tracks the current mode's colour — the
        // same radial-gradient language StreaksScreen already uses for its
        // flame, so this screen reads as part of the same app rather than
        // a bolted-on feature.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            modeColor.copy(alpha = 0.16f),
                            modeColor.copy(alpha = 0f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Top bar
            TopAppBar(
                title = { 
                    Text(
                        "Pomodoro Timer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(12.dp))

            AnimatedAppearance {
                // Mode selector
                ModeSelector(
                    currentMode = state.mode,
                    onModeChange = {
                        haptics.tap()
                        viewModel.switchMode(it)
                    },
                    enabled = state.timerState == TimerState.IDLE
                )
            }

            Spacer(Modifier.height(32.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Timer display with circular progress
                    TimerDisplay(
                        timeRemaining = state.timeRemaining,
                        totalTime = state.totalTime,
                        mode = state.mode,
                        modeColor = modeColor,
                        isRunning = state.timerState == TimerState.RUNNING
                    )

                    Spacer(Modifier.height(20.dp))

                    val sessionsPerCycle = state.settings.sessionsUntilLongBreak.coerceAtLeast(1)
                    val cyclePosition = cycleProgress(state.completedSessions, sessionsPerCycle)

                    Text(
                        text = "Session ${cyclePosition.coerceAtLeast(0)} of $sessionsPerCycle",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(10.dp))
                    SessionDots(
                        filled = cyclePosition,
                        total = sessionsPerCycle,
                        color = modeColor
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 2) {
                // Control buttons
                TimerControls(
                    timerState = state.timerState,
                    modeColor = modeColor,
                    locked = state.ultraSessionActive,
                    onStart = {
                        haptics.tap()
                        viewModel.startTimer()
                    },
                    onPause = {
                        haptics.tap()
                        viewModel.pauseTimer()
                    },
                    onReset = {
                        // Only interrupt with a confirmation if resetting would
                        // actually throw away real progress. A reset from a
                        // fresh, untouched timer has nothing to lose.
                        if (state.timerState != TimerState.IDLE && state.timeRemaining < state.totalTime) {
                            showResetConfirm = true
                        } else {
                            haptics.tap()
                            viewModel.resetTimer()
                        }
                    },
                    onSkip = {
                        haptics.tap()
                        viewModel.skipSession()
                    }
                )
            }

            // The "Up next" hand-off: shown when a focus session or break has
            // just ended on its own and the next one is waiting. Carries the
            // big Start button and, if auto-start is on, the short countdown.
            AnimatedVisibility(
                visible = state.handoffFrom != null && state.timerState == TimerState.IDLE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    HandoffCard(
                        endedMode = state.handoffFrom ?: lastHandoffFrom[0] ?: PomodoroMode.FOCUS,
                        nextMode = state.mode,
                        nextMinutes = state.totalTime / 60,
                        locksPhone = state.settings.ultraFocusEnabled && state.mode == PomodoroMode.FOCUS,
                        autoStartInSeconds = state.autoStartInSeconds,
                        onStart = {
                            haptics.confirm()
                            viewModel.startTimer()
                        },
                        onCancelAutoStart = {
                            haptics.tap()
                            viewModel.cancelAutoStart()
                        }
                    )
                }
            }

            // After SKIPPING a break, with Ultra Focus on: the manual way to
            // begin the next locked focus session. (When a break ends on its
            // own, the hand-off card above does this job instead.)
            if (state.awaitingNextFocus &&
                state.handoffFrom == null &&
                state.mode == PomodoroMode.FOCUS &&
                state.timerState == TimerState.IDLE
            ) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        haptics.confirm()
                        viewModel.startTimer()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Start next focus session", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))

            UltraFocusCard(
                enabled = state.settings.ultraFocusEnabled,
                autoStartNext = state.settings.ultraAutoStartNext,
                sessionActive = state.ultraSessionActive,
                // Can't be flipped in the middle of a running/paused focus session.
                canToggle = !(state.mode == PomodoroMode.FOCUS && state.timerState != TimerState.IDLE),
                notice = state.ultraNotice,
                onToggle = {
                    haptics.toggleTick()
                    viewModel.setUltraFocusEnabled(it)
                },
                onAutoStartChange = {
                    haptics.tap()
                    viewModel.setUltraAutoStartNext(it)
                }
            )

            Spacer(Modifier.height(28.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 3) {
                // Stats card
                StatsCard(
                    completedSessions = state.completedSessions,
                    focusSecondsToday = state.focusSecondsToday,
                    sessionsUntilLongBreak = state.settings.sessionsUntilLongBreak
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        ConfettiOverlay(
            trigger = showConfetti,
            onFinished = { showConfetti = false }
        )

        // Settings dialog
        if (showSettings) {
            PomodoroSettingsDialog(
                settings = state.settings,
                onDismiss = { showSettings = false },
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    showSettings = false
                },
                onResetStats = { viewModel.resetStats() }
            )
        }

        // Reset confirmation — only shown when there's real progress at stake
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                containerColor = CardSurface,
                title = {
                    Text(
                        when (state.mode) {
                            PomodoroMode.FOCUS -> "Give up this focus session?"
                            else -> "Restart this break?"
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        "You'll lose your progress on this timer and start over from the beginning.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptics.error()
                            viewModel.resetTimer()
                            showResetConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("Keep Going", color = TextMuted)
                    }
                }
            )
        }
    }
}

/** How many dots (of [sessionsPerCycle]) should read as "filled" for the
 *  current [completedSessions] count — shared by [SessionDots] and the
 *  "Session X of N" caption so they never disagree. Also doubles as the
 *  building block for the StatsCard's "until long break" figure. */
private fun cycleProgress(completedSessions: Int, sessionsPerCycle: Int): Int {
    val perCycle = sessionsPerCycle.coerceAtLeast(1)
    val inCycle = completedSessions % perCycle
    return if (inCycle == 0 && completedSessions > 0) perCycle else inCycle
}

@Composable
fun ModeSelector(
    currentMode: PomodoroMode,
    onModeChange: (PomodoroMode) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModeButton(
            text = "Focus",
            isSelected = currentMode == PomodoroMode.FOCUS,
            onClick = { onModeChange(PomodoroMode.FOCUS) },
            enabled = enabled,
            color = AccentBlue,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Short Break",
            isSelected = currentMode == PomodoroMode.SHORT_BREAK,
            onClick = { onModeChange(PomodoroMode.SHORT_BREAK) },
            enabled = enabled,
            color = AccentTeal,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Long Break",
            isSelected = currentMode == PomodoroMode.LONG_BREAK,
            onClick = { onModeChange(PomodoroMode.LONG_BREAK) },
            enabled = enabled,
            color = AccentAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) color else CardSurface
    val textColor = if (isSelected) Color.White else TextSecondary
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(48.dp)
            .pressScale(interactionSource, MotionTokens.PressScaleSmall),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = CardSurface.copy(alpha = 0.5f),
            disabledContentColor = TextTertiary
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(4.dp) else ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

/** Tabular-figure style for the countdown digits so "24:59" -> "25:00"
 *  doesn't visibly jiggle in width every second the way proportional
 *  digits do. */
private val TimerDigitsStyle = TextStyle(
    fontFeatureSettings = "tnum"
)

@Composable
fun TimerDisplay(
    timeRemaining: Int,
    totalTime: Int,
    mode: PomodoroMode,
    modeColor: Color,
    isRunning: Boolean
) {
    val rawProgress = if (totalTime > 0) timeRemaining.toFloat() / totalTime.toFloat() else 0f
    val reduced = LocalReducedMotion.current

    // The ring used to jump a visible notch every single second. Animating
    // toward the new progress over the same ~1s window turns that into one
    // continuous sweep — small change, reads as far more "premium".
    //
    // When a session ends and the next one is loaded, the ring has to jump
    // from empty back to full. A linear sweep looks mechanical for that, so a
    // bigger jump upward gets a softer ease-out "refill" instead.
    val previousProgress = remember { FloatArray(1) { rawProgress } }
    val isRefill = rawProgress > previousProgress[0] + 0.05f
    SideEffect { previousProgress[0] = rawProgress }

    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = when {
            reduced -> snap()
            isRefill -> tween(durationMillis = 800, easing = FastOutSlowInEasing)
            else -> tween(durationMillis = 950, easing = LinearEasing)
        },
        label = "ringProgress"
    )

    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60

    // A barely-there breathing pulse while the timer is actually running —
    // ~1.5% scale over a slow 3.4s cycle. Small enough to be felt rather
    // than seen, same philosophy as Motion.kt's press-scale tokens, and it
    // gives the screen a pulse of life instead of sitting dead-still for a
    // 25 minute focus block.
    val breathing = rememberInfiniteTransition(label = "breathing")
    val breathScale by breathing.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning && !reduced) 1.015f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = MotionEasing.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .scale(breathScale)
    ) {
        // Circular progress
        val cardSurface = CardSurface
        Canvas(
            modifier = Modifier.size(280.dp)
    ) {
            val strokeWidth = 20.dp.toPx()
            val size = this.size.minDimension - strokeWidth
            val topLeft = Offset((this.size.width - size) / 2, (this.size.height - size) / 2)

            // Background circle
            drawArc(
                color = cardSurface,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(size, size),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(modeColor, modeColor.copy(alpha = 0.6f), modeColor)
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(size, size),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Time text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.displayLarge.merge(TimerDigitsStyle),
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 56.sp
            )
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(MotionSpecs.enter()) togetherWith fadeOut(MotionSpecs.exit()))
                },
                label = "modeLabel"
            ) { targetMode ->
                Text(
                    text = when (targetMode) {
                        PomodoroMode.FOCUS -> "Focus Time"
                        PomodoroMode.SHORT_BREAK -> "Short Break"
                        PomodoroMode.LONG_BREAK -> "Long Break"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }
    }
}

/** Small row of dots showing where you are in the current focus/break
 *  cycle — [filled] of [total] lit up in the active mode colour. The
 *  at-a-glance progress indicator Forest/Flow both use and the old screen
 *  didn't have at all. */
@Composable
fun SessionDots(filled: Int, total: Int, color: Color) {
    val emptyDot = TextTertiary.copy(alpha = 0.3f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val isFilled = index < filled
            val dotColor by animateColorAsState(
                targetValue = if (isFilled) color else emptyDot,
                animationSpec = MotionSpecs.standard(),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .size(if (isFilled) 9.dp else 8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Composable
fun TimerControls(
    timerState: TimerState,
    modeColor: Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    // True during an Ultra Focus session: the phone is locked down, so
    // reset / skip / pause are switched off (see PomodoroViewModel).
    locked: Boolean = false
) {
    val controlsEnabled = timerState != TimerState.IDLE && !locked
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reset button
        val resetInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = onReset,
            interactionSource = resetInteraction,
            modifier = Modifier
                .size(56.dp)
                .pressScale(resetInteraction, MotionTokens.PressScaleSmall)
                .clip(CircleShape)
                .background(CardSurface),
            enabled = controlsEnabled
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Reset",
                tint = if (controlsEnabled) TextPrimary else TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play/Pause button (large center button)
        val playInteraction = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = {
                when (timerState) {
                    TimerState.IDLE, TimerState.PAUSED -> onStart()
                    TimerState.RUNNING -> { if (!locked) onPause() }
                }
            },
            interactionSource = playInteraction,
            modifier = Modifier
                .size(80.dp)
                .pressScale(playInteraction),
            containerColor = modeColor,
            contentColor = Color.White
        ) {
            AnimatedContent(
                targetState = timerState == TimerState.RUNNING,
                transitionSpec = {
                    (fadeIn(MotionSpecs.tactile()) togetherWith fadeOut(MotionSpecs.standard()))
                },
                label = "playPauseIcon"
            ) { running ->
                Icon(
                    imageVector = when {
                        running && locked -> Icons.Filled.Lock
                        running -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = when {
                        running && locked -> "Locked"
                        running -> "Pause"
                        else -> "Start"
                    },
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Skip button — jumps to the next session without waiting it out
        val skipInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = onSkip,
            interactionSource = skipInteraction,
            modifier = Modifier
                .size(56.dp)
                .pressScale(skipInteraction, MotionTokens.PressScaleSmall)
                .clip(CircleShape)
                .background(CardSurface),
            enabled = controlsEnabled
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Skip",
                tint = if (controlsEnabled) TextPrimary else TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * The "Up next" card shown right after a focus session or break ends on its
 * own. It answers "what now?" at the exact moment someone is asking it: what
 * just finished, what is waiting, and one big button to go. With auto-start
 * on, it also shows the short countdown and a "Not yet" way out.
 *
 * It only DISPLAYS things and calls [onStart] / [onCancelAutoStart]; starting
 * the session (including the Ultra Focus lockdown) is still done by
 * PomodoroViewModel.startTimer(), exactly as before.
 */
@Composable
fun HandoffCard(
    endedMode: PomodoroMode,
    nextMode: PomodoroMode,
    nextMinutes: Int,
    locksPhone: Boolean,
    autoStartInSeconds: Int,
    onStart: () -> Unit,
    onCancelAutoStart: () -> Unit
) {
    val accent = when (nextMode) {
        PomodoroMode.FOCUS -> AccentBlue
        PomodoroMode.SHORT_BREAK -> AccentTeal
        PomodoroMode.LONG_BREAK -> AccentAmber
    }
    val title = if (endedMode == PomodoroMode.FOCUS) "Focus complete" else "Break's over"
    val nextName = when (nextMode) {
        PomodoroMode.FOCUS -> "Focus"
        PomodoroMode.SHORT_BREAK -> "Short break"
        PomodoroMode.LONG_BREAK -> "Long break"
    }
    val buttonLabel = when (nextMode) {
        PomodoroMode.FOCUS -> if (locksPhone) "Start locked focus session" else "Start focus session"
        PomodoroMode.SHORT_BREAK -> "Start short break"
        PomodoroMode.LONG_BREAK -> "Start long break"
    }
    val buttonInteraction = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (endedMode == PomodoroMode.FOCUS) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Up next: $nextName · $nextMinutes min",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            Button(
                onClick = onStart,
                interactionSource = buttonInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(buttonInteraction),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (nextMode == PomodoroMode.FOCUS && locksPhone) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }

            if (autoStartInSeconds > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Starting automatically in ${autoStartInSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    TextButton(onClick = onCancelAutoStart) {
                        Text("Not yet", color = TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * The Ultra Focus switch. When on, each FOCUS session also locks the phone
 * (a normal Lockdown, for exactly the focus time) and the lock lifts by
 * itself for every break. The two chips choose what happens when a break
 * ends: wait for a "Start next focus session" tap (default) or start the
 * next locked session automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltraFocusCard(
    enabled: Boolean,
    autoStartNext: Boolean,
    sessionActive: Boolean,
    canToggle: Boolean,
    notice: String?,
    onToggle: (Boolean) -> Unit,
    onAutoStartChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ultra Focus",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (sessionActive) {
                            "Your phone is locked until this focus session ends."
                        } else {
                            "Locks your phone during focus time. Breaks stay free."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = canToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = TextTertiary.copy(alpha = 0.3f)
                    )
                )
            }

            if (notice != null) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentAmber
                )
            }

            if (enabled) {
                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                Text(
                    text = "When a break ends",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !autoStartNext,
                        onClick = { onAutoStartChange(false) },
                        label = { Text("I'll tap start") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = AccentBlue
                        )
                    )
                    FilterChip(
                        selected = autoStartNext,
                        onClick = { onAutoStartChange(true) },
                        label = { Text("Start automatically") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = AccentBlue
                        )
                    )
                }

                Text(
                    text = if (autoStartNext) {
                        "The next focus session locks your phone by itself as soon as the break ends."
                    } else {
                        "After each break, a \"Start next focus session\" button waits for you."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/** Formats seconds as "1h 24m" (or just "45m" under an hour, "< 1m" for a
 *  handful of seconds) — the same "hero" shape Forest/Focus Keeper use for
 *  their headline focus-time stat. */
private fun formatFocusDuration(totalSeconds: Int): String {
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalSeconds < 60 -> "< 1m"
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

@Composable
fun StatsCard(
    completedSessions: Int,
    focusSecondsToday: Int,
    sessionsUntilLongBreak: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Session Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Hero stat — the one number that matters most at a glance.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    val displaySeconds by animatedCountAsState(focusSecondsToday)
                    Text(
                        text = formatFocusDuration(displaySeconds),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Focused Today",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = TextTertiary.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Completed Today",
                    value = completedSessions,
                    icon = Icons.Filled.CheckCircle,
                    color = AccentTeal
                )

                StatItem(
                    // Kept as the original modulo formula on purpose (not the
                    // new cycleProgress helper above) — that helper treats
                    // "just finished a full cycle" as fully-filled dots,
                    // which is right for the SessionDots visual but would
                    // silently flip this particular stat to 0 right when a
                    // long break starts, instead of counting down toward
                    // the NEXT one. Same math the original screen used.
                    label = "Until Long Break",
                    value = sessionsUntilLongBreak - (completedSessions % sessionsUntilLongBreak.coerceAtLeast(1)),
                    icon = Icons.Filled.Timer,
                    color = AccentAmber
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }

        Column {
            AnimatedCount(
                value = value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun PomodoroSettingsDialog(
    settings: PomodoroSettings,
    onDismiss: () -> Unit,
    onSave: (PomodoroSettings) -> Unit,
    onResetStats: () -> Unit
) {
    var focusDuration by remember { mutableStateOf(settings.focusDuration) }
    var shortBreakDuration by remember { mutableStateOf(settings.shortBreakDuration) }
    var longBreakDuration by remember { mutableStateOf(settings.longBreakDuration) }
    var sessionsUntilLongBreak by remember { mutableStateOf(settings.sessionsUntilLongBreak) }
    var autoStartBreaks by remember { mutableStateOf(settings.autoStartBreaks) }
    var autoStartPomodoros by remember { mutableStateOf(settings.autoStartPomodoros) }
    var soundEnabled by remember { mutableStateOf(settings.soundEnabled) }
    var vibrationEnabled by remember { mutableStateOf(settings.vibrationEnabled) }
    val haptics = rememberHaptics()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text(
                "Pomodoro Settings",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Duration settings
                Text(
                    "Duration (minutes)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                DurationSetting(
                    label = "Focus Time",
                    value = focusDuration,
                    onValueChange = { focusDuration = it.coerceIn(1, 60); haptics.tap() },
                    color = AccentBlue
                )

                DurationSetting(
                    label = "Short Break",
                    value = shortBreakDuration,
                    onValueChange = { shortBreakDuration = it.coerceIn(1, 30); haptics.tap() },
                    color = AccentTeal
                )

                DurationSetting(
                    label = "Long Break",
                    value = longBreakDuration,
                    onValueChange = { longBreakDuration = it.coerceIn(5, 60); haptics.tap() },
                    color = AccentAmber
                )

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // Sessions until long break
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sessions until long break",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                sessionsUntilLongBreak = (sessionsUntilLongBreak - 1).coerceAtLeast(2)
                                haptics.tap()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Remove, null, tint = TextSecondary)
                        }
                        Text(
                            "$sessionsUntilLongBreak",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = {
                                sessionsUntilLongBreak = (sessionsUntilLongBreak + 1).coerceAtMost(10)
                                haptics.tap()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, tint = TextSecondary)
                        }
                    }
                }

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // Auto-start options
                Text(
                    "Auto-start Options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                SwitchSetting(
                    label = "Auto-start breaks",
                    checked = autoStartBreaks,
                    onCheckedChange = { autoStartBreaks = it; haptics.toggleTick() }
                )

                SwitchSetting(
                    label = "Auto-start focus sessions",
                    checked = autoStartPomodoros,
                    onCheckedChange = { autoStartPomodoros = it; haptics.toggleTick() }
                )

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // End-of-session alerts
                Text(
                    "End-of-session alerts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                SwitchSetting(
                    label = "Sound",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it; haptics.toggleTick() }
                )

                SwitchSetting(
                    label = "Vibration",
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it; haptics.toggleTick() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            PomodoroAlerts.preview(context, PomodoroMode.FOCUS, soundEnabled, vibrationEnabled)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test focus end", color = AccentBlue, fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = {
                            PomodoroAlerts.preview(context, PomodoroMode.SHORT_BREAK, soundEnabled, vibrationEnabled)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test break end", color = AccentTeal, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // Reset stats button
                TextButton(
                    onClick = {
                        haptics.tap()
                        onResetStats()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.RestartAlt, null, tint = AccentRed)
                        Text("Reset Session Stats", color = AccentRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onSave(
                        // .copy() keeps every setting this dialog doesn't show
                        // (the Ultra Focus options) exactly as they were.
                        settings.copy(
                            focusDuration = focusDuration,
                            shortBreakDuration = shortBreakDuration,
                            longBreakDuration = longBreakDuration,
                            sessionsUntilLongBreak = sessionsUntilLongBreak,
                            autoStartBreaks = autoStartBreaks,
                            autoStartPomodoros = autoStartPomodoros,
                            soundEnabled = soundEnabled,
                            vibrationEnabled = vibrationEnabled
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
fun DurationSetting(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Remove, null, tint = TextSecondary)
            }

            Text(
                "$value min",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = TextSecondary)
            }
        }
    }
}

@Composable
fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TextTertiary.copy(alpha = 0.3f)
            )
        )
    }
}
