package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

object StreakRepository {

    private const val PREFS = "streak_prefs"
    private const val KEY_STREAK              = "streak_days"
    private const val KEY_LAST_EVALUATED_DAY  = "streak_last_evaluated_day_key"
    private const val KEY_BROKEN_DAY          = "streak_broken_day_key"
    private const val KEY_BROKEN_DAY_SHIELDED = "streak_broken_day_shielded"
    private const val KEY_SHIELDS_AVAILABLE   = "shields_available"
    private const val KEY_SHIELDS_CAP         = "shields_cap"
    private const val KEY_SHIELDS_LAST_REFILL = "shields_last_refill_week"
    private const val KEY_HISTORY             = "streak_history_log"
    private const val HISTORY_MAX_DAYS        = 30
    private const val DEFAULT_SHIELDS_CAP = 2

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    private val _shieldsAvailable = MutableStateFlow(DEFAULT_SHIELDS_CAP)
    val shieldsAvailable: StateFlow<Int> = _shieldsAvailable

    private val _shieldsCap = MutableStateFlow(DEFAULT_SHIELDS_CAP)
    val shieldsCap: StateFlow<Int> = _shieldsCap

    private val _brokenToday = MutableStateFlow(false)
    val brokenToday: StateFlow<Boolean> = _brokenToday

    private val _history = MutableStateFlow<List<Pair<Int, Boolean>>>(emptyList())
    val history: StateFlow<List<Pair<Int, Boolean>>> = _history

    private lateinit var prefs: SharedPreferences
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        maybeRefillShields()
        evaluateFinishedDays(context)
        load()
        initialized = true
    }

    private fun load() {
        _streak.value           = prefs.getInt(KEY_STREAK, 0)
        _shieldsAvailable.value = prefs.getInt(KEY_SHIELDS_AVAILABLE, DEFAULT_SHIELDS_CAP)
        _shieldsCap.value       = prefs.getInt(KEY_SHIELDS_CAP, DEFAULT_SHIELDS_CAP)
        val brokenDayKey = prefs.getInt(KEY_BROKEN_DAY, -1)
        _brokenToday.value = (brokenDayKey == todayKey())
        _history.value = loadHistory()
    }

    fun recordSuccessfulDisable() {
        if (!initialized) return
        val today = todayKey()
        if (prefs.getInt(KEY_BROKEN_DAY, -1) == today) return
        val shieldsLeft = prefs.getInt(KEY_SHIELDS_AVAILABLE, DEFAULT_SHIELDS_CAP)
        if (shieldsLeft > 0) {
            val newShields = shieldsLeft - 1
            prefs.edit()
                .putInt(KEY_SHIELDS_AVAILABLE, newShields)
                .putInt(KEY_BROKEN_DAY, today)
                .putBoolean(KEY_BROKEN_DAY_SHIELDED, true)
                .apply()
            _shieldsAvailable.value = newShields
            _brokenToday.value = true
        } else {
            prefs.edit()
                .putInt(KEY_STREAK, 0)
                .putInt(KEY_BROKEN_DAY, today)
                .putBoolean(KEY_BROKEN_DAY_SHIELDED, false)
                .apply()
            _streak.value = 0
            _brokenToday.value = true
        }
    }

    fun evaluateFinishedDays(context: Context) {
        if (!::prefs.isInitialized) return
        val today     = todayKey()
        val yesterday = offsetDay(today, -1)
        val lastEvaluated = prefs.getInt(KEY_LAST_EVALUATED_DAY, -1)

        if (lastEvaluated == -1) {
            prefs.edit().putInt(KEY_LAST_EVALUATED_DAY, yesterday).apply()
            return
        }
        if (lastEvaluated >= yesterday) return

        val goalMinutes  = BlockerRepository.dailyGoalMinutes()
        val daysToCover  = daysBetween(lastEvaluated, yesterday).coerceIn(1, 90)
        val weeklyTotals = ScreenTimeTracker.weeklyTotals(context, days = daysToCover + 1)
        val brokenDay         = prefs.getInt(KEY_BROKEN_DAY, -1)
        val brokenDayShielded = prefs.getBoolean(KEY_BROKEN_DAY_SHIELDED, false)

        var cursor = lastEvaluated
        while (cursor != yesterday) {
            val day = offsetDay(cursor, 1)
            val disableBrokeIt = (brokenDay == day)
            val goalBrokeIt = if (goalMinutes <= 0) false
                else (((weeklyTotals[day]?.values?.sum() ?: 0L) / 60_000L).toInt() > goalMinutes)
            val dayWasBad = disableBrokeIt || goalBrokeIt

            if (dayWasBad) {
                if (disableBrokeIt) {
                    if (brokenDayShielded) {
                        recordHistoryDay(day, isClean = true)
                    } else {
                        prefs.edit().putInt(KEY_STREAK, 0).apply()
                        _streak.value = 0
                        recordHistoryDay(day, isClean = false)
                    }
                } else {
                    val shieldsLeft = prefs.getInt(KEY_SHIELDS_AVAILABLE, DEFAULT_SHIELDS_CAP)
                    if (shieldsLeft > 0) {
                        prefs.edit().putInt(KEY_SHIELDS_AVAILABLE, shieldsLeft - 1).apply()
                        _shieldsAvailable.value = shieldsLeft - 1
                        recordHistoryDay(day, isClean = true)
                    } else {
                        prefs.edit().putInt(KEY_STREAK, 0).apply()
                        _streak.value = 0
                        recordHistoryDay(day, isClean = false)
                    }
                }
            } else {
                val newStreak = prefs.getInt(KEY_STREAK, _streak.value) + 1
                prefs.edit().putInt(KEY_STREAK, newStreak).apply()
                _streak.value = newStreak
                recordHistoryDay(day, isClean = true)
            }
            cursor = day
        }
        prefs.edit().putInt(KEY_LAST_EVALUATED_DAY, yesterday).apply()
    }

    private fun maybeRefillShields() {
        val currentWeek = currentIsoWeek()
        val lastRefill  = prefs.getInt(KEY_SHIELDS_LAST_REFILL, -1)
        if (lastRefill == currentWeek) return
        val cap = prefs.getInt(KEY_SHIELDS_CAP, DEFAULT_SHIELDS_CAP)
        prefs.edit()
            .putInt(KEY_SHIELDS_AVAILABLE, cap)
            .putInt(KEY_SHIELDS_LAST_REFILL, currentWeek)
            .apply()
        _shieldsAvailable.value = cap
    }

    fun setShieldsCap(cap: Int) {
        val clamped    = cap.coerceIn(0, 7)
        val current    = prefs.getInt(KEY_SHIELDS_AVAILABLE, DEFAULT_SHIELDS_CAP)
        val newBalance = if (clamped > current) clamped else current
        prefs.edit()
            .putInt(KEY_SHIELDS_CAP, clamped)
            .putInt(KEY_SHIELDS_AVAILABLE, newBalance)
            .apply()
        _shieldsCap.value       = clamped
        _shieldsAvailable.value = newBalance
    }

    val MILESTONE_DAYS = setOf(3, 7, 14, 30, 60, 100)
    fun isMilestone(streakDays: Int): Boolean = streakDays in MILESTONE_DAYS

    private fun recordHistoryDay(dayKey: Int, isClean: Boolean) {
        val current = loadHistory().toMutableList()
        current.removeAll { it.first == dayKey }
        current.add(dayKey to isClean)
        val trimmed = current.sortedBy { it.first }.takeLast(HISTORY_MAX_DAYS)
        saveHistory(trimmed)
        _history.value = trimmed
    }

    private fun loadHistory(): List<Pair<Int, Boolean>> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val day = parts[0].toIntOrNull() ?: return@mapNotNull null
            day to (parts[1] == "1")
        }
    }

    private fun saveHistory(entries: List<Pair<Int, Boolean>>) {
        val raw = entries.joinToString(",") { "${it.first}:${if (it.second) "1" else "0"}" }
        prefs.edit().putString(KEY_HISTORY, raw).apply()
    }

    private fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun offsetDay(dayKey: Int, offsetDays: Int): Int {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, dayKey / 1000)
        c.set(Calendar.DAY_OF_YEAR, dayKey % 1000)
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun currentIsoWeek(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.WEEK_OF_YEAR)
    }

    private fun daysBetween(fromDay: Int, toDay: Int): Int {
        val from = Calendar.getInstance().apply {
            set(Calendar.YEAR, fromDay / 1000)
            set(Calendar.DAY_OF_YEAR, fromDay % 1000)
        }
        val to = Calendar.getInstance().apply {
            set(Calendar.YEAR, toDay / 1000)
            set(Calendar.DAY_OF_YEAR, toDay % 1000)
        }
        return ((to.timeInMillis - from.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
    }
}
