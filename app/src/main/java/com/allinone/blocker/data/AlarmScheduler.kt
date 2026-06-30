package com.allinone.blocker.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.allinone.blocker.receiver.AlarmTriggerReceiver

/**
 * Talks to Android's built-in alarm-scheduling system (AlarmManager) on
 * behalf of every Strict Alarm entry in the list.
 *
 * Each entry uses its stored [StrictAlarmEntry.requestCode] as the
 * PendingIntent request code. Because requestCode is a small unique integer
 * assigned at creation time and saved to disk, two different alarms can
 * NEVER share a request code, so they can never cancel or overwrite each other.
 */
object AlarmScheduler {

    /** Intent extra: which alarm entry this fired for. */
    const val EXTRA_ALARM_ID = "alarm_id"

    /** True if Android will currently let us schedule an exact wake-up alarm. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * Opens the system settings page where the user grants the
     * "Alarms & reminders" permission. Only needed on Android 12+.
     */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Schedules (or re-schedules) every entry in [alarms]. Safe to call
     * repeatedly — each call fully replaces what was scheduled before for
     * those entries.
     */
    fun scheduleAll(context: Context, alarms: List<StrictAlarmEntry>) {
        alarms.forEach { schedule(context, it) }
    }

    /**
     * Schedules (or re-schedules) a single alarm entry.
     * Safe to call repeatedly — replaces whatever was scheduled before for
     * THIS entry only; other entries are untouched.
     */
    fun schedule(context: Context, alarm: StrictAlarmEntry) {
        cancel(context, alarm)
        if (!alarm.enabled) return

        val triggerAt = alarm.nextTriggerMillis() ?: return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, alarm)

        val showIntent = PendingIntent.getActivity(
            context,
            alarm.requestCode + SHOW_INTENT_OFFSET,
            Intent(context, com.allinone.blocker.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        runCatching {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                pendingIntent
            )
        }
    }

    /**
     * Schedules a list of brand-new, independent alarms one at a time, with
     * a short pause between each Android scheduling call. The pause only
     * affects how fast we REGISTER the alarms with the OS — each one still
     * rings at the exact time it was given.
     *
     * Spacing the calls avoids hammering AlarmManager with a burst of exact
     * alarm registrations at once, which can be flaky on some devices.
     *
     * Runs on a background coroutine so it doesn't block the UI thread.
     */
    suspend fun scheduleAllSpaced(
        context: Context,
        alarms: List<StrictAlarmEntry>,
        spacingMillis: Long = 300L
    ) {
        alarms.forEachIndexed { i, alarm ->
            schedule(context, alarm)
            if (i != alarms.lastIndex) kotlinx.coroutines.delay(spacingMillis)
        }
    }

    /** Cancels the pending alarm for every entry in [alarms]. */
    fun cancelAll(context: Context, alarms: List<StrictAlarmEntry>) {
        alarms.forEach { cancel(context, it) }
    }

    /** Cancels the pending alarm for ONE entry. */
    fun cancel(context: Context, alarm: StrictAlarmEntry) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(buildPendingIntent(context, alarm)) }
    }

    /**
     * Legacy cancel by id only — used when we only have the id (e.g. delete
     * from list screen before the entry object is available). Falls back to
     * the old hash approach for cancellation only. Prefer cancel(context, alarm)
     * when you have the full alarm object.
     */
    fun cancel(context: Context, alarmId: String) {
        val legacyBase = 9001 + ((alarmId.hashCode() and 0x7FFFFFFF) % 100_000)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        runCatching {
            am.cancel(
                PendingIntent.getBroadcast(
                    context,
                    legacyBase,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )
            )
        }
    }

    private fun buildPendingIntent(context: Context, alarm: StrictAlarmEntry): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // The show-intent is offset far away from the broadcast intent so they
    // can never share a request code even when requestCode is small (e.g. 1).
    private const val SHOW_INTENT_OFFSET = 200_000
}
