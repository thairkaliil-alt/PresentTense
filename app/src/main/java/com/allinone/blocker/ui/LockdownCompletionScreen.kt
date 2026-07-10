package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// LockdownCompletionScreen.kt  —  Lockdown session completion / reward screen
//
// WHY THIS EXISTS
//   Peak-End Rule (Kahneman): people judge an experience almost entirely by
//   its most intense moment and how it ENDS. Lockdown already had a strong
//   peak (the void-swallow ignition in LockdownScreen.kt) and a zero-value
//   end — the screen just went back to normal. This is that missing end.
//
//   Reference points, same as PomodoroScreen.kt: Forest (a hero "time
//   invested" stat), Headspace/Calm (a brief, calm, reflective close, not a
//   loud one), and Duolingo — "honest, earned celebration: confetti only on
//   a REAL completion, never a fake nudge". That last part is why the
//   confetti burst here scales DOWN (not off, not up) when the session used
//   an emergency break — still real, still earned, just not the full,
//   uninterrupted win.
//
//   Deliberately NOT trying to hook you back in: no secondary CTA, no
//   "share your streak", no urgency framing. One honest acknowledgment of
//   something that already happened, then back to the app. See
//   LockdownCompletionRepository.kt's header comment for the fuller
//   rationale — this is an anti-compulsion app, and the reward mechanics
//   that make other apps sticky are exactly what it's trying not to be.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.LockdownCompletionRepository.CompletedSession
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionEasing
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Full-screen completion beat, shown whenever
 * [com.allinone.blocker.data.LockdownCompletionRepository.pendingCelebration]
 * is non-null. Deliberately a full takeover (like [UnlockChallengeScreen]
 * already does for a different reason) rather than a small toast — this is
 * the one moment in the whole feature designed to earn a dedicated beat.
 *
 * @param session    the completed session to celebrate.
 * @param onDismiss  called once, when the single "Done" action is tapped —
 *                    the caller is expected to call
 *                    [com.allinone.blocker.data.LockdownCompletionRepository.consumePendingCelebration]
 *                    here so this screen never reappears for the same session.
 */
@Composable
fun LockdownCompletionScreen(
    session: CompletedSession,
    onDismiss: () -> Unit
) {
    val haptics = rememberHaptics()
    var visible by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    // Keyed on the session's own start time — a fresh, distinct value per
    // real completion, exactly like PomodoroScreen.kt's celebrationTick, so
    // back-to-back completions each still get their own beat.
    LaunchedEffect(session.startedAtMillis) {
        haptics.confirm()
        visible = true
        showConfetti = true
    }

    val headline = remember(session.startedAtMillis) { pickHeadline(session) }

    // Full burst for a real, uninterrupted completion (and an extra boost on
    // a milestone); a visibly smaller one when an emergency break was used —
    // still real, still earned, just honestly smaller. Never zero: even a
    // break-assisted session is a session that actually happened.
    val pieceCount = when {
        session.completedCleanly && session.isMilestone -> 160
        session.completedCleanly -> 110
        else -> 45
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarkest)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(MotionSpecs.enter(MotionDurations.Slow)) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = MotionSpecs.enter(MotionDurations.Slow)
                ),
            exit = fadeOut(MotionSpecs.exit()) +
                slideOutVertically(
                    targetOffsetY = { -it / 10 },
                    animationSpec = MotionSpecs.exit()
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (session.isMilestone) {
                    AppFlame(
                        modifier = Modifier.size(72.dp),
                        pulseKey = session.lifetimeSessionsCompleted,
                        glow = true,
                        pulseScalePeak = 1.5f
                    )
                    Spacer(Modifier.height(18.dp))
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                }

                Text(
                    headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(28.dp))

                // Hero stat — the length THEY chose, that they just carried
                // out. Autonomy + competence (Self-Determination Theory) in
                // one number: "you picked this, and you did it".
                Text(
                    formatDurationForCelebration(session.plannedMinutes),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    session.reasonLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // Supporting stat — the Progress Principle payoff: a running
                // total that connects this one session to an ongoing
                // identity ("someone who does this"), not just a one-off.
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LifetimeStat(
                            value = "${session.lifetimeSessionsCompleted}",
                            label = if (session.lifetimeSessionsCompleted == 1) "session" else "sessions"
                        )
                        LifetimeStat(
                            value = formatDurationForCelebration(session.lifetimeMinutesLocked),
                            label = "total locked down"
                        )
                    }
                }

                if (!session.completedCleanly) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Finished using ${session.breaksUsed} emergency " +
                            if (session.breaksUsed == 1) "break." else "breaks.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(40.dp))

                // Single dismiss action — no secondary CTA, no upsell, no
                // "keep the streak going" pressure. One clean acknowledgment.
                Button(
                    onClick = {
                        haptics.tap()
                        visible = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        ConfettiOverlay(
            trigger = showConfetti,
            onFinished = { showConfetti = false },
            pieceCount = pieceCount
        )
    }
}

@Composable
private fun LifetimeStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

// Honest, non-manufactured variants — no urgency, no "streak" language, no
// slot-machine randomness (the choice is deterministic per session, not
// re-rolled on recomposition). Voice matches the rest of the app's copy.
private val HEADLINES = listOf(
    "Session complete.",
    "You stayed present.",
    "That's time you got back.",
    "You did the thing.",
    "Time well spent."
)

private fun pickHeadline(session: CompletedSession): String =
    HEADLINES[abs(session.startedAtMillis.hashCode()) % HEADLINES.size]

/** Mirrors LockdownScreen.kt's private formatDuration() — kept as its own small copy since that one isn't exposed outside that file. */
private fun formatDurationForCelebration(totalMinutes: Int): String =
    formatDurationForCelebration(totalMinutes.toLong())

private fun formatDurationForCelebration(totalMinutes: Long): String {
    val safeMinutes = totalMinutes.coerceAtLeast(0L)
    val d = safeMinutes / (24 * 60)
    val h = (safeMinutes % (24 * 60)) / 60
    val m = safeMinutes % 60
    val parts = buildList {
        if (d > 0) add("${d}d")
        if (h > 0) add("${h}h")
        if (m > 0 || isEmpty()) add("${m}m")
    }
    return parts.joinToString(" ")
}
