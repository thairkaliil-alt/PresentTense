package com.allinone.blocker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.allinone.blocker.BlockerApp
import com.allinone.blocker.R
import com.allinone.blocker.data.AccessibilityWatchdog
import com.allinone.blocker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Third protection layer — runs independently of Lockdown mode and Strict
 * Mode, and independently of the Accessibility Service itself (it has to:
 * the whole point is noticing the moment THAT gets switched off).
 *
 * Lockdown mode already corrals you away from the Settings app entirely —
 * but only while a lockdown session is live. Strict Mode's PIN/cooldown/
 * etc. only guards actions taken inside Present Tense's own screens.
 * Neither one sees you if you just walk into Android's own Settings app
 * on an ordinary day and flip the Accessibility switch off — this service
 * is what closes that gap.
 *
 * It does NOT try to block that switch — Android deliberately doesn't let
 * any third-party app do that (see README 12). Instead it watches
 * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, the one system value
 * Android itself updates the moment ANY accessibility service is turned
 * on or off — via Settings, the accessibility shortcut, or ADB. Unlike
 * watching AppBlockerAccessibilityService's own onDestroy() directly, this
 * can't be fooled by Android simply killing that service's process for an
 * ordinary reason (memory pressure, a swipe from Recents) — those never
 * touch this setting, only a genuine enable/disable does.
 *
 * A slower 20s self-check loop runs alongside the observer as a second,
 * independent check — the same "two independent checkers" approach
 * BlockerForegroundService already uses for its own watchdog — so a
 * missed or coalesced Settings-change notification on some OEM builds
 * still gets caught within moments either way.
 *
 * Started from BlockerApp.onCreate() (so it's up whenever the app process
 * exists for any reason) and re-armed from BootReceiver (Android doesn't
 * auto-restart a plain foreground service across reboot the way it does
 * an enabled Accessibility Service).
 */
class AccessibilityWatchdogService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private var observer: ContentObserver? = null
    private var selfCheckLoopStarted = false

    override fun onCreate() {
        super.onCreate()
        registerObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        // Catches anything that changed while this exact service instance
        // wasn't alive yet to observe it live (e.g. the process was fully
        // dead and this very call is what's starting it back up).
        AccessibilityWatchdog.checkForSilentDisable(applicationContext)
        startSelfCheckLoopOnce()
        return START_STICKY
    }

    private fun registerObserver() {
        val handler = Handler(Looper.getMainLooper())
        val contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                AccessibilityWatchdog.checkForSilentDisable(applicationContext)
            }
        }
        observer = contentObserver
        runCatching {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                contentObserver
            )
        }
    }

    private fun startSelfCheckLoopOnce() {
        if (selfCheckLoopStarted) return
        selfCheckLoopStarted = true
        scope.launch {
            while (true) {
                delay(20_000L)
                runCatching { AccessibilityWatchdog.checkForSilentDisable(applicationContext) }
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
            .setContentTitle(getString(R.string.accessibility_watchdog_notif_title))
            .setContentText(getString(R.string.accessibility_watchdog_notif_text))
            .setSmallIcon(R.drawable.ic_block)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4001

        /** Starts (or is a harmless repeat call on) the watchdog. Safe to call anywhere, anytime. */
        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, AccessibilityWatchdogService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }
        }
    }
}
