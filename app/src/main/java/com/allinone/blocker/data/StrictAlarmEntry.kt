package com.allinone.blocker.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * How many minutes apart the alarms created by the "Add multiple alarms"
 * feature are spaced. Currently fixed at 3 minutes.
 *
 * This constant is the single place to change if you later want to expose
 * this as a user-configurable setting in Advanced Settings — update this
 * value (or replace it with a value read from SharedPreferences) and
 * everything else will follow automatically.
 */
const val MULTI_ALARM_INTERVAL_MINUTES = 3

/**
 * One independent Strict Alarm. The app holds a LIST of these — each one
 * its own alarm with its own time, its own repeat days, and its own on/off
 * toggle — the same way a normal phone clock app's alarm list works.
 *
 * [requestCode] is a small, stable, UNIQUE integer stored with this alarm
 * and used as Android's PendingIntent request code. Using a stored integer
 * instead of a hash of the UUID prevents collisions between alarms.
 *
 * [daysOfWeek] uses java.util.Calendar values: Calendar.SUNDAY(1) ..
 * Calendar.SATURDAY(7), same as every other day-of-week set in this app.
 */
data class StrictAlarmEntry(
    val id: String,
    val requestCode: Int,          // unique stable int for Android scheduling
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = (1..7).toSet(),
    val label: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("requestCode", requestCode)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("daysOfWeek", JSONArray(daysOfWeek.toList()))
        put("label", label)
    }

    companion object {
        fun fromJson(o: JSONObject): StrictAlarmEntry {
            val days = mutableSetOf<Int>()
            val arr = o.optJSONArray("daysOfWeek")
            if (arr != null) {
                for (i in 0 until arr.length()) days.add(arr.getInt(i))
            } else {
                days.addAll(1..7)
            }
            // Legacy alarms saved before requestCode was stored get a code
            // derived from their id hash — same as the old behaviour, but
            // now it is stored and stable rather than recomputed each time.
            val legacyCode = (o.getString("id").hashCode() and 0x7FFFFFFF) % 100_000
            return StrictAlarmEntry(
                id          = o.getString("id"),
                requestCode = o.optInt("requestCode", legacyCode),
                enabled     = o.optBoolean("enabled", true),
                hour        = o.optInt("hour", 7),
                minute      = o.optInt("minute", 0),
                daysOfWeek  = days,
                label       = o.optString("label", "")
                // Legacy fields multiAlarmEnabled / alarmCount are intentionally
                // ignored on load — they belonged to the old burst feature which
                // has been replaced by true independent alarms.
            )
        }

        /** A fresh blank alarm, ready to be edited — used by the "+" add button. */
        fun newDefault(requestCode: Int): StrictAlarmEntry = StrictAlarmEntry(
            id          = java.util.UUID.randomUUID().toString(),
            requestCode = requestCode
        )
    }
}

/**
 * Next trigger time for this alarm after [nowMillis].
 * Returns null if the alarm is disabled or has no days selected.
 */
fun StrictAlarmEntry.nextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
    if (!enabled || daysOfWeek.isEmpty()) return null

    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

    for (dayOffset in 0..7) {
        val candidate = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerAt = candidate.timeInMillis
        val dayMatches = candidate.get(Calendar.DAY_OF_WEEK) in daysOfWeek
        if (dayMatches && triggerAt > nowMillis) return triggerAt
    }
    return null
}
