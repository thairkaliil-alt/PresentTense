package com.allinone.blocker.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
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
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.isOneTime
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.ui.AlarmRingActivity

class AlarmRingingService : Service() {

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this

        // TOP-PRIORITY FIX ("lockdown swallows the Strict Alarm ring
        // screen"): LockdownOverlay is a system overlay window painted on
        // top of literally every activity — including our own — see its
        // own header comment. With a lockdown session running (as it is for
        // most of an overnight schedule), that overlay was sitting on top
        // of AlarmRingActivity for the alarm's entire ring, which is why
        // the ring screen — puzzle or not — was invisible and untouchable,
        // and the only way in was digging out the alarm's notification (the
        // one thing this overlay can't cover, since Android draws it, not
        // us). Tearing the overlay down the instant the alarm starts fixes
        // that at the source. Safe to call unconditionally — a no-op when
        // no lockdown is running.
        //
        // This alone isn't quite enough, because several other places (the
        // accessibility service's live corral loop, the 45s watchdog,
        // MainActivity, the lockdown screen's own "go home" button) can all
        // try to bring the overlay back up moments later. LockdownOverlay
        // .show() and the corral's own decision function both now check
        // isAlarmCurrentlyRinging() (see companion object below) and refuse
        // to bring it back while this alarm is ringing, so none of them can
        // undo this.
        LockdownOverlay.hide()

        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)

        // FIX: alarmId now reaches the ring screen. Previously the ring
        // screen was never told which alarm fired, so it always fell back
        // to a generic label and — before this update — always ran the
        // same hard-coded challenge. Now it can look up this exact alarm's
        // label and its own Strict Mode / snooze settings.
        startForeground(NOTIF_ID, buildNotification(alarmId))
        startRinging()

        val alarm = BlockerRepository.strictAlarms.value.firstOrNull { it.id == alarmId }
        if (alarm != null) {
            if (alarm.isOneTime) {
                // A one-time alarm has now rung — it doesn't get a "next
                // day" rearm. Flip its own toggle off, same as stock
                // Android/Samsung Clock does the instant a non-repeating
                // alarm fires. The OS-level alarm itself was already a
                // one-shot registration, so there's nothing left to cancel.
                BlockerRepository.setStrictAlarmEntryEnabled(alarm.id, false)
            } else {
                AlarmScheduler.schedule(applicationContext, alarm)
            }
        }

        // KEY FIX: if the screen is already on and unlocked (user is actively
        // using the phone), the fullScreenIntent notification only shows as a
        // heads-up banner. We must directly startActivity() to force the alarm
        // screen to appear on top of whatever the user is doing.
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val screenIsOn = powerManager?.isInteractive == true
        val phoneIsUnlocked = keyguardManager?.isKeyguardLocked == false

        if (screenIsOn && phoneIsUnlocked) {
            val activityIntent = Intent(this, AlarmRingActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
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
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { /* No ringtone available — vibration below still runs. */ }

        runCatching {
            vibrator = getSystemService(Vibrator::class.java)
            val pattern = longArrayOf(0, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
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

        // The alarm is done ringing (dismissed, snoozed, or the service was
        // torn down some other way) — activeInstance is already cleared
        // above, so LockdownOverlay.show() below is no longer blocked by
        // isAlarmCurrentlyRinging(). If a lockdown session is still live,
        // put the lockdown screen straight back up ourselves right now,
        // instead of leaving a gap of up to ~3s (the accessibility
        // service's live tick) or ~45s (the watchdog) before anything else
        // notices and restores it on its own.
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        if (decision.active || decision.onBreak) {
            LockdownOverlay.show(applicationContext)
        }

        super.onDestroy()
    }

    private fun buildNotification(alarmId: String?): Notification {
        ensureAlarmChannel()

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmRingActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(getString(R.string.alarm_notif_title))
            .setContentText(getString(R.string.alarm_notif_text))
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
            getString(R.string.alarm_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_notif_text)
            setSound(null, null)
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 2001
        private const val ALARM_CHANNEL_ID = "alarm_ringing"
        @Volatile var activeInstance: AlarmRingingService? = null

        /**
         * True whenever a Strict Alarm is actively ringing right now. This
         * is the one signal every lockdown-enforcement code path checks so
         * it knows to leave the ring screen alone instead of covering it —
         * see [LockdownOverlay.show] and
         * [AppBlockerAccessibilityService.shouldCorralDuringLockdown].
         */
        fun isAlarmCurrentlyRinging(): Boolean = activeInstance != null
    }
}
