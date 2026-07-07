package com.allinone.blocker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Live, app-wide readout of the hold-to-lockdown "void" animation.
 *
 * [LockdownScreen] still owns all the actual logic — the hold timer, when it
 * commits, starting the lockdown, etc. This object only mirrors the two
 * values needed to *paint* the void: where it started ([origin]) and how far
 * it's grown ([progress], 0f = not happening, 1f = fully swallowed the
 * screen).
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
 * Plain Compose `mutableStateOf` vars are enough here (no StateFlow needed):
 * any composable that reads [origin] or [progress] during composition will
 * automatically recompose when LockdownScreen updates them each frame.
 */
object LockdownVoidOverlayState {
    var origin: Offset? by mutableStateOf(null)
    var progress: Float by mutableStateOf(0f)
}
