package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.allinone.blocker.ui.PomodoroAlerts
import com.allinone.blocker.ui.PomodoroMode

/**
 * Android calls this at the exact moment a Pomodoro focus session or break
 * is due to end — even if the screen is off or the app isn't on screen.
 * It only plays the end-of-session alert (chime + vibration + notification);
 * the timer on screen catches up on its own when the app is next opened.
 *
 * If the app was awake and already announced this same end time, [PomodoroAlerts.fire]
 * does nothing, so you never get a double chime.
 */
class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val ended = runCatching {
            PomodoroMode.valueOf(intent.getStringExtra(PomodoroAlerts.EXTRA_ENDED_MODE) ?: "")
        }.getOrNull() ?: return
        val title = intent.getStringExtra(PomodoroAlerts.EXTRA_TITLE) ?: "Pomodoro Timer"
        val text = intent.getStringExtra(PomodoroAlerts.EXTRA_TEXT) ?: ""
        val endsAt = intent.getLongExtra(PomodoroAlerts.EXTRA_ENDS_AT, 0L)

        // A receiver normally gets only a few seconds before Android may stop
        // it. goAsync() keeps it alive just long enough for the chime to finish.
        val pending = goAsync()
        val holdMs = PomodoroAlerts.fire(context.applicationContext, ended, title, text, endsAt)
        if (holdMs <= 0L) {
            pending.finish()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, holdMs)
        }
    }
}
