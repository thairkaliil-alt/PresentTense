package com.allinone.blocker.ui

// ─────────────────────────────────────────────────────────────────────────────
// CONFETTI — a celebration burst for big moments (streak milestones, etc.)
//
// PLAIN-ENGLISH SUMMARY:
// This is a reusable "throw confetti on the screen" effect. It's built from
// scratch with Compose's Canvas (no external library), so there's nothing to
// add to build.gradle.kts and nothing that can fail to download in a build —
// it's just Kotlin math drawing little shapes that launch, hang, and fall.
//
// WHY NOT A LIBRARY:
// Confetti libraries work fine, but pulling one in means a new Maven
// dependency that has to resolve correctly in GitHub Actions every single
// build. A hand-built version is a few KB of pure Kotlin, themed exactly to
// this app's colors, and can never break a build by failing to download.
//
// HOW THE MOTION WORKS (this is the part that makes it feel good, not cheap):
// Real confetti cannons — the kind you see in the best web confetti libraries
// and in apps like Duolingo — don't just rain from the top of the screen.
// They launch from the bottom corners, shoot up and inward, hang for a beat
// at the top of their arc (that hang is the actual "moment of victory" —
// the eye has time to register it), and only then drift back down, slower
// and slower, like real paper losing momentum to air resistance.
//
//   1. LAUNCH — each piece starts at a bottom corner (half the pieces from
//      the left, half from the right) and is fired up and inward at a
//      randomised angle and speed, so the two streams cross in the middle.
//   2. ARC — standard projectile motion: gravity pulls the upward velocity
//      down over time, so each piece traces a real parabola, not a straight
//      line. Pieces from a wider spread of speeds/angles arrive at different
//      heights, which is what makes a real burst look "full" instead of like
//      a single ring.
//   3. HANG + DRIFT — once a piece is past its apex, gravity is throttled
//      down (this is the deliberate, non-physically-"honest" trick that
//      makes confetti read as light paper instead of a falling rock) and a
//      gentle side-to-side flutter takes over. This whole phase is stretched
//      out in time, well past the ~2 seconds older confetti effects use —
//      slow enough to read as a satisfying float, not a flash.
//   4. TUMBLE — each piece's vertical scale oscillates independently of its
//      rotation, simulating a flat piece of paper flipping edge-on and
//      face-on as it falls (the same trick canvas-confetti and
//      react-confetti-explosion use) — this alone is most of what makes
//      confetti look like confetti instead of spinning squares.
//
// WHY NOT A LIBRARY (continued): the math above is the entire "trick" behind
// every polished confetti library out there — there's no proprietary magic
// to import, just gravity + drag + a flutter term, all of which is plain
// Kotlin here.
//
// HOW TO USE THIS LATER (already wired into the streak milestone moment in
// StreaksScreen.kt — you don't need to touch this today):
//   var showConfetti by remember { mutableStateOf(false) }
//   Box(Modifier.fillMaxSize()) {
//       ...your normal screen content...
//       ConfettiOverlay(trigger = showConfetti, onFinished = { showConfetti = false })
//   }
//   // then set showConfetti = true whenever you want a burst to fire
//
// DESIGN NOTES:
//   - Colors are pulled straight from the app's existing Material You accent
//     palette (amber/teal/red/green/purple/blue) so it never feels bolted on.
//   - One-shot: plays once each time [trigger] flips from false to true, then
//     calls onFinished() so the caller can clear it. It does not loop.
//   - Drawn with Canvas only — no images, no bitmaps, cheap to animate even
//     with 100+ pieces on screen at once.
//   - Confetti never blocks touches — it's purely visual, drawn on top.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentGreen
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ── Tunable constants — change these to restyle the whole effect ──────────
// All times are fractions of the overall animation (0..1), since the actual
// duration is set once in [ConfettiOverlay]'s tween below. Keeping the curve
// itself duration-independent makes it trivial to slow the whole thing down
// later without re-deriving any of the numbers below.

/** Total time the burst plays for. This is the dial for "slower/faster overall". */
private const val TOTAL_DURATION_MS = 3600

/** Pieces launch within this fraction of the very start — staggered, not instant, like a real cannon spray. */
private const val LAUNCH_STAGGER_FRACTION = 0.10f

/** Where (as a fraction of total time) the average piece reaches its peak height. Everything before this is launch; everything after is hang + fall. */
private const val APEX_FRACTION = 0.30f

/** Once a piece fades, it does so over this fraction of the tail end. */
private const val FADE_OUT_FRACTION = 0.22f

private data class ConfettiPiece(
    val fromLeft: Boolean,
    val originXFraction: Float,    // 0..1, near the bottom-left or bottom-right corner
    val launchAngleDeg: Float,     // measured from straight up (0 = vertical), negative tilts toward center
    val launchSpeed: Float,        // arbitrary units, tuned against gravity/drag below — higher = taller arc
    val delayFraction: Float,      // 0..LAUNCH_STAGGER_FRACTION, staggers the cannon spray
    val shape: ConfettiShape,
    val sizeDp: Float,
    val aspectRatio: Float,
    val color: Color,
    val spinTurnsPerSecond: Float,
    val spinDirection: Float,      // +1 or -1
    val flipSpeed: Float,          // controls the "card flip" tumble independent of spin
    val flipPhase: Float,
    val driftAmplitudeDp: Float,   // post-apex side-to-side flutter distance
    val driftFrequency: Float,
    val driftPhase: Float
)

private enum class ConfettiShape { RECT, CIRCLE }

private val ConfettiPalette = listOf(
    AccentAmber, AccentTeal, AccentRed, AccentGreen, AccentPurple, AccentBlue
)

private fun randomPieces(count: Int): List<ConfettiPiece> = List(count) { index ->
    val fromLeft = index % 2 == 0
    // Launch angle measured from straight up: a wide cone so the two
    // streams fan out and cross in the middle rather than firing as a
    // single thin jet. Biased inward (toward the opposite side) so the
    // two cannons visually meet over the center of the screen.
    val inwardBias = 18f
    val spread = (Random.nextFloat() - 0.5f) * 50f
    val angle = (if (fromLeft) -inwardBias else inwardBias) + spread

    ConfettiPiece(
        fromLeft          = fromLeft,
        originXFraction   = if (fromLeft) Random.nextFloat() * 0.08f else 0.92f + Random.nextFloat() * 0.08f,
        launchAngleDeg    = angle,
        launchSpeed       = 0.78f + Random.nextFloat() * 0.34f,
        delayFraction     = Random.nextFloat() * LAUNCH_STAGGER_FRACTION,
        shape             = if (Random.nextFloat() < 0.32f) ConfettiShape.CIRCLE else ConfettiShape.RECT,
        sizeDp            = 5f + Random.nextFloat() * 5f,
        aspectRatio       = 0.42f + Random.nextFloat() * 0.5f,
        color             = ConfettiPalette[Random.nextInt(ConfettiPalette.size)],
        spinTurnsPerSecond = 0.9f + Random.nextFloat() * 1.6f,
        spinDirection      = if (Random.nextBoolean()) 1f else -1f,
        flipSpeed          = 1.3f + Random.nextFloat() * 1.6f,
        flipPhase          = Random.nextFloat() * 6.283f,
        driftAmplitudeDp   = 10f + Random.nextFloat() * 16f,
        driftFrequency     = 0.45f + Random.nextFloat() * 0.5f,
        driftPhase         = Random.nextFloat() * 6.283f
    )
}

/**
 * Plays one confetti burst, sized to fill whatever it's placed in (use it as
 * the last child in a Box alongside your normal screen content). Pieces
 * launch from both bottom corners on an inward angle, arc upward, hang
 * briefly near the top — giving the eye a real beat to register the win —
 * then drift slowly back down with a paper-like flutter and tumble before
 * fading out. Fires once each time [trigger] flips from false to true;
 * draws nothing while false.
 *
 * @param trigger     set to true to fire a burst.
 * @param onFinished  called once the burst has fully fallen and faded out,
 *                    so the caller can flip [trigger] back to false.
 * @param pieceCount  how many confetti pieces to draw. 110 reads as a full,
 *                    generous celebration without feeling like clutter.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    onFinished: () -> Unit = {},
    pieceCount: Int = 110
) {
    if (!trigger) return

    val pieces = remember(trigger) { randomPieces(pieceCount) }
    val progress = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            // Linear on purpose: all the "ease" lives inside the physics model
            // itself (gravity, drag, the apex hang) rather than in the outer
            // tween, so the per-piece motion stays physically coherent.
            animationSpec = tween(durationMillis = TOTAL_DURATION_MS, easing = LinearEasing)
        )
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val overall = progress.value
        val w = size.width
        val h = size.height

        for (piece in pieces) {
            drawConfettiPiece(piece, overall, w, h)
        }
    }
}

/**
 * Draws one piece at its position for the current overall animation
 * progress. All the "feel" of the effect lives in this function.
 */
private fun DrawScope.drawConfettiPiece(piece: ConfettiPiece, overall: Float, w: Float, h: Float) {
    // Each piece has its own short launch delay so the cannon reads as a
    // spray, not a single synchronized pop. local runs 0..1 across the
    // remaining time after that delay.
    val local = ((overall - piece.delayFraction) / (1f - piece.delayFraction)).coerceIn(0f, 1f)
    if (local <= 0f) return

    // ── Vertical motion: launch + apex + slow hang/fall ────────────────
    // Two different "gravity strengths" are blended across the timeline:
    // a strong one during the up-going launch (so the arc reads as a real
    // throw, not a lazy lob), and a much weaker one after the apex (so the
    // fall is slow and floaty rather than the piece dropping like a stone).
    // This asymmetry — fast up, slow down — is exactly what reads as
    // "paper", because real paper has far more drag falling than a rigid
    // object would.
    val riseFraction = APEX_FRACTION
    val travelY: Float
    val travelX: Float

    if (local <= riseFraction) {
        // ── Launch phase: textbook projectile motion ────────────────────
        val t = local / riseFraction // 0..1 across the launch
        val angleRad = piece.launchAngleDeg * (Math.PI / 180.0).toFloat()
        val vx = sin(angleRad) * piece.launchSpeed
        val vy = -cos(angleRad) * piece.launchSpeed // negative = upward
        val gravity = 2.0f // strong, so the arc is snappy on the way up

        // Using a normalised "time" of t*riseDurationUnits for a clean parabola.
        val timeUnits = t * 1.0f
        travelX = vx * timeUnits
        travelY = vy * timeUnits + 0.5f * gravity * timeUnits * timeUnits
    } else {
        // ── Post-apex: weak gravity + flutter, stretched across the rest
        // of the animation — this is the slow part that gives the user a
        // real beat to enjoy before everything clears.
        val angleRad = piece.launchAngleDeg * (Math.PI / 180.0).toFloat()
        val vx = sin(angleRad) * piece.launchSpeed
        val vy = -cos(angleRad) * piece.launchSpeed
        val gravityStrong = 2.0f
        // Position + velocity at the exact apex instant, carried over so
        // there's no visible seam between the two phases.
        val apexX = vx * 1.0f
        val apexY = vy * 1.0f + 0.5f * gravityStrong * 1.0f * 1.0f
        val apexVy = vy + gravityStrong * 1.0f

        val fallT = (local - riseFraction) / (1f - riseFraction) // 0..1 across the fall
        val fallGravity = 0.55f // much weaker — this is what makes the fall slow and floaty
        val fallTimeUnits = fallT * 2.6f // stretches the fall's internal clock so it doesn't rush

        travelY = apexY + apexVy * fallTimeUnits + 0.5f * fallGravity * fallTimeUnits * fallTimeUnits
        // Horizontal drift settles into a gentle flutter instead of continuing in a straight line.
        val flutter = sin(fallT * piece.driftFrequency * 2f * Math.PI.toFloat() + piece.driftPhase) *
            (piece.driftAmplitudeDp.dp.toPx() / h.coerceAtLeast(1f))
        travelX = apexX + flutter * 4f // scaled into the same normalised unit space as apexX
    }

    // Map normalised motion units onto actual pixels. The scale factor is
    // tuned so a full-height arc/fall feels proportional on both small and
    // large phone screens, using h as the reference dimension.
    val unitToPx = h * 0.62f
    val originY = h * 1.02f
    val x = piece.originXFraction * w + travelX * unitToPx * 0.5f
    val y = originY + travelY * unitToPx

    if (y < -h * 0.25f || y > h * 1.25f) return // fully off-screen, skip drawing

    // ── Fade ─────────────────────────────────────────────────────────────
    // Fades in almost instantly (avoids a hard pop at launch) and fades out
    // gently over the configured tail fraction, never abruptly disappearing.
    var alpha = 1f
    if (local < 0.03f) alpha = local / 0.03f
    val fadeStart = 1f - FADE_OUT_FRACTION
    if (local > fadeStart) alpha = min(alpha, ((1f - local) / FADE_OUT_FRACTION).coerceIn(0f, 1f))
    if (alpha <= 0f) return

    // ── Tumble: spin + independent "card flip" vertical scale ─────────────
    // Real confetti doesn't just spin flat — it flips edge-on and face-on
    // as it falls, which is what makes it read as paper rather than a
    // sticker rotating in 2D. We fake the 3D flip cheaply with a scaleY
    // oscillation independent of the rotation itself.
    val elapsedSeconds = local * (TOTAL_DURATION_MS / 1000f)
    val spinDegrees = piece.spinDirection * piece.spinTurnsPerSecond * 360f * elapsedSeconds
    val flip = cos(elapsedSeconds * piece.flipSpeed * 2f * Math.PI.toFloat() + piece.flipPhase)
    val flipScaleY = max(0.15f, abs(flip))

    val pieceWidthPx = piece.sizeDp.dp.toPx()
    val pieceHeightPx = pieceWidthPx * piece.aspectRatio
    val pieceColor = piece.color.copy(alpha = alpha)

    rotate(degrees = spinDegrees, pivot = Offset(x, y)) {
        scale(scaleX = 1f, scaleY = flipScaleY, pivot = Offset(x, y)) {
            when (piece.shape) {
                ConfettiShape.RECT -> drawRect(
                    color = pieceColor,
                    topLeft = Offset(x - pieceWidthPx / 2f, y - pieceHeightPx / 2f),
                    size = Size(pieceWidthPx, pieceHeightPx)
                )
                ConfettiShape.CIRCLE -> drawCircle(
                    color = pieceColor,
                    radius = pieceWidthPx / 2f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
