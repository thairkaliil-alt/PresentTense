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
 * The three ways an optional Strict Mode challenge can ask you to prove
 * you're actually up before the alarm will stop ringing. See
 * [StrictAlarmEntry.strictModeEnabled] — none of this applies at all unless
 * that switch is turned on; a brand-new alarm never has it on.
 */
enum class AlarmChallengeType { MATH, TYPING, SHAKE }

/**
 * How hard the chosen challenge is. Only changes the numbers/targets inside
 * the challenge — it never changes whether a challenge is required at all;
 * that's [StrictAlarmEntry.strictModeEnabled]'s job.
 */
enum class AlarmChallengeDifficulty { EASY, MEDIUM, HARD }

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
    val label: String = "",

    // ── Strict Mode — every field below is OPTIONAL and OFF by default ───
    // A brand-new alarm (and every alarm saved before this feature existed —
    // see fromJson below) has strictModeEnabled = false, which means it
    // behaves exactly like a plain, normal alarm: one tap to dismiss. Only
    // once someone deliberately turns this switch on in the editor do any
    // of the fields below start doing anything.
    /** Master switch for the whole feature. */
    val strictModeEnabled: Boolean = false,
    /** Which challenge runs when strict mode is on. */
    val challengeType: AlarmChallengeType = AlarmChallengeType.MATH,
    /** How hard that challenge is. */
    val challengeDifficulty: AlarmChallengeDifficulty = AlarmChallengeDifficulty.MEDIUM,
    /** How many times in a row it must be solved — always read through
     *  [effectiveChallengeRounds], which clamps this to a sane 1..5. */
    val challengeRounds: Int = 1,
    /** Custom phrase for the TYPING challenge. Blank = use the built-in
     *  default — always read through [effectiveTypingPhrase]. */
    val typingPhrase: String = "",
    /** Whether the ring screen offers a Snooze button at all. */
    val snoozeEnabled: Boolean = true,
    /** How many minutes a tap on Snooze pushes the alarm back by. */
    val snoozeMinutes: Int = 9
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("requestCode", requestCode)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("daysOfWeek", JSONArray(daysOfWeek.toList()))
        put("label", label)
        put("strictModeEnabled", strictModeEnabled)
        put("challengeType", challengeType.name)
        put("challengeDifficulty", challengeDifficulty.name)
        put("challengeRounds", challengeRounds)
        put("typingPhrase", typingPhrase)
        put("snoozeEnabled", snoozeEnabled)
        put("snoozeMinutes", snoozeMinutes)
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
                label       = o.optString("label", ""),
                // Every field below is missing entirely on any alarm saved
                // before this feature existed, since those old JSON blobs
                // never had these keys. opt*() then falls back to the
                // default after the comma — strictModeEnabled defaults to
                // false — so every pre-existing alarm loads as a perfectly
                // normal alarm, unchanged from how it worked before.
                strictModeEnabled   = o.optBoolean("strictModeEnabled", false),
                challengeType       = runCatching {
                    AlarmChallengeType.valueOf(o.optString("challengeType", "MATH"))
                }.getOrDefault(AlarmChallengeType.MATH),
                challengeDifficulty = runCatching {
                    AlarmChallengeDifficulty.valueOf(o.optString("challengeDifficulty", "MEDIUM"))
                }.getOrDefault(AlarmChallengeDifficulty.MEDIUM),
                challengeRounds     = o.optInt("challengeRounds", 1).coerceIn(1, 5),
                typingPhrase        = o.optString("typingPhrase", ""),
                snoozeEnabled       = o.optBoolean("snoozeEnabled", true),
                snoozeMinutes       = o.optInt("snoozeMinutes", 9).coerceIn(1, 30)
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

/** [StrictAlarmEntry.challengeRounds], clamped to a sane 1..5 no matter what
 *  got saved — one place that guarantees this instead of trusting every
 *  call site to remember the range. */
val StrictAlarmEntry.effectiveChallengeRounds: Int get() = challengeRounds.coerceIn(1, 5)

/** The phrase the TYPING challenge asks you to retype. Falls back to a
 *  built-in default the moment no custom phrase has been set, so the
 *  challenge always has something sensible to show. */
val StrictAlarmEntry.effectiveTypingPhrase: String get() =
    typingPhrase.ifBlank { "I am up and I am staying up" }
