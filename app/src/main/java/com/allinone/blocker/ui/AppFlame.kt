package com.allinone.blocker.ui

// ─────────────────────────────────────────────────────────────────────────────
// APP FLAME — the custom-designed streak flame icon
//
// PLAIN-ENGLISH SUMMARY:
// This file turns the two hand-drawn flame shapes (originally exported as
// SVGs) into something Compose can draw and animate, and wraps them in one
// reusable composable: AppFlame.
//
// WHAT'S INSIDE:
//   1. flameFrame1() / flameFrame2() — two ImageVectors built from the exact
//      SVG path data you designed. They're two slightly different "poses"
//      of the same flame (same colors, same overall silhouette, slightly
//      different curve shapes), pre-aligned to the same square canvas so
//      swapping between them doesn't shift the flame on screen.
//   2. AppFlame(...) — the composable you use anywhere in the app.
//      By default it just shows the flame, perfectly still (frame 1, your
//      original design, untouched).
//   3. AppFlame(..., pulseKey = streak) — pass in a value that changes
//      whenever you want the flame to "react" (e.g. the streak count).
//      Every time that value changes, the flame plays one quick pulse:
//        - scales up and back down (a punchy little "pop")
//        - swaps to frame 2 for an instant at the peak of the pop, then
//          back to frame 1 — this is what gives it a hand-drawn flicker
//          feel instead of a plain icon bounce.
//      Then it goes completely still again until pulseKey changes again.
//
// HOW TO USE THIS LATER (you don't need to do this today — this file does
// nothing on its own until something calls AppFlame):
//   Anywhere you currently have something like:
//       Icon(imageVector = Icons.Filled.LocalFireDepartment, tint = flameColor, modifier = Modifier.size(64.dp))
//   you could swap it for:
//       AppFlame(modifier = Modifier.size(64.dp))
//   or, to make it pulse whenever the streak number changes:
//       AppFlame(modifier = Modifier.size(64.dp), pulseKey = streak)
//
// NOTE ON COLOR:
//   Your flame already has its own built-in orange body + cream core — it
//   doesn't need a "tint" the way a plain system icon does. The only color
//   control exposed here is `desaturated`, which greys the flame out for a
//   "streak broken" state (to match the grey-out already used elsewhere in
//   the app), without altering your actual design otherwise.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos

// ── Your exact path data, taken directly from the SVG files you made ──────
// Both frames have been mathematically re-centered onto the SAME 0..255.18
// local canvas (the original SVGs used a giant 656x1236 page with the flame
// sitting off-center; this crops to just the flame, with ~6% padding, using
// one shared crop box for both frames so they align perfectly when swapped).
// The shapes and colors themselves are 100% untouched — only the canvas
// changed.
private const val FLAME_CANVAS_SIZE = 255.18f

private const val FRAME1_BODY =
    "M104.310 233.040C54.329 219.647 24.667 168.273 38.060 118.291L60.109 36.003C61.538 30.669 67.022 27.503 72.356 28.932L152.472 50.399C202.853 63.899 232.654 116.048 219.155 166.429V166.429C205.762 216.410 154.291 246.432 104.310 233.040V233.040Z"
private const val FRAME1_BODY2 =
    "M61.973 208.596C25.384 172.008 25.384 112.685 61.973 76.096L122.212 15.858C126.117 11.952 132.449 11.952 136.354 15.858L195.003 74.507C231.885 111.388 231.619 171.451 194.737 208.333V208.333C158.148 244.921 98.562 245.185 61.973 208.596V208.596Z"
private const val FRAME1_CORE =
    "M100.783 177.290C85.537 162.044 85.430 137.217 100.676 121.970L124.595 98.051C126.396 96.250 129.316 96.250 131.117 98.051L155.676 122.610C170.805 137.739 170.805 162.268 155.676 177.397V177.397C140.547 192.526 115.912 192.419 100.783 177.290V177.290Z"

private const val FRAME2_BODY =
    "M95.901 231.112C46.975 214.266 20.970 160.948 37.817 112.022L65.552 31.473C67.350 26.251 73.041 23.475 78.263 25.273L156.686 52.277C206.003 69.258 232.094 123.359 215.112 172.675V172.675C198.266 221.601 144.826 247.959 95.901 231.112V231.112Z"
private const val FRAME2_BODY2 =
    "M60.474 207.097C23.057 169.681 23.057 109.016 60.474 71.599L119.383 12.690C124.851 7.222 133.715 7.222 139.182 12.690L196.502 70.009C234.212 107.719 233.946 169.124 196.236 206.833V206.833C158.819 244.250 97.891 244.514 60.474 207.097V207.097Z"
private const val FRAME2_CORE =
    "M102.502 178.775C85.468 164.482 83.127 138.985 97.420 121.951L117.036 98.573C120.275 94.714 126.029 94.210 129.888 97.449L153.982 117.665C170.884 131.848 173.089 157.048 158.906 173.950V173.950C144.723 190.853 119.405 192.958 102.502 178.775V178.775Z"

// Your original colors, kept exact.
private val FlameBodyOuter = Color(0xFFFF8800)
private val FlameBodyInner = Color(0xFFFF8C00)
private val FlameCore       = Color(0xFFFFE3C3)

// True sinusoidal ease — velocity is zero at both ends, so every oscillation
// glides in and out with no visible "stop and reverse" jolt. This is the
// difference between motion that soothes and motion that twitches.
private val SineEasing = Easing { t -> ((1f - cos(t * Math.PI).toFloat()) / 2f) }

/** Builds one flame "pose" (frame) as a Compose ImageVector. */
private fun buildFlame(name: String, bodyPath: String, body2Path: String, corePath: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = FLAME_CANVAS_SIZE.dp,
        defaultHeight = FLAME_CANVAS_SIZE.dp,
        viewportWidth = FLAME_CANVAS_SIZE,
        viewportHeight = FLAME_CANVAS_SIZE
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(bodyPath).toNodes(),
            name = "body",
            fill = androidx.compose.ui.graphics.SolidColor(FlameBodyOuter)
        )
        addPath(
            pathData = PathParser().parsePathString(body2Path).toNodes(),
            name = "body2",
            fill = androidx.compose.ui.graphics.SolidColor(FlameBodyInner)
        )
        addPath(
            pathData = PathParser().parsePathString(corePath).toNodes(),
            name = "core",
            fill = androidx.compose.ui.graphics.SolidColor(FlameCore)
        )
    }.build()

// Built once and cached — building an ImageVector is cheap but there's no
// reason to rebuild it on every recomposition.
private var cachedFrame1: ImageVector? = null
private var cachedFrame2: ImageVector? = null

private fun flameFrame1(): ImageVector =
    cachedFrame1 ?: buildFlame("AppFlameFrame1", FRAME1_BODY, FRAME1_BODY2, FRAME1_CORE).also { cachedFrame1 = it }

private fun flameFrame2(): ImageVector =
    cachedFrame2 ?: buildFlame("AppFlameFrame2", FRAME2_BODY, FRAME2_BODY2, FRAME2_CORE).also { cachedFrame2 = it }

// ─────────────────────────────────────────────────────────────────────────────
// AppFlame — the composable you actually use in screens
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Your custom streak flame icon.
 *
 * @param modifier            sizing/positioning, same as any Icon (e.g. Modifier.size(64.dp))
 * @param desaturated         true greys the flame out (for "streak broken" states).
 *                             Leave false to show your normal orange/cream design.
 * @param pulseKey            optional. Pass any value that changes when you want the
 *                             flame to play a one-shot "pop + flicker" reaction (for
 *                             example, pass the current streak count so it pulses
 *                             every time the streak goes up). Leave it out entirely
 *                             for a flame that never animates on its own.
 * @param flicker             optional. When true, the flame is continuously "alive":
 *                             it breathes (gently elongates and settles), sways from
 *                             its base like a real candle, and the two hand-drawn
 *                             frames crossfade into each other so the silhouette is
 *                             always softly morphing instead of snapping between two
 *                             poses. Several motions of different, non-matching speeds
 *                             are layered, so the overall movement never repeats on a
 *                             visible loop — it reads as organic, not mechanical.
 *                             Defaults to false — completely still unless asked for.
 * @param glow                optional. When true, casts a soft, warm pool of light on
 *                             the surface beneath the flame, breathing gently in time
 *                             with it. Deliberately faint — a touch of warmth and depth,
 *                             not a spotlight. Skipped automatically when desaturated
 *                             (a broken streak gives off no light). Defaults to false.
 * @param pulseScalePeak      optional. How big the flame swells at the top of a pulse.
 *                             Default 1.28 is a gentle pop; pass something larger (e.g.
 *                             1.7) for a pulse that really grabs attention — like the
 *                             once-a-day streak increase on the home badge.
 * @param pulseLiftFraction   optional. How far the flame rises during a pulse, as a
 *                             fraction of its own height (0 = no lift). Combined with a
 *                             larger pulseScalePeak this reads as the flame leaping up.
 */
@Composable
fun AppFlame(
    modifier: Modifier = Modifier,
    desaturated: Boolean = false,
    pulseKey: Any? = null,
    flicker: Boolean = false,
    glow: Boolean = false,
    pulseScalePeak: Float = 1.28f,
    pulseLiftFraction: Float = 0f
) {
    // Frame shown for the one-shot pulse (the "+1 streak" pop). During a
    // continuous flicker the frames are crossfaded instead (see `blend`),
    // so this only matters when flicker is off.
    var showFrame2 by remember { mutableStateOf(false) }

    // Scale used for the pop. Rests at 1f — completely still until a pulse.
    val scale = remember { Animatable(1f) }
    // Vertical lift used for the pop (fraction of height, rests at 0f).
    val lift = remember { Animatable(0f) }

    // ── Continuous, layered "alive" motion (only when flicker = true) ────
    // Instead of snapping between two poses, we run several gentle waves at
    // DIFFERENT, deliberately non-matching speeds and combine them. Because
    // the periods don't line up, the overall motion never settles into a
    // visible loop — the eye reads it as a real, organic flame rather than a
    // repeating GIF. Every wave uses SineEasing, so each one eases in and
    // out with zero velocity at the turn-around: no twitch, no jerk.
    val anim = flicker || glow
    val t = rememberInfiniteTransition(label = "flame")

    // Vertical breathing: the flame slowly elongates and settles.
    val breathe: State<Float> = if (anim) t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = SineEasing), RepeatMode.Reverse),
        label = "breathe"
    ) else remember { mutableStateOf(0f) }

    // Sway: a slow lean left/right, pivoting from the base like a candle.
    val sway: State<Float> = if (anim) t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4300, easing = SineEasing), RepeatMode.Reverse),
        label = "sway"
    ) else remember { mutableStateOf(0f) }

    // Gentle rise: the body drifts upward a touch as it breathes.
    val rise: State<Float> = if (anim) t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100, easing = SineEasing), RepeatMode.Reverse),
        label = "rise"
    ) else remember { mutableStateOf(0f) }

    // Crossfade weight between the two hand-drawn frames — the silhouette is
    // always softly morphing rather than holding on one shape.
    val blend: State<Float> = if (anim) t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2300, easing = SineEasing), RepeatMode.Reverse),
        label = "blend"
    ) else remember { mutableStateOf(0f) }

    // Glow breathing — its own speed again, so the cast light doesn't pulse
    // in lockstep with the flame's shape (real light lags the flame).
    val glowWave: State<Float> = if (anim) t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = SineEasing), RepeatMode.Reverse),
        label = "glow"
    ) else remember { mutableStateOf(0f) }

    // remember(pulseKey): first appearance does NOT pulse (nothing to compare
    // against). Each later change plays one pop. Only used when flicker is off.
    var hasPulsedBefore by remember { mutableStateOf(false) }
    LaunchedEffect(pulseKey) {
        if (pulseKey == null || !hasPulsedBefore) {
            hasPulsedBefore = true
            return@LaunchedEffect
        }
        // The flame leaps: it swells and rises together, then settles back.
        if (pulseLiftFraction > 0f) {
            launch {
                lift.animateTo(
                    targetValue = pulseLiftFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessHigh
                    )
                )
                lift.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness    = Spring.StiffnessMedium
                    )
                )
            }
        }
        scale.animateTo(
            targetValue = pulseScalePeak,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessHigh
            )
        )
        showFrame2 = true
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness    = Spring.StiffnessMedium
            )
        )
        showFrame2 = false
    }

    val colorFilter = if (desaturated) {
        ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
        )
    } else null

    val glowOn = glow && !desaturated

    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        // ── Soft pool of warm light cast beneath the flame ───────────────
        // Drawn behind everything, centred low so it reads as light landing
        // on a surface. Kept faint on purpose — a hint of depth, never a
        // spotlight that pulls focus.
        if (glowOn) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val a = 0.10f + 0.10f * glowWave.value      // ~0.10–0.20
                        val center = Offset(size.width / 2f, size.height * 0.80f)
                        val radius = size.maxDimension * (0.85f + 0.06f * glowWave.value)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    FlameBodyOuter.copy(alpha = a),
                                    FlameBodyOuter.copy(alpha = 0f)
                                ),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )
                    }
            )
        }

        // ── The flame itself ─────────────────────────────────────────────
        // All transforms live in one graphicsLayer pivoting near the base
        // (0.5, 0.92) so the flame leans and stretches from where it "sits".
        // Distances are expressed as fractions of the layer size, so the
        // motion looks identical at 12.dp and at 64.dp.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.92f)
                    val b = breathe.value
                    scaleX = scale.value * (1f - 0.03f * b)
                    scaleY = scale.value * (1f + 0.06f * b)
                    rotationZ = 2.0f * sway.value
                    translationX = 0.018f * size.width * sway.value
                    translationY = -0.02f * size.height * rise.value - lift.value * size.height
                }
        ) {
            if (flicker) {
                // Crossfade: frame 1 fades out as frame 2 fades in, and back.
                Image(
                    imageVector = flameFrame1(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - blend.value },
                    colorFilter = colorFilter
                )
                Image(
                    imageVector = flameFrame2(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = blend.value },
                    colorFilter = colorFilter
                )
            } else {
                // Still (or one-shot pulse): a single frame, swapped at the
                // peak of a pop for a hand-drawn flicker feel.
                Image(
                    imageVector = if (showFrame2) flameFrame2() else flameFrame1(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = colorFilter
                )
            }
        }
    }
}
