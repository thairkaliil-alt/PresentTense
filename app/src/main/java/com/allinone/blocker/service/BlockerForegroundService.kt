package com.allinone.blocker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.allinone.blocker.BlockerApp
import com.allinone.blocker.R
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownGuard
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Persistent foreground service, started only while a lockdown session is
 * live (see LockdownGuard). It does three things:
 *  1. Shows a visible, low-priority "Lockdown active" notification. Having a
 *     real foreground notification makes Android — and phone-maker battery
 *     managers / "clean up recent apps" features — much less eager to kill
 *     the process than a bare background app would be.
 *  2. Ticks ScreenTimeTracker every 60s as a steady backup to the
 *     instant-on-switch updates the accessibility service already triggers.
 *  3. Fights back the instant it's swiped out of Recent Apps: see
 *     onTaskRemoved() below.
 */
class BlockerForegroundService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private var tickLoopStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(BlockerApp.NOTIF_ID, buildNotification())
        startTickLoopOnce()
        return START_STICKY
    }

    private fun startTickLoopOnce() {
        if (tickLoopStarted) return
        tickLoopStarted = true
        scope.launch {
            while (true) {
                runCatching { ScreenTimeTracker.reconcile(applicationContext) }
                delay(60_000L)
            }
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BlockerApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_lockdown_title))
            .setContentText(getString(R.string.notif_lockdown_text))
            .setSmallIcon(R.drawable.ic_block)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Called the instant the user swipes Present Tense out of Recent Apps
     * (on most devices this is also what happens when a phone's "clean up
     * background apps" feature decides to close it). If a lockdown session
     * is still supposed to be running, fight back immediately:
     *  - Try to bring the lockdown screen straight back to the front, in
     *    case this process survives a little longer.
     *  - Re-arm the watchdog alarm, so that even if Android finishes killing
     *    this process a moment later, LockdownWatchdogReceiver will wake up
     *    in a brand-new process shortly after and restore the lockdown
     *    screen anyway.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        if (decision.active) {
            LockdownGuard.relaunchLockdownScreen(applicationContext)
        }
        if (decision.active || decision.onBreak) {
            LockdownGuard.armWatchdog(applicationContext)
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
