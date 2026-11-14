package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroScreen.kt  —  Focus timer
//
// THE IDEA OF THIS VERSION: the next step is always one tap away.
//
//   • Every phase (Focus / Short break / Long break) owns the WHOLE screen
//     in its own colour. When a session ends, the colour washes over to the
//     next phase and that phase is already loaded and waiting — nothing to
//     hunt for.
//   • One big button says exactly what it does ("Start short break",
//     "Start locked focus"). That is the confirm button. The old separate
//     "Up next" card and the "Start next focus session" button are gone.
//   • With auto-start on, that same button slowly fills up over 5 seconds
//     and starts by itself — with a "Not yet" escape.
//   • While a session is running you get the compact trio from the design
//     reference: reset · pause · skip. (Ultra Focus locks all three.)
//   • The three mode tabs are now small optional pills. The normal flow
//     never needs them.
//
// The timer logic is untouched in spirit: everything here only DISPLAYS state
// and calls PomodoroViewModel. Starting the Ultra Focus lockdown is still
// done by PomodoroViewModel.startTimer().
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import com.allinone.blocker.ui.theme.*
import com.allinone.blocker.ui.motion.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource

// ─────────────────────────────────────────────────────────────────────────
// PHASE LOOK  (one colour per phase, shared by every piece below)
// ─────────────────────────────────────────────────────────────────────────

private fun phaseAccent(mode: PomodoroMode): Color = when (mode) {
    PomodoroMode.FOCUS -> AccentBlue
    PomodoroMode.SHORT_BREAK -> AccentTeal
    PomodoroMode.LONG_BREAK -> AccentAmber
}

/** The app's accents are light, so for a full-screen background we darken them
 *  a little. That keeps the white text readable in every phase. */
private fun phaseBackdrop(accent: Color): Color = lerp(accent, Color.Black, 0.40f)

private val PhaseChoices = listOf(
    PomodoroMode.FOCUS to "Focus",
    PomodoroMode.SHORT_BREAK to "Short break",
    PomodoroMode.LONG_BREAK to "Long break"
)

private val OnPhase = Color.White
private val OnPhaseSoft = Color.White.copy(alpha = 0.75f)

/** Finds the Activity behind a Context (needed to tint the phone's status bar). */
private fun android.content.Context.findHostActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** The little message above the timer: where you are, or what just happened. */
private data class PhaseStatus(val headline: String, val sub: String, val celebrate: Boolean)

private fun phaseStatus(state: PomodoroState, lockedUntil: String): PhaseStatus {
    val mode = state.mode
    return when {
        state.ultraSessionActive -> PhaseStatus(
            "Phone locked",
            if (lockedUntil.isNotEmpty()) "Until $lockedUntil" else "Until this session ends",
            false
        )
        state.timerState == TimerState.RUNNING ->
            if (mode == PomodoroMode.FOCUS) PhaseStatus("Focusing", "Stay with it", false)
            else PhaseStatus("Break time", "Step away from the screen", false)
        state.timerState == TimerState.PAUSED ->
            PhaseStatus("Paused", "Resume when you're ready", false)
        state.handoffFrom == PomodoroMode.FOCUS -> PhaseStatus(
            "Focus complete",
            if (mode == PomodoroMode.LONG_BREAK) "Great work. You've earned a long break."
            else "Nice work. Take a short break.",
            true
        )
        state.handoffFrom != null -> PhaseStatus("Break's over", "Ready for the next round?", true)
        mode == PomodoroMode.FOCUS -> PhaseStatus(
            "Ready to focus",
            if (state.settings.ultraFocusEnabled) "Your phone locks while you focus" else "One session at a time",
            false
        )
        else -> PhaseStatus("Time to recharge", "Take a breather", false)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// THE SCREEN
// ─────────────────────────────────────────────────────────────────────────

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
    val context = LocalContext.current
    val view = LocalView.current

    // The colour of the current phase. It animates, so Focus -> Break is a
    // smooth wash instead of a hard cut.
    val accent = phaseAccent(state.mode)
    val backdrop by animateColorAsState(
        targetValue = phaseBackdrop(accent),
        animationSpec = MotionSpecs.enter(MotionDurations.Slow),
        label = "phaseBackdrop"
    )
    val backdropDeep = lerp(backdrop, Color.Black, 0.28f)

    // Tint the phone's status bar to the phase colour while this screen is
    // open (so there is no mismatched strip at the top), and put it back
    // exactly as it was when the screen closes.
    val hostWindow = remember(view) { view.context.findHostActivity()?.window }
    DisposableEffect(hostWindow) {
        val window = hostWindow
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousColor = window.statusBarColor
            val previousLight = controller.isAppearanceLightStatusBars
            onDispose {
                window.statusBarColor = previousColor
                controller.isAppearanceLightStatusBars = previousLight
            }
        }
    }
    SideEffect {
        hostWindow?.let { w ->
            w.statusBarColor = backdrop.toArgb()
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = false
        }
    }

    // Fires exactly once per real focus-session completion (celebrationTick
    // is a monotonically increasing counter, not a Boolean, so back-to-back
    // completions each get their own beat). seenCelebrationTick starts at
    // whatever the count already is when this screen opens, so a session that
    // finished while you were elsewhere does NOT replay its confetti.
    var seenCelebrationTick by remember { mutableStateOf(state.celebrationTick) }
    LaunchedEffect(state.celebrationTick) {
        if (state.celebrationTick > seenCelebrationTick) {
            seenCelebrationTick = state.celebrationTick
            haptics.confirm()
            showConfetti = true
        }
    }

    // "Until 14:25" text for a locked Ultra Focus session.
    val lockedUntil = remember(state.ultraEndsAtMillis) {
        if (state.ultraEndsAtMillis > 0L) {
            android.text.format.DateFormat.getTimeFormat(context)
                .format(java.util.Date(state.ultraEndsAtMillis))
        } else {
            ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(backdrop, backdropDeep)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            TopAppBar(
                title = {
                    Text(
                        "Pomodoro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnPhase
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = OnPhase)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, "Settings", tint = OnPhase)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(4.dp))

            // Small optional phase pills — the normal flow never needs them.
            PhasePills(
                current = state.mode,
                enabled = state.timerState == TimerState.IDLE && !state.ultraSessionActive,
                selectedTextColor = backdrop,
                onSelect = {
                    haptics.tap()
                    viewModel.switchMode(it)
                }
            )

            Spacer(Modifier.height(18.dp))

            PhaseStatusLine(status = phaseStatus(state, lockedUntil))

            Spacer(Modifier.height(10.dp))

            TimerDisplay(
                timeRemaining = state.timeRemaining,
                totalTime = state.totalTime,
                mode = state.mode,
                isRunning = state.timerState == TimerState.RUNNING
            )

            Spacer(Modifier.height(18.dp))

            // Session dots + caption. In a FOCUS phase the dots show finished
            // sessions in this round; in a break they include the one you just
            // finished (so a long break shows all of them lit).
            val perCycle = state.settings.sessionsUntilLongBreak.coerceAtLeast(1)
            val filledDots = if (state.mode == PomodoroMode.FOCUS) {
                state.completedSessions % perCycle
            } else {
                cycleProgress(state.completedSessions, perCycle)
            }
            Text(
                text = if (state.mode == PomodoroMode.FOCUS) {
                    "Session ${filledDots + 1} of $perCycle"
                } else {
                    "$filledDots of $perCycle sessions done"
                },
                style = MaterialTheme.typography.labelLarge,
                color = OnPhaseSoft
            )
            Spacer(Modifier.height(10.dp))
            SessionDots(filled = filledDots, total = perCycle)

            Spacer(Modifier.height(28.dp))

            // The heart of it: ONE area that is either the big confirm button
            // (idle / waiting for the next phase) or the compact running controls.
            PhaseActions(
                state = state,
                textOnWhite = backdrop,
                onStart = {
                    haptics.confirm()
                    viewModel.startTimer()
                },
                onPause = {
                    haptics.tap()
                    viewModel.pauseTimer()
                },
                onReset = {
                    // Only interrupt with a confirmation if resetting would
                    // actually throw away real progress.
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
                },
                onCancelAutoStart = {
                    haptics.tap()
                    viewModel.cancelAutoStart()
                }
            )

            Spacer(Modifier.height(28.dp))

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

            Spacer(Modifier.height(20.dp))

            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs) {
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
 *  current [completedSessions] count. A just-finished full round counts as
 *  completely filled (that's what you see during the long break). */
private fun cycleProgress(completedSessions: Int, sessionsPerCycle: Int): Int {
    val perCycle = sessionsPerCycle.coerceAtLeast(1)
    val inCycle = completedSessions % perCycle
    return if (inCycle == 0 && completedSessions > 0) perCycle else inCycle
}

// ─────────────────────────────────────────────────────────────────────────
// PHASE PILLS  (small, optional manual switch)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PhasePills(
    current: PomodoroMode,
    enabled: Boolean,
    selectedTextColor: Color,
    onSelect: (PomodoroMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PhaseChoices.forEach { (mode, label) ->
            val selected = mode == current
            val pillColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Transparent,
                animationSpec = MotionSpecs.standard(),
                label = "pillColor"
            )
            Box(
                modifier = Modifier
                    .pressable(
                        enabled = enabled && !selected,
                        pressedScale = MotionTokens.PressScaleSmall,
                        onClick = { onSelect(mode) }
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillColor)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        selected -> selectedTextColor
                        enabled -> OnPhase.copy(alpha = 0.9f)
                        else -> OnPhase.copy(alpha = 0.45f)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// STATUS LINE  (fixed height so nothing jumps when the message changes)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PhaseStatusLine(status: PhaseStatus) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(MotionSpecs.enter()) togetherWith fadeOut(MotionSpecs.exit()))
            },
            label = "phaseStatus"
        ) { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (s.celebrate) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = OnPhase,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = s.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnPhase
                    )
                }
                Text(
                    text = s.sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnPhaseSoft,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// TIMER RING
// ─────────────────────────────────────────────────────────────────────────

/** Tabular-figure style for the countdown digits so "24:59" -> "25:00"
 *  doesn't visibly jiggle in width every second. */
private val TimerDigitsStyle = TextStyle(
    fontFeatureSettings = "tnum"
)

@Composable
fun TimerDisplay(
    timeRemaining: Int,
    totalTime: Int,
    mode: PomodoroMode,
    isRunning: Boolean
) {
    val rawProgress = if (totalTime > 0) timeRemaining.toFloat() / totalTime.toFloat() else 0f
    val reduced = LocalReducedMotion.current

    // One continuous sweep instead of a visible notch every second. When the
    // next phase is loaded the ring has to refill from empty to full — that
    // gets a softer ease-out instead of the mechanical linear sweep.
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

    // A barely-there breathing pulse while the timer is running.
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
        Canvas(modifier = Modifier.size(264.dp)) {
            val strokeWidth = 10.dp.toPx()
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = Offset((this.size.width - diameter) / 2, (this.size.height - diameter) / 2)

            // Track
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.displayLarge.merge(TimerDigitsStyle),
                fontWeight = FontWeight.Light,
                color = OnPhase,
                fontSize = 64.sp
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
                    color = OnPhaseSoft
                )
            }
        }
    }
}

/** Row of dots for the current round: finished sessions are solid white,
 *  the rest are hollow rings — the same look as the design reference. */
@Composable
fun SessionDots(filled: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { index ->
            val isFilled = index < filled
            val fillColor by animateColorAsState(
                targetValue = if (isFilled) Color.White else Color.Transparent,
                animationSpec = MotionSpecs.standard(),
                label = "dotFill"
            )
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(1.5.dp, Color.White.copy(alpha = if (isFilled) 1f else 0.6f), CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// THE ACTION AREA  (big confirm button  <->  compact running controls)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PhaseActions(
    state: PomodoroState,
    textOnWhite: Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    onCancelAutoStart: () -> Unit
) {
    AnimatedContent(
        targetState = state.timerState == TimerState.IDLE,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(MotionSpecs.enter()) togetherWith fadeOut(MotionSpecs.exit()))
        },
        label = "phaseActions"
    ) { idle ->
        if (idle) {
            ReadyActions(
                state = state,
                textOnWhite = textOnWhite,
                onStart = onStart,
                onSkip = onSkip,
                onCancelAutoStart = onCancelAutoStart
            )
        } else {
            RunningControls(
                state = state,
                iconOnWhite = textOnWhite,
                onStart = onStart,
                onPause = onPause,
                onReset = onReset,
                onSkip = onSkip
            )
        }
    }
}

/** Nothing is running: ONE big button that starts whatever is waiting. */
@Composable
private fun ReadyActions(
    state: PomodoroState,
    textOnWhite: Color,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onCancelAutoStart: () -> Unit
) {
    val mode = state.mode
    val locks = state.settings.ultraFocusEnabled && mode == PomodoroMode.FOCUS
    val label = when (mode) {
        PomodoroMode.FOCUS -> when {
            locks -> "Start locked focus"
            state.handoffFrom != null -> "Start next focus"
            else -> "Start focus"
        }
        PomodoroMode.SHORT_BREAK -> "Start short break"
        PomodoroMode.LONG_BREAK -> "Start long break"
    }

    // While auto-start counts down, the button fills up like a loading bar.
    val counting = state.autoStartInSeconds > 0
    val fillTarget = if (counting) {
        (PomodoroViewModel.AUTO_START_SECONDS - state.autoStartInSeconds + 1).toFloat() /
            PomodoroViewModel.AUTO_START_SECONDS
    } else {
        0f
    }
    val fill by animateFloatAsState(
        targetValue = fillTarget.coerceIn(0f, 1f),
        animationSpec = if (counting) tween(durationMillis = 1000, easing = LinearEasing) else snap(),
        label = "autoStartFill"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryPill(
            label = label,
            icon = if (locks) Icons.Filled.Lock else Icons.Filled.PlayArrow,
            textColor = textOnWhite,
            fill = fill,
            onClick = onStart
        )

        // Fixed-height line under the button, so nothing jumps around.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                counting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Starting in ${state.autoStartInSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPhaseSoft
                    )
                    TextButton(onClick = onCancelAutoStart) {
                        Text("Not yet", color = OnPhase, fontWeight = FontWeight.SemiBold)
                    }
                }
                mode != PomodoroMode.FOCUS -> TextButton(onClick = onSkip) {
                    Text("Skip break", color = OnPhaseSoft)
                }
                locks -> Text(
                    text = "Locks your phone for ${state.totalTime / 60} min. Breaks stay free.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnPhaseSoft,
                    textAlign = TextAlign.Center
                )
                else -> Unit
            }
        }
    }
}

/** The big white pill: icon + label, with an optional "filling up" sweep. */
@Composable
private fun PrimaryPill(
    label: String,
    icon: ImageVector,
    textColor: Color,
    fill: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(32.dp))
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
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
    }
}

/** A session is running or paused: reset · pause/play · skip. */
@Composable
private fun RunningControls(
    state: PomodoroState,
    iconOnWhite: Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit
) {
    val locked = state.ultraSessionActive
    val running = state.timerState == TimerState.RUNNING
    val sideEnabled = !locked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundGhostButton(
            icon = Icons.Filled.Refresh,
            description = "Reset",
            enabled = sideEnabled,
            onClick = onReset
        )

        // Big play / pause button
        Box(
            modifier = Modifier
                .size(84.dp)
                .pressable(enabled = !locked, onClick = { if (running) onPause() else onStart() })
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = running,
                transitionSpec = {
                    (fadeIn(MotionSpecs.tactile()) togetherWith fadeOut(MotionSpecs.standard()))
                },
                label = "playPauseIcon"
            ) { isRunning ->
                Icon(
                    imageVector = when {
                        isRunning && locked -> Icons.Filled.Lock
                        isRunning -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = when {
                        isRunning && locked -> "Locked"
                        isRunning -> "Pause"
                        else -> "Resume"
                    },
                    tint = iconOnWhite,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        RoundGhostButton(
            icon = Icons.Filled.SkipNext,
            description = if (state.mode == PomodoroMode.FOCUS) "Skip to break" else "Skip break",
            enabled = sideEnabled,
            onClick = onSkip
        )
    }
}

@Composable
private fun RoundGhostButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .pressable(
                enabled = enabled,
                pressedScale = MotionTokens.PressScaleSmall,
                onClick = onClick
            )
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = OnPhase.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * The Ultra Focus switch. When on, each FOCUS session also locks the phone
 * (a normal Lockdown, for exactly the focus time) and the lock lifts by
 * itself for every break. The two chips choose what happens when a break
 * ends: wait for a "Start locked focus" tap (default) or start the
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
                        "After each break, a \"Start locked focus\" button waits for you."
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
