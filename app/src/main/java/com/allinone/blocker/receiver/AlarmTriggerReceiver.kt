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
 *
 * SESSION 2 of the multi-alarm rework: now also carries forward WHICH alarm
 * entry (by id) fired, not just which burst slot, since there can be
 * several independent alarms now.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        val index = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_INDEX, 0) ?: 0

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_INDEX, index)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
