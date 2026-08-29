package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import com.allinone.blocker.service.AccessibilityOffAlarmService
import com.allinone.blocker.ui.AccessibilityOffAlarmActivity
import com.allinone.blocker.ui.Permissions

// ─────────────────────────────────────────────────────────────────────────────
// AccessibilityWatchdog.kt
//
// PLAIN-ENGLISH SUMMARY:
// This is the "third layer" — separate from Lockdown mode and separate
// from Strict Mode. Those two only catch you in specific situations
// (Lockdown: only while a session is live. Strict Mode: only if you try
// to disable something from inside Present Tense's own screens). Neither
// one notices if you just walk straight into Android's Settings app on an
// ordinary day and flip the Accessibility switch off — which is exactly
// how that became a free, unaccounted-for habit.
//
// Android will not let ANY app actually block that switch (see the
// README) — so this doesn't try to. Instead it watches for the moment the
// switch gets flipped, and reacts to it immediately and loudly instead of
// letting it pass silently:
//   1. Marks today's streak broken — the same consequence you already get
//      for completing Strict Mode's in-app disable challenge, so going
//      around through Settings instead isn't a free pass.
//   2. Fires the Instant Off-Alarm — a full-screen, hard-to-miss alert
//      with sound and vibration (see AccessibilityOffAlarmService).
//
// This whole reaction is gated behind the "Ultra-Strict Layer" toggle (see
// UltraStrictMode.kt) — OFF by default, since firing a full-screen alarm
// every single time is too much for everyday use. checkForSilentDisable
// below still runs constantly regardless (cheap, harmless), it just does
// nothing while BlockerRepository.ultraStrict.value.enabled is false.
//
// The actual watching happens in AccessibilityWatchdogService (a
// ContentObserver on Android's own "which accessibility services are
// enabled" system setting). This file is just the small, testable piece
// that decides WHAT COUNTS as "you just turned it off" and what to do
// about it — kept separate so the detection plumbing and the decision
// logic don't get tangled together.
// ─────────────────────────────────────────────────────────────────────────────

object AccessibilityWatchdog {

    private const val PREFS = "accessibility_watchdog_prefs"
    private const val KEY_LAST_KNOWN_ENABLED = "last_known_enabled"

    // In-memory only, on purpose — same reasoning as
    // AlarmRingingService.activeInstance: this just needs to stop this
    // process from launching a second alarm on top of one that's already
    // showing, if the ContentObserver and the periodic self-check both
    // notice the same disable within moments of each other.
    @Volatile private var alarmCurrentlyShowing = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Call this the moment Present Tense's Accessibility Service is
     * confirmed connected — AppBlockerAccessibilityService.onServiceConnected()
     * is the one reliable "it's genuinely on right now" signal Android
     * gives us. This is what clears a running alarm — sound, vibration,
     * AND the full-screen Off-Alarm screen itself (see
     * AccessibilityOffAlarmActivity.closeIfShowing) — the instant the
     * permission is turned back on: the fastest way back to normal is
     * just re-enabling it, and this is how that gets noticed right away
     * instead of waiting for the next periodic check. Works even if that
     * screen is currently backgrounded (e.g. the user is over in
     * Settings right now flipping the switch).
     */
    fun recordEnabled(context: Context) {
        val appContext = context.applicationContext
        prefs(appContext).edit().putBoolean(KEY_LAST_KNOWN_ENABLED, true).apply()
        if (alarmCurrentlyShowing) {
            alarmCurrentlyShowing = false
            AccessibilityOffAlarmService.stop(appContext)
            AccessibilityOffAlarmActivity.closeIfShowing()
        }
    }

    /**
     * The core check. Compares Android's current Accessibility state
     * against what this last knew it to be:
     *
     *  - was ON, now OFF  → the user just switched it off in Settings.
     *    This is the one transition this whole file exists to catch.
     *  - was OFF, now ON  → re-enabled from outside our observation (e.g.
     *    the accessibility shortcut fired before onServiceConnected did)
     *    — resync silently, no alarm.
     *  - unchanged        → no-op besides resyncing the stored value.
     *
     * Safe to call as often as you like from as many places as you like —
     * the ContentObserver, the periodic self-check, app startup, boot —
     * it only ever actually acts on the specific ON→OFF edge, and
     * [alarmCurrentlyShowing] stops it from launching the alarm twice if
     * two callers notice the same transition close together.
     *
     * The very first time this ever runs on a device (key not written
     * yet), [wasEnabled] defaults to whatever [nowEnabled] already is —
     * so a fresh install, or the first time onboarding grants the
     * permission, can never itself look like a disable.
     */
    fun checkForSilentDisable(context: Context) {
        val appContext = context.applicationContext
        val nowEnabled = Permissions.hasAccessibility(appContext)
        val wasEnabled = prefs(appContext).getBoolean(KEY_LAST_KNOWN_ENABLED, nowEnabled)

        prefs(appContext).edit().putBoolean(KEY_LAST_KNOWN_ENABLED, nowEnabled).apply()

        if (!BlockerRepository.ultraStrict.value.enabled) {
            // Ultra-Strict Layer is off — this whole watcher is dormant.
            // [KEY_LAST_KNOWN_ENABLED] still gets kept in sync above while
            // it's off, so switching Ultra-Strict back ON later starts
            // fresh from whatever Accessibility's state is AT THAT MOMENT,
            // instead of treating everything that happened while it was
            // off as a violation the instant it's switched back on.
            if (alarmCurrentlyShowing) {
                alarmCurrentlyShowing = false
                AccessibilityOffAlarmService.stop(appContext)
                AccessibilityOffAlarmActivity.closeIfShowing()
            }
            return
        }

        if (wasEnabled && !nowEnabled) {
            if (!alarmCurrentlyShowing) {
                alarmCurrentlyShowing = true
                StreakRepository.recordSuccessfulDisable()
                AccessibilityOffAlarmService.start(appContext)
            }
        } else if (nowEnabled && alarmCurrentlyShowing) {
            // Belt-and-braces: caught the re-enable here instead of via
            // recordEnabled (e.g. a periodic check happened to run first).
            alarmCurrentlyShowing = false
            AccessibilityOffAlarmService.stop(appContext)
            AccessibilityOffAlarmActivity.closeIfShowing()
        }
    }
}
