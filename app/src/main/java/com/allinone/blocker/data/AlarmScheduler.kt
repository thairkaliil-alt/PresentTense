package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.service.AlarmRingingService

/**
 * This is what Android calls the instant a scheduled alarm time arrives —
 * even if the phone was asleep or our app wasn't running. It does almost
 * nothing itself: it just hands off to AlarmRingingService, because a
 * BroadcastReceiver only gets a few seconds to run before Android kills it,
 * which isn't enough time to play a ringtone, vibrate, and wait for the
 * user to solve a puzzle.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
