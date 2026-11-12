package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroAlerts.kt  —  What happens the moment a focus session or break ends
//
// WHY THIS FILE EXISTS
//   Before, a finished session produced one plain notification and a 20 ms
//   "tick" vibration that only fired if the Pomodoro screen happened to be
//   open. Forest / Session / Flow / Pomofocus all do the same three things at
//   the end of a session, and this file is the one place that does them here:
//
//     1. A chime you can actually hear (a different one for "focus done"
//        and "break over", so you know which one it is without looking).
//     2. A real vibration pattern, not a tick.
//     3. A notification that opens the app when tapped.
//
//   The chime is drawn with maths (sine waves) when it is played, so there
//   is no sound file to add to the project.
//
// WHY AN ALARM
//   The countdown on screen only runs while Android lets the app run. With
//   the screen off, Android can pause it. So the start of every session also
//   asks Android's AlarmManager to wake the app at the exact end time —
//   the same trick the Strict Alarm feature uses — and PomodoroAlarmReceiver
//   calls [fire] from there. If the app is awake and notices the end first,
//   it calls [fire] itself. [claim] makes sure only ONE of them ever plays.
//
// ULTRA FOCUS
//   Nothing here starts or stops a lockdown. It only announces the end.
// ═══════════════════════════════════════════════════════════════════════════

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.allinone.blocker.R
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.receiver.PomodoroAlarmReceiver
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

object PomodoroAlerts {

    /** Same file the PomodoroViewModel already uses for its settings. */
    const val PREFS_NAME = "pomodoro_prefs"
    const val KEY_SOUND = "alert_sound"
    const val KEY_VIBRATION = "alert_vibration"

    // Extras carried by the alarm so the receiver knows what to announce.
    const val EXTRA_ENDED_MODE = "pomodoro_ended_mode"
    const val EXTRA_TITLE = "pomodoro_title"
    const val EXTRA_TEXT = "pomodoro_text"
    const val EXTRA_ENDS_AT = "pomodoro_ends_at"

    private const val CHANNEL_ID = "pomodoro_alerts"
    // The old channel played Android's default ping and could not be changed
    // after creation, so it is removed and replaced by CHANNEL_ID.
    private const val OLD_CHANNEL_ID = "pomodoro_channel"
    private const val NOTIFICATION_ID = 1001
    private const val ALARM_REQUEST_CODE = 71001
    private const val KEY_LAST_ALERTED = "last_alerted_ends_at"
    private const val SAMPLE_RATE = 44100

    // ─────────────────────────────────────────────────────────────────────
    // BACKGROUND ALARM
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Asks Android to wake the app at [endsAtMillis] (wall-clock time) and
     * call [fire]. Calling this again replaces the previous alarm, which is
     * how a changed setting or a resumed timer updates it.
     */
    fun scheduleEnd(
        context: Context,
        endsAtMillis: Long,
        ended: PomodoroMode,
        title: String,
        text: String
    ) {
        val app = context.applicationContext
        val intent = Intent(app, PomodoroAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ENDED_MODE, ended.name)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_ENDS_AT, endsAtMillis)
        }
        val pending = PendingIntent.getBroadcast(
            app,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (AlarmScheduler.canScheduleExact(app)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
            } else {
                // Android hasn't allowed exact alarms: still wakes the phone,
                // just possibly a little late.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
            }
        }
    }

    /** Cancels the pending end-of-session alarm (pause, reset, skip, finished). */
    fun cancelEnd(context: Context) {
        val app = context.applicationContext
        runCatching {
            val pending = PendingIntent.getBroadcast(
                app,
                ALARM_REQUEST_CODE,
                Intent(app, PomodoroAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pending != null) {
                val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(pending)
                pending.cancel()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // THE ALERT ITSELF
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Announces that a session ended: notification + (if switched on) chime
     * and vibration. Safe to call from both the alarm and the running timer —
     * whoever gets here second for the same [endsAtMillis] does nothing.
     *
     * @return how long (ms) the caller should keep the app alive so the chime
     *         can finish; 0 if nothing was played.
     */
    fun fire(
        context: Context,
        ended: PomodoroMode,
        title: String,
        text: String,
        endsAtMillis: Long
    ): Long {
        val app = context.applicationContext
        if (!claim(app, endsAtMillis)) return 0L

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sound = prefs.getBoolean(KEY_SOUND, true)
        val vibration = prefs.getBoolean(KEY_VIBRATION, true)

        postNotification(app, title, text)
        return deliver(app, ended, sound, vibration)
    }

    /** Plays the alert without a notification — used by the "Test" buttons in settings. */
    fun preview(context: Context, ended: PomodoroMode, sound: Boolean, vibration: Boolean) {
        deliver(context.applicationContext, ended, sound, vibration)
    }

    private fun deliver(context: Context, ended: PomodoroMode, sound: Boolean, vibration: Boolean): Long {
        if (vibration) vibrate(context, ended)
        return if (sound) playChime(context, ended) else 0L
    }

    /** True the first time it is asked about a given end time, false after that. */
    private fun claim(context: Context, endsAtMillis: Long): Boolean {
        if (endsAtMillis <= 0L) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_LAST_ALERTED, 0L) == endsAtMillis) return false
        prefs.edit().putLong(KEY_LAST_ALERTED, endsAtMillis).apply()
        return true
    }

    // ─────────────────────────────────────────────────────────────────────
    // VIBRATION
    // ─────────────────────────────────────────────────────────────────────

    private fun vibrate(context: Context, ended: PomodoroMode) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        // [wait, buzz, wait, buzz, ...] in ms. Focus done = three firm pulses
        // (a "you did it"), break over = two shorter ones (a "heads up").
        val timings: LongArray
        val amplitudes: IntArray
        if (ended == PomodoroMode.FOCUS) {
            timings = longArrayOf(0, 180, 110, 180, 110, 420)
            amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        } else {
            timings = longArrayOf(0, 150, 100, 150)
            amplitudes = intArrayOf(0, 220, 0, 220)
        }
        runCatching {
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            // Tagged as an alarm-type vibration so Android still lets it
            // through while the app is in the background.
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attrs)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHIME  (drawn with sine waves — no sound file needed)
    // ─────────────────────────────────────────────────────────────────────

    private class Note(
        val freq: Double,
        val startSec: Double,
        val lengthSec: Double,
        val gain: Double
    )

    private fun notesFor(ended: PomodoroMode): List<Note> =
        if (ended == PomodoroMode.FOCUS) {
            // Rising four-note arpeggio: C5 - E5 - G5 - C6. Reads as "done!".
            listOf(
                Note(523.25, 0.00, 1.7, 1.0),
                Note(659.25, 0.20, 1.7, 1.0),
                Note(783.99, 0.40, 1.7, 1.0),
                Note(1046.50, 0.62, 1.8, 0.9)
            )
        } else {
            // Two-note "ding-dong" falling from E6 to C6. Reads as "heads up".
            listOf(
                Note(1318.51, 0.00, 1.2, 0.9),
                Note(1046.50, 0.35, 1.6, 1.0)
            )
        }

    private fun renderChime(notes: List<Note>): ShortArray {
        var totalSec = 0.0
        for (n in notes) {
            val end = n.startSec + n.lengthSec
            if (end > totalSec) totalSec = end
        }
        val total = (totalSec * SAMPLE_RATE).toInt() + 1
        val mix = FloatArray(total)

        for (n in notes) {
            val startIdx = (n.startSec * SAMPLE_RATE).toInt()
            val len = (n.lengthSec * SAMPLE_RATE).toInt()
            val w = 2.0 * PI * n.freq
            for (i in 0 until len) {
                val idx = startIdx + i
                if (idx >= total) break
                val t = i.toDouble() / SAMPLE_RATE
                // 12 ms fade-in and 30 ms fade-out so there is never a "click".
                val attack = minOf(1.0, t / 0.012)
                val release = minOf(1.0, (len - i).toDouble() / (SAMPLE_RATE * 0.03))
                // A soft bell: the note itself plus two quieter overtones that
                // die away faster, and a smooth overall fade.
                val body = sin(w * t) +
                    0.25 * sin(2.0 * w * t) * exp(-3.0 * t) +
                    0.08 * sin(3.0 * w * t) * exp(-5.0 * t)
                mix[idx] += (n.gain * attack * release * exp(-2.6 * t) * body).toFloat()
            }
        }

        // Scale so the loudest moment sits comfortably below clipping.
        var peak = 0f
        for (v in mix) {
            val a = abs(v)
            if (a > peak) peak = a
        }
        val scale = if (peak > 0f) 0.75f / peak else 0f
        val out = ShortArray(total)
        for (i in 0 until total) {
            out[i] = (mix[i] * scale * 32767f).toInt().toShort()
        }
        return out
    }

    /** Plays the chime and returns how long (ms) it needs to finish. */
    private fun playChime(context: Context, ended: PomodoroMode): Long {
        try {
            val samples = renderChime(notesFor(ended))
            val durationMs = samples.size * 1000L / SAMPLE_RATE + 400L

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Alarm volume: you hear it even if the phone is on
                        // silent / the media volume is down. It still obeys
                        // the Sound switch in Pomodoro settings.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { track.release() } },
                durationMs
            )
            return durationMs
        } catch (e: Exception) {
            // Something went wrong building the chime: fall back to Android's
            // own notification sound so the alert is never silent by accident.
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.play()
            }
            return 2500L
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOTIFICATION
    // ─────────────────────────────────────────────────────────────────────

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Tells you when a focus session or break ends"
            // The chime and vibration above are played by the app itself (so
            // the in-app switches control them), so the channel stays quiet
            // to avoid a double sound.
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun postNotification(context: Context, title: String, text: String) {
        runCatching {
            ensureChannel(context)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_block)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            if (launch != null) {
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        launch,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, builder.build())
        }
    }
}
