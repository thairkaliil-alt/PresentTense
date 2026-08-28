package com.allinone.blocker.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.allinone.blocker.R
import com.allinone.blocker.ui.AccessibilityOffAlarmActivity

/**
 * The Instant Off-Alarm itself — started by AccessibilityWatchdog the
 * moment it notices Present Tense's Accessibility permission was switched
 * off in Settings. Deliberately mirrors AlarmRingingService's proven
 * attention-grabbing pattern (full-screen intent + alarm-stream sound +
 * vibration + direct startActivity fallback for an already-unlocked
 * screen) instead of reinventing it, since that pattern is already tested
 * and working for Strict Alarm.
 *
 * Two differences from Strict Alarm on purpose:
 *  - The sound plays once, not on a loop — this is a notice, not a
 *    wake-up call that needs to be actively fought off.
 *  - It's silenced automatically the instant Accessibility is confirmed
 *    back on (see AccessibilityWatchdog.recordEnabled) — there's no
 *    puzzle to solve, because the actual "off switch" here is Android's
 *    own Settings screen, not anything inside this app.
 */
class AccessibilityOffAlarmService : Service() {

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this

        startForeground(NOTIF_ID, buildNotification())
        startRinging()

        // Same reasoning as AlarmRingingService: a fullScreenIntent
        // notification only shows as a heads-up banner if the screen is
        // already on and unlocked — startActivity() directly is needed to
        // actually take over the screen in that case.
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val screenIsOn = powerManager?.isInteractive == true
        val phoneIsUnlocked = keyguardManager?.isKeyguardLocked == false

        if (screenIsOn && phoneIsUnlocked) {
            val activityIntent = Intent(this, AccessibilityOffAlarmActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            startActivity(activityIntent)
        }

        return START_NOT_STICKY
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = false
                prepare()
                start()
            }
        }.onFailure { /* No ringtone available — vibration below still runs. */ }

        runCatching {
            vibrator = getSystemService(Vibrator::class.java)
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        }
    }

    fun stopRinging() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        runCatching { vibrator?.cancel() }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        ensureAlarmChannel()

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AccessibilityOffAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(getString(R.string.accessibility_alarm_notif_title))
            .setContentText(getString(R.string.accessibility_alarm_notif_text))
            .setSmallIcon(R.drawable.ic_block)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureAlarmChannel() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (nm.getNotificationChannel(ALARM_CHANNEL_ID) != null) return
        val channel = android.app.NotificationChannel(
            ALARM_CHANNEL_ID,
            getString(R.string.accessibility_alarm_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.accessibility_alarm_notif_text)
            setSound(null, null)
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 3001
        private const val ALARM_CHANNEL_ID = "accessibility_off_alarm"
        @Volatile var activeInstance: AccessibilityOffAlarmService? = null

        /** Starts the alarm — safe to call even if it's already ringing. */
        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, AccessibilityOffAlarmService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }
        }

        /** Stops the alarm if it's currently ringing — no-op otherwise. */
        fun stop(context: Context) {
            activeInstance?.stopRinging()
        }
    }
}
