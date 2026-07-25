package com.allinone.blocker.ui.motion

// ═══════════════════════════════════════════════════════════════════════════
// Haptics.kt  —  Tiny, reusable vibration "ticks" for touch feedback
//
// WHY THIS FILE EXISTS
//   Same idea as Motion.kt: a light tick on a toggle, a slightly stronger
//   buzz on a real success — these only feel intentional if every screen
//   uses the same few, hand-tuned pulses instead of everyone rolling their
//   own vibrate() call. This is that single shared source.
//
// WHY VIBRATOR INSTEAD OF Compose's LocalHapticFeedback
//   Compose's HapticFeedbackType only ships two constants on the Compose UI
//   version this app is pinned to (LongPress, TextHandleMove — see
//   ReorderableColumn.kt and LockdownScreen.kt, the only two places that
//   already use it). The richer set that would let us tell a "barely-there
//   tick" apart from a "stronger confirm" (Confirm, ToggleOn, VirtualKey,
//   etc.) only shipped in a newer Compose UI release than this project is
//   on, so reaching for them here would fail to compile.
//   Instead this talks to Android's Vibrator directly — the exact same
//   android.os.Vibrator / VibrationEffect APIs AlarmRingingService.kt
//   already uses for the alarm pattern — which gives full control over
//   duration and strength on every device back to this app's minSdk (26),
//   no version gate required, and no new dependency.
//
// HOW TO USE
//   val haptics = rememberHaptics()
//   Switch(checked = on, onCheckedChange = { haptics.toggleTick(); ... })
//
// RESPECTING THE SYSTEM SETTING
//   [LocalReducedHaptics] mirrors [LocalReducedMotion]'s pattern (a
//   composition-local read once at the theme root) but currently always
//   resolves to false. There used to be a version of this that read the OS
//   "Touch feedback" setting (Settings.System.HAPTIC_FEEDBACK_ENABLED) and
//   used it to silence every vibration below — that was wrong. That setting
//   only governs View.performHapticFeedback() (keyboard key presses,
//   long-press, switches), not the direct Vibrator/VibrationEffect calls
//   this file makes, so it was muting every custom haptic in the app
//   whenever someone had turned off the unrelated "Touch feedback" toggle.
//   See Theme.kt for the full explanation. [LocalReducedHaptics] is kept
//   around as the mechanism in case a real, correctly-scoped reduced-haptics
//   control (e.g. an in-app toggle) gets added later.
// ═══════════════════════════════════════════════════════════════════════════

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * true when haptics should be suppressed app-wide. Provided at the theme
 * root in Theme.kt — currently always false; see that file's comment for
 * why this is no longer wired to the OS "Touch feedback" setting. Defaults
 * to false so haptics still fire in Previews or before the theme sets it.
 */
val LocalReducedHaptics = staticCompositionLocalOf { false }

/**
 * Fires short, hand-tuned vibration pulses for touch feedback. Every method
 * silently does nothing if the device has no vibrator, or the user has
 * turned off touch feedback / haptics in system settings.
 */
class Haptics internal constructor(
    private val vibrator: Vibrator?,
    private val reduced: Boolean
) {
    /**
     * Barely-there pulse — used for the PIN entry screen, once per digit
     * typed. Short and low-amplitude on purpose: this fires up to 6 times
     * in a row, so it needs to read as "felt, not heard".
     */
    fun digitTick() = pulse(durationMs = 8L, amplitude = 40)

    /** Light tick for flipping a Switch on/off (blocked apps, whitelist). */
    fun toggleTick() = pulse(durationMs = 12L, amplitude = 90)

    /** Light tick for picking/tapping an app in a list (App Picker). */
    fun tap() = pulse(durationMs = 12L, amplitude = 90)

    /**
     * A single, barely-there pulse for the "locking in" moment at the very
     * start of a lockdown session (see LockdownLauncherActivity's entry
     * ritual). Same barely-there feel as [digitTick], given its own name
     * since it marks a different, one-time moment rather than a repeated one.
     */
    fun lockIn() = pulse(durationMs = 10L, amplitude = 60)

    /**
     * Same barely-there feel again, this time repeated once per cycle of
     * the lockdown screen's breathing ring (indefinite locks only — see
     * LockdownHeroInstrument's header comment) — a touch echo of the visual
     * "still alive, still holding" pulse, not a distinct alert of its own.
     */
    fun breathPulse() = pulse(durationMs = 8L, amplitude = 35)

    /**
     * Stronger "you did it" pulse — a quick two-beat pattern instead of a
     * single buzz, so it reads as a distinct, bigger moment than the light
     * ticks above. Used for finishing a Strict Mode challenge and for
     * hitting a streak milestone.
     */
    fun confirm() {
        if (reduced) return
        val v = vibrator ?: return
        // timings: [wait, buzz, wait, buzz] in ms. amplitudes line up 1:1;
        // the "wait" slots use 0 amplitude since they're silent gaps.
        val timings    = longArrayOf(0, 20, 40, 30)
        val amplitudes = intArrayOf(0, 160, 0, 220)
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    /**
     * Sharp, blunt "no" pulse for a failed Strict Mode attempt — wrong PIN,
     * wrong math answer, wrong scramble. Deliberately a single flat punch,
     * not a rising rhythm like [confirm]'s two-beat pattern, so it reads as
     * a dead end rather than a step forward.
     */
    fun error() = pulse(durationMs = 35L, amplitude = 200)

    private fun pulse(durationMs: Long, amplitude: Int) {
        if (reduced) return
        val v = vibrator ?: return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }
}

/**
 * Remembers a [Haptics] wired to this screen's Vibrator + the current
 * [LocalReducedHaptics] setting. Cheap to call more than once per screen
 * (e.g. once per list row) — it's just a remembered wrapper, not a new
 * system-service lookup each time a composition re-runs unnecessarily.
 */
@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    val reduced = LocalReducedHaptics.current
    return remember(context, reduced) {
        Haptics(
            vibrator = context.getSystemService(Vibrator::class.java),
            reduced  = reduced
        )
    }
}
