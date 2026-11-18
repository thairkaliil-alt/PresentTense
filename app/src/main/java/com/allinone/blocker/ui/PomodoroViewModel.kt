package com.allinone.blocker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allinone.blocker.data.Achievement
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.DailyTasksRepository
import com.allinone.blocker.data.DailyTasksState
import com.allinone.blocker.data.DayStats
import com.allinone.blocker.data.PomodoroStatsMath
import com.allinone.blocker.data.PomodoroStatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PomodoroMode {
    FOCUS, SHORT_BREAK, LONG_BREAK
}

enum class TimerState {
    IDLE, RUNNING, PAUSED
}

data class PomodoroSettings(
    val focusDuration: Int = 25,        // minutes
    val shortBreakDuration: Int = 5,    // minutes
    val longBreakDuration: Int = 15,    // minutes
    val sessionsUntilLongBreak: Int = 4,
    // How many focus sessions you want to finish per day. Drives the goal
    // ring and the "Daily goal reached" celebration on the stats card.
    val dailyGoalSessions: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartPomodoros: Boolean = false,
    // ── Ultra Focus ──────────────────────────────────────────────────────
    // When on, every FOCUS session also starts a full-phone Lockdown that
    // lasts exactly as long as the focus time, and ends by itself when the
    // focus time ends (so breaks are always free).
    val ultraFocusEnabled: Boolean = false,
    // After a break: false = wait for the user to tap "Start next focus
    // session" (the default), true = start the next locked focus session
    // automatically as soon as the break ends.
    val ultraAutoStartNext: Boolean = false,
    // ── End-of-session alerts ────────────────────────────────────────────
    // Chime and vibration played when a focus session or break ends
    // (see PomodoroAlerts.kt).
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

data class PomodoroState(
    val mode: PomodoroMode = PomodoroMode.FOCUS,
    val timerState: TimerState = TimerState.IDLE,
    val timeRemaining: Int = 25 * 60,   // seconds
    val totalTime: Int = 25 * 60,       // seconds
    // Where you are in the current round (0..sessionsUntilLongBreak). It ONLY
    // drives the little dots and when the long break comes. It is NOT the
    // "sessions today" stat — that comes from the logbook (PomodoroStats.kt).
    val cycleCount: Int = 0,
    // Seconds of the focus countdown that is running RIGHT NOW and are not in
    // the logbook yet (they are saved when it pauses / ends). The stats card
    // adds this on top of the logbook so the numbers tick up live.
    val liveFocusSeconds: Int = 0,
    // The calendar day the stats card treats as "today". Updated by itself
    // when midnight passes, even if the app stays open.
    val today: LocalDate = LocalDate.now(),
    // The "well done!" message earned by the last finished session (goal
    // reached, new record, streak…). Cleared when the next focus starts.
    val achievement: Achievement? = null,
    val settings: PomodoroSettings = PomodoroSettings(),
    // Bumped by +1 every time a focus session completes. The screen watches
    // this (not a plain Boolean) so a LaunchedEffect can fire a celebration
    // exactly once per completion, even if two completions happen back to
    // back before the UI recomposes in between.
    val celebrationTick: Int = 0,
    // True while a focus session is running AND the Lockdown that Ultra
    // Focus started for it is still in force. While true, pause / reset /
    // skip are disabled — the lockdown can't be ended early, so the timer
    // must not be able to wander away from it either.
    val ultraSessionActive: Boolean = false,
    // The exact moment (ms) the Ultra Focus lockdown ends. The timer reads
    // this so the countdown and the lockdown can never drift apart.
    val ultraEndsAtMillis: Long = 0L,
    // True after a break finishes (or is skipped) while Ultra Focus is on:
    // the screen then shows the "Start next focus session" button.
    val awaitingNextFocus: Boolean = false,
    // A short one-off message for the Ultra Focus card, e.g. when a
    // lockdown was already running so a new one wasn't started.
    val ultraNotice: String? = null,
    // ── Hand-off between sessions ────────────────────────────────────────
    // Set to the mode that JUST FINISHED (on its own, not skipped) while the
    // next session sits ready and waiting. The screen shows the "Up next"
    // hand-off card while this is non-null. Cleared as soon as the next
    // session starts or the user picks a mode themselves.
    val handoffFrom: PomodoroMode? = null,
    // Seconds left in the visible "starting automatically in N…" countdown
    // that gives the user a moment to cancel. 0 = no countdown running.
    val autoStartInSeconds: Int = 0
)

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state

    // The logbook: one line per calendar day (see PomodoroStats.kt). The stats
    // card draws today / week / all-time / streak straight from this.
    private val stats = PomodoroStatsRepository.get(application)
    val history: StateFlow<Map<LocalDate, DayStats>> = stats.days

    // The Daily Tasks (see DailyTasks.kt). Finishing a full Pomodoro set fills
    // the built-in "Complete a full Pomodoro set" task. The screen watches this
    // to show a dot on the tasks icon when a chest is ready to open.
    private val dailyTasks = DailyTasksRepository.get(application)
    val dailyTasksState: StateFlow<DailyTasksState> = dailyTasks.state

    private var timerJob: Job? = null
    private var autoStartJob: Job? = null
    private val prefs = application.getSharedPreferences(PomodoroAlerts.PREFS_NAME, Context.MODE_PRIVATE)

    // Wall-clock moment the running session ends. The countdown is worked out
    // from this (not by counting ticks) so it stays correct even if Android
    // pauses the app for a while — and it is what the background alarm uses.
    private var runEndsAtMillis: Long = 0L

    // The end time the background alarm was scheduled for. Passed to
    // PomodoroAlerts.fire so the alarm and the running timer can never both
    // play the chime for the same session.
    private var alarmEndsAt: Long = 0L

    // ── Bookkeeping for the focus countdown that is running right now ────
    // A focus session can be run in several pieces (start, pause, resume…).
    // Each piece gets a unique id and remembers how many seconds were left
    // when it began. When the piece ends, exactly the seconds that really
    // ran go into the logbook — once. (0 = no focus piece is running.)
    private var segRunId: Long = 0L
    private var segStartRemaining: Int = 0

    // The day (as a day number) the round counter was last used on. A new day
    // starts a fresh round, but only at a clean "ready to focus" moment.
    private var cycleDayEpoch: Long = 0L

    companion object {
        // Remembered across app restarts so an Ultra Focus session that is
        // in progress can be picked back up if Android clears this screen.
        private const val KEY_ULTRA_ENDS_AT = "ultra_ends_at"
        private const val KEY_ULTRA_STARTED_AT = "ultra_started_at"

        // Same idea for ANY running session (focus or break), so it can be
        // picked back up if Android clears the app while the screen is off.
        private const val KEY_RUN_MODE = "run_mode"
        private const val KEY_RUN_ENDS_AT = "run_ends_at"
        private const val KEY_RUN_TOTAL = "run_total"
        private const val KEY_RUN_START_REMAINING = "run_start_remaining"
        private const val KEY_RUN_ID = "run_id"

        // The round counter (dots + long break) and the day it was last used.
        private const val KEY_CYCLE = "cycle_count"
        private const val KEY_CYCLE_DAY = "cycle_day"

        // Set once the numbers from the OLD counter system were moved into the logbook.
        private const val KEY_STATS_MIGRATED = "stats_migrated_v2"

        // The "a session just finished and the next one is waiting" note.
        // Saved to the phone so it survives the main screen being rebuilt
        // (which happens every time a lockdown ends and the app is re-opened)
        // — otherwise the overlay would flash and then vanish.
        private const val KEY_HANDOFF_FROM = "handoff_from"
        private const val KEY_HANDOFF_AT = "handoff_at"
        private const val KEY_HANDOFF_LOCKED = "handoff_locked"

        // The newest timer. When the main screen is rebuilt, the OLD timer is
        // cleaned up AFTER the new one has started, and must not wipe out
        // the new one's shared hand-off note.
        @Volatile
        private var latestInstance: PomodoroViewModel? = null

        /** How long the "starting automatically…" countdown lasts (the screen's button fill uses this too). */
        const val AUTO_START_SECONDS = 5
    }

    init {
        loadSettings()
        migrateOldStatsIfNeeded()
        loadCycle()
        restoreUltraSessionIfAny()
        restoreRunSessionIfAny()
        restoreHandoffIfAny()
        syncDay()
        // Lets the "Session complete" screen start the next phase in one tap.
        latestInstance = this
        PomodoroHandoff.register { startTimer() }
        startDayWatcher()
    }

    private fun loadSettings() {
        val settings = PomodoroSettings(
            focusDuration = prefs.getInt("focus_duration", 25),
            shortBreakDuration = prefs.getInt("short_break_duration", 5),
            longBreakDuration = prefs.getInt("long_break_duration", 15),
            sessionsUntilLongBreak = prefs.getInt("sessions_until_long_break", 4),
            dailyGoalSessions = prefs.getInt("daily_goal_sessions", 4).coerceIn(1, 20),
            autoStartBreaks = prefs.getBoolean("auto_start_breaks", false),
            autoStartPomodoros = prefs.getBoolean("auto_start_pomodoros", false),
            ultraFocusEnabled = prefs.getBoolean("ultra_focus_enabled", false),
            ultraAutoStartNext = prefs.getBoolean("ultra_auto_start_next", false),
            soundEnabled = prefs.getBoolean(PomodoroAlerts.KEY_SOUND, true),
            vibrationEnabled = prefs.getBoolean(PomodoroAlerts.KEY_VIBRATION, true)
        )
        _state.value = _state.value.copy(settings = settings)

        // Initialize time based on current mode
        val totalTime = durationSecondsFor(_state.value.mode, settings)
        _state.value = _state.value.copy(
            timeRemaining = totalTime,
            totalTime = totalTime
        )
    }

    private fun durationSecondsFor(mode: PomodoroMode, settings: PomodoroSettings): Int =
        when (mode) {
            PomodoroMode.FOCUS -> settings.focusDuration * 60
            PomodoroMode.SHORT_BREAK -> settings.shortBreakDuration * 60
            PomodoroMode.LONG_BREAK -> settings.longBreakDuration * 60
        }

    // ─────────────────────────────────────────────────────────────────────
    // LOGBOOK + ROUND COUNTER
    //
    // The old version kept two running counters that were wiped at midnight,
    // and one of them ("completed sessions") also decided when the long break
    // came. That mixed two jobs into one number, so it could drift, and the
    // app never knew anything about yesterday.
    //
    // Now there are two separate things:
    //   • The LOGBOOK (PomodoroStats.kt): every piece of real focus time is
    //     written into the line for the day it ended on. All statistics are
    //     calculated from it.
    //   • The ROUND COUNTER (cycleCount): only "how many sessions into this
    //     round am I?" — used for the dots and the long break, nothing else.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * One-time move of the numbers the OLD system had saved ("completed
     * sessions" + "focus seconds today") into the new logbook, so nothing you
     * already earned today disappears when you install this update.
     */
    private fun migrateOldStatsIfNeeded() {
        if (prefs.getBoolean(KEY_STATS_MIGRATED, false)) return

        val oldSessions = prefs.getInt("completed_sessions", 0)
        val oldSeconds = prefs.getInt("focus_seconds_today", 0)
        val oldDayKey = prefs.getInt("stats_day_key", 0)   // old scheme: year*1000 + dayOfYear
        val today = LocalDate.now()
        val oldDate = runCatching {
            if (oldDayKey > 0) LocalDate.ofYearDay(oldDayKey / 1000, oldDayKey % 1000) else today
        }.getOrDefault(today)

        stats.addLegacyDay(oldDate, oldSessions, oldSeconds)

        // The old counter also served as the round counter, so keep that too.
        val cycle = if (oldDate == today) oldSessions else 0
        prefs.edit()
            .putInt(KEY_CYCLE, cycle)
            .putLong(KEY_CYCLE_DAY, today.toEpochDay())
            .putBoolean(KEY_STATS_MIGRATED, true)
            .remove("completed_sessions")
            .remove("focus_seconds_today")
            .remove("stats_day_key")
            .commit()
    }

    private fun loadCycle() {
        val today = LocalDate.now()
        cycleDayEpoch = prefs.getLong(KEY_CYCLE_DAY, today.toEpochDay())
        _state.value = _state.value.copy(
            cycleCount = prefs.getInt(KEY_CYCLE, 0).coerceAtLeast(0),
            today = today
        )
    }

    private fun saveCycle(count: Int) {
        cycleDayEpoch = LocalDate.now().toEpochDay()
        prefs.edit()
            .putInt(KEY_CYCLE, count)
            .putLong(KEY_CYCLE_DAY, cycleDayEpoch)
            .apply()
    }

    /**
     * Keeps "today" correct. Called when the app starts, whenever a session
     * starts, and every 30 seconds in the background — so the stats card flips
     * to the new day by itself right after midnight, even if the app was left
     * open. A new day also starts a fresh round, but only when you are sitting
     * at a clean "ready to focus" moment (never in the middle of a session or
     * while a break is waiting).
     */
    private fun syncDay() {
        // The Daily Tasks flip to the new day at midnight too.
        dailyTasks.refreshDay()
        val today = LocalDate.now()
        var s = _state.value
        if (s.today != today) {
            s = s.copy(today = today, achievement = null)
        }
        val cleanFocusIdle = s.mode == PomodoroMode.FOCUS &&
            s.timerState == TimerState.IDLE &&
            !s.ultraSessionActive
        if (cleanFocusIdle && cycleDayEpoch != today.toEpochDay()) {
            saveCycle(0)
            s = s.copy(cycleCount = 0)
        }
        _state.value = s
    }

    private fun startDayWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                syncDay()
            }
        }
    }

    /**
     * Writes the focus time of the piece that is ending RIGHT NOW into the
     * logbook, then forgets the piece.
     *
     *  • [remaining]  seconds left on the clock at this moment.
     *  • [completed]  true only when the countdown really reached zero —
     *                 that is the one case that adds +1 session.
     *
     * Returns the "well done!" message this earned, if any. A piece is saved
     * at most once (the logbook refuses repeats), so a restart can never
     * double count it.
     */
    private fun closeFocusSegment(remaining: Int, completed: Boolean): Achievement? {
        val hadRun = segRunId > 0L
        // Nothing was running and nothing finished: nothing to save.
        if (!hadRun && !completed) return null

        // Safety net: a finished session must never be lost, even if its
        // bookkeeping is somehow missing. It is then counted with 0 seconds.
        val runId = if (hadRun) segRunId else System.currentTimeMillis()
        val seconds = when {
            !hadRun -> 0
            completed -> segStartRemaining
            else -> (segStartRemaining - remaining).coerceIn(0, segStartRemaining)
        }
        segRunId = 0L
        segStartRemaining = 0

        val now = System.currentTimeMillis()
        val before = stats.days.value
        val saved = stats.recordFocusSegment(runId, now, seconds, completed)
        if (!saved || !completed) return null

        return PomodoroStatsMath.detectAchievement(
            before = before,
            after = stats.days.value,
            today = PomodoroStatsMath.dateOf(now),
            goal = _state.value.settings.dailyGoalSessions
        )
    }

    /** Seconds left on the running clock right now (exact, from the real end time). */
    private fun remainingNow(): Int =
        if (runEndsAtMillis > 0L) secondsUntil(runEndsAtMillis) else _state.value.timeRemaining

    // ─────────────────────────────────────────────────────────────────────
    // WHAT COMES NEXT, AND WHAT THE ALERT SAYS
    // ─────────────────────────────────────────────────────────────────────

    /** The break that follows a focus session, given how many are done after it. */
    private fun nextModeAfterFocus(completedAfter: Int): PomodoroMode {
        val perCycle = _state.value.settings.sessionsUntilLongBreak.coerceAtLeast(1)
        return if (completedAfter % perCycle == 0) PomodoroMode.LONG_BREAK else PomodoroMode.SHORT_BREAK
    }

    private fun nextModeAfter(mode: PomodoroMode, cycleCount: Int): PomodoroMode =
        if (mode == PomodoroMode.FOCUS) nextModeAfterFocus(cycleCount + 1) else PomodoroMode.FOCUS

    /**
     * Pins a "what's next" note for the Ultra Focus celebration screen (see
     * PomodoroHandoff.kt). Only used after a LOCKED focus session, because
     * that is the only time the celebration screen appears.
     */
    private fun publishHandoffNote(endedMode: PomodoroMode) {
        val s = _state.value
        PomodoroHandoff.publish(
            PomodoroHandoff.Pending(
                endedMode = endedMode,
                nextMode = s.mode,
                nextMinutes = s.totalTime / 60,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    /** Writes the "next session is waiting" note to the phone's storage. */
    private fun saveHandoff(from: PomodoroMode, locked: Boolean, at: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_HANDOFF_FROM, from.name)
            .putLong(KEY_HANDOFF_AT, at)
            .putBoolean(KEY_HANDOFF_LOCKED, locked)
            .apply()
    }

    private fun clearSavedHandoff() {
        prefs.edit()
            .remove(KEY_HANDOFF_FROM)
            .remove(KEY_HANDOFF_AT)
            .remove(KEY_HANDOFF_LOCKED)
            .apply()
    }

    /**
     * Puts the "session finished, next one waiting" state back after the main
     * screen was rebuilt. Without this the finished-session overlay flashes
     * for a moment and then disappears, because the in-memory note is lost
     * with the old screen.
     *
     * Only applies when the timer is sitting idle: a session that is running
     * again, or a hand-off that was just worked out, always wins over it.
     */
    private fun restoreHandoffIfAny() {
        val raw = prefs.getString(KEY_HANDOFF_FROM, null) ?: return
        val s = _state.value
        if (s.handoffFrom != null) return   // already set up by a finish-while-away above

        val from = runCatching { PomodoroMode.valueOf(raw) }.getOrNull()
        val at = prefs.getLong(KEY_HANDOFF_AT, 0L)
        val locked = prefs.getBoolean(KEY_HANDOFF_LOCKED, false)
        if (from == null || at <= 0L ||
            s.timerState != TimerState.IDLE ||
            PomodoroStatsMath.dateOf(at) != LocalDate.now()
        ) {
            clearSavedHandoff()
            return
        }

        // Same result the timer reached when the session first ended. The
        // round counter already includes the session that just finished.
        val next = if (from == PomodoroMode.FOCUS) nextModeAfterFocus(s.cycleCount) else PomodoroMode.FOCUS
        switchMode(next, s.cycleCount)        // (this also clears the saved note...)
        saveHandoff(from, locked, at)         // ...so keep it saved, with its original time
        _state.value = _state.value.copy(
            handoffFrom = from,
            awaitingNextFocus = from != PomodoroMode.FOCUS && _state.value.settings.ultraFocusEnabled
        )
        if (locked && from == PomodoroMode.FOCUS) {
            PomodoroHandoff.publish(
                PomodoroHandoff.Pending(
                    endedMode = from,
                    nextMode = next,
                    nextMinutes = _state.value.totalTime / 60,
                    createdAtMillis = at
                )
            )
        }
    }

    /** Title + text for the end-of-session notification. */
    private fun alertCopy(ended: PomodoroMode, next: PomodoroMode): Pair<String, String> {
        val s = _state.value.settings
        val nextMinutes = durationSecondsFor(next, s) / 60
        val nextName = when (next) {
            PomodoroMode.FOCUS -> "focus session"
            PomodoroMode.SHORT_BREAK -> "short break"
            PomodoroMode.LONG_BREAK -> "long break"
        }
        return if (ended == PomodoroMode.FOCUS) {
            val text = if (s.autoStartBreaks) {
                "Nice work. Your $nextMinutes-min $nextName is starting."
            } else {
                "Nice work. Time for a $nextMinutes-min $nextName."
            }
            "Focus session complete" to text
        } else {
            val text = when {
                s.ultraFocusEnabled && s.ultraAutoStartNext ->
                    "Your next Ultra Focus session ($nextMinutes min) is locking in now."
                s.ultraFocusEnabled ->
                    "Open Present Tense and tap Start locked focus when you're ready."
                s.autoStartPomodoros ->
                    "Your next $nextMinutes-min focus session is starting."
                else ->
                    "Ready to focus? Your next session is $nextMinutes min."
            }
            "Break's over" to text
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // RUN SESSION  (end time, background alarm, survive-a-restart memory)
    // ─────────────────────────────────────────────────────────────────────

    /** Asks Android to wake the app and play the alert at [endsAt]. */
    private fun scheduleEndAlarm(endsAt: Long) {
        if (endsAt <= System.currentTimeMillis()) return
        alarmEndsAt = endsAt
        val s = _state.value
        val next = nextModeAfter(s.mode, s.cycleCount)
        val copy = alertCopy(s.mode, next)
        PomodoroAlerts.scheduleEnd(
            getApplication<Application>(), endsAt, s.mode, copy.first, copy.second,
            locked = s.ultraSessionActive
        )
        // The live countdown notification (PomodoroNotification.kt). This one
        // function runs every time a session starts, resumes or is restored
        // after the app was closed, so the notification is always in step with
        // the alarm above. It counts down by itself, so it needs no updates.
        PomodoroNotification.show(
            getApplication<Application>(), s.mode, endsAt, s.cycleCount,
            s.settings.sessionsUntilLongBreak, locked = s.ultraSessionActive
        )
    }

    /** Re-sends the alarm if one is running — used when a setting that changes its wording is edited. */
    private fun refreshEndAlarm() {
        if (_state.value.timerState == TimerState.RUNNING && alarmEndsAt > 0L) {
            scheduleEndAlarm(alarmEndsAt)
        }
    }

    /** Called when a session (re)starts running: fixes its end time, remembers it, sets the alarm. */
    private fun beginRunSession() {
        val s = _state.value
        val now = System.currentTimeMillis()
        runEndsAtMillis = now + s.timeRemaining * 1000L

        // Start the bookkeeping for this piece of focus time (breaks need none).
        segRunId = if (s.mode == PomodoroMode.FOCUS) now else 0L
        segStartRemaining = s.timeRemaining

        prefs.edit()
            .putString(KEY_RUN_MODE, s.mode.name)
            .putLong(KEY_RUN_ENDS_AT, runEndsAtMillis)
            .putInt(KEY_RUN_TOTAL, s.totalTime)
            .putInt(KEY_RUN_START_REMAINING, s.timeRemaining)
            .putLong(KEY_RUN_ID, now)
            .apply()
        scheduleEndAlarm(if (s.ultraSessionActive) s.ultraEndsAtMillis else runEndsAtMillis)
    }

    /** Called when the running session stops for any reason (pause, reset, skip, finished). */
    private fun clearRunSession() {
        runEndsAtMillis = 0L
        alarmEndsAt = 0L
        segRunId = 0L
        segStartRemaining = 0
        prefs.edit()
            .remove(KEY_RUN_MODE)
            .remove(KEY_RUN_ENDS_AT)
            .remove(KEY_RUN_TOTAL)
            .remove(KEY_RUN_START_REMAINING)
            .remove(KEY_RUN_ID)
            .apply()
        PomodoroAlerts.cancelEnd(getApplication<Application>())
        // Nothing is running any more, so the live countdown goes too.
        PomodoroNotification.cancel(getApplication<Application>())
    }

    /**
     * A focus session reached its end while the app was closed. It is written
     * into the logbook on the day it REALLY ended (so a session that finished
     * yesterday counts for yesterday instead of being lost), and — if it ended
     * today — the break hand-off is shown. The alarm has already played the
     * chime, so nothing is announced again here.
     */
    private fun finishFocusWhileAway(endsAt: Long, runId: Long, seconds: Int, locked: Boolean) {
        stats.recordFocusSegment(runId, endsAt, seconds, completed = true)

        if (PomodoroStatsMath.dateOf(endsAt) != LocalDate.now()) {
            // It ended on an earlier day: it is counted there. Today starts fresh.
            saveCycle(0)
            switchMode(PomodoroMode.FOCUS, 0)
            return
        }

        val newCycle = _state.value.cycleCount + 1
        saveCycle(newCycle)
        val nextMode = nextModeAfterFocus(newCycle)
        // The last session of a round brings you to the long break: a full set.
        if (nextMode == PomodoroMode.LONG_BREAK) {
            dailyTasks.recordPomodoroSet(runId, endsAt)
        }
        switchMode(nextMode, newCycle)
        _state.value = _state.value.copy(handoffFrom = PomodoroMode.FOCUS)
        if (locked) publishHandoffNote(PomodoroMode.FOCUS)
        saveHandoff(PomodoroMode.FOCUS, locked)
    }

    /** A break reached its end while the app was closed: get the next focus session ready. */
    private fun finishBreakWhileAway(mode: PomodoroMode, endsAt: Long) {
        if (PomodoroStatsMath.dateOf(endsAt) != LocalDate.now()) return
        switchMode(PomodoroMode.FOCUS, _state.value.cycleCount)
        _state.value = _state.value.copy(
            handoffFrom = mode,
            awaitingNextFocus = _state.value.settings.ultraFocusEnabled
        )
        saveHandoff(mode, locked = false)
    }

    /**
     * If Android cleared the app while a focus session or break was running
     * (screen off, phone low on memory), pick it back up — or, if it already
     * ended while we were gone, credit it and show the hand-off.
     *
     * Important: the seconds of a run that is STILL going are never saved at
     * this point. They are only saved when that run ends (pause / finish), so
     * restarting the app can never count the same seconds twice.
     */
    private fun restoreRunSessionIfAny() {
        // The Ultra Focus restore above already took over this session.
        if (_state.value.ultraSessionActive) return
        val endsAt = prefs.getLong(KEY_RUN_ENDS_AT, 0L)
        if (endsAt <= 0L) return

        val mode = runCatching {
            PomodoroMode.valueOf(prefs.getString(KEY_RUN_MODE, "") ?: "")
        }.getOrNull()
        if (mode == null) {
            clearRunSession()
            return
        }

        val settings = _state.value.settings
        val total = prefs.getInt(KEY_RUN_TOTAL, 0).let {
            if (it > 0) it else durationSecondsFor(mode, settings)
        }
        val startRemaining = prefs.getInt(KEY_RUN_START_REMAINING, total).coerceIn(0, total)
        // Runs saved by an older version have no id: their end time is unique too.
        val runId = prefs.getLong(KEY_RUN_ID, 0L).let { if (it > 0L) it else endsAt }
        val now = System.currentTimeMillis()

        if (now < endsAt) {
            val remaining = secondsUntil(endsAt)
            runEndsAtMillis = endsAt
            if (mode == PomodoroMode.FOCUS) {
                segRunId = runId
                segStartRemaining = startRemaining
            }
            _state.value = _state.value.copy(
                mode = mode,
                timerState = TimerState.RUNNING,
                timeRemaining = remaining,
                totalTime = total.coerceAtLeast(remaining),
                liveFocusSeconds = if (mode == PomodoroMode.FOCUS) {
                    (startRemaining - remaining).coerceAtLeast(0)
                } else {
                    0
                }
            )
            scheduleEndAlarm(endsAt)
            launchTickLoop()
        } else {
            clearRunSession()
            if (mode == PomodoroMode.FOCUS) {
                finishFocusWhileAway(endsAt, runId, startRemaining, locked = false)
            } else {
                finishBreakWhileAway(mode, endsAt)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ULTRA FOCUS  (Pomodoro focus time <-> full-phone Lockdown)
    //
    // The whole feature rests on one simple fact: Lockdown is started with
    // a number of minutes and then ends by itself. So an Ultra Focus session
    // just starts a Lockdown for exactly the focus time. When the focus time
    // is over the lockdown is over too — nothing ever has to "end" it, which
    // keeps the app's rule that a running lockdown always runs its course.
    // Breaks are therefore automatically free, and the next focus session
    // simply starts a fresh lockdown.
    // ─────────────────────────────────────────────────────────────────────

    /** Seconds left until [endsAtMillis], rounded up, never below 0. */
    private fun secondsUntil(endsAtMillis: Long): Int =
        (((endsAtMillis - System.currentTimeMillis()) + 999L) / 1000L)
            .toInt()
            .coerceAtLeast(0)

    private fun clearUltraSession() {
        prefs.edit()
            .putLong(KEY_ULTRA_ENDS_AT, 0L)
            .putLong(KEY_ULTRA_STARTED_AT, 0L)
            .apply()
        _state.value = _state.value.copy(ultraSessionActive = false, ultraEndsAtMillis = 0L)
    }

    /**
     * Starts the lockdown for a brand-new Ultra Focus session. If a lockdown
     * is ALREADY running (manual or scheduled) we deliberately do not start
     * another one: starting a new manual lockdown replaces the old end time,
     * which could make an existing long lockdown shorter. The focus session
     * then just runs as a normal one, already covered by the lockdown.
     */
    private fun startUltraLockdownIfPossible() {
        val s = _state.value
        if (!BlockerRepository.isInitialized) {
            _state.value = s.copy(ultraNotice = "Couldn't start the lockdown, so this is a normal focus session.")
            return
        }
        if (BlockerRepository.isLockdownSessionRunning()) {
            _state.value = s.copy(
                ultraNotice = "A lockdown is already running, so this focus session is already protected by it."
            )
            return
        }

        val minutes = ((s.timeRemaining + 59) / 60).coerceAtLeast(1)
        BlockerRepository.startManualLock(minutes)
        val endsAt = BlockerRepository.manualLockUntil.value

        prefs.edit()
            .putLong(KEY_ULTRA_ENDS_AT, endsAt)
            .putLong(KEY_ULTRA_STARTED_AT, System.currentTimeMillis())
            .apply()
        _state.value = _state.value.copy(
            ultraSessionActive = true,
            ultraEndsAtMillis = endsAt,
            ultraNotice = null
        )

        // Same step the Lockdown screen takes right after it starts a session.
        LockdownLauncherActivity.launch(getApplication<Application>())
    }

    /**
     * If Android cleared this screen while an Ultra Focus session was in
     * progress (the phone was locked down the whole time, so this can
     * happen), pick the session back up — or, if it already finished while
     * we were gone, credit it and move on to the break.
     */
    private fun restoreUltraSessionIfAny() {
        val endsAt = prefs.getLong(KEY_ULTRA_ENDS_AT, 0L)
        if (endsAt <= 0L) return
        val startedAt = prefs.getLong(KEY_ULTRA_STARTED_AT, 0L)
        val now = System.currentTimeMillis()

        // Only continue if the lockdown on the phone is still the one we started.
        val lockIsStillOurs = BlockerRepository.isInitialized &&
            BlockerRepository.manualLockUntil.value == endsAt
        if (!lockIsStillOurs) {
            clearUltraSession()
            return
        }

        val settings = _state.value.settings
        val sessionSeconds = if (startedAt in 1 until endsAt) {
            ((endsAt - startedAt) / 1000L).toInt()
        } else {
            settings.focusDuration * 60
        }

        // The details saved when this run began (older versions saved fewer).
        val savedRunId = prefs.getLong(KEY_RUN_ID, 0L)
        val runId = when {
            savedRunId > 0L -> savedRunId
            startedAt > 0L -> startedAt
            else -> endsAt
        }
        val startRemaining = prefs.getInt(KEY_RUN_START_REMAINING, 0).let {
            if (it > 0) it else sessionSeconds
        }

        if (now < endsAt) {
            // Still in progress — resume the countdown where it really is.
            val remaining = secondsUntil(endsAt)
            runEndsAtMillis = endsAt
            segRunId = runId
            segStartRemaining = startRemaining
            _state.value = _state.value.copy(
                mode = PomodoroMode.FOCUS,
                timerState = TimerState.RUNNING,
                timeRemaining = remaining,
                totalTime = sessionSeconds.coerceAtLeast(remaining),
                liveFocusSeconds = (startRemaining - remaining).coerceAtLeast(0),
                ultraSessionActive = true,
                ultraEndsAtMillis = endsAt
            )
            scheduleEndAlarm(endsAt)
            launchTickLoop()
        } else {
            // It finished while we weren't here. It ran its full course under
            // lockdown, so it counts — on the day it ended.
            clearUltraSession()
            clearRunSession()
            finishFocusWhileAway(endsAt, runId, startRemaining, locked = true)
        }
    }

    /** Turns Ultra Focus on or off. Takes effect from the NEXT focus session. */
    fun setUltraFocusEnabled(enabled: Boolean) {
        val s = _state.value
        // Never flip it in the middle of a running/paused focus session.
        if (s.mode == PomodoroMode.FOCUS && s.timerState != TimerState.IDLE) return
        prefs.edit().putBoolean("ultra_focus_enabled", enabled).apply()
        _state.value = s.copy(
            settings = s.settings.copy(ultraFocusEnabled = enabled),
            ultraNotice = null,
            awaitingNextFocus = if (enabled) s.awaitingNextFocus else false
        )
        // A running break's "break over" wording depends on this.
        refreshEndAlarm()
    }

    /** false = manual "Start next focus session" button (default), true = automatic. */
    fun setUltraAutoStartNext(auto: Boolean) {
        val s = _state.value
        prefs.edit().putBoolean("ultra_auto_start_next", auto).apply()
        _state.value = s.copy(settings = s.settings.copy(ultraAutoStartNext = auto))
        refreshEndAlarm()
    }

    // ─────────────────────────────────────────────────────────────────────
    // TIMER
    // ─────────────────────────────────────────────────────────────────────

    fun startTimer() {
        if (_state.value.timerState == TimerState.RUNNING) return

        // Starting by hand (or the countdown reaching zero): any visible
        // "starting automatically…" countdown is no longer needed.
        autoStartJob?.cancel()
        autoStartJob = null
        PomodoroHandoff.clear()
        clearSavedHandoff()

        // Make sure "today" and the round counter are right before we begin.
        syncDay()

        val before = _state.value
        _state.value = before.copy(
            ultraNotice = null,
            // The celebration from the last session stays on screen through
            // the break, and goes away when the next focus session begins.
            achievement = if (before.mode == PomodoroMode.FOCUS) null else before.achievement
        )

        // A brand-new focus session (not resuming a paused one) with Ultra
        // Focus switched on: lock the phone for exactly the focus time.
        if (before.mode == PomodoroMode.FOCUS &&
            before.timerState == TimerState.IDLE &&
            before.settings.ultraFocusEnabled
        ) {
            startUltraLockdownIfPossible()
        }

        _state.value = _state.value.copy(
            timerState = TimerState.RUNNING,
            awaitingNextFocus = false,
            handoffFrom = null,
            autoStartInSeconds = 0
        )
        beginRunSession()
        launchTickLoop()
    }

    private fun launchTickLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.timeRemaining > 0 && _state.value.timerState == TimerState.RUNNING) {
                delay(1000)
                if (_state.value.timerState != TimerState.RUNNING) break
                val s = _state.value
                val tickingFocus = s.mode == PomodoroMode.FOCUS

                // If the lockdown that Ultra Focus started is no longer the
                // one on the phone (e.g. it was cancelled during its short
                // grace period), stop treating this as a locked session so
                // the normal controls come back.
                var ultra = s.ultraSessionActive
                if (ultra && BlockerRepository.manualLockUntil.value != s.ultraEndsAtMillis) {
                    ultra = false
                    prefs.edit()
                        .putLong(KEY_ULTRA_ENDS_AT, 0L)
                        .putLong(KEY_ULTRA_STARTED_AT, 0L)
                        .apply()
                }

                // The clock on screen is always read from a real end time, so
                // it can never drift — in an Ultra Focus session that end time
                // is the lockdown's own, so the two always finish together
                // even if the phone was busy or asleep for a moment.
                val newRemaining = when {
                    ultra -> secondsUntil(s.ultraEndsAtMillis)
                    runEndsAtMillis > 0L -> secondsUntil(runEndsAtMillis)
                    else -> (s.timeRemaining - 1).coerceAtLeast(0)
                }

                // Focus seconds of this run so far (shown live on the stats
                // card; they go into the logbook when this run ends).
                val live = if (tickingFocus && segRunId > 0L) {
                    (segStartRemaining - newRemaining).coerceIn(0, segStartRemaining)
                } else {
                    0
                }

                _state.value = s.copy(
                    timeRemaining = newRemaining,
                    ultraSessionActive = ultra,
                    liveFocusSeconds = live
                )
            }

            if (_state.value.timeRemaining == 0) {
                onTimerComplete()
            }
        }
    }

    fun pauseTimer() {
        // A locked focus session can't be paused — the lockdown keeps
        // counting down whether the timer does or not.
        if (_state.value.ultraSessionActive) return
        val s = _state.value
        if (s.timerState != TimerState.RUNNING) return

        // Freeze the exact time left, then stop the clock and the alarm.
        val exact = remainingNow()
        if (exact <= 0) return   // it is ending this very moment; let it finish
        timerJob?.cancel()

        // The focus time so far goes into the logbook now; resuming starts a new piece.
        if (s.mode == PomodoroMode.FOCUS) closeFocusSegment(exact, completed = false)
        clearRunSession()
        _state.value = s.copy(
            timerState = TimerState.PAUSED,
            timeRemaining = exact,
            liveFocusSeconds = 0
        )
    }

    fun resetTimer() {
        if (_state.value.ultraSessionActive) return
        timerJob?.cancel()
        val s = _state.value
        // Giving up a running focus session still keeps the time you really focused.
        if (s.mode == PomodoroMode.FOCUS) closeFocusSegment(remainingNow(), completed = false)
        clearRunSession()
        val totalTime = durationSecondsFor(s.mode, s.settings)
        _state.value = s.copy(
            timerState = TimerState.IDLE,
            timeRemaining = totalTime,
            totalTime = totalTime,
            liveFocusSeconds = 0
        )
    }

    /**
     * Jumps straight to the next mode without finishing the countdown.
     * Skipping a FOCUS session deliberately does NOT award a completed
     * session credit — that stat should mean "actually focused for the
     * full duration", not "tapped skip". The seconds you really focused
     * before skipping still count as focus time. Skipping a break never
     * needed that protection, so it's a plain, immediate switch either way.
     *
     * Not possible during an Ultra Focus session: the lockdown can't be
     * skipped, so neither can the focus time that goes with it.
     */
    fun skipSession() {
        // Allowed while idle too, so "Skip break" works on the ready screen.
        if (_state.value.ultraSessionActive) return
        timerJob?.cancel()
        val currentState = _state.value
        if (currentState.mode == PomodoroMode.FOCUS) closeFocusSegment(remainingNow(), completed = false)
        clearRunSession()
        val nextMode = if (currentState.mode == PomodoroMode.FOCUS) {
            PomodoroMode.SHORT_BREAK
        } else {
            PomodoroMode.FOCUS
        }
        switchMode(nextMode, currentState.cycleCount)
        // Skipping a break with Ultra Focus on: offer the manual start button.
        if (nextMode == PomodoroMode.FOCUS && currentState.settings.ultraFocusEnabled) {
            _state.value = _state.value.copy(awaitingNextFocus = true)
        }
    }

    private fun onTimerComplete() {
        val currentState = _state.value
        val ended = currentState.mode
        val settings = currentState.settings

        // Remember which end time the alarm was set for, then retire the
        // alarm: we are announcing this ending ourselves right now.
        val finishedAt = alarmEndsAt

        // Remember which focus run this was (it is forgotten once it is saved),
        // so a finished set can never be counted twice by the Daily Tasks.
        val finishedRunId = if (segRunId > 0L) segRunId else System.currentTimeMillis()

        // A finished focus session goes into the logbook BEFORE the run
        // memory is cleared (the logbook needs that memory to save it once).
        val achievement = if (ended == PomodoroMode.FOCUS) {
            closeFocusSegment(0, completed = true)
        } else {
            null
        }
        clearRunSession()

        when (ended) {
            PomodoroMode.FOCUS -> {
                // Focus session completed. If it was an Ultra Focus session
                // its lockdown has reached its end time right now too, so we
                // just forget it — there is nothing to switch off.
                val wasLockedSession = currentState.ultraSessionActive
                clearUltraSession()

                val newCycleCount = currentState.cycleCount + 1
                saveCycle(newCycleCount)

                val nextMode = nextModeAfterFocus(newCycleCount)

                // The last focus session of a round brings you to the long
                // break. That is a full Pomodoro set: tick today's daily task.
                if (nextMode == PomodoroMode.LONG_BREAK) {
                    dailyTasks.recordPomodoroSet(finishedRunId, System.currentTimeMillis())
                }

                // Chime + vibration + notification (skipped automatically if
                // the background alarm already played them for this end time).
                val copy = alertCopy(ended, nextMode)
                PomodoroAlerts.fire(
                    getApplication<Application>(), ended, copy.first, copy.second, finishedAt,
                    locked = wasLockedSession
                )

                switchMode(nextMode, newCycleCount)
                _state.value = _state.value.copy(
                    celebrationTick = _state.value.celebrationTick + 1,
                    handoffFrom = PomodoroMode.FOCUS,
                    achievement = achievement,
                    liveFocusSeconds = 0
                )
                if (wasLockedSession) publishHandoffNote(PomodoroMode.FOCUS)
                saveHandoff(PomodoroMode.FOCUS, wasLockedSession)

                if (settings.autoStartBreaks) {
                    beginAutoStartCountdown()
                }
            }
            PomodoroMode.SHORT_BREAK, PomodoroMode.LONG_BREAK -> {
                // Break completed
                val copy = alertCopy(ended, PomodoroMode.FOCUS)
                PomodoroAlerts.fire(getApplication<Application>(), ended, copy.first, copy.second, finishedAt)

                switchMode(PomodoroMode.FOCUS, currentState.cycleCount)
                _state.value = _state.value.copy(
                    handoffFrom = ended,
                    // Ultra Focus has its own "what happens next" choice:
                    // manual button (default) or start automatically.
                    awaitingNextFocus = settings.ultraFocusEnabled
                )
                saveHandoff(ended, locked = false)

                val autoStart = if (settings.ultraFocusEnabled) {
                    settings.ultraAutoStartNext
                } else {
                    settings.autoStartPomodoros
                }
                if (autoStart) {
                    beginAutoStartCountdown()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AUTO-START COUNTDOWN
    //
    // Auto-start used to flip straight into the next session the instant the
    // last one ended, which felt abrupt (and, for a locked Ultra Focus
    // session, a bit startling). Now there's a short visible countdown the
    // user can cancel or skip past with "Start now".
    // ─────────────────────────────────────────────────────────────────────

    private fun beginAutoStartCountdown() {
        autoStartJob?.cancel()
        val waitingMode = _state.value.mode
        autoStartJob = viewModelScope.launch {
            var left = AUTO_START_SECONDS
            while (left > 0) {
                _state.value = _state.value.copy(autoStartInSeconds = left)
                delay(1000)
                left--
            }
            autoStartJob = null
            val s = _state.value
            if (s.mode == waitingMode && s.timerState == TimerState.IDLE) {
                startTimer()
            } else {
                _state.value = s.copy(autoStartInSeconds = 0)
            }
        }
    }

    /** The "Not yet" button: stop the countdown but keep the hand-off card and its Start button. */
    fun cancelAutoStart() {
        autoStartJob?.cancel()
        autoStartJob = null
        _state.value = _state.value.copy(autoStartInSeconds = 0)
    }

    /**
     * The "Stop for now" button on the session-finished overlay: nothing else
     * starts by itself. Any waiting break is dropped and the timer goes back
     * to a calm "ready to focus" state. The round dots and today's stats are
     * left exactly as they were.
     *
     * Does nothing if a session is already running (e.g. the auto-start
     * countdown reached zero a split second before the tap): a running
     * session — especially a locked Ultra Focus one — must never be
     * cut loose from its timer.
     */
    fun stopAfterSession() {
        autoStartJob?.cancel()
        autoStartJob = null
        val s = _state.value
        if (s.timerState != TimerState.IDLE) {
            _state.value = s.copy(autoStartInSeconds = 0)
            return
        }
        switchMode(PomodoroMode.FOCUS, s.cycleCount)
    }

    fun switchMode(newMode: PomodoroMode, cycleCount: Int = _state.value.cycleCount) {
        timerJob?.cancel()
        autoStartJob?.cancel()
        autoStartJob = null
        PomodoroHandoff.clear()
        clearSavedHandoff()

        val settings = _state.value.settings
        val totalTime = durationSecondsFor(newMode, settings)

        _state.value = _state.value.copy(
            mode = newMode,
            timerState = TimerState.IDLE,
            timeRemaining = totalTime,
            totalTime = totalTime,
            cycleCount = cycleCount,
            liveFocusSeconds = 0,
            awaitingNextFocus = false,
            handoffFrom = null,
            autoStartInSeconds = 0
        )
    }

    fun updateSettings(newSettings: PomodoroSettings) {
        val settings = newSettings.copy(dailyGoalSessions = newSettings.dailyGoalSessions.coerceIn(1, 20))
        prefs.edit().apply {
            putInt("focus_duration", settings.focusDuration)
            putInt("short_break_duration", settings.shortBreakDuration)
            putInt("long_break_duration", settings.longBreakDuration)
            putInt("sessions_until_long_break", settings.sessionsUntilLongBreak)
            putInt("daily_goal_sessions", settings.dailyGoalSessions)
            putBoolean("auto_start_breaks", settings.autoStartBreaks)
            putBoolean("auto_start_pomodoros", settings.autoStartPomodoros)
            putBoolean("ultra_focus_enabled", settings.ultraFocusEnabled)
            putBoolean("ultra_auto_start_next", settings.ultraAutoStartNext)
            putBoolean(PomodoroAlerts.KEY_SOUND, settings.soundEnabled)
            putBoolean(PomodoroAlerts.KEY_VIBRATION, settings.vibrationEnabled)
            apply()
        }

        _state.value = _state.value.copy(settings = settings)

        // Update current timer duration if idle
        if (_state.value.timerState == TimerState.IDLE) {
            resetTimer()
        } else {
            refreshEndAlarm()
        }
    }

    /**
     * The "Clear Stats History" button: wipes the whole logbook and starts a
     * fresh round. If a focus countdown is running right now, only the time
     * from this moment on is counted afterwards.
     */
    fun resetStats() {
        stats.clearAll()
        saveCycle(0)
        if (segRunId > 0L) {
            segStartRemaining = _state.value.timeRemaining
            prefs.edit().putInt(KEY_RUN_START_REMAINING, segStartRemaining).apply()
        }
        _state.value = _state.value.copy(
            cycleCount = 0,
            liveFocusSeconds = 0,
            achievement = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        autoStartJob?.cancel()
        // Nothing to save here on purpose: a focus run that is still going is
        // remembered by its start/end times and picked up again on the next
        // start (see restoreRunSessionIfAny). Saving partial seconds here is
        // what used to cause double counting.
        // When the main screen is rebuilt, this OLD timer is cleaned up after
        // the NEW one has already started. Only the newest timer may take
        // down the shared hand-off note, or it would wipe the new one's.
        if (latestInstance === this) {
            latestInstance = null
            PomodoroHandoff.unregister()
            PomodoroHandoff.clear()
        }
    }
}
