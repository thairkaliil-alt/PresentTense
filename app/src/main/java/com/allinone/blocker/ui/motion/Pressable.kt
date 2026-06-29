package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// Pressable.kt  —  Tactile press feedback
//
// The single most impactful micro-interaction: a control that subtly "gives"
// under a finger. Every successful consumer app does this (iOS buttons, Linear,
// Things, Duolingo). It costs almost nothing and makes the whole UI feel alive
// and responsive — the user's brain reads it as "the app heard me".
//
// Two entry points:
//   • Modifier.pressable(onClick)  — drop-in replacement for Modifier.clickable
//     that adds the scale feedback AND removes the default grey ripple (we use
//     motion, not a wash of colour, as the affordance).
//   • Modifier.pressScale()        — JUST the scale reaction, for cases where
//     click handling is already wired elsewhere (e.g. a Card's own onClick).
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role

/**
 * Drop-in alternative to [Modifier.clickable] that adds a subtle press-scale and
 * drops the default ripple. Use for cards, list rows, custom buttons.
 *
 * @param pressedScale how far the control shrinks while held. Defaults to the
 *        canonical 4% ([MotionTokens.PressScale]); pass [MotionTokens.PressScaleSmall]
 *        for small/dense targets like icons and chips.
 * @param indication pass a ripple here if you DO want one on top of the scale
 *        (e.g. `LocalIndication.current`). Null = motion-only (the default look).
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = MotionTokens.PressScale,
    role: Role? = Role.Button,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .pressScale(interactionSource, pressedScale)
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

/**
 * Just the scale reaction, driven by an existing [interactionSource]. Use when
 * the clickable/toggleable is declared elsewhere (Material `Card(onClick=…)`,
 * `Switch`, etc.) but you still want the tactile squeeze.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = MotionTokens.PressScale
): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = MotionSpecs.tactile(),
        label = "pressScale"
    )
    this.scale(scale)
}

/**
 * Convenience for the (common) case where you have your own interaction source
 * already and just want the press-scale modifier inline with the standard
 * Material ripple kept. Mostly sugar over [pressScale] + [Indication].
 */
@Composable
fun rememberPressInteraction(): MutableInteractionSource =
    remember { MutableInteractionSource() }
