package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// AnimatedValues.kt  —  Numbers & values that move
//
// A streak count or a "hours saved" stat that snaps from 4 to 5 is a missed
// moment. Rolling it up is a tiny dopamine hit — the same mechanic Duolingo,
// fitness rings and Robinhood use to make progress *feel* earned. This is the
// "addictive but honest" lever the brief asks for: it rewards real progress,
// it doesn't manufacture fake urgency.
//
// Components:
//   • AnimatedCount  — an Int that counts up/down to its new value as Text.
//   • animatedCountAsState — the raw animated Int, if you need to format it
//     yourself (e.g. "3 day streak", "1h 24m").
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color

/**
 * The animated Int, ticking toward [targetValue] with the shared "expressive"
 * feel. Respects reduced-motion (snaps instantly). Format it however you like:
 *
 *   val n by animatedCountAsState(streak)
 *   Text("$n day streak")
 */
@Composable
fun animatedCountAsState(
    targetValue: Int,
    durationMs: Int = MotionDurations.Slow
): State<Int> {
    val reduced = LocalReducedMotion.current
    return animateIntAsState(
        targetValue = targetValue,
        animationSpec = if (reduced) MotionSpecs.standard(0)
        else MotionSpecs.enter(durationMs),
        label = "animatedCount"
    )
}

/**
 * Convenience Text that rolls from its previous number to [value]. Use for
 * streak counters, blocked-attempt tallies, minutes-saved, etc.
 */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    prefix: String = "",
    suffix: String = ""
) {
    val animated by animatedCountAsState(value)
    Text(
        text = "$prefix$animated$suffix",
        modifier = modifier,
        style = style,
        color = color
    )
}
