package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownGuard
import com.allinone.blocker.data.LockdownScheduleAlarms

/**
 * Fires from the exact AlarmManager wake-up [LockdownScheduleAlarms] arms
 * for the precise moment a scheduled (or manual) lockdown session is due
 * to start, end, or resume after a break — even if the phone is asleep and
 * Present Tense isn't running at all.
 *
 * This is what makes a scheduled lockdown ("lock every night 11pm–7am")
 * actually START ON ITS OWN at 11pm, the same way an alarm clock rings on
 * its own — instead of only ever being noticed the next time someone
 * opens the app or unlocks the phone. See LockdownScheduleAlarms' header
 * comment for the full story of the bug this closes.
 */
class LockdownScheduleTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        if (!BlockerRepository.isInitialized) BlockerRepository.init(appContext)
        if (!LockdownCompletionRepository.isInitialized) LockdownCompletionRepository.init(appContext)

        // Delegate the "is a session live right now, and if so is whatever
        // is on screen safe to leave alone" decision to the exact same,
        // already battle-tested logic the 45-second watchdog uses (see
        // LockdownWatchdogReceiver's own BUGFIX notes about whitelisted
        // apps) rather than duplicating it here. This starts the
        // "Lockdown active" notification + 45s watchdog the instant a
        // schedule's window opens, and brings the lockdown screen up
        // immediately if something unsafe is in the foreground.
        LockdownWatchdogReceiver().onReceive(appContext, intent)

        // The watchdog above only handles the LIVE case — by design it
        // does nothing when a session has just ended (see its own comment:
        // by the time it would fire in that state, the accessibility
        // service's loop has normally already torn things down itself).
        // But THIS alarm can also fire for the very first time right as a
        // schedule's window CLOSES, while the phone was asleep the whole
        // time — so, unlike the watchdog, make sure that end is actually
        // recorded and cleaned up too.
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        if (!decision.active && !decision.onBreak) {
            LockdownCompletionRepository.recordCompletionIfNeeded()
            LockdownGuard.ensureStopped(appContext)
        }

        // Whatever just happened, work out the next moment anything will
        // change and sleep exactly until then — this is what keeps the
        // whole chain going indefinitely, on its own, with no polling.
        LockdownScheduleAlarms.rearm(appContext)
    }
}
