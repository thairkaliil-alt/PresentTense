package com.allinone.blocker.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Minutes between each alarm when multi-alarm mode is on. */
const val STRICT_ALARM_INTERVAL_MINUTES = 3

/** Maximum number of alarms in one multi-alarm burst. */
const val STRICT_ALARM_MAX_COUNT = 10

/**
 * One independent Strict Alarm. The app holds a LIST of these — each one
 * its own alarm with its own time, its own repeat days, its own on/off
 * toggle, and its own optional "burst" (multi-ring) setting — the same way
 * a normal phone clock app's alarm list works.
 *
 * [daysOfWeek] uses java.util.Calendar values: Calendar.SUNDAY(1) ..
 * Calendar.SATURDAY(7), same as every other day-of-week set in this app.
 */
data class StrictAlarmEntry(
    val id: String,
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = (1..7).toSet(),
    val label: String = "",
    val multiAlarmEnabled: Boolean = false,
    val alarmCount: Int = 1
) {
    fun effectiveAlarmCount(): Int =
        if (multiAlarmEnabled) alarmCount.coerceIn(1, STRICT_ALARM_MAX_COUNT) else 1

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("daysOfWeek", JSONArray(daysOfWeek.toList()))
        put("label", label)
        put("multiAlarmEnabled", multiAlarmEnabled)
        put("alarmCount", alarmCount)
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
            return StrictAlarmEntry(
                id = o.getString("id"),
                enabled = o.optBoolean("enabled", true),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                daysOfWeek = days,
                label = o.optString("label", ""),
                multiAlarmEnabled = o.optBoolean("multiAlarmEnabled", false),
                alarmCount = o.optInt("alarmCount", 1).coerceIn(1, STRICT_ALARM_MAX_COUNT)
            )
        }

        /** A fresh blank alarm, ready to be edited — used by the "+" add button. */
        fun newDefault(): StrictAlarmEntry = StrictAlarmEntry(
            id = java.util.UUID.randomUUID().toString()
        )
    }
}

/**
 * Next ring time for alarm slot [index] in this entry's burst. Slot 0 is the
 * base time; each slot after that is [STRICT_ALARM_INTERVAL_MINUTES] later.
 * Returns null if the entry is disabled or has no days selected.
 */
fun StrictAlarmEntry.nextTriggerMillisForIndex(
    index: Int,
    nowMillis: Long = System.currentTimeMillis()
): Long? {
    if (!enabled || daysOfWeek.isEmpty()) return null

    val offsetMillis = index * STRICT_ALARM_INTERVAL_MINUTES * 60_000L
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }

    for (dayOffset in 0..7) {
        val candidate = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerAt = candidate.timeInMillis + offsetMillis
        val baseDayMatches = candidate.get(Calendar.DAY_OF_WEEK) in daysOfWeek
        if (baseDayMatches && triggerAt > nowMillis) {
            return triggerAt
        }
    }
    return null
}

/** The single next ring time for this entry (slot 0 of its burst). */
fun StrictAlarmEntry.nextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long? =
    nextTriggerMillisForIndex(0, nowMillis)

/** Upcoming ring times for every slot in this entry's next burst, sorted earliest first. */
fun StrictAlarmEntry.allNextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): List<Long> {
    val count = effectiveAlarmCount()
    return (0 until count).mapNotNull { nextTriggerMillisForIndex(it, nowMillis) }.sorted()
}
