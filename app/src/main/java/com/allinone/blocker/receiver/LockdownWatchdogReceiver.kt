package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownGuard

/**
 * Fires roughly every 45 seconds while a lockdown session is live (armed by
 * [LockdownGuard]). This is the last line of defense: even if the whole
 * Present Tense process was killed — e.g. the user swiped it out of
 * Recent Apps — Android's AlarmManager still wakes this receiver up in a
 * fresh process, and it puts the lockdown screen straight back in front of
 * the user, exactly like the "Digital Detox" style kiosk apps do without
 * needing full Device Owner control.
 *
 * If the session has genuinely ended (time's up, or the whole schedule
 * window closed), this simply does nothing and does NOT reschedule itself,
 * so the tick loop naturally stops instead of running forever.
 */
class LockdownWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        if (!BlockerRepository.isInitialized) BlockerRepository.init(appContext)

        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        val sessionLive = decision.active || decision.onBreak
        if (!sessionLive) return // nothing to protect right now — let the chain stop here

        if (decision.active) {
            // Enforced right now (not on an emergency break) — pull the
            // lockdown screen back to the front in case the app got killed.
            LockdownGuard.relaunchLockdownScreen(appContext)
        }

        // Re-arm: keeps the notification alive and schedules the next tick.
        LockdownGuard.ensureRunning(appContext)
    }
}
