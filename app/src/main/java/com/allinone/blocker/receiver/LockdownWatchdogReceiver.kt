package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownCompletionRepository
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

    companion object {
        // The live loop ticks every 3s while a session is live, so anything
        // healthy should update the heartbeat well within this window. Set
        // generously above that (rather than right at 3s) to absorb normal
        // scheduling jitter — e.g. brief CPU contention or Doze deferring the
        // loop by a few seconds — without mistaking a merely-slow tick for a
        // dead process.
        private const val HEARTBEAT_STALE_AFTER_MS = 20_000L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        if (!BlockerRepository.isInitialized) BlockerRepository.init(appContext)
        if (!LockdownCompletionRepository.isInitialized) LockdownCompletionRepository.init(appContext)

        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        val sessionLive = decision.active || decision.onBreak
        if (!sessionLive) {
            // The session is genuinely over (not merely paused for a
            // break) — this is one of several independent places that can
            // notice that; see LockdownCompletionRepository's header
            // comment for why this call is always safe to make here.
            LockdownCompletionRepository.recordCompletionIfNeeded()
            return // nothing to protect right now — let the chain stop here
        }

        if (decision.active) {
            // Enforced right now (not on an emergency break). BUGFIX: this
            // used to call relaunchLockdownScreen() unconditionally on every
            // tick (~45s), which meant it dragged the user out of a
            // whitelisted deep-focus app roughly every 45 seconds even
            // though nothing was wrong — this is the "whitelisted app keeps
            // auto-closing during lockdown" bug. This is only meant to be a
            // backstop for the rare case the whole process got killed (e.g.
            // swiped from Recent Apps); it should never fire while the real
            // enforcer (the accessibility service's live loop) is still up
            // and already doing its job correctly.
            //
            // So instead of independently re-checking what's on screen (an
            // extra system query every tick), we just ask: is that loop
            // still alive? It writes a heartbeat every ~3s while a session
            // is live (see AppBlockerAccessibilityService.tickLockdownGuard).
            // If the heartbeat is recent, the enforcer is alive and already
            // exempting whitelisted apps correctly — nothing to do here. If
            // it's gone stale, the process was genuinely killed and this is
            // the one case this backstop exists for.
            val heartbeatAge = System.currentTimeMillis() - BlockerRepository.lastLockdownHeartbeatAt()
            val enforcerAlive = heartbeatAge in 0 until HEARTBEAT_STALE_AFTER_MS
            if (!enforcerAlive) {
                LockdownGuard.relaunchLockdownScreen(appContext)
            }
        }

        // Re-arm: keeps the notification alive and schedules the next tick.
        LockdownGuard.ensureRunning(appContext)
    }
}
