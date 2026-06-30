package com.allinone.blocker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.allinone.blocker.R
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.ui.AlarmRingActivity

/**
 * Runs while the Strict Alarm is actively ringing. Plays the ringtone on a
 * loop, vibrates the phone, shows AlarmRingActivity full-screen (even over
 * the lock screen), and keeps itself alive as a foreground service so
 * Android doesn't kill it mid-ring.
 *
 * This service is stopped by AlarmRingActivity once the puzzle is solved
 * (see AlarmRingActivity.dismiss()).
 */
class AlarmRingingService : Service() {

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        startForeground(NOTIF_ID, buildNotification())
        startRinging()

        // Re-arm this alarm for the next occurrence right away — so it still
        // rings tomorrow (or next week) even if the user never gets to the
        // dismiss puzzle (e.g. phone dies, app is killed mid-ring).
        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        val alarm = BlockerRepository.strictAlarms.value.firstOrNull { it.id == alarmId }
        if (alarm != null) {
            AlarmScheduler.schedule(applicationContext, alarm)
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
            val pattern = longArrayOf(0, 800, 400) // wait, buzz, pause — repeats
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    /** Called by AlarmRingActivity once the dismiss puzzle is solved. */
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
            Intent(this, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
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
            setSound(null, null) // we play the ringtone ourselves via MediaPlayer
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 2001
        private const val ALARM_CHANNEL_ID = "alarm_ringing"

        // Lets AlarmRingActivity reach back into the running service instance
        // to stop the ringing once the puzzle is solved, without needing a
        // full bind/unbind dance for something this simple.
        @Volatile var activeInstance: AlarmRingingService? = null
    }
}
