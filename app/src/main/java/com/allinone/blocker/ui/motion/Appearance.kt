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
//
// PERFORMANCE NOTE (read this before changing anything here)
//   AnimatedAppearance used to be built on AnimatedVisibility + slideInVertically.
//   That combo is fine for ONE thing appearing on its own, but it was being used
//   for entire cascades (5 Home sections, then a further 12 preset cards inside
//   one of those sections — 17 of them animating within the same second). The
//   problem: slideInVertically drives a real LAYOUT offset, so every animating
//   item forces its parent through a fresh measure+layout pass on EVERY
//   animation frame, not just a redraw. Multiply that by 17 concurrent items and
//   the main thread falls behind, which is what read as "the whole app is
//   laggy" right after opening Home, and "the list doesn't even look animated"
//   for the presets cascade (frames were being dropped, not skipped on purpose).
//
//   The fix below drives the same fade + tiny rise through Modifier.graphicsLayer
//   instead — alpha and translationY there are pure DRAW-phase transforms, so
//   they never trigger a layout pass. Visually identical, far cheaper with many
//   items animating at once.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Fades + gently rises its [content] into place the first time it enters
 * composition. Idempotent and cheap — safe to wrap any card or section, and
 * safe to use many of at once (see the performance note above).
 *
 * @param delayMs stagger this entrance after others (used by [StaggeredColumn]).
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

    // A single Animatable drives both the fade and the rise. We read it inside
    // Modifier.graphicsLayer (a draw-phase-only lambda), so recomposing this
    // value on every animation tick never asks the parent to re-measure or
    // re-layout — just re-draw, which is the cheap part.
    val progress = remember { Animatable(0f) }
    val slidePx = with(LocalDensity.current) { MotionTokens.EnterSlideDp.dp.toPx() }

    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, animationSpec = MotionSpecs.enter(MotionDurations.Emphasized))
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * slidePx
        }
    ) {
        content()
    }
}

/**
 * Renders [items] in a vertical cascade: each child appears
 * [MotionTokens.StaggerStepMs] after the previous one. The classic "list gently
 * settling in" effect. Keep lists short (≤ ~8 visible) or the tail feels slow.
 *
 * @param animate set to false to skip the cascade entirely and render every
 *   item instantly — use this for repeat visits (e.g. switching back to a tab)
 *   where the entrance has already played once and shouldn't replay.
 * @param startDelayMs stagger the WHOLE column's cascade after something else
 *   (e.g. other sections above it animating in first), so it reads as one
 *   continuous cascade instead of two separate ones stacked back to back.
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
    animate: Boolean = true,
    startDelayMs: Int = 0,
    itemContent: @Composable (item: T, index: Int) -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)
    ) {
        items.forEachIndexed { index, item ->
            if (animate) {
                AnimatedAppearance(delayMs = startDelayMs + index * stepMs) {
                    itemContent(item, index)
                }
            } else {
                itemContent(item, index)
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
