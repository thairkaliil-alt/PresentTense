package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroNotification.kt  —  The notification you see WHILE a session runs
//
// WHY THIS FILE EXISTS
//   PomodoroAlerts.kt announces the moment a session ENDS. Before this file
//   there was nothing on the notification shade while a session was running
//   (apart from the generic "Lockdown is active" one during Ultra Focus). Now
//   every focus session and every break has its own live notification:
//
//       collapsed:   24:31                      FOCUS
//                                        Session 2 of 4
//
//       expanded:    FOCUS                Session 2 of 4
//                    24:31
//                    Ends at 14:25               ● ◉ ○ ○
//
// HOW THE COUNTDOWN WORKS
//   The time is drawn by Android's own Chronometer (a ticking clock widget).
//   We only tell it WHEN the session ends, once. After that the phone counts
//   down by itself, to the second — even if the app is closed, and without
//   using any battery from us. So the notification is only posted when a
//   session starts / resumes / is restored, and removed when it stops.
//
// WHO CALLS WHAT
//   • PomodoroViewModel.scheduleEndAlarm()  → [show]   (session is running)
//   • PomodoroViewModel.clearRunSession()   → [cancel] (pause, reset, skip, done)
//   • PomodoroAlerts.fire()                 → [cancel] (the alarm ended it while
//                                                       the app was closed)
//
// WHAT IT DELIBERATELY DOES NOT HAVE
//   No Pause / Skip / Stop buttons. A button on the notification would be a
//   way around Strict Mode and Ultra Focus (which must not be pausable).
//   Tapping the notification just opens the app.
//
// SAFETY NET
//   [show] also tells Android to remove the notification 2 minutes after the
//   session should have ended, so it can never get stuck on screen.
// ═══════════════════════════════════════════════════════════════════════════

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.allinone.blocker.R
import java.util.Date

object PomodoroNotification {

    private const val CHANNEL_ID = "pomodoro_session"

    // Must be different from every other notification id in the app
    // (1001 lockdown, 2001 alarm, 3001 / 4001 watchdogs, 5001 session-ended).
    private const val NOTIFICATION_ID = 5002

    // How long after the expected end Android should clean the notification
    // up by itself, in case nothing else did.
    private const val SAFETY_MARGIN_MS = 2 * 60_000L

    // Above this many sessions per round the dots would be too wide to show.
    private const val MAX_DOTS = 8

    /**
     * Shows (or refreshes) the live notification for a running session.
     *
     * @param endsAtMillis    wall-clock time the session ends
     * @param cycleCount      the ViewModel's round counter (sessions finished)
     * @param sessionsPerRound "sessions until long break" from the settings
     * @param locked          true during an Ultra Focus (locked) session
     */
    fun show(
        context: Context,
        mode: PomodoroMode,
        endsAtMillis: Long,
        cycleCount: Int,
        sessionsPerRound: Int,
        locked: Boolean
    ) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (endsAtMillis <= now) {
            cancel(app)
            return
        }

        runCatching {
            ensureChannel(app)

            val per = sessionsPerRound.coerceAtLeast(1)
            val accent = accentFor(mode, isNight(app))
            val label = labelFor(mode, locked)
            val detail = detailFor(mode, cycleCount, per)
            val dots = dotsFor(mode, cycleCount, per)
            val endsText = "Ends at " + DateFormat.getTimeFormat(app).format(Date(endsAtMillis))

            // The Chronometer counts against the phone's "time since boot"
            // clock, so convert the end time to that clock. The extra 999 ms
            // makes it round UP, exactly like the timer inside the app, so
            // the two always show the same second.
            val base = SystemClock.elapsedRealtime() + (endsAtMillis - now) + 999L

            val collapsed = RemoteViews(app.packageName, R.layout.notification_pomodoro_collapsed)
            bindCommon(collapsed, base, label, detail, accent)

            val expanded = RemoteViews(app.packageName, R.layout.notification_pomodoro_expanded)
            bindCommon(expanded, base, label, detail, accent)
            expanded.setTextViewText(R.id.pomo_ends, endsText)
            expanded.setTextViewText(R.id.pomo_dots, dots)
            expanded.setTextColor(R.id.pomo_dots, accent)

            val builder = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif_pomodoro)
                .setColor(accent)
                // Title / text are not drawn (the custom views are), but they
                // are what screen readers and smart watches use.
                .setContentTitle(label)
                .setContentText(detail)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setLocalOnly(true)
                .setTimeoutAfter((endsAtMillis - now) + SAFETY_MARGIN_MS)

            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launch != null) {
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        app,
                        0,
                        launch,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }

            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, builder.build())
        }
    }

    /** Removes the notification. Safe to call any time, even if none is showing. */
    fun cancel(context: Context) {
        runCatching {
            val nm = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // FILLING IN THE LAYOUT
    // ─────────────────────────────────────────────────────────────────────

    /** The parts both layouts share: the ticking countdown, the label and the detail line. */
    private fun bindCommon(
        views: RemoteViews,
        chronometerBase: Long,
        label: String,
        detail: String,
        accent: Int
    ) {
        // true = count DOWN (24:31 → 24:30 …) instead of up.
        views.setChronometerCountDown(R.id.pomo_time, true)
        views.setChronometer(R.id.pomo_time, chronometerBase, null, true)
        views.setTextViewText(R.id.pomo_label, label)
        views.setTextColor(R.id.pomo_label, accent)
        views.setTextViewText(R.id.pomo_detail, detail)
    }

    // ─────────────────────────────────────────────────────────────────────
    // WORDS, COLOURS AND DOTS
    // ─────────────────────────────────────────────────────────────────────

    private fun labelFor(mode: PomodoroMode, locked: Boolean): String = when (mode) {
        PomodoroMode.FOCUS -> if (locked) "FOCUS · LOCKED" else "FOCUS"
        PomodoroMode.SHORT_BREAK -> "SHORT BREAK"
        PomodoroMode.LONG_BREAK -> "LONG BREAK"
    }

    /**
     * How many sessions of the current round are finished. The ViewModel's
     * round counter keeps counting up (4, 5, 6 …), so wrap it with %.
     * Right after the 4th session (counter 4, 8 …) the round is full, not empty.
     */
    private fun finishedInRound(mode: PomodoroMode, cycleCount: Int, per: Int): Int {
        val count = cycleCount.coerceAtLeast(0)
        val inRound = count % per
        return when {
            mode == PomodoroMode.FOCUS -> inRound
            count > 0 && inRound == 0 -> per
            else -> inRound
        }
    }

    private fun detailFor(mode: PomodoroMode, cycleCount: Int, per: Int): String {
        val finished = finishedInRound(mode, cycleCount, per)
        return when (mode) {
            PomodoroMode.FOCUS -> "Session ${finished + 1} of $per"
            PomodoroMode.SHORT_BREAK -> "$finished of $per done"
            PomodoroMode.LONG_BREAK -> "Round complete"
        }
    }

    /** ● finished, ◉ the one you are in now, ○ still to come. */
    private fun dotsFor(mode: PomodoroMode, cycleCount: Int, per: Int): String {
        if (per > MAX_DOTS) return ""
        val finished = finishedInRound(mode, cycleCount, per)
        return (1..per).joinToString(" ") { n ->
            when {
                n <= finished -> "●"
                mode == PomodoroMode.FOCUS && n == finished + 1 -> "◉"
                else -> "○"
            }
        }
    }

    /**
     * The same three phase colours as the Pomodoro screen (blue / teal / amber).
     * In light mode they are a little darker so the small label stays readable
     * on a white notification.
     */
    private fun accentFor(mode: PomodoroMode, night: Boolean): Int = when (mode) {
        PomodoroMode.FOCUS -> if (night) 0xFF5C9CC4.toInt() else 0xFF2F6F96.toInt()
        PomodoroMode.SHORT_BREAK -> if (night) 0xFF2DB8A0.toInt() else 0xFF1B7F6D.toInt()
        PomodoroMode.LONG_BREAK -> if (night) 0xFFF4A33C.toInt() else 0xFFB26A00.toInt()
    }

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ─────────────────────────────────────────────────────────────────────
    // CHANNEL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * A quiet channel of its own (low importance = no sound, no pop-up), so
     * you can switch this notification off in Android settings without
     * touching the "session finished" alerts.
     */
    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live countdown while a focus session or break is running"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }
}
