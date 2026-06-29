package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.GeofenceManager
import com.allinone.blocker.data.ScreenTimeSyncWorker
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.service.BlockerForegroundService
import kotlin.concurrent.thread

/** Re-arms the background service after a reboot (README 12: RECEIVE_BOOT_COMPLETED). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        BlockerRepository.init(context)

        ScreenTimeSyncWorker.schedulePeriodic(context)
        thread { runCatching { ScreenTimeTracker.reconcile(context.applicationContext) } }

        // Android clears every AlarmManager alarm on reboot, so every
        // enabled Strict Alarm entry needs to be re-scheduled here, the
        // same way the foreground service below gets re-started.
        // SESSION 2 of the multi-alarm rework: reschedules the whole list.
        AlarmScheduler.scheduleAll(context, BlockerRepository.strictAlarms.value)

        // Android also clears every registered geofence on reboot, so
        // Location Lock zones need to be re-armed here too — otherwise
        // the lock silently stops working after every restart.
        GeofenceManager.rearm(context)

        if (BlockerRepository.protectionEnabled.value) {
            runCatching {
                context.startForegroundService(
                    Intent(context, BlockerForegroundService::class.java)
                )
            }
        }
    }
}
