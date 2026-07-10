package com.allinone.blocker.receiver

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
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
            // So first we ask: is that loop still alive? It writes a
            // heartbeat every ~3s while a session is live (see
            // AppBlockerAccessibilityService.tickLockdownGuard). If the
            // heartbeat is recent, the enforcer is alive and already
            // exempting whitelisted apps correctly — nothing to do here.
            val heartbeatAge = System.currentTimeMillis() - BlockerRepository.lastLockdownHeartbeatAt()
            val enforcerAlive = heartbeatAge in 0 until HEARTBEAT_STALE_AFTER_MS

            // SECOND BUGFIX ("whitelisted app STILL kept closing every ~45s
            // even after the heartbeat check above was added"): this used
            // to call relaunchLockdownScreen() unconditionally the moment
            // the heartbeat looked stale — on the assumption that a dead
            // enforcer automatically meant something dangerous was on
            // screen. That assumption was wrong. On plenty of phones
            // (this is a very common, well-known behavior on several
            // Android makers' battery managers — Xiaomi/MIUI, Oppo/ColorOS,
            // Huawei/EMUI, and others), the OS aggressively kills
            // background accessibility services on a short cycle
            // REGARDLESS of what's on screen — including while you're
            // sitting safely inside a whitelisted app doing nothing wrong.
            // Every time that happened, this watchdog "recovered" by
            // yanking you out of the very app you were allowed to be in.
            //
            // The actual fix: before forcing the lockdown screen back,
            // check what's actually on screen right now. Only step in if
            // it's genuinely something that shouldn't be there.
            if (!enforcerAlive && !isCurrentForegroundAppSafe(appContext)) {
                LockdownGuard.relaunchLockdownScreen(appContext)
            }
        }

        // Re-arm: keeps the notification alive and schedules the next tick.
        LockdownGuard.ensureRunning(appContext)
    }

    /**
     * True if whatever app is actually on screen right now is one that's
     * allowed during lockdown (our own app, the whitelist, or the
     * always-exempt phone/SMS apps) — meaning the watchdog has nothing to
     * fix and should leave the user alone. False (the fail-safe default)
     * if we can't tell, since it's safer to briefly show the lockdown
     * screen unnecessarily than to risk leaving a real escape open.
     *
     * This receiver has no accessibility node access of its own (only the
     * live AppBlockerAccessibilityService does), so instead this reads the
     * same usage-event log ScreenTimeTracker uses: the last "moved to
     * foreground" event with no matching "moved to background" after it
     * is, by definition, whatever's on screen right now.
     */
    private fun isCurrentForegroundAppSafe(context: Context): Boolean {
        val pkg = currentForegroundPackage(context) ?: return false
        if (pkg == context.packageName) return true // our own app (e.g. the lockdown screen itself)
        if (LockdownEngine.isSystemSettingsPackage(pkg)) return false // never exempt, whitelist or not
        return BlockerRepository.isWhitelisted(pkg) || LockdownEngine.isAlwaysExempt(context, pkg)
    }

    /** @return the package currently in the foreground, or null if it can't be determined. */
    private fun currentForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        // A generous look-back window (comfortably wider than the 3s live
        // loop and the ~45s watchdog tick) so a brief gap in logged events
        // can't make this falsely come back empty.
        val events = runCatching { usm.queryEvents(now - 60_000L, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var lastForeground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            val isPause = event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
            if (isResume) {
                lastForeground = event.packageName
            } else if (isPause && event.packageName == lastForeground) {
                lastForeground = null
            }
        }
        return lastForeground
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
