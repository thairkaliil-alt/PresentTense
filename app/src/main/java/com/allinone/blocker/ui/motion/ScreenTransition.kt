package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// ScreenTransition.kt  —  Whole-screen content swaps
//
// The app routes with a single `when (screen)`. Wrapping that in [ScreenSwitch]
// gives every navigation a consistent, calm cross-fade-with-a-nudge instead of
// a hard cut — the difference between "this app was thrown together" and "this
// app was designed".
//
// Why a fade-through (out then in) and not a horizontal slide? A slide implies a
// spatial hierarchy (forward/back) that this flat tab+stack model doesn't really
// have. A shared-axis fade-through is the Material-recommended default for
// peer destinations and never points the user the "wrong" way.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Cross-fades between whatever [targetState] selects, applying the shared motion
 * tokens. Drop-in around a `when (screen)` block:
 *
 *   ScreenSwitch(targetState = screen) { current ->
 *       when (current) { Screen.HOME -> HomeScreen(...) ; ... }
 *   }
 *
 * The outgoing screen fades+accelerates away while the incoming one
 * fades+decelerates in with a whisper of scale (0.98→1.0) so it feels like it's
 * coming forward, not just blinking on.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <S> ScreenSwitch(
    targetState: S,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                fadeIn(MotionSpecs.standard()) togetherWith fadeOut(MotionSpecs.standard())
            } else {
                (
                    fadeIn(MotionSpecs.enter(MotionDurations.Emphasized)) +
                        scaleIn(
                            animationSpec = MotionSpecs.enter(MotionDurations.Emphasized),
                            initialScale = 0.98f
                        )
                ) togetherWith fadeOut(MotionSpecs.exit(MotionDurations.Quick))
            }
        },
        label = "screenSwitch"
    ) { state ->
        content(state)
    }
}

/**
 * Directional "push" transition for stacked/detail screens (Settings, Streaks,
 * Blocked Apps, Strict Alarm …). The incoming screen slides in from the right
 * edge while fading; the outgoing one drifts a little left and fades. This reads
 * as moving *deeper* into the app — a far more noticeable, intentional gesture
 * than a plain cross-fade, and it matches the push/pop mental model people
 * already have from iOS and every top app.
 *
 * Use this around the sub-screen router; keep [ScreenSwitch] for peer tabs.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <S> ScreenPush(
    targetState: S,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                fadeIn(MotionSpecs.standard()) togetherWith fadeOut(MotionSpecs.standard())
            } else {
                (
                    slideInHorizontally(
                        animationSpec = MotionSpecs.enter(MotionDurations.Emphasized),
                        // Enter from ~12% of the width — a clear slide, not a full swipe.
                        initialOffsetX = { full -> full / 8 }
                    ) + fadeIn(MotionSpecs.enter(MotionDurations.Emphasized))
                ) togetherWith (
                    slideOutHorizontally(
                        animationSpec = MotionSpecs.exit(MotionDurations.Standard),
                        targetOffsetX = { full -> -full / 12 }
                    ) + fadeOut(MotionSpecs.exit(MotionDurations.Standard))
                )
            }
        },
        label = "screenPush",
        content = { state -> content(state) }
    )
}
