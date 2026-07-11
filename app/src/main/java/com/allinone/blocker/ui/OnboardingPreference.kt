package com.allinone.blocker.ui

import android.content.Context

// ─────────────────────────────────────────────────────────────────────────────
// OnboardingPreference.kt
//
// PLAIN-ENGLISH SUMMARY:
// Remembers one single true/false fact: "has this person already been
// through the first-run permission walkthrough (OnboardingScreen.kt)?"
//
// This is read synchronously in MainActivity.onCreate — BEFORE Compose even
// starts drawing anything — using the same simple SharedPreferences pattern
// as ThemePreference.kt. That matters because it means there's no flicker
// where the Home screen flashes on screen for a frame before the onboarding
// walkthrough pops up on top of it; the app already knows which one to show
// from the very first frame.
// ─────────────────────────────────────────────────────────────────────────────
object OnboardingPreference {

    private const val PREFS_NAME = "blocker_onboarding_prefs"
    private const val KEY_DONE   = "onboarding_complete"

    /** True once the user has finished (or skipped) the walkthrough at least once. */
    fun isComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DONE, false)
    }

    /** Called once, the moment the user finishes or skips the walkthrough. */
    fun setComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, true)
            .apply()
    }
}
