package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroStats.kt  —  the Pomodoro "logbook"
//
// WHY THIS FILE EXISTS
//   The old Pomodoro stats were two running counters ("sessions today" and
//   "focus seconds today") that were wiped at midnight. That is why the app
//   had no idea about yesterday, last week, or all-time totals — and why the
//   numbers could drift (double counting, lost sessions around midnight).
//
//   Now there is a LOGBOOK: one line per calendar day, e.g.
//
//        2026-11-14   3 sessions   4500 focus-seconds
//
//   Everything you see in the stats card (today, this week, all-time,
//   streaks, best day…) is simply CALCULATED from that logbook. Nothing is
//   ever "reset at midnight" — a new day just means a new line.
//
// WHAT COUNTS
//   • focus seconds  = every second you really spent in a focus countdown,
//                      even if you pause or skip afterwards.
//   • sessions       = focus countdowns that ran all the way to zero.
//                      (Skipping or giving up never adds a session.)
//   • A piece of focus time belongs to the day it ENDED on.
//
// THE THREE PARTS BELOW
//   1. Small data holders (DayStats, Achievement, …).
//   2. PomodoroStatsMath  — pure calculations. No Android in here at all,
//                           which is what makes them easy to test.
//   3. PomodoroStatsRepository — saves/loads the logbook on the phone.
// ═══════════════════════════════════════════════════════════════════════════

/** One line of the logbook. */
data class DayStats(
    val sessions: Int = 0,
    val focusSeconds: Int = 0
)

/** What kind of "well done!" moment a finished session unlocked. */
enum class AchievementKind { FIRST_EVER, MILESTONE, DAILY_GOAL, BEST_DAY, STREAK }

/** A short celebration message shown on the stats card after a session. */
data class Achievement(
    val kind: AchievementKind,
    val title: String,
    val detail: String
)

/** All the headline numbers the compact stats card needs, in one bundle. */
data class StatsSummary(
    val goal: Int,
    val todaySessions: Int,
    val todayFocusSeconds: Int,
    val yesterdaySessions: Int,
    val totalSessions: Int,
    val totalFocusSeconds: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val bestDaySessions: Int,
    val bestDay: LocalDate?
)

/** One column of the weekly chart. */
data class WeekDay(
    val date: LocalDate,
    val sessions: Int,
    val focusSeconds: Int,
    val isToday: Boolean,
    val isFuture: Boolean
)

// ───────────────────────────────────────────────────────────────────────────
// PURE CALCULATIONS
// ───────────────────────────────────────────────────────────────────────────

object PomodoroStatsMath {

    /** All-time session counts that earn a "milestone" message. */
    private val milestones = intArrayOf(10, 25, 50, 100, 250, 500, 1000, 2500, 5000)

    // ── Saving format ─────────────────────────────────────────────────────
    // The logbook is stored as one small piece of text:
    //     "2026-11-13,2,3000;2026-11-14,3,4500"
    //      date,sessions,focusSeconds  — lines separated by ";"
    // Bad or damaged pieces are skipped instead of crashing the app.

    fun encode(days: Map<LocalDate, DayStats>): String =
        days.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key},${it.value.sessions},${it.value.focusSeconds}" }

    fun decode(text: String?): Map<LocalDate, DayStats> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = HashMap<LocalDate, DayStats>()
        for (part in text.split(';')) {
            val f = part.split(',')
            if (f.size < 3) continue
            val date = runCatching { LocalDate.parse(f[0].trim()) }.getOrNull() ?: continue
            val sessions = f[1].trim().toIntOrNull() ?: continue
            val seconds = f[2].trim().toIntOrNull() ?: continue
            if (sessions < 0 || seconds < 0) continue
            out[date] = DayStats(sessions, seconds)
        }
        return out
    }

    // ── Dates ─────────────────────────────────────────────────────────────

    /** The calendar day (on this phone's clock) that a moment in time falls on. */
    fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** The first day of the week that [date] belongs to (Monday, Sunday… depending on [firstDay]). */
    fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /** The seven columns of the week starting at [weekStart]. */
    fun week(days: Map<LocalDate, DayStats>, weekStart: LocalDate, today: LocalDate): List<WeekDay> =
        (0 until 7).map { i ->
            val date = weekStart.plusDays(i.toLong())
            val stats = days[date] ?: DayStats()
            WeekDay(
                date = date,
                sessions = stats.sessions,
                focusSeconds = stats.focusSeconds,
                isToday = date == today,
                isFuture = date.isAfter(today)
            )
        }

    // ── Streaks & records ─────────────────────────────────────────────────

    private fun hasSession(days: Map<LocalDate, DayStats>, date: LocalDate): Boolean =
        (days[date]?.sessions ?: 0) > 0

    /**
     * How many days in a row (ending today) you finished at least one session.
     * If today has none YET, the streak is still alive: it counts back from
     * yesterday, so you don't "lose" it before the day is over.
     */
    fun currentStreak(days: Map<LocalDate, DayStats>, today: LocalDate): Int {
        var day = if (hasSession(days, today)) today else today.minusDays(1)
        var count = 0
        while (hasSession(days, day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** The longest run of consecutive session-days ever. */
    fun longestStreak(days: Map<LocalDate, DayStats>): Int {
        val active = days.filter { it.value.sessions > 0 }.keys.sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in active) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            if (run > best) best = run
            previous = date
        }
        return best
    }

    /** The day with the most sessions (ties: more focus time, then the more recent day). */
    fun bestDay(days: Map<LocalDate, DayStats>): Pair<LocalDate, DayStats>? {
        var best: Pair<LocalDate, DayStats>? = null
        for ((date, stats) in days) {
            if (stats.sessions <= 0) continue
            val current = best
            val better = current == null ||
                stats.sessions > current.second.sessions ||
                (stats.sessions == current.second.sessions && stats.focusSeconds > current.second.focusSeconds) ||
                (stats.sessions == current.second.sessions &&
                    stats.focusSeconds == current.second.focusSeconds &&
                    date.isAfter(current.first))
            if (better) best = date to stats
        }
        return best
    }

    // ── The compact-card bundle ───────────────────────────────────────────

    fun summarize(days: Map<LocalDate, DayStats>, today: LocalDate, goal: Int): StatsSummary {
        val todayStats = days[today] ?: DayStats()
        val yesterdayStats = days[today.minusDays(1)] ?: DayStats()
        var totalSessions = 0
        var totalSeconds = 0L
        for (stats in days.values) {
            totalSessions += stats.sessions
            totalSeconds += stats.focusSeconds
        }
        val best = bestDay(days)
        return StatsSummary(
            goal = goal.coerceAtLeast(1),
            todaySessions = todayStats.sessions,
            todayFocusSeconds = todayStats.focusSeconds,
            yesterdaySessions = yesterdayStats.sessions,
            totalSessions = totalSessions,
            totalFocusSeconds = totalSeconds,
            currentStreak = currentStreak(days, today),
            longestStreak = longestStreak(days),
            bestDaySessions = best?.second?.sessions ?: 0,
            bestDay = best?.first
        )
    }

    // ── "Well done!" moments ──────────────────────────────────────────────

    /**
     * Compares the logbook from just BEFORE a session was saved with the
     * logbook just AFTER, and returns the single most exciting thing that
     * happened (or null if it was just a normal good session).
     *
     * Priority: first-ever session > all-time milestone > daily goal >
     * personal-best day > streak.
     */
    fun detectAchievement(
        before: Map<LocalDate, DayStats>,
        after: Map<LocalDate, DayStats>,
        today: LocalDate,
        goal: Int
    ): Achievement? {
        val g = goal.coerceAtLeast(1)
        val b = summarize(before, today, g)
        val a = summarize(after, today, g)

        // Nothing new was finished, so nothing to celebrate.
        if (a.totalSessions <= b.totalSessions) return null

        if (b.totalSessions == 0) {
            return Achievement(AchievementKind.FIRST_EVER, "First session done!", "Every streak starts here.")
        }

        val milestone = milestones.firstOrNull { b.totalSessions < it && a.totalSessions >= it }
        if (milestone != null) {
            return Achievement(AchievementKind.MILESTONE, "$milestone sessions!", "Your all-time focus count just hit $milestone.")
        }

        if (b.todaySessions < g && a.todaySessions >= g) {
            val word = if (g == 1) "session" else "sessions"
            return Achievement(AchievementKind.DAILY_GOAL, "Daily goal reached", "$g $word today. Well done.")
        }

        val previousBest = after.filterKeys { it != today }.values.maxOfOrNull { it.sessions } ?: 0
        if (a.todaySessions >= 3 && previousBest >= 2 && a.todaySessions > previousBest) {
            return Achievement(AchievementKind.BEST_DAY, "New personal best", "${a.todaySessions} sessions in one day. A new record.")
        }

        if (b.todaySessions == 0 && a.todaySessions >= 1 && a.currentStreak >= 2) {
            return Achievement(AchievementKind.STREAK, "${a.currentStreak}-day streak", "Come back tomorrow to keep it going.")
        }

        return null
    }
}

// ───────────────────────────────────────────────────────────────────────────
// THE LOGBOOK ON THE PHONE
// ───────────────────────────────────────────────────────────────────────────

/**
 * Loads and saves the logbook. There is exactly one of these for the whole
 * app (get it with [PomodoroStatsRepository.get]); the screen watches
 * [days] and redraws by itself whenever a line changes.
 */
class PomodoroStatsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _days = MutableStateFlow(PomodoroStatsMath.decode(prefs.getString(KEY_DAYS, null)))

    /** The whole logbook: calendar day -> that day's totals. */
    val days: StateFlow<Map<LocalDate, DayStats>> = _days

    // The id of the most recent piece of focus time that was saved. A piece
    // is never saved twice, even if the app dies at an awkward moment and
    // tries again on the next start.
    private var lastRunId: Long = prefs.getLong(KEY_LAST_RUN, 0L)

    /**
     * Saves one piece of focus time.
     *
     * @param runId     unique id of this piece (its start time). Saving the
     *                  same id twice does nothing — that is the guard against
     *                  double counting.
     * @param atMillis  when the piece ended. This decides WHICH DAY it counts for.
     * @param seconds   how many seconds of real focus it contained.
     * @param completed true if this piece finished a session (the countdown
     *                  reached zero) — that is what adds +1 session.
     * @return false if it was ignored (already saved, or nothing to save).
     */
    @Synchronized
    fun recordFocusSegment(runId: Long, atMillis: Long, seconds: Int, completed: Boolean): Boolean {
        if (runId > 0L && runId == lastRunId) return false
        val secs = seconds.coerceAtLeast(0)
        if (secs == 0 && !completed) return false

        val date = PomodoroStatsMath.dateOf(atMillis)
        val current = _days.value
        val old = current[date] ?: DayStats()
        val updated = DayStats(
            sessions = old.sessions + if (completed) 1 else 0,
            focusSeconds = old.focusSeconds + secs
        )
        save(current + (date to updated), if (runId > 0L) runId else lastRunId)
        return true
    }

    /** One-time import of the numbers the OLD counter system had stored. */
    @Synchronized
    fun addLegacyDay(date: LocalDate, sessions: Int, focusSeconds: Int) {
        if (sessions <= 0 && focusSeconds <= 0) return
        val old = _days.value[date] ?: DayStats()
        val updated = DayStats(
            sessions = old.sessions + sessions.coerceAtLeast(0),
            focusSeconds = old.focusSeconds + focusSeconds.coerceAtLeast(0)
        )
        save(_days.value + (date to updated), lastRunId)
    }

    /** Wipes the whole logbook (the "Clear Stats History" button). */
    @Synchronized
    fun clearAll() {
        save(emptyMap(), lastRunId)
    }

    private fun save(map: Map<LocalDate, DayStats>, runId: Long) {
        lastRunId = runId
        // commit() (not apply()) on purpose: the write is tiny, and we want it
        // safely on disk right away, even if Android stops the app a moment later.
        prefs.edit()
            .putString(KEY_DAYS, PomodoroStatsMath.encode(map))
            .putLong(KEY_LAST_RUN, runId)
            .commit()
        _days.value = map
    }

    companion object {
        private const val FILE_NAME = "pomodoro_history"
        private const val KEY_DAYS = "days_v1"
        private const val KEY_LAST_RUN = "last_run_id"

        @Volatile
        private var instance: PomodoroStatsRepository? = null

        fun get(context: Context): PomodoroStatsRepository =
            instance ?: synchronized(this) {
                instance ?: PomodoroStatsRepository(context.applicationContext).also { instance = it }
            }
    }
}
