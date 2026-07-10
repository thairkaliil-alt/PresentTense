package com.allinone.blocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.GeofenceManager
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.ScreenTimeSyncWorker
import com.allinone.blocker.data.StreakRepository
import com.allinone.blocker.ui.InstalledApps

class BlockerApp : Application() {

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
