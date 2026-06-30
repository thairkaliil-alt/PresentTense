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
 * Each entry uses its stored [StrictAlarmEntry.requestCode] as the base for
 * its PendingIntent request codes (10 slots, one per burst index). Because
 * requestCode is a small unique integer assigned at creation time and saved
 * to disk, two different alarms can NEVER share a request code, so they can
 * never cancel or overwrite each other.
 */
object AlarmScheduler {

    /** Intent extra: which alarm entry this fired for. */
    const val EXTRA_ALARM_ID = "alarm_id"

    /** Intent extra: which slot (0..9) in that entry's burst just fired. */
    const val EXTRA_ALARM_INDEX = "alarm_index"

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
     * Schedules (or re-schedules) every slot in one entry's next burst.
     * Safe to call repeatedly — replaces whatever was scheduled before for
     * THIS entry only; other entries are untouched.
     */
    fun schedule(context: Context, alarm: StrictAlarmEntry) {
        cancel(context, alarm)
        if (!alarm.enabled) return

        val count = alarm.effectiveAlarmCount()
        for (index in 0 until count) {
            scheduleIndex(context, alarm, index)
        }
    }

    /**
     * Re-arms a single slot after it fires.
     */
    fun scheduleIndex(context: Context, alarm: StrictAlarmEntry, index: Int) {
        val triggerAt = alarm.nextTriggerMillisForIndex(index)
        if (triggerAt == null) {
            cancelIndex(context, alarm, index)
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, alarm, index)

        val showIntent = PendingIntent.getActivity(
            context,
            requestCodeFor(alarm, index) + SHOW_INTENT_OFFSET,
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
     * Schedules a batch of brand-new, independent alarms one at a time, with
     * a short pause between each Android scheduling call. This is only about
     * *how fast we tell Android about each alarm* — it has nothing to do with
     * when the alarms actually ring (each alarm rings at the exact time the
     * user picked for it). Spacing the calls out avoids hammering Android's
     * AlarmManager with a burst of calls back-to-back, which can be flaky on
     * some devices when many exact alarms are registered in the same instant.
     *
     * Runs on a background coroutine so it doesn't block the UI thread.
     */
    suspend fun scheduleAllSpaced(
        context: Context,
        alarms: List<StrictAlarmEntry>,
        spacingMillis: Long = 3000L
    ) {
        alarms.forEachIndexed { i, alarm ->
            schedule(context, alarm)
            if (i != alarms.lastIndex) kotlinx.coroutines.delay(spacingMillis)
        }
    }

    /** Cancels every pending slot for every entry in [alarms]. */
    fun cancelAll(context: Context, alarms: List<StrictAlarmEntry>) {
        alarms.forEach { cancel(context, it) }
    }

    /** Cancels every pending slot for ONE entry. */
    fun cancel(context: Context, alarm: StrictAlarmEntry) {
        for (index in 0 until STRICT_ALARM_MAX_COUNT) {
            cancelIndex(context, alarm, index)
        }
    }

    /**
     * Legacy cancel by id only — used when we only have the id (e.g. delete
     * from list screen before the entry object is available). Falls back to
     * the old hash approach for cancellation only. Prefer cancel(context, alarm)
     * when you have the full alarm object.
     */
    fun cancel(context: Context, alarmId: String) {
        // We don't have the requestCode here, so we can't perfectly cancel.
        // This path is only hit from the delete dialog in StrictAlarmListScreen.
        // That screen has the full alarm list — see note in StrictAlarmListScreen.kt.
        // For safety, attempt the old hash-based cancel (covers legacy alarms).
        val legacyBase = 9001 + ((alarmId.hashCode() and 0x7FFFFFFF) % 100_000) * 10
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (index in 0 until STRICT_ALARM_MAX_COUNT) {
            val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_INDEX, index)
            }
            runCatching {
                am.cancel(
                    PendingIntent.getBroadcast(
                        context,
                        legacyBase + index,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                    )
                )
            }
        }
    }

    private fun cancelIndex(context: Context, alarm: StrictAlarmEntry, index: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.cancel(buildPendingIntent(context, alarm, index)) }
    }

    private fun buildPendingIntent(context: Context, alarm: StrictAlarmEntry, index: Int): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_INDEX, index)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarm, index),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Each alarm entry gets its own private block of 10 request codes (one per
    // burst slot). We use the alarm's stored requestCode directly — a small
    // unique integer assigned at creation time — so there is zero chance of
    // two alarms sharing a code. The show-intent block is offset far away so
    // it never collides with the broadcast block.
    private const val REQUEST_CODE_MULTIPLIER = 10
    private const val SHOW_INTENT_OFFSET = 200_000

    private fun requestCodeFor(alarm: StrictAlarmEntry, index: Int): Int {
        // alarm.requestCode is unique per alarm (assigned and stored at creation).
        // Multiply by 10 to reserve 10 consecutive slots (one per burst index).
        return alarm.requestCode * REQUEST_CODE_MULTIPLIER + index
    }
}
