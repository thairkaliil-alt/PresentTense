package com.allinone.blocker.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allinone.blocker.R
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
    val ultraAutoStartNext: Boolean = false
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
    val ultraNotice: String? = null
)

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state

    private var timerJob: Job? = null
    private val prefs = application.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "pomodoro_channel"
        private const val NOTIFICATION_ID = 1001

        // Remembered across app restarts so an Ultra Focus session that is
        // in progress can be picked back up if Android clears this screen.
        private const val KEY_ULTRA_ENDS_AT = "ultra_ends_at"
        private const val KEY_ULTRA_STARTED_AT = "ultra_started_at"
    }

    init {
        createNotificationChannel()
        loadSettings()
        loadState()
        restoreUltraSessionIfAny()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Pomodoro timer completion"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
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
            ultraAutoStartNext = prefs.getBoolean("ultra_auto_start_next", false)
        )
        _state.value = _state.value.copy(settings = settings)
        
        // Initialize time based on current mode
        val currentMode = _state.value.mode
        val totalTime = when (currentMode) {
            PomodoroMode.FOCUS -> settings.focusDuration * 60
            PomodoroMode.SHORT_BREAK -> settings.shortBreakDuration * 60
            PomodoroMode.LONG_BREAK -> settings.longBreakDuration * 60
        }
        _state.value = _state.value.copy(
            timeRemaining = totalTime,
            totalTime = totalTime
        )
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
            _state.value = _state.value.copy(
                mode = PomodoroMode.FOCUS,
                timerState = TimerState.RUNNING,
                timeRemaining = remaining,
                totalTime = sessionSeconds.coerceAtLeast(remaining),
                focusSecondsToday = _state.value.focusSecondsToday + elapsed,
                ultraSessionActive = true,
                ultraEndsAtMillis = endsAt
            )
            launchTickLoop()
        } else {
            // It finished while we weren't here. It ran its full course under
            // lockdown, so it counts — but only if it ended today.
            clearUltraSession()
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
                val nextMode = if (newCompleted % settings.sessionsUntilLongBreak.coerceAtLeast(1) == 0) {
                    PomodoroMode.LONG_BREAK
                } else {
                    PomodoroMode.SHORT_BREAK
                }
                switchMode(nextMode, newCompleted)
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
    }

    /** false = manual "Start next focus session" button (default), true = automatic. */
    fun setUltraAutoStartNext(auto: Boolean) {
        val s = _state.value
        prefs.edit().putBoolean("ultra_auto_start_next", auto).apply()
        _state.value = s.copy(settings = s.settings.copy(ultraAutoStartNext = auto))
    }

    // ─────────────────────────────────────────────────────────────────────
    // TIMER
    // ─────────────────────────────────────────────────────────────────────

    fun startTimer() {
        if (_state.value.timerState == TimerState.RUNNING) return

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
            awaitingNextFocus = false
        )
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

                // In an Ultra Focus session the clock on screen is read from
                // the lockdown's own end time, so the two always finish
                // together even if the phone was busy or asleep for a moment.
                val newRemaining = if (ultra) {
                    secondsUntil(s.ultraEndsAtMillis)
                } else {
                    (s.timeRemaining - 1).coerceAtLeast(0)
                }

                _state.value = s.copy(
                    timeRemaining = newRemaining,
                    ultraSessionActive = ultra,
                    focusSecondsToday = if (tickingFocus) s.focusSecondsToday + 1 else s.focusSecondsToday
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
        _state.value = _state.value.copy(timerState = TimerState.PAUSED)
        timerJob?.cancel()
        persistFocusSecondsToday()
    }

    fun resetTimer() {
        if (_state.value.ultraSessionActive) return
        timerJob?.cancel()
        persistFocusSecondsToday()
        val totalTime = when (_state.value.mode) {
            PomodoroMode.FOCUS -> _state.value.settings.focusDuration * 60
            PomodoroMode.SHORT_BREAK -> _state.value.settings.shortBreakDuration * 60
            PomodoroMode.LONG_BREAK -> _state.value.settings.longBreakDuration * 60
        }
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
        if (_state.value.timerState == TimerState.IDLE) return
        if (_state.value.ultraSessionActive) return
        timerJob?.cancel()
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
        
        // Show notification
        showCompletionNotification()

        when (currentState.mode) {
            PomodoroMode.FOCUS -> {
                // Focus session completed. If it was an Ultra Focus session
                // its lockdown has reached its end time right now too, so we
                // just forget it — there is nothing to switch off.
                clearUltraSession()

                val newCompletedSessions = currentState.completedSessions + 1
                prefs.edit().putInt("completed_sessions", newCompletedSessions).apply()

                // Determine next mode
                val nextMode = if (newCompletedSessions % currentState.settings.sessionsUntilLongBreak == 0) {
                    PomodoroMode.LONG_BREAK
                } else {
                    PomodoroMode.SHORT_BREAK
                }

                switchMode(nextMode, newCompletedSessions)
                _state.value = _state.value.copy(celebrationTick = _state.value.celebrationTick + 1)

                if (currentState.settings.autoStartBreaks) {
                    startTimer()
                }
            }
            PomodoroMode.SHORT_BREAK, PomodoroMode.LONG_BREAK -> {
                // Break completed
                switchMode(PomodoroMode.FOCUS, currentState.completedSessions)

                if (currentState.settings.ultraFocusEnabled) {
                    // Ultra Focus has its own "what happens next" choice:
                    // manual button (default) or start automatically.
                    _state.value = _state.value.copy(awaitingNextFocus = true)
                    if (currentState.settings.ultraAutoStartNext) {
                        startTimer()
                    }
                } else if (currentState.settings.autoStartPomodoros) {
                    startTimer()
                }
            }
        }
    }

    private fun showCompletionNotification() {
        val settings = _state.value.settings
        val message = when (_state.value.mode) {
            PomodoroMode.FOCUS -> "Focus session completed! Time for a break."
            PomodoroMode.SHORT_BREAK ->
                if (settings.ultraFocusEnabled) ultraBreakOverMessage(settings.ultraAutoStartNext)
                else "Short break finished! Ready to focus?"
            PomodoroMode.LONG_BREAK ->
                if (settings.ultraFocusEnabled) ultraBreakOverMessage(settings.ultraAutoStartNext)
                else "Long break over! Let's get back to work."
        }

        val notification = NotificationCompat.Builder(getApplication(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pomodoro Timer")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ultraBreakOverMessage(autoStart: Boolean): String =
        if (autoStart) {
            "Break over — your next Ultra Focus session is starting now."
        } else {
            "Break over. Open Present Tense and tap Start next focus session when you're ready."
        }

    fun switchMode(newMode: PomodoroMode, completedSessions: Int = _state.value.completedSessions) {
        timerJob?.cancel()

        val settings = _state.value.settings
        val totalTime = when (newMode) {
            PomodoroMode.FOCUS -> settings.focusDuration * 60
            PomodoroMode.SHORT_BREAK -> settings.shortBreakDuration * 60
            PomodoroMode.LONG_BREAK -> settings.longBreakDuration * 60
        }

        _state.value = _state.value.copy(
            mode = newMode,
            timerState = TimerState.IDLE,
            timeRemaining = totalTime,
            totalTime = totalTime,
            completedSessions = completedSessions,
            awaitingNextFocus = false
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
            apply()
        }

        _state.value = _state.value.copy(settings = newSettings)

        // Update current timer duration if idle
        if (_state.value.timerState == TimerState.IDLE) {
            resetTimer()
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
        persistFocusSecondsToday()
    }
}
