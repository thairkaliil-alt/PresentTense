package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allinone.blocker.data.AccessibilityWatchdog
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.GeofenceManager
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownGuard
import com.allinone.blocker.data.LockdownScheduleAlarms
import com.allinone.blocker.data.ScreenTimeSyncWorker
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.service.AccessibilityWatchdogService
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

        // Blocking itself runs through the accessibility service, which
        // Android re-enables on its own after reboot (as long as
        // Accessibility permission was granted). But if a lockdown session
        // was already running across the reboot (e.g. an overnight
        // schedule), re-arm its notification + watchdog alarm right away
        // instead of waiting for the accessibility service to notice.
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        if (decision.active || decision.onBreak) {
            LockdownGuard.ensureRunning(context.applicationContext)
        }

        // Android clears every AlarmManager alarm on reboot, same as it
        // does for Strict Alarms above — so the "wake the device for the
        // next scheduled lockdown" alarm (see LockdownScheduleAlarms) needs
        // to be explicitly re-armed here too, not only relied on via
        // BlockerApp's own startup observer, so a scheduled lockdown can
        // never silently go quiet after a reboot.
        LockdownScheduleAlarms.rearm(context.applicationContext)

        // Third protection layer: BlockerApp.onCreate() already starts this
        // (Application.onCreate() always runs before any receiver in a
        // freshly-started process), so this is intentionally
        // belt-and-braces — same idiom as the re-arms above — rather than
        // strictly required. Explicit here anyway so the watchdog's
        // re-arm-after-reboot story doesn't silently depend on that
        // ordering guarantee never changing.
        AccessibilityWatchdogService.start(context)
        AccessibilityWatchdog.checkForSilentDisable(context)
    }
}
