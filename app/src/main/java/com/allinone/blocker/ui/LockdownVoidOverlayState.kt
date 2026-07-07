package com.allinone.blocker.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Live, app-wide readout of the hold-to-lockdown "void" animation.
 *
 * [LockdownScreen] still owns all the actual logic — the hold timer, when it
 * commits, starting the lockdown, etc. This object only mirrors what's
 * needed to *paint* the void: where it started ([origin]) and a reference to
 * the live progress ([progressState], 0f = not happening, 1f = fully
 * swallowed the screen).
 *
 * Why this exists: the void used to be drawn inside a Box wrapping
 * LockdownScreen's own Scaffold. That Box only fills the content slot the
 * app's root Scaffold hands to the Lockdown tab — which stops short of the
 * bottom tab bar. So the animation looked like it covered "the lockdown
 * screen" instead of "the whole phone", which defeats the point of a
 * transition into lockdown. AppRoot (in MainActivity.kt) reads this object
 * and draws the real [VoidExpansion] above its entire root Scaffold —
 * bottom bar included — so the void genuinely eats the whole screen.
 *
 * [progressState] holds a *reference* to LockdownScreen's own animation
 * state, not a copy of its current number. A Compose `State` object's
 * identity never changes — only its `.value` does — so this reference only
 * needs to be handed over once (when a hold starts), not re-copied here on
 * every single animation frame. AppRoot reads `.value` directly, right where
 * it draws, which is what lets the progress tick 60 times a second without
 * forcing LockdownScreen (or anything in between) to recompose just to keep
 * this mirror fed. An earlier version stored a plain `Float` here instead,
 * which meant something had to actively re-copy `voidProgress.value` into it
 * on every frame — that extra hop, multiplied by everything that screen was
 * already doing, was the main source of this animation feeling laggy.
 */
object LockdownVoidOverlayState {
    var origin: Offset? by mutableStateOf(null)
    var progressState: State<Float>? by mutableStateOf(null)
}
