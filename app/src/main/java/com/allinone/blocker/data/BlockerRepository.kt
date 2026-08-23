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
    // Snapshot of maxBreaksPerSession/breakDurationMinutes taken the instant a
    // session starts — see maxBreaksPerSession()/breakDurationSeconds() below
    // for why a session must never consult the live StrictModeConfig again
    // after that moment.
    private const val KEY_SESSION_MAX_BREAKS = "lockdown_session_max_breaks"
    private const val KEY_SESSION_BREAK_DURATION_SECONDS = "lockdown_session_break_duration_seconds"
    private const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"
    private const val KEY_LOCKDOWN_HEARTBEAT_AT = "lockdown_heartbeat_at"
    // See markScheduleOccurrenceCancelled/isScheduleOccurrenceCancelled — a
    // set of "scheduleId<sep>startedAtMillis" strings for grace-period-cancelled
    // scheduled occurrences (Option C). Deliberately separate from KEY_SCHEDULES:
    // this never touches the LockdownSchedule list itself.
    private const val KEY_CANCELLED_OCCURRENCES = "grace_cancelled_schedule_occurrences"
    private const val OCCURRENCE_KEY_SEPARATOR = "::"
    // SESSION_LIMIT's session-window tracking (see sessionWindowUsedMs/
    // addSessionStint below) — KEY_SESSION_WINDOW_START is when the current
    // window began per package, KEY_SESSION_WINDOW_USED is how many ms of
    // use have landed inside it so far. Persisted (not just in-memory) so a
    // killed/restarted accessibility service doesn't quietly hand back a
    // free reset.
    private const val KEY_SESSION_WINDOW_START = "session_window_start"
    private const val KEY_SESSION_WINDOW_USED = "session_window_used_ms"

    private lateinit var prefs: SharedPreferences
    // Kept only so context-requiring checks (like the Active Plan auto-lock
    // in hasActiveTimedBlock/activeTimedBlocks below) can run from anywhere
    // without every call site having to pass a Context in. Always the
    // Application context — never an Activity — so this can't leak a screen.
    private lateinit var appContext: Context
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

    // -1 / -1L means "no session has ever taken a snapshot yet" — see
    // maxBreaksPerSession()/breakDurationSeconds(), which fall back to the
    // live StrictModeConfig only in that case.
    private val _sessionMaxBreaks = MutableStateFlow(-1)
    private val _sessionBreakDurationSeconds = MutableStateFlow(-1L)

    private val _strictMode = MutableStateFlow(StrictModeConfig())
    val strictMode: StateFlow<StrictModeConfig> = _strictMode

    // The list of independent Strict Alarms — see StrictAlarmEntry.kt.
    private val _strictAlarms = MutableStateFlow<List<StrictAlarmEntry>>(emptyList())
    val strictAlarms: StateFlow<List<StrictAlarmEntry>> = _strictAlarms

    private val opensToday = HashMap<String, Int>()
    private val lastUseAt = HashMap<String, Long>()
    private val sessionWindowStartAt = HashMap<String, Long>()
    private val sessionWindowUsedMs = HashMap<String, Long>()

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
        _sessionMaxBreaks.value = prefs.getInt(KEY_SESSION_MAX_BREAKS, -1)
        _sessionBreakDurationSeconds.value = prefs.getLong(KEY_SESSION_BREAK_DURATION_SECONDS, -1L)

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
        loadLongCounter(KEY_SESSION_WINDOW_START, sessionWindowStartAt)
        loadLongCounter(KEY_SESSION_WINDOW_USED, sessionWindowUsedMs)
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
        // The phone's Settings app is where Accessibility Service / Device
        // Admin get turned off — i.e. the actual kill switch for lockdown
        // enforcement — so it can never be added to the whitelist, no
        // matter what calls this. (Defense in depth: the accessibility
        // service also hard-corrals it regardless of whitelist state —
        // see AppBlockerAccessibilityService.shouldCorralDuringLockdown —
        // this just stops it from ever getting saved in the first place.)
        if (LockdownEngine.isSystemSettingsPackage(pkg)) return
        _whitelist.value = _whitelist.value + pkg
        persistWhitelist()
    }

    /** True for entries the whitelist UI should refuse to toggle on — see [addToWhitelist]. */
    fun isProtectedFromWhitelist(pkg: String): Boolean = LockdownEngine.isSystemSettingsPackage(pkg)

    fun removeFromWhitelist(pkg: String) {
        _whitelist.value = _whitelist.value - pkg
        persistWhitelist()
    }

    fun isWhitelisted(pkg: String): Boolean = _whitelist.value.contains(pkg)

    /**
     * A cheap "still alive and enforcing" timestamp, written every ~3s by
     * AppBlockerAccessibilityService's live-lockdown loop (tickLockdownGuard)
     * for as long as a lockdown session is running. LockdownWatchdogReceiver
     * reads this instead of independently re-deriving what's on screen: if
     * this timestamp is recent, the real enforcer is alive and already doing
     * its job correctly (including exempting whitelisted apps), so the
     * watchdog has nothing to do. Only when this goes stale — meaning the
     * whole process was killed — does the watchdog need to step in. A plain
     * Long write/read; no StateFlow needed since nothing observes this live.
     */
    fun recordLockdownHeartbeat(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LOCKDOWN_HEARTBEAT_AT, atMillis).apply()
    }

    fun lastLockdownHeartbeatAt(): Long = prefs.getLong(KEY_LOCKDOWN_HEARTBEAT_AT, 0L)

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

    /** Saves a new ordering of the schedule list (from drag-to-reorder). */
    fun reorderSchedules(reordered: List<LockdownSchedule>) {
        _schedules.value = reordered
        persistSchedules()
    }

    private fun persistSchedules() {
        val arr = JSONArray()
        _schedules.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
    }

    fun newScheduleId(): String = UUID.randomUUID().toString()

    fun startManualLock(minutes: Int) {
        val now = System.currentTimeMillis()
        val until = now + minutes * 60_000L
        _manualLockUntil.value = until
        prefs.edit().putLong(KEY_MANUAL_LOCK_UNTIL, until).apply()
        resetBreaksForNewSession()
        // Records the start of this session so LockdownCompletionRepository
        // can build a real completion record + celebration once it's
        // observed to have ended — see that file's header comment.
        LockdownCompletionRepository.markManualSessionStarted(now, minutes, indefinite = false)
        LockdownGuard.ensureRunning(appContext)
    }

    fun startManualLockIndefinite() {
        val now = System.currentTimeMillis()
        _manualLockUntil.value = Long.MAX_VALUE
        prefs.edit().putLong(KEY_MANUAL_LOCK_UNTIL, Long.MAX_VALUE).apply()
        resetBreaksForNewSession()
        // Indefinite sessions never end on their own (no endManualLock()),
        // so LockdownCompletionRepository is told about this one purely so
        // it can correctly close out whatever PREVIOUS session was still
        // being tracked — it will never generate a completion record for
        // this one itself. See markManualSessionStarted's kdoc.
        LockdownCompletionRepository.markManualSessionStarted(now, -1, indefinite = true)
        LockdownGuard.ensureRunning(appContext)
    }

    // No endManualLock(): a running lockdown always runs its course. The
    // only sanctioned way out mid-session is startEmergencyBreak(), which is
    // deliberately limited (see breaksRemaining()/maxBreaksPerSession) — a
    // free, unlimited "end early" button would make Lockdown Mode no
    // stronger than just... not turning it on.
    //
    // clearManualLockForGraceCancel() below looks like it breaks that rule,
    // but it doesn't: it's a narrow, time-boxed exception, only ever called
    // by LockdownGracePeriod, and only while still inside the short grace
    // window right after a session begins (see LockdownGracePeriod.GRACE_PERIOD_MS).
    // The point of that window is undoing a mistake made BEFORE the session
    // had any real effect — not an ongoing escape hatch — so once it closes
    // this function is never reached again for that session, and every
    // session that makes it past the grace window still always runs its
    // course exactly as documented above.

    /**
     * The ONE place a manual session ever gets set back to 0 before it would
     * naturally end. See the comment directly above for why this doesn't
     * weaken the "no endManualLock()" rule — this is deliberately NOT a
     * general-purpose end-early function, and nothing outside
     * [LockdownGracePeriod] should ever call it.
     */
    fun clearManualLockForGraceCancel() {
        _manualLockUntil.value = 0L
        prefs.edit().putLong(KEY_MANUAL_LOCK_UNTIL, 0L).apply()
    }

    /**
     * True if the schedule occurrence identified by [scheduleId] + the exact
     * moment it started ([startedAtMillis]) was cancelled during its grace
     * period (see [LockdownGracePeriod] / [markScheduleOccurrenceCancelled]).
     * [LockdownEngine.evaluate] checks this before treating a matching
     * window as active, so a cancelled occurrence is skipped — but only that
     * ONE occurrence; the schedule itself is untouched and still fires
     * normally at its next scheduled time (e.g. the following day).
     */
    fun isScheduleOccurrenceCancelled(scheduleId: String, startedAtMillis: Long): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getStringSet(KEY_CANCELLED_OCCURRENCES, emptySet())
            ?.contains(occurrenceKey(scheduleId, startedAtMillis)) == true
    }

    /**
     * Marks one specific occurrence of a recurring schedule as cancelled —
     * see [isScheduleOccurrenceCancelled]. Only ever called from
     * [LockdownGracePeriod]'s cancel path, during that occurrence's short
     * grace window. Prunes entries old enough that [LockdownEngine.evaluate]
     * could never match them again anyway, so this set doesn't grow forever.
     */
    fun markScheduleOccurrenceCancelled(scheduleId: String, startedAtMillis: Long) {
        if (!::prefs.isInitialized) return
        val current = (prefs.getStringSet(KEY_CANCELLED_OCCURRENCES, emptySet()) ?: emptySet()).toMutableSet()
        current.add(occurrenceKey(scheduleId, startedAtMillis))
        val cutoffMillis = startedAtMillis - 2 * 24 * 60 * 60_000L
        val pruned = current.filter { entry ->
            val ts = entry.substringAfterLast(OCCURRENCE_KEY_SEPARATOR).toLongOrNull()
            ts == null || ts >= cutoffMillis
        }.toSet()
        prefs.edit().putStringSet(KEY_CANCELLED_OCCURRENCES, pruned).apply()
    }

    private fun occurrenceKey(scheduleId: String, startedAtMillis: Long) =
        "$scheduleId$OCCURRENCE_KEY_SEPARATOR$startedAtMillis"

    // BUGFIX ("emergency breaks vanished overnight, mid-session, with no
    // setting ever touched"): these two used to read straight from the live
    // StrictModeConfig — _strictMode.value.maxBreaksPerSession /
    // breakDurationMinutes — every single time, for as long as the session
    // ran. setStrictMode() has its own guard that's SUPPOSED to stop that
    // live value from changing while a session is running (see its
    // comment), but that guard only works if it correctly notices a session
    // is running at the exact instant something calls setStrictMode() —
    // and across a many-hour session (phone asleep, the background process
    // getting killed and restarted by the OS one or more times, a stray
    // geofence transition, etc.) that's one more thing that has to go right
    // every time, for hours on end, with zero margin: the moment it's wrong
    // even once, the live value can slip, and because these two functions
    // kept reading that same live value forever after, the session's break
    // allotment silently — and permanently, for the rest of that session —
    // followed it down. There was no way to recover once that happened.
    //
    // The fix: stop depending on catching every possible moment the live
    // setting could change, and instead make the session immune to it.
    // The instant a session starts, resetBreaksForNewSession() below writes
    // down (freezes) this session's own max-breaks/duration. From then on,
    // for the rest of THAT session, these two functions only ever consult
    // that frozen snapshot — never the live StrictModeConfig again — so it
    // no longer matters whether something nudges the live setting mid-
    // session; this session simply isn't looking at it anymore. The next
    // session takes a fresh snapshot from whatever the live setting says at
    // the time it starts, so legitimate changes made BETWEEN sessions (the
    // only time the settings screen allows changes anyway) still apply
    // normally.
    //
    // Falls back to the live config only when no snapshot has ever been
    // taken yet (a fresh install, or before this app update's first
    // session) — see _sessionMaxBreaks/_sessionBreakDurationSeconds.
    fun maxBreaksPerSession(): Int =
        _sessionMaxBreaks.value.takeIf { it >= 0 } ?: _strictMode.value.maxBreaksPerSession

    fun breakDurationSeconds(): Long =
        _sessionBreakDurationSeconds.value.takeIf { it >= 0L } ?: (_strictMode.value.breakDurationMinutes * 60L)

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
        // Freeze THIS session's own break allotment from whatever the live
        // setting says right now — see maxBreaksPerSession()/
        // breakDurationSeconds() above for why the session must never
        // consult the live StrictModeConfig again after this point.
        val snapshotMaxBreaks = _strictMode.value.maxBreaksPerSession
        val snapshotDurationSeconds = _strictMode.value.breakDurationMinutes * 60L
        _sessionMaxBreaks.value = snapshotMaxBreaks
        _sessionBreakDurationSeconds.value = snapshotDurationSeconds
        prefs.edit()
            .putInt(KEY_BREAK_USES, 0)
            .putLong(KEY_BREAK_UNTIL, 0L)
            .putInt(KEY_SESSION_MAX_BREAKS, snapshotMaxBreaks)
            .putLong(KEY_SESSION_BREAK_DURATION_SECONDS, snapshotDurationSeconds)
            .apply()
    }

    fun maybeResetBreaksForScheduledSession(sessionEndsAtMillis: Long) {
        if (sessionEndsAtMillis <= 0) return
        if (prefs.getLong(KEY_BREAK_SESSION_ANCHOR, -1L) == sessionEndsAtMillis) return
        prefs.edit().putLong(KEY_BREAK_SESSION_ANCHOR, sessionEndsAtMillis).apply()
        resetBreaksForNewSession()
    }

    /**
     * True while a lockdown session (manual or scheduled) is currently live —
     * including while an emergency break is running, since a break is a
     * temporary pause *within* a session, not the end of one. Used to freeze
     * the emergency-break settings themselves (see [setStrictMode]) so a
     * session can't be quietly loosened from the inside.
     */
    fun isLockdownSessionRunning(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val decision = LockdownEngine.evaluate(
            manualLockUntil = _manualLockUntil.value,
            schedules = _schedules.value,
            nowMillis = nowMillis,
            breakUntilMillis = _breakUntil.value
        )
        return decision.active || decision.onBreak
    }

    fun setStrictMode(config: StrictModeConfig) {
        // Emergency-break count/duration are the one part of Strict Mode
        // config that must never change mid-session — otherwise "2 breaks
        // of 5 minutes" is just a suggestion you can raise to "99 breaks of
        // 60 minutes" the moment you're inside a break, which defeats the
        // entire point of the limit (this was a real bug). Every other field
        // (PIN, frictions, pledge, location zones) is left alone here —
        // those already have their own guards on the settings screen
        // (StrictModeGate) — this only pins the two break fields back to
        // their session-start values while a session is live.
        val safeConfig = if (isLockdownSessionRunning()) {
            val current = _strictMode.value
            config.copy(
                maxBreaksPerSession = current.maxBreaksPerSession,
                breakDurationMinutes = current.breakDurationMinutes
            )
        } else {
            config
        }
        _strictMode.value = safeConfig
        prefs.edit().putString(KEY_STRICT_MODE, safeConfig.toJson().toString()).apply()
    }

    /** One blocked app that is currently mid-block for a reason that has a built-in end time. */
    data class ActiveTimedBlock(val appName: String, val reason: String)

    /**
     * Every blocked app that is, right now, being actively blocked by a
     * rule with a built-in expiry (see [BlockEngine.evaluateTimeBound] for
     * exactly which rule types count and why PERMANENT never does). This
     * drives Strict Mode's Active Plan auto-lock — see
     * [StrictModeGate.isSettingsLockedByPlan].
     */
    fun activeTimedBlocks(nowMillis: Long = System.currentTimeMillis()): List<ActiveTimedBlock> {
        if (!::appContext.isInitialized) return emptyList()
        return _apps.value.mapNotNull { app ->
            val decision = BlockEngine.evaluateTimeBound(appContext, app, nowMillis)
            if (decision.blocked) ActiveTimedBlock(app.appName, decision.reason) else null
        }
    }

    /** True if at least one blocked app is currently mid-block for a reason that will end on its own. */
    fun hasActiveTimedBlock(nowMillis: Long = System.currentTimeMillis()): Boolean =
        activeTimedBlocks(nowMillis).isNotEmpty()

    // List-based CRUD, mirrors addSchedule/updateSchedule/removeSchedule above.

  fun addStrictAlarmEntry(entry: StrictAlarmEntry) {
        _strictAlarms.value = _strictAlarms.value + entry
        persistStrictAlarms()
    }

    /**
     * Adds several brand-new, fully independent alarm entries in one go
     * (used by the "Quick add" multi-time screen). Each entry in [entries]
     * is a separate alarm — its own card, its own on/off, its own days —
     * the same as if you had tapped "+" and saved several times in a row.
     * This just batches the repository write into a single update.
     */
    fun addStrictAlarmEntries(entries: List<StrictAlarmEntry>) {
        if (entries.isEmpty()) return
        _strictAlarms.value = _strictAlarms.value + entries
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

    fun removeStrictAlarmEntries(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _strictAlarms.value = _strictAlarms.value.filterNot { it.id in idSet }
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

    /**
     * How many milliseconds of [pkg]'s SESSION_LIMIT allowance are already
     * used up from stints EARLIER in the current session window — not
     * counting whatever stint is live right now, which the caller (the
     * accessibility service) tracks separately and adds on top. If more
     * than [windowMinutes] has passed since the window began, it's rolled
     * over to a fresh, empty one first — that rollover is what makes the
     * window actually "reset" after that long.
     */
    fun sessionWindowUsedMs(pkg: String, windowMinutes: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        rolloverSessionWindowIfNeeded(pkg, windowMinutes, nowMillis)
        return sessionWindowUsedMs[pkg] ?: 0L
    }

    /**
     * Folds a just-finished stint of using [pkg] ([stintMs] long) into its
     * running session-window total. Called the moment [pkg] leaves the
     * foreground, so the next time it's opened, [sessionWindowUsedMs]
     * already reflects this time — closing and immediately reopening the
     * app can never hand back a clean slate; only actually stepping away
     * for the full [windowMinutes] does.
     */
    fun addSessionStint(pkg: String, stintMs: Long, windowMinutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        if (stintMs <= 0) return
        rolloverSessionWindowIfNeeded(pkg, windowMinutes, nowMillis)
        sessionWindowUsedMs[pkg] = (sessionWindowUsedMs[pkg] ?: 0L) + stintMs
        saveLongCounter(KEY_SESSION_WINDOW_START, sessionWindowStartAt)
        saveLongCounter(KEY_SESSION_WINDOW_USED, sessionWindowUsedMs)
    }

    private fun rolloverSessionWindowIfNeeded(pkg: String, windowMinutes: Int, nowMillis: Long) {
        val start = sessionWindowStartAt[pkg]
        val windowMs = windowMinutes.coerceAtLeast(1) * 60_000L
        if (start == null || nowMillis - start >= windowMs) {
            sessionWindowStartAt[pkg] = nowMillis
            sessionWindowUsedMs[pkg] = 0L
            saveLongCounter(KEY_SESSION_WINDOW_START, sessionWindowStartAt)
            saveLongCounter(KEY_SESSION_WINDOW_USED, sessionWindowUsedMs)
        }
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
