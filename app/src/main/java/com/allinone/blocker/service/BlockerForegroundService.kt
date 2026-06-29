package com.allinone.blocker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.allinone.blocker.BlockerApp
import com.allinone.blocker.R
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Persistent foreground service (README 13) so the blocker survives the app
 * being closed or swiped away. The accessibility service does the real blocking
 * work; this keeps a visible, low-priority notification and a live process - and
 * now also ticks ScreenTimeTracker every 60s as a steady backup to the
 * instant-on-switch updates the accessibility service already triggers.
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
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_block)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
