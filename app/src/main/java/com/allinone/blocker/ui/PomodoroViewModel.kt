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
    val autoStartPomodoros: Boolean = false
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
    val celebrationTick: Int = 0
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
    }

    init {
        createNotificationChannel()
        loadSettings()
        loadState()
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
            autoStartPomodoros = prefs.getBoolean("auto_start_pomodoros", false)
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

    private fun todayKey(): Int {
        val c = Calendar.getInstance()
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

    fun startTimer() {
        if (_state.value.timerState == TimerState.RUNNING) return

        _state.value = _state.value.copy(timerState = TimerState.RUNNING)

        timerJob = viewModelScope.launch {
            while (_state.value.timeRemaining > 0 && _state.value.timerState == TimerState.RUNNING) {
                delay(1000)
                if (_state.value.timerState != TimerState.RUNNING) break
                val tickingFocus = _state.value.mode == PomodoroMode.FOCUS
                _state.value = _state.value.copy(
                    timeRemaining = (_state.value.timeRemaining - 1).coerceAtLeast(0),
                    focusSecondsToday = if (tickingFocus) _state.value.focusSecondsToday + 1 else _state.value.focusSecondsToday
                )
            }

            if (_state.value.timeRemaining == 0) {
                onTimerComplete()
            }
        }
    }

    fun pauseTimer() {
        _state.value = _state.value.copy(timerState = TimerState.PAUSED)
        timerJob?.cancel()
        persistFocusSecondsToday()
    }

    fun resetTimer() {
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
     */
    fun skipSession() {
        if (_state.value.timerState == TimerState.IDLE) return
        timerJob?.cancel()
        persistFocusSecondsToday()
        val currentState = _state.value
        val nextMode = if (currentState.mode == PomodoroMode.FOCUS) {
            PomodoroMode.SHORT_BREAK
        } else {
            PomodoroMode.FOCUS
        }
        switchMode(nextMode, currentState.completedSessions)
    }

    private fun onTimerComplete() {
        rolloverStatsIfNewDay()
        persistFocusSecondsToday()
        val currentState = _state.value
        
        // Show notification
        showCompletionNotification()

        when (currentState.mode) {
            PomodoroMode.FOCUS -> {
                // Focus session completed
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

                if (currentState.settings.autoStartPomodoros) {
                    startTimer()
                }
            }
        }
    }

    private fun showCompletionNotification() {
        val message = when (_state.value.mode) {
            PomodoroMode.FOCUS -> "Focus session completed! Time for a break."
            PomodoroMode.SHORT_BREAK -> "Short break finished! Ready to focus?"
            PomodoroMode.LONG_BREAK -> "Long break over! Let's get back to work."
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
            completedSessions = completedSessions
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
