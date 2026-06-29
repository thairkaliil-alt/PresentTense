package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

object BlockerRepository {

    private const val PREFS = "blocker_prefs"
    private const val KEY_APPS = "apps"
    private const val KEY_WEBSITES = "blocked_websites"
    private const val KEY_REELS = "reels_kill_switch"
    private const val KEY_PROTECTION_ON = "protection_enabled"
    private const val KEY_OPENS = "opens_today"
    private const val KEY_LAST_USE = "last_use"
    private const val KEY_DAY = "day_key"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_SCHEDULES = "lockdown_schedules"
    private const val KEY_MANUAL_LOCK_UNTIL = "manual_lock_until"
    private const val KEY_STRICT_MODE = "strict_mode"
    private const val KEY_STRICT_ALARMS_LIST = "strict_alarms_list"
    private const val KEY_BREAK_UNTIL = "lockdown_break_until"
    private const val KEY_BREAK_USES = "lockdown_break_uses_this_session"
    private const val KEY_BREAK_SESSION_ANCHOR = "lockdown_break_session_anchor"
    private const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"

    private lateinit var prefs: SharedPreferences
    @Volatile private var initialized = false

    private val _apps = MutableStateFlow<List<BlockedApp>>(emptyList())
    val apps: StateFlow<List<BlockedApp>> = _apps

    private val _websites = MutableStateFlow<List<BlockedWebsite>>(emptyList())
    val websites: StateFlow<List<BlockedWebsite>> = _websites

    private val _reelsKillSwitch = MutableStateFlow(false)
    val reelsKillSwitch: StateFlow<Boolean> = _reelsKillSwitch

    val protectionEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    private val _whitelist = MutableStateFlow<Set<String>>(emptySet())
    val whitelist: StateFlow<Set<String>> = _whitelist

    private val _schedules = MutableStateFlow<List<LockdownSchedule>>(emptyList())
    val schedules: StateFlow<List<LockdownSchedule>> = _schedules

    private val _manualLockUntil = MutableStateFlow(0L)
    val manualLockUntil: StateFlow<Long> = _manualLockUntil

    private val _breakUntil = MutableStateFlow(0L)
    val breakUntil: StateFlow<Long> = _breakUntil

    private val _breakUsesThisSession = MutableStateFlow(0)
    val breakUsesThisSession: StateFlow<Int> = _breakUsesThisSession

    private val _strictMode = MutableStateFlow(StrictModeConfig())
    val strictMode: StateFlow<StrictModeConfig> = _strictMode

    // The list of independent Strict Alarms — see StrictAlarmEntry.kt.
    private val _strictAlarms = MutableStateFlow<List<StrictAlarmEntry>>(emptyList())
    val strictAlarms: StateFlow<List<StrictAlarmEntry>> = _strictAlarms

    private val opensToday = HashMap<String, Int>()
    private val lastUseAt = HashMap<String, Long>()

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
        initialized = true
    }

    val isInitialized: Boolean get() = initialized

    private fun load() {
        rolloverDayIfNeeded()
        _reelsKillSwitch.value = prefs.getBoolean(KEY_REELS, false)
        _manualLockUntil.value = prefs.getLong(KEY_MANUAL_LOCK_UNTIL, 0L)
        _breakUntil.value = prefs.getLong(KEY_BREAK_UNTIL, 0L)
        _breakUsesThisSession.value = prefs.getInt(KEY_BREAK_USES, 0)

        val list = mutableListOf<BlockedApp>()
        prefs.getString(KEY_APPS, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) list.add(BlockedApp.fromJson(arr.getJSONObject(i)))
            }
        }
        _apps.value = list

        val websiteList = mutableListOf<BlockedWebsite>()
        prefs.getString(KEY_WEBSITES, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) websiteList.add(BlockedWebsite.fromJson(arr.getJSONObject(i)))
            }
        }
        _websites.value = websiteList

        val wl = mutableSetOf<String>()
        prefs.getString(KEY_WHITELIST, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) wl.add(arr.getString(i))
            }
        }
        _whitelist.value = wl

        val sched = mutableListOf<LockdownSchedule>()
        prefs.getString(KEY_SCHEDULES, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) sched.add(LockdownSchedule.fromJson(arr.getJSONObject(i)))
            }
        }
        _schedules.value = sched

        prefs.getString(KEY_STRICT_MODE, null)?.let { raw ->
            runCatching { _strictMode.value = StrictModeConfig.fromJson(JSONObject(raw)) }
        }

        val alarmEntries = mutableListOf<StrictAlarmEntry>()
        prefs.getString(KEY_STRICT_ALARMS_LIST, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) alarmEntries.add(StrictAlarmEntry.fromJson(arr.getJSONObject(i)))
            }
        }
        _strictAlarms.value = alarmEntries

        loadCounter(KEY_OPENS, opensToday)
        loadLongCounter(KEY_LAST_USE, lastUseAt)
    }

    fun upsertApp(app: BlockedApp) {
        val list = _apps.value.toMutableList()
        val idx = list.indexOfFirst { it.packageName == app.packageName }
        if (idx >= 0) list[idx] = app else list.add(app)
        _apps.value = list
        persistApps()
    }

    fun removeApp(packageName: String) {
        _apps.value = _apps.value.filterNot { it.packageName == packageName }
        persistApps()
    }

    fun appFor(packageName: String): BlockedApp? =
        _apps.value.firstOrNull { it.packageName == packageName }

    private fun persistApps() {
        val arr = JSONArray()
        _apps.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_APPS, arr.toString()).apply()
    }

    fun upsertWebsite(website: BlockedWebsite) {
        val list = _websites.value.toMutableList()
        val idx = list.indexOfFirst { it.domain == website.domain }
        if (idx >= 0) list[idx] = website else list.add(website)
        _websites.value = list
        persistWebsites()
    }

    fun removeWebsite(domain: String) {
        _websites.value = _websites.value.filterNot { it.domain == domain }
        persistWebsites()
    }

    fun websiteFor(domain: String): BlockedWebsite? =
        _websites.value.firstOrNull { it.domain == domain }

    fun isWebsiteBlocked(domain: String): Boolean {
        if (domain.isBlank()) return false
        return _websites.value.any { entry ->
            entry.enabled && (domain == entry.domain || domain.endsWith("." + entry.domain))
        }
    }

    private fun persistWebsites() {
        val arr = JSONArray()
        _websites.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_WEBSITES, arr.toString()).apply()
    }

    fun setReelsKillSwitch(on: Boolean) {
        _reelsKillSwitch.value = on
        prefs.edit().putBoolean(KEY_REELS, on).apply()
    }

    fun addToWhitelist(pkg: String) {
        _whitelist.value = _whitelist.value + pkg
        persistWhitelist()
    }

    fun removeFromWhitelist(pkg: String) {
        _whitelist.value = _whitelist.value - pkg
        persistWhitelist()
    }

    fun isWhitelisted(pkg: String): Boolean = _whitelist.value.contains(pkg)

    private fun persistWhitelist() {
        prefs.edit().putString(KEY_WHITELIST, JSONArray(_whitelist.value.toList()).toString()).apply()
    }

    fun addSchedule(schedule: LockdownSchedule) {
        _schedules.value = _schedules.value + schedule
        persistSchedules()
    }

    fun updateSchedule(updated: LockdownSchedule) {
        _schedules.value = _schedules.value.map { if (it.id == updated.id) updated else it }
        persistSchedules()
    }

    fun removeSchedule(id: String) {
        _schedules.value = _schedules.value.filterNot { it.id == id }
        persistSchedules()
    }

    private fun persistSchedules() {
        val arr = JSONArray()
        _schedules.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
    }

    fun newScheduleId(): String = UUID.randomUUID().toString()

    fun startManualLock(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        _manualLockUntil.value = until
        prefs.edit().putLong(KEY_MANUAL_LOCK_UNTIL, until).apply()
        resetBreaksForNewSession()
    }

    fun startManualLockIndefinite() {
        _manualLockUntil.value = Long.MAX_VALUE
        prefs.edit().putLong(KEY_MANUAL_LOCK_UNTIL, Long.MAX_VALUE).apply()
        resetBreaksForNewSession()
    }

    fun endManualLock() {
        _manualLockUntil.value = 0L
        prefs.edit().putLong(KEY_MANUAL_LOCK_UNTIL, 0L).apply()
        endBreakNow()
    }

    // Break settings are now read from StrictModeConfig so the user can
    // configure them from the Strict Mode settings screen.
    fun maxBreaksPerSession(): Int = _strictMode.value.maxBreaksPerSession
    fun breakDurationSeconds(): Long = _strictMode.value.breakDurationMinutes * 60L

    fun breaksRemaining(): Int =
        (maxBreaksPerSession() - _breakUsesThisSession.value).coerceAtLeast(0)

    fun canStartBreak(): Boolean =
        breaksRemaining() > 0 && _breakUntil.value <= System.currentTimeMillis()

    fun startEmergencyBreak(): Boolean {
        if (!canStartBreak()) return false
        val until = System.currentTimeMillis() + breakDurationSeconds() * 1000L
        _breakUntil.value = until
        val newCount = _breakUsesThisSession.value + 1
        _breakUsesThisSession.value = newCount
        prefs.edit()
            .putLong(KEY_BREAK_UNTIL, until)
            .putInt(KEY_BREAK_USES, newCount)
            .apply()
        return true
    }

    fun endBreakNow() {
        if (_breakUntil.value == 0L) return
        _breakUntil.value = 0L
        prefs.edit().putLong(KEY_BREAK_UNTIL, 0L).apply()
    }

    fun resetBreaksForNewSession() {
        _breakUsesThisSession.value = 0
        _breakUntil.value = 0L
        prefs.edit()
            .putInt(KEY_BREAK_USES, 0)
            .putLong(KEY_BREAK_UNTIL, 0L)
            .apply()
    }

    fun maybeResetBreaksForScheduledSession(sessionEndsAtMillis: Long) {
        if (sessionEndsAtMillis <= 0) return
        if (prefs.getLong(KEY_BREAK_SESSION_ANCHOR, -1L) == sessionEndsAtMillis) return
        prefs.edit().putLong(KEY_BREAK_SESSION_ANCHOR, sessionEndsAtMillis).apply()
        resetBreaksForNewSession()
    }

    fun setStrictMode(config: StrictModeConfig) {
        _strictMode.value = config
        prefs.edit().putString(KEY_STRICT_MODE, config.toJson().toString()).apply()
    }

    /**
     * Starts (or extends) an Active Plan countdown for Strict Mode. [durationMillis]
     * is clamped to [StrictModeConfig.MAX_CUSTOM_PLAN_DAYS] here as well as in the
     * UI, so there's no path — including a future screen someone adds later — that
     * can sneak past the cap and create a runaway lock with no end time.
     */
    fun startStrictPlan(durationMillis: Long, label: String) {
        val maxMillis = StrictModeConfig.MAX_CUSTOM_PLAN_DAYS * 24L * 60 * 60 * 1000
        val clamped = durationMillis.coerceIn(1L, maxMillis)
        val current = _strictMode.value
        setStrictMode(
            current.copy(
                activeFrictions = current.activeFrictions + FrictionType.PLAN_LOCK,
                planActiveUntil = System.currentTimeMillis() + clamped,
                planLabel = label
            )
        )
    }

    // List-based CRUD, mirrors addSchedule/updateSchedule/removeSchedule above.

    fun addStrictAlarmEntry(entry: StrictAlarmEntry) {
        _strictAlarms.value = _strictAlarms.value + entry
        persistStrictAlarms()
    }

    fun updateStrictAlarmEntry(updated: StrictAlarmEntry) {
        _strictAlarms.value = _strictAlarms.value.map { if (it.id == updated.id) updated else it }
        persistStrictAlarms()
    }

    fun removeStrictAlarmEntry(id: String) {
        _strictAlarms.value = _strictAlarms.value.filterNot { it.id == id }
        persistStrictAlarms()
    }

    fun setStrictAlarmEntryEnabled(id: String, enabled: Boolean) {
        _strictAlarms.value = _strictAlarms.value.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        persistStrictAlarms()
    }

    /** Saves a new ordering of the alarm list (from drag-to-reorder). */
    fun reorderStrictAlarms(reordered: List<StrictAlarmEntry>) {
        _strictAlarms.value = reordered
        persistStrictAlarms()
    }

    private fun persistStrictAlarms() {
        val arr = JSONArray()
        _strictAlarms.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_STRICT_ALARMS_LIST, arr.toString()).apply()
    }

    fun newStrictAlarmId(): String = UUID.randomUUID().toString()

/**
 * Returns the next available request code for a new alarm.
 * We find the highest requestCode already in use and add 1,
 * so every alarm gets a permanently unique number.
 */
fun nextAlarmRequestCode(): Int {
    val existing = _strictAlarms.value
    return if (existing.isEmpty()) 1
    else existing.maxOf { it.requestCode } + 1
}

    fun opensToday(pkg: String): Int {
        rolloverDayIfNeeded()
        return opensToday[pkg] ?: 0
    }

    fun recordOpen(pkg: String) {
        rolloverDayIfNeeded()
        opensToday[pkg] = (opensToday[pkg] ?: 0) + 1
        saveCounter(KEY_OPENS, opensToday)
    }

    fun lastUse(pkg: String): Long = lastUseAt[pkg] ?: 0L

    fun recordUse(pkg: String, whenMillis: Long) {
        lastUseAt[pkg] = whenMillis
        saveLongCounter(KEY_LAST_USE, lastUseAt)
    }

    fun dailyGoalMinutes(): Int {
        if (!::prefs.isInitialized) return 0
        return prefs.getInt(KEY_DAILY_GOAL_MINUTES, 0)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(0, 1440)
        prefs.edit().putInt(KEY_DAILY_GOAL_MINUTES, clamped).apply()
    }

    private fun rolloverDayIfNeeded() {
        if (!::prefs.isInitialized) return
        val today = dayKey()
        if (prefs.getInt(KEY_DAY, -1) != today) {
            opensToday.clear()
            prefs.edit()
                .putInt(KEY_DAY, today)
                .remove(KEY_OPENS)
                .apply()
        }
    }

    private fun dayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun loadCounter(key: String, into: HashMap<String, Int>) {
        into.clear()
        prefs.getString(key, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                o.keys().forEach { into[it] = o.getInt(it) }
            }
        }
    }

    private fun saveCounter(key: String, map: HashMap<String, Int>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(key, o.toString()).apply()
    }

    private fun loadLongCounter(key: String, into: HashMap<String, Long>) {
        into.clear()
        prefs.getString(key, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                o.keys().forEach { into[it] = o.getLong(it) }
            }
        }
    }

    private fun saveLongCounter(key: String, map: HashMap<String, Long>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(key, o.toString()).apply()
    }
}
