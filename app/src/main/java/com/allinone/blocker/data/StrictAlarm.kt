package com.allinone.blocker.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * The user's Strict Alarm settings. Supports a single alarm or a burst of up
 * to [STRICT_ALARM_MAX_COUNT] alarms spaced [STRICT_ALARM_INTERVAL_MINUTES]
 * minutes apart when [multiAlarmEnabled] is on.
 *
 * [daysOfWeek] uses java.util.Calendar values: Calendar.SUNDAY(1) ..
 * Calendar.SATURDAY(7), exactly like LockdownSchedule, so the two features
 * stay consistent for anyone reading both.
 */
/** Minutes between each alarm when multi-alarm mode is on. */
const val STRICT_ALARM_INTERVAL_MINUTES = 3

/** Maximum number of alarms in one multi-alarm burst. */
const val STRICT_ALARM_MAX_COUNT = 10

data class StrictAlarm(
    val enabled: Boolean = false,
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
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("daysOfWeek", JSONArray(daysOfWeek.toList()))
        put("label", label)
        put("multiAlarmEnabled", multiAlarmEnabled)
        put("alarmCount", alarmCount)
    }

    companion object {
        fun fromJson(o: JSONObject): StrictAlarm {
            val days = mutableSetOf<Int>()
            val arr = o.optJSONArray("daysOfWeek")
            if (arr != null) {
                for (i in 0 until arr.length()) days.add(arr.getInt(i))
            } else {
                days.addAll(1..7)
            }
            return StrictAlarm(
                enabled = o.optBoolean("enabled", false),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                daysOfWeek = days,
                label = o.optString("label", ""),
                multiAlarmEnabled = o.optBoolean("multiAlarmEnabled", false),
                alarmCount = o.optInt("alarmCount", 1).coerceIn(1, STRICT_ALARM_MAX_COUNT)
            )
        }
    }
}

/**
 * Figures out the next real clock-time (in milliseconds since epoch) that
 * this alarm should ring at, given the days it's allowed to ring on.
 *
 * Returns null if the alarm is disabled or has no days selected — meaning
 * "there is nothing to schedule".
 */
fun StrictAlarm.nextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long? =
    nextTriggerMillisForIndex(0, nowMillis)

/**
 * Next ring time for alarm slot [index] in a multi-alarm burst. Slot 0 is the
 * base time; each slot after that is [STRICT_ALARM_INTERVAL_MINUTES] later.
 */
fun StrictAlarm.nextTriggerMillisForIndex(
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

/** Upcoming ring times for every slot in the next burst, sorted earliest first. */
fun StrictAlarm.allNextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): List<Long> {
    val count = effectiveAlarmCount()
    return (0 until count).mapNotNull { nextTriggerMillisForIndex(it, nowMillis) }.sorted()
}
