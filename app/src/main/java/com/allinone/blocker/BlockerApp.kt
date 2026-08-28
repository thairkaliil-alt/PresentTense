package com.allinone.blocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.allinone.blocker.data.AccessibilityWatchdog
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.GeofenceManager
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.LockdownScheduleAlarms
import com.allinone.blocker.data.ScreenTimeSyncWorker
import com.allinone.blocker.data.StreakRepository
import com.allinone.blocker.service.AccessibilityWatchdogService
import com.allinone.blocker.ui.InstalledApps
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BlockerApp : Application() {

    // Isolated, app-lifetime background scope. SupervisorJob so a failure
    // in one task here can never cancel any other; the exception handler
    // logs instead of letting a hiccup crash the whole app — same pattern
    // as AppBlockerAccessibilityService.ioScope, for the same reason.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e("BlockerApp", "Background task failed (isolated, not fatal)", throwable)
        }
    )

    override fun onCreate() {
        super.onCreate()
        BlockerRepository.init(this)

        // Lockdown completion tracking (the session-end celebration) — no
        // dependency on the other repositories, but initialised right after
        // BlockerRepository since LockdownEngine.evaluate() can call into it
        // as a side effect (marking a scheduled session's start) from
        // essentially anywhere in the app.
        LockdownCompletionRepository.init(this)

        // Initialise the streak system. This must come AFTER BlockerRepository
        // because StreakRepository reads the daily goal from it, and AFTER
        // the app has a Context so it can open its own SharedPreferences file.
        StreakRepository.init(this)

        createNotificationChannel()

        // Third protection layer: starts the always-on watchdog that
        // notices the instant Accessibility gets switched off in Settings
        // — independent of Lockdown mode and Strict Mode, see
        // AccessibilityWatchdogService's own header comment for the full
        // reasoning. checkForSilentDisable() also runs right here, so the
        // case where Accessibility was switched off while this app's whole
        // process was dead (force-stopped, or across a reboot before this
        // line ran) gets caught the instant the process exists again, not
        // just whenever the service itself gets around to its own check.
        AccessibilityWatchdogService.start(this)
        AccessibilityWatchdog.checkForSilentDisable(this)

        ScreenTimeSyncWorker.schedulePeriodic(this)

        // Geofences also get cleared if the OS kills the app's process to
        // save battery (common on some phones), not just on a full reboot —
        // so re-arm them here too, not only in BootReceiver. This is cheap
        // and a no-op if there are no zones configured.
        GeofenceManager.rearm(this)

        // Kick off the installed-apps scan now, in the background, so the
        // "Add app" / "Whitelist" / Stats screens almost never have to wait
        // on it later - see InstalledApps.kt for why this matters.
        InstalledApps.ensureLoaded(this)

        // BUGFIX ("scheduled lockdown doesn't start on its own — only
        // kicks in once I open the app"): keeps the "wake the device for
        // the next scheduled lockdown" alarm (see LockdownScheduleAlarms)
        // always pointed at the right moment. Any time a schedule is
        // added, edited, removed, or toggled; a manual lockdown is started
        // or stopped; or an emergency break starts or ends — this
        // re-arms the alarm automatically, from one single place, instead
        // of relying on every screen that touches those to remember to do
        // it itself. combine()'s first emission (using whatever was just
        // loaded from disk above) also covers "re-arm on every app start",
        // so nothing extra is needed for that case.
        appScope.launch {
            combine(
                BlockerRepository.schedules,
                BlockerRepository.manualLockUntil,
                BlockerRepository.breakUntil
            ) { _, _, _ -> Unit }.collect {
                LockdownScheduleAlarms.rearm(this@BlockerApp)
            }
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_text)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "blocking_active"
        const val NOTIF_ID = 1001
    }
}
