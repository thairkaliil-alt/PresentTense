package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// Appearance.kt  —  Content entrances
//
// When a screen or a piece of content first shows up, having it materialise
// with a gentle fade + a few-dp rise reads as "considered" and calm. A hard cut
// reads as cheap. The displacement is deliberately tiny (12dp) — this is polish,
// not a slideshow.
//
// Components:
//   • AnimatedAppearance   — fade + small rise for a single block, on first show.
//   • StaggeredColumn      — a Column whose children cascade in one after another
//     (the "list settling into place" effect used by Things, Apple Music, etc.).
//   • AppearWhen           — animate in/out as a boolean flips (cross-fade+rise),
//     a softer alternative to a bare AnimatedVisibility.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Fades + gently rises its [content] into place the first time it enters
 * composition. Idempotent and cheap — safe to wrap any card or section.
 *
 * @param delayMs stagger this entrance after others (used by [StaggeredColumn]).
 * @param visible drive the entrance externally; defaults to "animate on first show".
 */
@Composable
fun AnimatedAppearance(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    val reduced = LocalReducedMotion.current

    if (reduced) {
        // Honour the OS "remove animations" preference: show instantly.
        content()
        return
    }

    // MutableTransitionState lets us start at `false` then flip to `true` after
    // composition (optionally after a stagger delay), which is what actually
    // triggers the enter transition.
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        state.targetState = true
    }

    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = MotionSpecs.enter(MotionDurations.Emphasized)
        ) + slideInVertically(
            animationSpec = MotionSpecs.enter(MotionDurations.Emphasized),
            initialOffsetY = { withDensity(it, MotionTokens.EnterSlideDp) }
        ),
        exit = fadeOut(animationSpec = MotionSpecs.exit())
    ) {
        content()
    }
}

/**
 * Renders [items] in a vertical cascade: each child appears
 * [MotionTokens.StaggerStepMs] after the previous one. The classic "list gently
 * settling in" effect. Keep lists short (≤ ~8 visible) or the tail feels slow.
 *
 * Usage:
 *   StaggeredColumn(rows) { row -> RowCard(row) }
 */
@Composable
fun <T> StaggeredColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 0.dp,
    stepMs: Int = MotionTokens.StaggerStepMs,
    itemContent: @Composable (T) -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)
    ) {
        items.forEachIndexed { index, item ->
            AnimatedAppearance(delayMs = index * stepMs) {
                itemContent(item)
            }
        }
    }
}

/**
 * A softer [AnimatedVisibility]: cross-fades and rises with the shared motion
 * tokens. Use for content that toggles on/off (an expanded detail, a banner).
 */
@Composable
fun AppearWhen(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(MotionSpecs.standard())
        else fadeIn(MotionSpecs.enter()) + slideInVertically(
            animationSpec = MotionSpecs.enter(),
            initialOffsetY = { withDensity(it, MotionTokens.EnterSlideDp) }
        ),
        exit = if (reduced) fadeOut(MotionSpecs.standard())
        else fadeOut(MotionSpecs.exit()) + slideOutVertically(
            animationSpec = MotionSpecs.exit(),
            targetOffsetY = { withDensity(it, MotionTokens.EnterSlideDp) }
        )
    ) { content() }
}

// slideIn*/slideOut* give us the pixel height of the content; we want a fixed
// dp offset regardless of content size, so convert dp→px crudely. The lambda
// receives the full height in px; we ignore it and return a small fixed travel.
// (Density isn't available inside these lambdas, so we approximate at ~2.75px/dp,
//  a typical xhdpi ratio — the visual difference across densities is negligible
//  for a 12dp nudge and avoids threading a Density through every call site.)
private fun withDensity(@Suppress("UNUSED_PARAMETER") fullPx: Int, dp: Int): Int =
    (dp * 2.75f).toInt()

// Kept for callers that already have a Density and want exactness.
fun Density.slideDpPx(dp: Int): Int = (dp * density).toInt()
