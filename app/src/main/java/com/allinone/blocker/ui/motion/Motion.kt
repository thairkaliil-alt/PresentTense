package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// Motion.kt  —  Central motion design system for Present Tense
//
// WHY THIS FILE EXISTS
//   Micro-interactions only feel "premium" when they are *consistent*. A scale
//   here, a fade there, each with its own ad-hoc duration, reads as noise. This
//   file is the single source of truth for every duration, easing curve and
//   spring in the app, so every screen breathes with the same rhythm.
//
// THE SCIENCE (condensed)
//   • Doherty Threshold — feedback under ~400ms keeps a user feeling "in flow".
//     Anything slower than that and the UI feels like it's making them wait.
//   • Material 3 / iOS HIG — entrances DECELERATE (ease-out), exits ACCELERATE
//     (ease-in). Objects arriving should settle gently; objects leaving should
//     get out of the way quickly.
//   • Subtlety wins — Nielsen Norman & top consumer apps (Things, Linear,
//     Duolingo) use SMALL displacements (a few dp) and SMALL scale deltas
//     (~3-5%). Big movements grab attention; the goal here is the opposite:
//     motion that's *felt* more than *seen*.
//   • Springs > tweens for anything touch-driven — a spring's velocity carries
//     the user's gesture, which is why a pressed button feels physical.
//   • Accessibility — respect the OS "remove animations" setting. See
//     [LocalReducedMotion] and [reduceMotion].
//
// HOW TO USE
//   Don't hand-roll tween(237) in a screen. Reach for a token:
//     animateFloatAsState(target, animationSpec = MotionSpecs.emphasized())
//     Modifier.pressable { ... }          // tactile press
//     AnimatedAppearance { CardContent() } // entrance
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

// ─────────────────────────────────────────────────────────────────────────────
// DURATIONS  (milliseconds)
//
// Named by *intent*, not by number, so the feel can be retuned globally without
// touching call sites. All sit comfortably under the 400ms Doherty threshold.
// ─────────────────────────────────────────────────────────────────────────────
object MotionDurations {
    /** Instant tactile feedback — press states, ripUnderlays. */
    const val Quick = 120

    /** The default for most state changes — color, small scale, toggles. */
    const val Standard = 220

    /** Content entrances, expand/collapse, screen-level fades. */
    const val Emphasized = 320

    /** Largest we ever go — full-screen content swaps. Still under Doherty. */
    const val Slow = 420
}

// ─────────────────────────────────────────────────────────────────────────────
// EASING CURVES
//
// Mirror the Material 3 "emphasized" set. Enter decelerates, exit accelerates,
// standard is the symmetric everyday curve.
// ─────────────────────────────────────────────────────────────────────────────
object MotionEasing {
    /** Symmetric — for changes with no clear "arriving" or "leaving" direction. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Decelerate — for elements ENTERING the screen. Settles softly. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Accelerate — for elements LEAVING the screen. Exits briskly. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}

// ─────────────────────────────────────────────────────────────────────────────
// SPRINGS & SPECS
//
// Factory functions (not vals) because AnimationSpec is generic over the
// animated type — calling MotionSpecs.standard<Float>() keeps type-safety while
// sharing one definition.
// ─────────────────────────────────────────────────────────────────────────────
object MotionSpecs {

    /** Everyday tween — color fades, alpha, simple value changes. */
    fun <T> standard(durationMs: Int = MotionDurations.Standard): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = MotionEasing.Standard)

    /** Entrance tween — use for things appearing. */
    fun <T> enter(durationMs: Int = MotionDurations.Emphasized): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = MotionEasing.Decelerate)

    /** Exit tween — use for things disappearing. */
    fun <T> exit(durationMs: Int = MotionDurations.Standard): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = MotionEasing.Accelerate)

    /**
     * Tactile spring for touch-driven motion (press, drag, selection pop).
     *
     * Tuned to feel SNAPPY, not floaty. The earlier soft spring (stiffness 600)
     * read as "laggy" on larger surfaces because the scale crept toward target
     * over ~250ms — on a quick tap you'd barely see it move. This is stiff enough
     * to snap down/back almost immediately, with a touch of underdamping so the
     * release has a faint, alive rebound instead of a dead stop.
     */
    fun <T> tactile(): AnimationSpec<T> =
        spring(
            dampingRatio = 0.55f,    // a little bounce on release — reads as "alive"
            stiffness    = 1600f     // snaps; ~2× StiffnessMediumLow
        )

   /**
     * A touch more bounce than [tactile] — for celebratory beats (a streak tick,
     * a successful toggle). Use sparingly; bounce is loud.
     */
    fun <T> expressive(): AnimationSpec<T> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        )

    /**
     * Placement spring for REORDERABLE LISTS — how a sibling row glides into
     * its new slot when another row is dragged past it.
     *
     * Tuned to *glide*, not bounce: just a hair under critically-damped, so
     * there's a faint, natural ease to the motion but never a visible wobble
     * or overshoot. This is the same restrained feel iOS Reminders / Things /
     * Todoist use for their list-reorder — confident and quiet, not springy.
     */
    fun <T> reorderGlide(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = 0.86f,
            stiffness    = 380f
        )

    /**
     * Pickup / settle spring for the ONE row actively being dragged — the
     * lift when you grab it, and the drop when you let go. Livelier than
     * [reorderGlide] on purpose: unlike the passive siblings sliding out of
     * the way, this is the row the user is physically holding, so picking
     * it up and setting it down should read as a distinct, felt event.
     */
    fun <T> reorderPickup(): AnimationSpec<T> =
        spring(
            dampingRatio = 0.7f,
            stiffness    = 700f
        )

    /**
     * The lockdown "ignition" fill — a full-screen circular reveal that grows
     * outward from wherever the user held down. Softer damping and lower
     * stiffness than [tactile]/[expressive] on purpose: those are for small
     * controls that need to snap back instantly, but a shape this large reads
     * as *heavier* — it should settle like a spreading droplet of liquid
     * rather than snapping to size. A touch of underdamping gives it a faint,
     * organic overshoot instead of a mechanical stop.
     */
    fun <T> liquidExpand(): AnimationSpec<T> =
        spring(
            dampingRatio = 0.78f,
            stiffness    = 180f
        )
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED CONSTANTS  —  the canonical "small" values every component leans on.
// ─────────────────────────────────────────────────────────────────────────────
object MotionTokens {
    /** Scale a control shrinks to while pressed. ~4% — felt, not seen. */
    const val PressScale = 0.96f

    /** Scale a small/dense control (icon, chip) shrinks to. */
    const val PressScaleSmall = 0.92f

    /** Vertical travel (dp) for content sliding into place. Intentionally tiny. */
    const val EnterSlideDp = 12

    /** Stagger (ms) between successive items in a list/column entrance. */
    const val StaggerStepMs = 45
}

// ─────────────────────────────────────────────────────────────────────────────
// REDUCED MOTION
//
// Provided at the theme root from the OS animator-duration-scale (0 == user
// asked for no animations). Components read this and collapse to instant /
// cross-fade-only behaviour. Defaults to false so previews still animate.
// ─────────────────────────────────────────────────────────────────────────────
val LocalReducedMotion = staticCompositionLocalOf { false }
