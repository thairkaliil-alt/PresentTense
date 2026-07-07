package com.allinone.blocker.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.allinone.blocker.receiver.LockdownWatchdogReceiver
import com.allinone.blocker.service.BlockerForegroundService
import com.allinone.blocker.ui.LockdownLauncherActivity

/**
 * Keeps a lockdown session alive even if Android — or the user, by swiping
 * Present Tense out of Recent Apps — kills the app's process.
 *
 * Two independent safety nets sit on top of the normal, instant
 * accessibility-service redirect (see AppBlockerAccessibilityService):
 *
 * 1. A low-priority "Lockdown active" notification (BlockerForegroundService).
 *    This tells Android the app is doing something real *right now*, which
 *    makes battery managers and phone-maker "clean up recent apps" features
 *    much less eager to kill the process — the same trick real digital-detox
 *    apps use. It also gets a chance to fight back the instant it's swiped
 *    away, via onTaskRemoved().
 *
 * 2. A watchdog alarm (AlarmManager) that wakes up roughly every 45 seconds
 *    while a session is live. If the app got killed anyway, this still
 *    fires — AlarmManager starts a brand-new process just for it — and it
 *    puts the lockdown screen straight back in front of the user.
 *
 * Both are driven from one place (this object) so there's a single on/off
 * switch: call [ensureRunning] whenever a lockdown session is confirmed
 * live, and [ensureStopped] the moment it's confirmed over.
 */
object LockdownGuard {

    private const val WATCHDOG_REQUEST_CODE = 71_001
    private const val WATCHDOG_TICK_MS = 45_000L

    /**
     * Starts (or keeps alive) the notification + watchdog alarm. Safe to call
     * repeatedly — this is called every ~3s while a session is live, so it
     * only actually talks to Android when the service isn't already up
     * (tracked via [BlockerForegroundService.isRunning]), instead of
     * re-issuing startForegroundService() and rebuilding the notification on
     * every single tick.
     */
    fun ensureRunning(context: Context) {
        val appContext = context.applicationContext
        if (!BlockerForegroundService.isRunning()) {
            val serviceIntent = Intent(appContext, BlockerForegroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }
            }
        }
        armWatchdog(appContext)
    }

    /**
     * Stops the notification and cancels the watchdog alarm. Call once a
     * session is truly over. Skips the stopService() call (and its binder
     * round-trip) when the service isn't running, since this gets called on
     * every idle tick of the background loop, not just on the one real
     * transition from "running" to "stopped".
     */
    fun ensureStopped(context: Context) {
        val appContext = context.applicationContext
        if (BlockerForegroundService.isRunning()) {
            runCatching {
                appContext.stopService(Intent(appContext, BlockerForegroundService::class.java))
            }
        }
        disarmWatchdog(appContext)
    }

    /** Brings the lockdown screen back to the front. Used by the watchdog and onTaskRemoved. */
    fun relaunchLockdownScreen(context: Context) {
        LockdownLauncherActivity.launch(context)
    }

    /** Schedules the next watchdog tick roughly [WATCHDOG_TICK_MS] from now. */
    fun armWatchdog(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_TICK_MS,
                watchdogPendingIntent(appContext)
            )
        }
    }

    /** Cancels any pending watchdog tick. */
    fun disarmWatchdog(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(watchdogPendingIntent(appContext)) }
    }

    private fun watchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LockdownWatchdogReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
