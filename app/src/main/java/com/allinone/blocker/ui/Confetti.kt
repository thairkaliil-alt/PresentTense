package com.allinone.blocker.ui

// ─────────────────────────────────────────────────────────────────────────────
// CONFETTI — a celebration burst for big moments (streak milestones, etc.)
//
// PLAIN-ENGLISH SUMMARY:
// This is a reusable "throw confetti on the screen" effect. It's built from
// scratch with Compose's Canvas (no external library), so there's nothing to
// add to build.gradle.kts and nothing that can fail to download in a build —
// it's just Kotlin math drawing little rectangles that fall and spin.
//
// WHY NOT A LIBRARY:
// Confetti libraries work fine, but pulling one in means a new Maven
// dependency that has to resolve correctly in GitHub Actions every single
// build. A hand-built version is a few KB of pure Kotlin, themed exactly to
// this app's colors, and can never break a build by failing to download.
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
//   - Pieces fall from above the screen, drift sideways, and rotate — real
//     confetti tumbles, it doesn't fall in a straight line.
//   - Colors are pulled straight from the app's existing Material You accent
//     palette (amber/teal/red/green/purple/blue) so it never feels bolted on.
//   - One-shot: plays once each time [trigger] flips from false to true, then
//     calls onFinished() so the caller can clear it. It does not loop.
//   - Drawn with Canvas only — no images, no bitmaps, cheap to animate even
//     with 80-120 pieces on screen at once.
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentGreen
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import kotlin.math.sin
import kotlin.random.Random

/** One confetti piece's fixed "recipe" — randomised once, then animated by a single shared progress value. */
private data class ConfettiPiece(
    val startXFraction: Float,    // 0..1 across the width, where it starts
    val fallDelay: Float,         // 0..0.25 — pieces don't all start at once, feels more organic
    val driftAmplitudeDp: Float,  // how far it sways side to side as it falls
    val driftFrequency: Float,    // how many sways it completes during the fall
    val driftPhase: Float,        // offsets the sway so pieces don't sway in lockstep
    val rotationTurns: Float,     // full turns completed during the fall
    val rotationDirection: Float, // +1 or -1
    val sizeDp: Float,
    val aspectRatio: Float,       // height as a fraction of width — varies for variety
    val color: Color
)

private val ConfettiPalette = listOf(
    AccentAmber, AccentTeal, AccentRed, AccentGreen, AccentPurple, AccentBlue
)

private fun randomPieces(count: Int): List<ConfettiPiece> = List(count) {
    ConfettiPiece(
        startXFraction    = Random.nextFloat(),
        fallDelay         = Random.nextFloat() * 0.25f,
        driftAmplitudeDp  = 14f + Random.nextFloat() * 22f,
        driftFrequency    = 1.5f + Random.nextFloat() * 2f,
        driftPhase        = Random.nextFloat() * 6.283f,
        rotationTurns     = 1.5f + Random.nextFloat() * 3f,
        rotationDirection = if (Random.nextBoolean()) 1f else -1f,
        sizeDp            = 6f + Random.nextFloat() * 6f,
        aspectRatio       = 0.4f + Random.nextFloat() * 0.6f,
        color             = ConfettiPalette[Random.nextInt(ConfettiPalette.size)]
    )
}

/**
 * Plays one confetti burst, sized to fill whatever it's placed in (use it as
 * the last child in a Box alongside your normal screen content). Fires once
 * each time [trigger] flips from false to true; draws nothing while false.
 *
 * @param trigger     set to true to fire a burst.
 * @param onFinished  called once the burst has fully fallen and faded out,
 *                    so the caller can flip [trigger] back to false.
 * @param pieceCount  how many confetti pieces to draw. 90 reads as a full,
 *                    generous celebration without feeling like clutter.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    onFinished: () -> Unit = {},
    pieceCount: Int = 90
) {
    if (!trigger) return

    val pieces = remember(trigger) { randomPieces(pieceCount) }
    val progress = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2200, easing = LinearEasing)
        )
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val progressValue = progress.value
        val w = size.width
        val h = size.height
        // Pieces fall from just above the top edge to just below the bottom edge.
        val travelDistance = h * 1.15f

        for (piece in pieces) {
            // Local progress: each piece has its own start delay, then runs
            // 0..1 across the time that remains in the overall animation.
            val local = ((progressValue - piece.fallDelay) / (1f - piece.fallDelay)).coerceIn(0f, 1f)
            if (local <= 0f) continue

            val y = -h * 0.1f + travelDistance * local
            val sway = sin(local * piece.driftFrequency * 2f * Math.PI.toFloat() + piece.driftPhase) *
                piece.driftAmplitudeDp.dp.toPx()
            val x = piece.startXFraction * w + sway

            // Fade out gently in the last 20% of the fall so pieces don't "pop" off-screen.
            val alpha = if (local > 0.8f) ((1f - local) / 0.2f).coerceIn(0f, 1f) else 1f
            if (alpha <= 0f) continue

            val rotationDegrees = piece.rotationDirection * piece.rotationTurns * 360f * local
            val pieceWidthPx = piece.sizeDp.dp.toPx()
            val pieceHeightPx = pieceWidthPx * piece.aspectRatio

            rotate(degrees = rotationDegrees, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - pieceWidthPx / 2f, y - pieceHeightPx / 2f),
                    size = Size(pieceWidthPx, pieceHeightPx)
                )
            }
        }
    }
}
