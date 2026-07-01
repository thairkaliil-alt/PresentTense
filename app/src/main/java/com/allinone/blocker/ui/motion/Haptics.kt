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
//   [LocalReducedHaptics] mirrors [LocalReducedMotion]'s pattern exactly:
//   read once at the theme root (see Theme.kt) from the OS-level "Touch
//   feedback" / "vibrate on tap" setting (Settings.System
//   .HAPTIC_FEEDBACK_ENABLED — the same toggle under Settings > Sound &
//   vibration on stock Android). When the user has switched that off,
//   every tick below silently no-ops, the same way reduced-motion collapses
//   animations to instant.
// ═══════════════════════════════════════════════════════════════════════════

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * true when the OS-level "touch feedback" / "vibrate on tap" setting is
 * turned off. Provided once at the theme root in Theme.kt. Defaults to
 * false so haptics still fire in Previews or before the theme sets it.
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
