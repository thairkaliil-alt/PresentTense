package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════════
// DailyTasks.kt  —  the "Daily Tasks" checklist behind the Pomodoro rewards
//
// THE IDEA
//   Every day you get a short list of tasks. Each task has a progress bar
//   (for example 0 / 1) and a treasure chest. When the bar is full the chest
//   is ready, and you tap it to open it.
//
//   • There is ONE built-in task that is always there:
//         "Complete a full Pomodoro set"
//     A full set = all the focus sessions of a round (4 by default) finished,
//     which brings you to the long break. The timer (PomodoroViewModel)
//     tells this file when that happens, so the bar fills by itself.
//
//   • You can also add your OWN tasks (for example "Drink water 0 / 8").
//     Those cannot be tracked automatically, so you add progress by hand on
//     the Daily Tasks screen.
//
// WHAT RESETS AT MIDNIGHT, AND WHAT DOES NOT
//   • Progress and opened chests reset every new day.
//   • The tasks themselves (including your own) stay — they come back with
//     a fresh, empty bar the next morning.
//
// THE PARTS BELOW
//   1. Small data holders (DailyTask, DailyTasksState).
//   2. DailyTasksRepository — keeps everything and saves it on the phone.
//      The screens watch [DailyTasksRepository.state] and redraw by themselves.
// ═══════════════════════════════════════════════════════════════════════════

/** Fixed numbers and names used by the daily tasks. */
object DailyTaskRules {
    /** The id of the built-in "full Pomodoro set" task. */
    const val POMODORO_SET_ID = "pomodoro_set"

    /** The built-in task's colour (a number into the colour list in DailyTasksScreen.kt). */
    const val POMODORO_SET_COLOR = 4

    const val MAX_TITLE_LENGTH = 40
    const val MAX_TARGET = 99

    /** How many tasks of your own you can have (keeps the list tidy). */
    const val MAX_CUSTOM_TASKS = 10

    /** How many colours the task colour list has. */
    const val COLOR_COUNT = 6

    const val DEFAULT_EMOJI = "\uD83C\uDFAF" // 🎯
}

/** One task, exactly as the screen needs to draw it. */
data class DailyTask(
    val id: String,
    val title: String,
    /** Small grey line under the title (only the built-in task has one). */
    val subtitle: String?,
    val emoji: String,
    /** Which colour of the palette this task uses. */
    val colorIndex: Int,
    /** How many times it must be done today (the "2" in "1 / 2"). */
    val target: Int,
    /** How many times it was done today (the "1" in "1 / 2"). */
    val progress: Int,
    /** True once today's chest has been opened. */
    val claimed: Boolean,
    /** True for the built-in task: it fills by itself and cannot be deleted. */
    val isBuiltIn: Boolean
) {
    val done: Boolean get() = progress >= target

    /** 0.0 … 1.0, for the progress bar. */
    val fraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

/** Everything the Daily Tasks screen shows, in one bundle. */
data class DailyTasksState(
    /** The calendar day these numbers belong to. */
    val day: LocalDate,
    val tasks: List<DailyTask>
) {
    /** How many chests are full and still waiting to be opened. */
    val chestsReady: Int get() = tasks.count { it.done && !it.claimed }

    val customCount: Int get() = tasks.count { !it.isBuiltIn }
}

// ───────────────────────────────────────────────────────────────────────────
// THE TASKS ON THE PHONE
// ───────────────────────────────────────────────────────────────────────────

/**
 * Loads and saves the daily tasks. There is exactly one of these for the whole
 * app (get it with [DailyTasksRepository.get]).
 */
class DailyTasksRepository private constructor(context: Context) {

    /** A task you made yourself, as it is saved. */
    private data class CustomTaskDef(
        val id: String,
        val title: String,
        val target: Int,
        val emoji: String,
        val colorIndex: Int
    )

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private var customDefs: List<CustomTaskDef> = loadCustom()

    // Today's numbers. They are thrown away when a new day starts.
    private var day: LocalDate = LocalDate.now()
    private var progress: Map<String, Int> = emptyMap()
    private var claimed: Set<String> = emptySet()

    // The id of the last full set that was counted, so the same set can never
    // be counted twice (for example if the app is restarted at a bad moment).
    private var lastSetRunId: Long = prefs.getLong(KEY_LAST_SET_RUN, 0L)

    private val _state = MutableStateFlow(DailyTasksState(day, emptyList()))

    /** The whole list for today. The screens watch this and redraw by themselves. */
    val state: StateFlow<DailyTasksState> = _state

    init {
        loadToday()
        publish()
    }

    // ── Changes ───────────────────────────────────────────────────────────

    /** Call now and then (the screens do): flips everything to the new day after midnight. */
    @Synchronized
    fun refreshDay() = change { }

    /**
     * Called by the Pomodoro timer when the LAST focus session of a round has
     * just finished (the moment the long break is reached). Fills the built-in
     * "full Pomodoro set" task.
     *
     * @param runId    unique id of the finishing focus session. The same id is
     *                 never counted twice.
     * @param atMillis when the set was finished. A set that finished on an
     *                 earlier day does not count for today.
     * @return true if today's bar moved forward.
     */
    @Synchronized
    fun recordPomodoroSet(runId: Long, atMillis: Long): Boolean = change {
        if (runId > 0L && runId == lastSetRunId) return@change false
        if (runId > 0L) {
            lastSetRunId = runId
            prefs.edit().putLong(KEY_LAST_SET_RUN, runId).apply()
        }

        val finishedOn = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (finishedOn != day) return@change false
        if ((progress[DailyTaskRules.POMODORO_SET_ID] ?: 0) >= 1) return@change false

        progress = progress + (DailyTaskRules.POMODORO_SET_ID to 1)
        saveToday()
        true
    }

    /** Adds a task of your own. Returns false if the name is empty or you hit the limit. */
    @Synchronized
    fun addCustomTask(title: String, target: Int, emoji: String, colorIndex: Int): Boolean = change {
        val cleanTitle = title.trim().replace(Regex("\\s+"), " ").take(DailyTaskRules.MAX_TITLE_LENGTH)
        if (cleanTitle.isEmpty() || customDefs.size >= DailyTaskRules.MAX_CUSTOM_TASKS) {
            return@change false
        }
        val def = CustomTaskDef(
            id = "custom_" + UUID.randomUUID().toString(),
            title = cleanTitle,
            target = target.coerceIn(1, DailyTaskRules.MAX_TARGET),
            emoji = emoji.ifBlank { DailyTaskRules.DEFAULT_EMOJI },
            colorIndex = colorIndex.coerceIn(0, DailyTaskRules.COLOR_COUNT - 1)
        )
        customDefs = customDefs + def
        saveCustom()
        true
    }

    /** Removes a task of your own (the built-in task cannot be removed). */
    @Synchronized
    fun deleteCustomTask(id: String) = change {
        if (customDefs.none { it.id == id }) return@change
        customDefs = customDefs.filterNot { it.id == id }
        progress = progress - id
        claimed = claimed - id
        saveCustom()
        saveToday()
    }

    /**
     * Moves one of YOUR tasks forward (+1) or back (-1), by hand.
     * Not allowed on the built-in task, and not allowed to go back once the
     * chest was opened (otherwise the chest could be opened again and again).
     *
     * @return true if the bar actually moved.
     */
    @Synchronized
    fun addProgress(id: String, delta: Int): Boolean = change {
        val def = customDefs.firstOrNull { it.id == id } ?: return@change false
        if (delta < 0 && id in claimed) return@change false
        val old = progress[id] ?: 0
        val new = (old + delta).coerceIn(0, def.target)
        if (new == old) return@change false
        progress = progress + (id to new)
        saveToday()
        true
    }

    /**
     * Opens the chest of a finished task.
     * @return true if it was opened now (false if the task is not finished, or already opened).
     */
    @Synchronized
    fun claim(id: String): Boolean = change {
        val task = _state.value.tasks.firstOrNull { it.id == id } ?: return@change false
        if (!task.done || id in claimed) return@change false
        claimed = claimed + id
        saveToday()
        true
    }

    // ── Inside the repository ─────────────────────────────────────────────

    /**
     * Runs one change. Before it, a new day is noticed (and everything is
     * reset for it); after it, the new list is published to the screens.
     */
    private fun <T> change(block: () -> T): T {
        rollDayIfNeeded()
        val result = block()
        publish()
        return result
    }

    private fun rollDayIfNeeded() {
        val today = LocalDate.now()
        if (today == day) return
        day = today
        progress = emptyMap()
        claimed = emptySet()
        saveToday()
        // Show the fresh list right away, so nothing below reads yesterday's state.
        publish()
    }

    private fun publish() {
        val list = ArrayList<DailyTask>()

        val setId = DailyTaskRules.POMODORO_SET_ID
        list += DailyTask(
            id = setId,
            title = "Complete a full Pomodoro set",
            subtitle = "All focus sessions, then the long break",
            emoji = "\uD83C\uDF45", // 🍅
            colorIndex = DailyTaskRules.POMODORO_SET_COLOR,
            target = 1,
            progress = (progress[setId] ?: 0).coerceIn(0, 1),
            claimed = setId in claimed,
            isBuiltIn = true
        )

        for (def in customDefs) {
            list += DailyTask(
                id = def.id,
                title = def.title,
                subtitle = null,
                emoji = def.emoji,
                colorIndex = def.colorIndex,
                target = def.target,
                progress = (progress[def.id] ?: 0).coerceIn(0, def.target),
                claimed = def.id in claimed,
                isBuiltIn = false
            )
        }
        _state.value = DailyTasksState(day, list)
    }

    // ── Saving and loading ────────────────────────────────────────────────
    // Bad or damaged data is skipped instead of crashing the app.

    private fun saveCustom() {
        val array = JSONArray()
        for (def in customDefs) {
            array.put(
                JSONObject()
                    .put("id", def.id)
                    .put("title", def.title)
                    .put("target", def.target)
                    .put("emoji", def.emoji)
                    .put("color", def.colorIndex)
            )
        }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    private fun loadCustom(): List<CustomTaskDef> = runCatching {
        val array = JSONArray(prefs.getString(KEY_CUSTOM, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val title = o.optString("title").trim()
            if (id.isBlank() || title.isEmpty()) return@mapNotNull null
            CustomTaskDef(
                id = id,
                title = title.take(DailyTaskRules.MAX_TITLE_LENGTH),
                target = o.optInt("target", 1).coerceIn(1, DailyTaskRules.MAX_TARGET),
                emoji = o.optString("emoji").ifBlank { DailyTaskRules.DEFAULT_EMOJI },
                colorIndex = o.optInt("color", 0).coerceIn(0, DailyTaskRules.COLOR_COUNT - 1)
            )
        }.take(DailyTaskRules.MAX_CUSTOM_TASKS)
    }.getOrDefault(emptyList())

    private fun saveToday() {
        val progressJson = JSONObject()
        for ((id, value) in progress) progressJson.put(id, value)
        val claimedJson = JSONArray()
        for (id in claimed) claimedJson.put(id)
        prefs.edit()
            .putLong(KEY_DAY, day.toEpochDay())
            .putString(KEY_PROGRESS, progressJson.toString())
            .putString(KEY_CLAIMED, claimedJson.toString())
            .apply()
    }

    private fun loadToday() {
        day = LocalDate.now()
        // Numbers saved on an earlier day are simply not used: a new day starts empty.
        if (prefs.getLong(KEY_DAY, -1L) != day.toEpochDay()) {
            progress = emptyMap()
            claimed = emptySet()
            return
        }
        progress = runCatching {
            val o = JSONObject(prefs.getString(KEY_PROGRESS, "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
        claimed = runCatching {
            val a = JSONArray(prefs.getString(KEY_CLAIMED, "[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val FILE_NAME = "daily_tasks"
        private const val KEY_CUSTOM = "custom_tasks_v1"
        private const val KEY_DAY = "day_epoch"
        private const val KEY_PROGRESS = "progress_v1"
        private const val KEY_CLAIMED = "claimed_v1"
        private const val KEY_LAST_SET_RUN = "last_set_run_id"

        @Volatile
        private var instance: DailyTasksRepository? = null

        fun get(context: Context): DailyTasksRepository =
            instance ?: synchronized(this) {
                instance ?: DailyTasksRepository(context.applicationContext).also { instance = it }
            }
    }
}
