package com.allinone.blocker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allinone.blocker.data.BlockerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

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
    val completedSessions: Int = 0,     // focus sessions finished today
    val focusSecondsToday: Int = 0,     // total real focused time today, in seconds
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

        /** How long the "starting automatically…" countdown lasts (the screen's button fill uses this too). */
        const val AUTO_START_SECONDS = 5
    }

    init {
        loadSettings()
        loadState()
        restoreUltraSessionIfAny()
        restoreRunSessionIfAny()
        // Lets the "Session complete" screen start the next phase in one tap.
        PomodoroHandoff.register { startTimer() }
    }

    private fun loadSettings() {
        val settings = PomodoroSettings(
            focusDuration = prefs.getInt("focus_duration", 25),
            shortBreakDuration = prefs.getInt("short_break_duration", 5),
            longBreakDuration = prefs.getInt("long_break_duration", 15),
            sessionsUntilLongBreak = prefs.getInt("sessions_until_long_break", 4),
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
    // DAILY STATS
    //
    // "Completed Today" and "Focused Today" only mean something if they
    // actually reset at midnight. The old version persisted completed
    // session count forever, so it silently drifted into "completed ever" —
    // technically wrong and, worse, it made the stat feel meaningless over
    // time. Both counters now live alongside a day key (year*1000+dayOfYear,
    // same scheme StreakRepository already uses elsewhere in the app) and
    // get zeroed the first time they're touched on a new day.
    // ─────────────────────────────────────────────────────────────────────

    private fun todayKey(): Int = dayKeyOf(System.currentTimeMillis())

    private fun dayKeyOf(millis: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun rolloverStatsIfNewDay() {
        val today = todayKey()
        val savedDay = prefs.getInt("stats_day_key", today)
        if (savedDay != today) {
            prefs.edit()
                .putInt("stats_day_key", today)
                .putInt("completed_sessions", 0)
                .putInt("focus_seconds_today", 0)
                .apply()
        }
    }

    private fun loadState() {
        rolloverStatsIfNewDay()
        val completedSessions = prefs.getInt("completed_sessions", 0)
        val focusSecondsToday = prefs.getInt("focus_seconds_today", 0)
        _state.value = _state.value.copy(
            completedSessions = completedSessions,
            focusSecondsToday = focusSecondsToday
        )
    }

    private fun persistFocusSecondsToday() {
        prefs.edit().putInt("focus_seconds_today", _state.value.focusSecondsToday).apply()
    }

    // ─────────────────────────────────────────────────────────────────────
    // WHAT COMES NEXT, AND WHAT THE ALERT SAYS
    // ─────────────────────────────────────────────────────────────────────

    /** The break that follows a focus session, given how many are done after it. */
    private fun nextModeAfterFocus(completedAfter: Int): PomodoroMode {
        val perCycle = _state.value.settings.sessionsUntilLongBreak.coerceAtLeast(1)
        return if (completedAfter % perCycle == 0) PomodoroMode.LONG_BREAK else PomodoroMode.SHORT_BREAK
    }

    private fun nextModeAfter(mode: PomodoroMode, completedSessions: Int): PomodoroMode =
        if (mode == PomodoroMode.FOCUS) nextModeAfterFocus(completedSessions + 1) else PomodoroMode.FOCUS

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
        val next = nextModeAfter(s.mode, s.completedSessions)
        val copy = alertCopy(s.mode, next)
        PomodoroAlerts.scheduleEnd(getApplication<Application>(), endsAt, s.mode, copy.first, copy.second)
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
        runEndsAtMillis = System.currentTimeMillis() + s.timeRemaining * 1000L
        prefs.edit()
            .putString(KEY_RUN_MODE, s.mode.name)
            .putLong(KEY_RUN_ENDS_AT, runEndsAtMillis)
            .putInt(KEY_RUN_TOTAL, s.totalTime)
            .putInt(KEY_RUN_START_REMAINING, s.timeRemaining)
            .apply()
        scheduleEndAlarm(if (s.ultraSessionActive) s.ultraEndsAtMillis else runEndsAtMillis)
    }

    /** Called when the running session stops for any reason (pause, reset, skip, finished). */
    private fun clearRunSession() {
        runEndsAtMillis = 0L
        alarmEndsAt = 0L
        prefs.edit()
            .remove(KEY_RUN_MODE)
            .remove(KEY_RUN_ENDS_AT)
            .remove(KEY_RUN_TOTAL)
            .remove(KEY_RUN_START_REMAINING)
            .apply()
        PomodoroAlerts.cancelEnd(getApplication<Application>())
    }

    /**
     * If Android cleared the app while a focus session or break was running
     * (screen off, phone low on memory), pick it back up — or, if it already
     * ended while we were gone, credit it and show the hand-off. The alarm
     * has already played the chime in that case, so nothing is announced
     * again here.
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
        val now = System.currentTimeMillis()

        if (now < endsAt) {
            val remaining = secondsUntil(endsAt)
            val elapsed = (startRemaining - remaining).coerceAtLeast(0)
            runEndsAtMillis = endsAt
            _state.value = _state.value.copy(
                mode = mode,
                timerState = TimerState.RUNNING,
                timeRemaining = remaining,
                totalTime = total.coerceAtLeast(remaining),
                focusSecondsToday = if (mode == PomodoroMode.FOCUS) {
                    _state.value.focusSecondsToday + elapsed
                } else {
                    _state.value.focusSecondsToday
                }
            )
            scheduleEndAlarm(endsAt)
            launchTickLoop()
        } else {
            clearRunSession()
            if (dayKeyOf(endsAt) != todayKey()) return
            if (mode == PomodoroMode.FOCUS) {
                val newCompleted = _state.value.completedSessions + 1
                val newFocusSeconds = _state.value.focusSecondsToday + startRemaining
                prefs.edit()
                    .putInt("completed_sessions", newCompleted)
                    .putInt("focus_seconds_today", newFocusSeconds)
                    .apply()
                _state.value = _state.value.copy(
                    completedSessions = newCompleted,
                    focusSecondsToday = newFocusSeconds
                )
                switchMode(nextModeAfterFocus(newCompleted), newCompleted)
                _state.value = _state.value.copy(handoffFrom = PomodoroMode.FOCUS)
            } else {
                switchMode(PomodoroMode.FOCUS, _state.value.completedSessions)
                _state.value = _state.value.copy(
                    handoffFrom = mode,
                    awaitingNextFocus = settings.ultraFocusEnabled
                )
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

        if (now < endsAt) {
            // Still in progress — resume the countdown where it really is.
            val remaining = secondsUntil(endsAt)
            val elapsed = (sessionSeconds - remaining).coerceAtLeast(0)
            runEndsAtMillis = endsAt
            _state.value = _state.value.copy(
                mode = PomodoroMode.FOCUS,
                timerState = TimerState.RUNNING,
                timeRemaining = remaining,
                totalTime = sessionSeconds.coerceAtLeast(remaining),
                focusSecondsToday = _state.value.focusSecondsToday + elapsed,
                ultraSessionActive = true,
                ultraEndsAtMillis = endsAt
            )
            scheduleEndAlarm(endsAt)
            launchTickLoop()
        } else {
            // It finished while we weren't here. It ran its full course under
            // lockdown, so it counts — but only if it ended today.
            clearUltraSession()
            clearRunSession()
            if (dayKeyOf(endsAt) == todayKey()) {
                val newCompleted = _state.value.completedSessions + 1
                val newFocusSeconds = _state.value.focusSecondsToday + sessionSeconds
                prefs.edit()
                    .putInt("completed_sessions", newCompleted)
                    .putInt("focus_seconds_today", newFocusSeconds)
                    .apply()
                _state.value = _state.value.copy(
                    completedSessions = newCompleted,
                    focusSecondsToday = newFocusSeconds
                )
                switchMode(nextModeAfterFocus(newCompleted), newCompleted)
                _state.value = _state.value.copy(handoffFrom = PomodoroMode.FOCUS)
                publishHandoffNote(PomodoroMode.FOCUS)
            }
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

        val before = _state.value
        _state.value = before.copy(ultraNotice = null)

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
                // Normally 1 second; more if the phone slept and we are catching up.
                val elapsed = (s.timeRemaining - newRemaining).coerceAtLeast(0)

                _state.value = s.copy(
                    timeRemaining = newRemaining,
                    ultraSessionActive = ultra,
                    focusSecondsToday = if (tickingFocus) s.focusSecondsToday + elapsed else s.focusSecondsToday
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
        // Freeze the exact time left, then stop the clock and the alarm.
        val exact = if (runEndsAtMillis > 0L) secondsUntil(runEndsAtMillis) else s.timeRemaining
        _state.value = s.copy(timerState = TimerState.PAUSED, timeRemaining = exact)
        timerJob?.cancel()
        clearRunSession()
        persistFocusSecondsToday()
    }

    fun resetTimer() {
        if (_state.value.ultraSessionActive) return
        timerJob?.cancel()
        clearRunSession()
        persistFocusSecondsToday()
        val totalTime = durationSecondsFor(_state.value.mode, _state.value.settings)
        _state.value = _state.value.copy(
            timerState = TimerState.IDLE,
            timeRemaining = totalTime,
            totalTime = totalTime
        )
    }

    /**
     * Jumps straight to the next mode without finishing the countdown.
     * Skipping a FOCUS session deliberately does NOT award a completed
     * session credit — that stat should mean "actually focused for the
     * full duration", not "tapped skip". Skipping a break never needed
     * that protection, so it's a plain, immediate switch either way.
     *
     * Not possible during an Ultra Focus session: the lockdown can't be
     * skipped, so neither can the focus time that goes with it.
     */
    fun skipSession() {
        // Allowed while idle too, so "Skip break" works on the ready screen.
        if (_state.value.ultraSessionActive) return
        timerJob?.cancel()
        clearRunSession()
        persistFocusSecondsToday()
        val currentState = _state.value
        val nextMode = if (currentState.mode == PomodoroMode.FOCUS) {
            PomodoroMode.SHORT_BREAK
        } else {
            PomodoroMode.FOCUS
        }
        switchMode(nextMode, currentState.completedSessions)
        // Skipping a break with Ultra Focus on: offer the manual start button.
        if (nextMode == PomodoroMode.FOCUS && currentState.settings.ultraFocusEnabled) {
            _state.value = _state.value.copy(awaitingNextFocus = true)
        }
    }

    private fun onTimerComplete() {
        rolloverStatsIfNewDay()
        persistFocusSecondsToday()
        val currentState = _state.value
        val ended = currentState.mode
        val settings = currentState.settings

        // Remember which end time the alarm was set for, then retire the
        // alarm: we are announcing this ending ourselves right now.
        val finishedAt = alarmEndsAt
        clearRunSession()

        when (ended) {
            PomodoroMode.FOCUS -> {
                // Focus session completed. If it was an Ultra Focus session
                // its lockdown has reached its end time right now too, so we
                // just forget it — there is nothing to switch off.
                val wasLockedSession = currentState.ultraSessionActive
                clearUltraSession()

                val newCompletedSessions = currentState.completedSessions + 1
                prefs.edit().putInt("completed_sessions", newCompletedSessions).apply()

                val nextMode = nextModeAfterFocus(newCompletedSessions)

                // Chime + vibration + notification (skipped automatically if
                // the background alarm already played them for this end time).
                val copy = alertCopy(ended, nextMode)
                PomodoroAlerts.fire(getApplication<Application>(), ended, copy.first, copy.second, finishedAt)

                switchMode(nextMode, newCompletedSessions)
                _state.value = _state.value.copy(
                    celebrationTick = _state.value.celebrationTick + 1,
                    handoffFrom = PomodoroMode.FOCUS
                )
                if (wasLockedSession) publishHandoffNote(PomodoroMode.FOCUS)

                if (settings.autoStartBreaks) {
                    beginAutoStartCountdown()
                }
            }
            PomodoroMode.SHORT_BREAK, PomodoroMode.LONG_BREAK -> {
                // Break completed
                val copy = alertCopy(ended, PomodoroMode.FOCUS)
                PomodoroAlerts.fire(getApplication<Application>(), ended, copy.first, copy.second, finishedAt)

                switchMode(PomodoroMode.FOCUS, currentState.completedSessions)
                _state.value = _state.value.copy(
                    handoffFrom = ended,
                    // Ultra Focus has its own "what happens next" choice:
                    // manual button (default) or start automatically.
                    awaitingNextFocus = settings.ultraFocusEnabled
                )

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

    fun switchMode(newMode: PomodoroMode, completedSessions: Int = _state.value.completedSessions) {
        timerJob?.cancel()
        autoStartJob?.cancel()
        autoStartJob = null
        PomodoroHandoff.clear()

        val settings = _state.value.settings
        val totalTime = durationSecondsFor(newMode, settings)

        _state.value = _state.value.copy(
            mode = newMode,
            timerState = TimerState.IDLE,
            timeRemaining = totalTime,
            totalTime = totalTime,
            completedSessions = completedSessions,
            awaitingNextFocus = false,
            handoffFrom = null,
            autoStartInSeconds = 0
        )
    }

    fun updateSettings(newSettings: PomodoroSettings) {
        prefs.edit().apply {
            putInt("focus_duration", newSettings.focusDuration)
            putInt("short_break_duration", newSettings.shortBreakDuration)
            putInt("long_break_duration", newSettings.longBreakDuration)
            putInt("sessions_until_long_break", newSettings.sessionsUntilLongBreak)
            putBoolean("auto_start_breaks", newSettings.autoStartBreaks)
            putBoolean("auto_start_pomodoros", newSettings.autoStartPomodoros)
            putBoolean("ultra_focus_enabled", newSettings.ultraFocusEnabled)
            putBoolean("ultra_auto_start_next", newSettings.ultraAutoStartNext)
            putBoolean(PomodoroAlerts.KEY_SOUND, newSettings.soundEnabled)
            putBoolean(PomodoroAlerts.KEY_VIBRATION, newSettings.vibrationEnabled)
            apply()
        }

        _state.value = _state.value.copy(settings = newSettings)

        // Update current timer duration if idle
        if (_state.value.timerState == TimerState.IDLE) {
            resetTimer()
        } else {
            refreshEndAlarm()
        }
    }

    fun resetStats() {
        prefs.edit()
            .putInt("completed_sessions", 0)
            .putInt("focus_seconds_today", 0)
            .apply()
        _state.value = _state.value.copy(completedSessions = 0, focusSecondsToday = 0)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        autoStartJob?.cancel()
        persistFocusSecondsToday()
        PomodoroHandoff.unregister()
        PomodoroHandoff.clear()
    }
}
