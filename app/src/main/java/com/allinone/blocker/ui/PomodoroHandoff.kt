package com.allinone.blocker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroHandoff.kt  —  the "what's next?" note between two screens
//
// Why this file exists:
//   When an Ultra Focus session ends, the app shows the full-screen
//   "Session complete" celebration (LockdownCompletionScreen). That screen
//   used to have only a "Done" button, so you still had to go back to the
//   timer and find the Start button for your break.
//
//   This tiny object is a shared note board. The Pomodoro timer pins a note
//   on it when a locked focus session finishes ("a short break is up next").
//   The celebration screen reads the note and shows ONE button — "Start
//   short break" — that starts the break straight away.
//
// It holds no timer logic itself. Starting the break is still done by
// PomodoroViewModel.startTimer(); this object only remembers who to call.
// ═══════════════════════════════════════════════════════════════════════════
object PomodoroHandoff {

    /** The note: what just ended, what is waiting, and when the note was written. */
    data class Pending(
        val endedMode: PomodoroMode,
        val nextMode: PomodoroMode,
        val nextMinutes: Int,
        val createdAtMillis: Long
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending

    // "Who do I call to start the next session?" — set by the timer.
    @Volatile
    private var starter: (() -> Unit)? = null

    fun publish(note: Pending) {
        _pending.value = note
    }

    fun clear() {
        _pending.value = null
    }

    fun register(start: () -> Unit) {
        starter = start
    }

    fun unregister() {
        starter = null
    }

    /** Starts the waiting session. Returns false if the timer isn't available. */
    fun startNext(): Boolean {
        val start = starter ?: return false
        start()
        return true
    }
}
