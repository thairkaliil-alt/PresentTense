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
 * SESSION 2 of the multi-alarm rework: this file now schedules each
 * [StrictAlarmEntry] independently instead of one single alarm. Each entry
 * gets its own private block of request codes (10 slots each, for its
 * burst), built from a stable number derived from the entry's id, so two
 * different alarms never collide or cancel each other.
 */
object AlarmScheduler {

    /** Intent extra: which alarm entry this fired for. */
    const val EXTRA_ALARM_ID = "alarm_id"

    /** Intent extra: which slot (0..9) in that entry's burst just fired. */
    const val EXTRA_ALARM_INDEX = "alarm_index"

    /** True if Android will currently let us schedule an exact wake-up alarm. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true // pre-Android 12: always allowed
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * Opens the system settings page where the user grants the
     * "Alarms & reminders" permission. Only needed on Android 12+ if
     * [canScheduleExact] returns false.
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
     * those entries. Call this after init/load, and after any add/update/
     * remove/toggle on the alarm list.
     */
    fun scheduleAll(context: Context, alarms: List<StrictAlarmEntry>) {
        alarms.forEach { schedule(context, it) }
    }

    /**
     * Schedules (or re-schedules) every slot in one entry's next burst.
     * Safe to call repeatedly — replaces whatever was scheduled before for
     * THIS entry only; other entries are untouched.
     */
    fun schedule(context: Context, alarm: StrictAlarmEntry) {
        cancel(context, alarm.id)
        if (!alarm.enabled) return

        val count = alarm.effectiveAlarmCount()
        for (index in 0 until count) {
            scheduleIndex(context, alarm, index)
        }
    }

    /**
     * Re-arms a single slot after it fires. Each slot in a multi-alarm burst
     * repeats on its own offset (base time + index × 3 minutes).
     */
    fun scheduleIndex(context: Context, alarm: StrictAlarmEntry, index: Int) {
        val triggerAt = alarm.nextTriggerMillisForIndex(index)
        if (triggerAt == null) {
            cancelIndex(context, alarm.id, index)
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, alarm.id, index)

        val showIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(alarm.id, index) + SHOW_INTENT_OFFSET,
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

    /** Cancels every pending slot for every entry in [alarms]. */
    fun cancelAll(context: Context, alarms: List<StrictAlarmEntry>) {
        alarms.forEach { cancel(context, it.id) }
    }

    /** Cancels every pending slot for ONE entry (identified by [alarmId]). */
    fun cancel(context: Context, alarmId: String) {
        for (index in 0 until STRICT_ALARM_MAX_COUNT) {
            cancelIndex(context, alarmId, index)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // LEGACY single-alarm overloads.
    //
    // SleepCalculatorScreen.kt and StrictAlarmScreen.kt still use the old
    // single StrictAlarm object (not the new list) — they're scheduled to
    // be migrated to the new alarm list screen in a later session, not
    // this one. These overloads keep them compiling and working exactly as
    // before, by routing the old StrictAlarm through the same scheduling
    // code under one fixed, reserved id so it never collides with any real
    // entry in the new list.
    // ─────────────────────────────────────────────────────────────────────

    private const val LEGACY_SINGLE_ALARM_ID = "__legacy_single_strict_alarm__"

    /** Legacy overload for the old single-alarm screens. */
    fun schedule(context: Context, alarm: StrictAlarm) {
        schedule(context, alarm.toEntry())
    }

    /** Legacy overload for the old single-alarm screens. */
    fun scheduleIndex(context: Context, alarm: StrictAlarm, index: Int) {
        scheduleIndex(context, alarm.toEntry(), index)
    }

    /** Legacy overload for the old single-alarm screens. */
    fun cancel(context: Context) {
        cancel(context, LEGACY_SINGLE_ALARM_ID)
    }

    private fun StrictAlarm.toEntry(): StrictAlarmEntry = StrictAlarmEntry(
        id = LEGACY_SINGLE_ALARM_ID,
        enabled = enabled,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek,
        label = label,
        multiAlarmEnabled = multiAlarmEnabled,
        alarmCount = alarmCount
    )

    private fun cancelIndex(context: Context, alarmId: String, index: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(buildPendingIntent(context, alarmId, index)) }
    }

    private fun buildPendingIntent(context: Context, alarmId: String, index: Int): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_INDEX, index)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarmId, index),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Each alarm entry gets its own private block of 10 request codes (one
    // per possible burst slot), so scheduling/cancelling one alarm can never
    // collide with or wipe out another alarm's pending alarms. The "show
    // intent" pending intents use a second, far-away block (SHOW_INTENT_OFFSET)
    // so they never collide with the broadcast pending intents either.
    private const val REQUEST_CODE_BASE = 9001
    private const val SHOW_INTENT_OFFSET = 200_000

    private fun requestCodeFor(alarmId: String, index: Int): Int {
        // Stable, positive, well-spread integer from the id's hashCode.
        // Multiplying by 10 + adding the index reserves exactly 10 slots
        // per id with no overlap between different ids in practice.
        val idSlot = (alarmId.hashCode() and 0x7FFFFFFF) % 100_000
        return REQUEST_CODE_BASE + idSlot * 10 + index
    }
}
