package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// FeedbackEffects.kt  —  Shake / red-flash / success-checkmark for challenge
// steps in UnlockChallengeScreen.kt
//
// WHY THIS FILE EXISTS
//   Strict Mode's unlock challenge (PIN, math puzzle, word scramble, ...) is
//   arguably the single most emotionally important screen in the app —
//   someone is testing their own willpower there. Before this file, a wrong
//   answer just silently reset with zero feedback, and a right answer just
//   silently moved on. Both felt broken rather than real. This file is the
//   one shared place that fixes that for every challenge type, instead of
//   five near-duplicate animation blocks scattered across the step
//   composables.
//
// HOW TO USE (see UnlockChallengeScreen.kt for real examples)
//   val feedback = rememberChallengeFeedback()
//   val scope = rememberCoroutineScope()
//
//   // on a wrong attempt:
//   haptics.error()
//   scope.launch { feedback.fail() }
//
//   // on a correct attempt:
//   scope.launch {
//       haptics.confirm()
//       feedback.succeed()
//       delay(MotionDurations.Emphasized.toLong())
//       onPassed()
//   }
//
//   // wrap whatever should shake/flash (the input field, or the input +
//   // its card together):
//   Modifier.shakeAndFlash(feedback)
//
//   // drop the checkmark wherever it should appear (usually overlaid on a
//   // corner of the same group that shakes):
//   SuccessCheckmark(feedback)
//
// WHY SHAKE ISN'T TUNED LIKE THE "SUBTLETY WINS" TOKENS IN Motion.kt
//   Motion.kt's guidance ("small displacements, felt not seen") is for
//   everyday state changes. A failed willpower check is deliberately the
//   one exception: the whole point is that it reads as sharp and immediate
//   — the universal "wrong" gesture from iOS lock screens and banking apps
//   — not another gentle micro-interaction. The duration and easing curve
//   still come straight from Motion.kt's tokens; only the displacement
//   distance is intentionally bigger than the rest of the app.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Sideways travel of a shake beat, in dp. See the file header for why this
 *  is intentionally bigger than Motion.kt's usual "a few dp, felt not seen"
 *  guidance — this is the one place in the app that SHOULD grab attention. */
private const val ShakeAmplitudeDp = 9f

/**
 * Holds the animated values behind a challenge step's fail/success feedback:
 * a horizontal shake + a red flash for "wrong", a scale-and-fade checkmark
 * for "right". One instance is shared by both [shakeAndFlash] and
 * [SuccessCheckmark] so the visuals always stay in sync.
 */
class ChallengeFeedback internal constructor(
    internal val shakeOffsetDp: Animatable<Float, AnimationVector1D>,
    internal val flashAlpha: Animatable<Float, AnimationVector1D>,
    internal val successAlpha: Animatable<Float, AnimationVector1D>,
    internal val successScale: Animatable<Float, AnimationVector1D>
) {
    /** True while the success checkmark is showing — steps use this to
     *  disable input/buttons so a second tap can't sneak in mid-animation. */
    val isSucceeding: Boolean get() = successAlpha.value > 0f

    /**
     * Plays the "wrong" beat: a sharp decaying shake plus a red flash that
     * fades out together, both finishing inside [MotionDurations.Standard]
     * — the same duration token every other state-change in the app uses.
     */
    suspend fun fail() = coroutineScope {
        shakeOffsetDp.snapTo(0f)
        flashAlpha.snapTo(1f)
        launch {
            shakeOffsetDp.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = MotionDurations.Standard
                    0f at 0 using MotionEasing.Standard
                    -ShakeAmplitudeDp at (MotionDurations.Standard * 0.15f).toInt() using MotionEasing.Standard
                    ShakeAmplitudeDp at (MotionDurations.Standard * 0.35f).toInt() using MotionEasing.Standard
                    -ShakeAmplitudeDp * 0.6f at (MotionDurations.Standard * 0.55f).toInt() using MotionEasing.Standard
                    ShakeAmplitudeDp * 0.3f at (MotionDurations.Standard * 0.78f).toInt() using MotionEasing.Standard
                }
            )
        }
        launch {
            flashAlpha.animateTo(0f, MotionSpecs.standard())
        }
    }

    /**
     * Plays the "right" beat: the checkmark pops in with a tactile spring
     * and fades in quickly. Callers are responsible for holding briefly
     * (see the [MotionDurations.Emphasized] delay in the usage example
     * above) before actually advancing, so the checkmark is visible for a
     * beat instead of being instantly covered by the next step's entrance.
     */
    suspend fun succeed() = coroutineScope {
        successScale.snapTo(0.6f)
        successAlpha.snapTo(0f)
        launch { successAlpha.animateTo(1f, MotionSpecs.enter(MotionDurations.Quick)) }
        launch { successScale.animateTo(1f, MotionSpecs.tactile()) }
    }
}

/** Remembers a [ChallengeFeedback] scoped to whatever step composable calls it. */
@Composable
fun rememberChallengeFeedback(): ChallengeFeedback {
    val shakeOffsetDp = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val successAlpha = remember { Animatable(0f) }
    val successScale = remember { Animatable(0.6f) }
    return remember(shakeOffsetDp, flashAlpha, successAlpha, successScale) {
        ChallengeFeedback(shakeOffsetDp, flashAlpha, successAlpha, successScale)
    }
}

/**
 * Applies the shake + red flash from [feedback] to whatever it's attached
 * to — usually the input field, or the input field grouped with its card.
 * Safe to leave attached permanently; it's a no-op until [ChallengeFeedback
 * .fail] is called.
 */
fun Modifier.shakeAndFlash(feedback: ChallengeFeedback, cornerRadius: Dp = 14.dp): Modifier =
    this
        .offset { IntOffset(x = feedback.shakeOffsetDp.value.dp.roundToPx(), y = 0) }
        .drawWithContent {
            drawContent()
            val alpha = feedback.flashAlpha.value
            if (alpha > 0f) {
                drawRoundRect(
                    color = AccentRed.copy(alpha = alpha * 0.35f),
                    cornerRadius = CornerRadius(cornerRadius.toPx())
                )
            }
        }

/**
 * A small filled checkmark that scales up and fades in when [feedback]'s
 * success beat plays, and disappears again once it resets for the next
 * step. Meant to be overlaid on a corner of the same group that shakes —
 * see call sites in UnlockChallengeScreen.kt.
 */
@Composable
fun SuccessCheckmark(feedback: ChallengeFeedback, modifier: Modifier = Modifier) {
    if (feedback.successAlpha.value <= 0f) return
    Box(
        modifier = modifier
            .size(28.dp)
            .graphicsLayer {
                alpha = feedback.successAlpha.value
                scaleX = feedback.successScale.value
                scaleY = feedback.successScale.value
            }
            .clip(CircleShape)
            .background(AccentBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}
