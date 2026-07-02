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
const val STRICT_ALARM_INTERVAL_MINUTES = MULTI_ALARM_INTERVAL_MINUTES

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
 * An EMPTY set is a deliberate, meaningful state — not "off": it means
 * "one-time alarm, no repeat", exactly like a fresh alarm in stock
 * Android/Samsung Clock. See [nextTriggerMillis] for how that's resolved
 * into an actual trigger time, and [isOneTime].
 */
data class StrictAlarmEntry(
    val id: String,
    val requestCode: Int,          // unique stable int for Android scheduling
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 0,
    // Defaults to "no repeat days" (a one-time alarm) — NOT every day. A
    // brand-new alarm should point at the next available time (today if
    // it hasn't passed yet, otherwise tomorrow) and ring once, the same as
    // every top alarm app. It only becomes a repeating alarm once the user
    // deliberately picks day chips in the editor.
    val daysOfWeek: Set<Int> = emptySet(),
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
 * True when this alarm has no repeat days chosen — a one-time alarm that
 * rings once (today or tomorrow, whichever is next) and then turns itself
 * off, instead of repeating daily. This is the default for a brand-new
 * alarm; it only becomes `false` once the user picks at least one day chip.
 */
val StrictAlarmEntry.isOneTime: Boolean get() = daysOfWeek.isEmpty()

/**
 * Next trigger time for this alarm after [nowMillis]. Returns null only
 * when the alarm is disabled.
 *
 * Two modes, matching how every mainstream alarm app behaves:
 *   • ONE-TIME (daysOfWeek empty) — next occurrence of hour:minute: today
 *     if that hasn't passed yet, otherwise tomorrow. Fires exactly once.
 *   • REPEATING (daysOfWeek set) — next occurrence that falls on one of
 *     the chosen days, searching up to 7 days ahead.
 */
fun StrictAlarmEntry.nextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
    if (!enabled) return null

    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

    if (isOneTime) {
        val todayAtTime = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (todayAtTime.timeInMillis > nowMillis) return todayAtTime.timeInMillis
        return (todayAtTime.clone() as Calendar)
            .apply { add(Calendar.DAY_OF_YEAR, 1) }
            .timeInMillis
    }

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

fun StrictAlarmEntry.effectiveAlarmCount(): Int {
    return 1
}
