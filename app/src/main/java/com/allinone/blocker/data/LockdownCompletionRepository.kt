package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Closes the one real gap in Lockdown Mode: a session that runs its full
 * course currently just... stops. No acknowledgment, no record, nothing.
 * Per the Peak-End Rule, the END of an experience is remembered almost as
 * strongly as its most intense moment — and right now Lockdown has a
 * genuine peak (the void-swallow ignition) and a zero-value end. This
 * object is the small persisted store that closes that gap: it notices a
 * session finishing, remembers it happened, keeps a running lifetime total
 * (the Progress Principle payoff), and hands the UI exactly one thing to
 * celebrate at a time via [pendingCelebration].
 *
 * DELIBERATELY NOT a compulsion loop — this is an anti-compulsion app, and
 * some retention tactics (variable rewards, loss-aversion streak framing,
 * infinite engagement hooks) are the same mechanics that make the apps it
 * blocks addictive. So:
 *   - No randomness in what's shown — the same completed session always
 *     produces the same, honest record.
 *   - No "streak about to die" / urgency framing here — that's
 *     [StreakRepository]'s job, and it already avoids this too.
 *   - The celebration only ever reflects something that ALREADY happened
 *     (a session that already ran its full course), never anticipation of
 *     what's next.
 *
 * HOW A SESSION IS TRACKED (see LockdownGuard/LockdownWatchdogReceiver/
 * AppBlockerAccessibilityService/LockdownLauncherActivity for the detection
 * side): there is no single moment a session "ends" from this object's
 * point of view. Instead:
 *   1. Something calls [markManualSessionStarted] or
 *      [maybeMarkScheduledSessionStarted] the moment a session begins,
 *      which persists a small [OngoingSessionMarker] — this survives
 *      process death, same as everything else in this app.
 *   2. Multiple independent places in the app (see the callers of
 *      [recordCompletionIfNeeded]) each independently notice, in their own
 *      way and on their own schedule, the moment [LockdownEngine] reports
 *      the session is no longer active AND no longer on a break. Whichever
 *      one notices FIRST calls [recordCompletionIfNeeded], which reads the
 *      marker, builds the [CompletedSession] record, updates the lifetime
 *      counters, and queues it as the one [pendingCelebration]. Every other
 *      caller for that same session is a guaranteed no-op (keyed off the
 *      session's own start time) — see the comment on that function.
 */
object LockdownCompletionRepository {

    private const val PREFS = "lockdown_completion_prefs"
    private const val KEY_ONGOING_SESSION = "ongoing_session"
    private const val KEY_LAST_RECORDED_START = "last_recorded_session_start"
    private const val KEY_LIFETIME_SESSIONS = "lifetime_sessions_completed"
    private const val KEY_LIFETIME_MINUTES = "lifetime_minutes_locked"
    private const val KEY_PENDING_CELEBRATION = "pending_celebration"

    /**
     * Session-count milestones that earn the amplified celebration (bigger
     * confetti burst + an [AppFlame] pulse). Mirrors the role of
     * [StreakRepository.MILESTONE_DAYS], but for individual completed
     * lockdown sessions rather than daily streaks — a deliberately separate
     * concept (see the header comment on that file).
     */
    val MILESTONE_SESSION_COUNTS = setOf(10, 25, 50, 100, 250, 500)

    /** Also treat every round 10-hour lifetime total as a milestone. */
    private const val MILESTONE_HOUR_STEP_MINUTES = 10 * 60

    enum class SessionKind { MANUAL, SCHEDULED }

    /** One completed session, exactly as shown on the celebration screen. */
    data class CompletedSession(
        val startedAtMillis: Long,
        val endedAtMillis: Long,
        /** The length the user actually chose/scheduled — the "you did the thing you set out to do" stat, not raw elapsed wall time (which can run a few seconds/minutes long purely due to detection lag). */
        val plannedMinutes: Int,
        val sessionKind: SessionKind,
        /** e.g. "Manual lockdown", or a schedule's own label ("Weeknights", etc.). */
        val reasonLabel: String,
        val breaksUsed: Int,
        /** True if it ran with zero emergency breaks — the "real, uninterrupted completion" that earns the full celebration per the Duolingo reference in PomodoroScreen.kt. */
        val completedCleanly: Boolean,
        val isMilestone: Boolean,
        /** Snapshot of the running totals AFTER this session is counted. */
        val lifetimeSessionsCompleted: Int,
        val lifetimeMinutesLocked: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("startedAtMillis", startedAtMillis)
            put("endedAtMillis", endedAtMillis)
            put("plannedMinutes", plannedMinutes)
            put("sessionKind", sessionKind.name)
            put("reasonLabel", reasonLabel)
            put("breaksUsed", breaksUsed)
            put("completedCleanly", completedCleanly)
            put("isMilestone", isMilestone)
            put("lifetimeSessionsCompleted", lifetimeSessionsCompleted)
            put("lifetimeMinutesLocked", lifetimeMinutesLocked)
        }

        companion object {
            fun fromJson(o: JSONObject): CompletedSession = CompletedSession(
                startedAtMillis = o.optLong("startedAtMillis", 0L),
                endedAtMillis = o.optLong("endedAtMillis", 0L),
                plannedMinutes = o.optInt("plannedMinutes", 0),
                sessionKind = runCatching { SessionKind.valueOf(o.getString("sessionKind")) }
                    .getOrDefault(SessionKind.MANUAL),
                reasonLabel = o.optString("reasonLabel", "Lockdown"),
                breaksUsed = o.optInt("breaksUsed", 0),
                completedCleanly = o.optBoolean("completedCleanly", true),
                isMilestone = o.optBoolean("isMilestone", false),
                lifetimeSessionsCompleted = o.optInt("lifetimeSessionsCompleted", 0),
                lifetimeMinutesLocked = o.optLong("lifetimeMinutesLocked", 0L)
            )
        }
    }

    /** The session currently believed to be live — persisted so a killed process doesn't lose track of it. Not shown to the UI directly; only [CompletedSession] is. */
    private data class OngoingSessionMarker(
        val startedAtMillis: Long,
        /** Long.MAX_VALUE for an indefinite manual session — see [markManualSessionStarted]. */
        val plannedEndAtMillis: Long,
        val plannedMinutes: Int,
        val sessionKind: SessionKind,
        val reasonLabel: String,
        /** The [LockdownSchedule.id] this occurrence came from, or null for a MANUAL session. Lets the grace-period cancel (see [LockdownGracePeriod]) know exactly which schedule's occurrence to suppress, without recomputing which schedule "must have" produced this marker. */
        val scheduleId: String? = null,
        /**
         * The real wall-clock moment THIS app process first became aware of this
         * session — as opposed to [startedAtMillis], which for a SCHEDULED session
         * is the schedule's own start-of-window time and can already be in the past
         * the moment this marker is first created (e.g. a schedule whose window
         * happens to already cover "now" the instant it's saved/enabled, or a
         * session only picked up late after the phone was asleep). [startedAtMillis]
         * has to stay exactly what it is for [plannedMinutes] and the cancelled-
         * occurrence keying (see [BlockerRepository.markScheduleOccurrenceCancelled])
         * to keep working — this field exists purely so [LockdownGracePeriod] has an
         * honest "when did the safety net actually begin counting down" anchor
         * instead of borrowing a timestamp that means something else. For a MANUAL
         * session the two are always identical (see [markManualSessionStarted]).
         */
        val firstObservedAtMillis: Long = startedAtMillis
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("startedAtMillis", startedAtMillis)
            put("plannedEndAtMillis", plannedEndAtMillis)
            put("plannedMinutes", plannedMinutes)
            put("sessionKind", sessionKind.name)
            put("reasonLabel", reasonLabel)
            put("scheduleId", scheduleId ?: JSONObject.NULL)
            put("firstObservedAtMillis", firstObservedAtMillis)
        }

        companion object {
            fun fromJson(o: JSONObject): OngoingSessionMarker {
                val startedAtMillis = o.optLong("startedAtMillis", 0L)
                return OngoingSessionMarker(
                    startedAtMillis = startedAtMillis,
                    plannedEndAtMillis = o.optLong("plannedEndAtMillis", 0L),
                    plannedMinutes = o.optInt("plannedMinutes", 0),
                    sessionKind = runCatching { SessionKind.valueOf(o.getString("sessionKind")) }
                        .getOrDefault(SessionKind.MANUAL),
                    reasonLabel = o.optString("reasonLabel", "Lockdown"),
                    scheduleId = if (o.isNull("scheduleId")) null else o.optString("scheduleId", null),
                    // Falls back to startedAtMillis for a marker persisted by an older
                    // build that predates this field, which just resumes the old
                    // (buggy) behavior for that one in-flight session instead of crashing.
                    firstObservedAtMillis = o.optLong("firstObservedAtMillis", startedAtMillis)
                )
            }
        }
    }

    /**
     * Read-only snapshot of whatever [OngoingSessionMarker] is currently
     * persisted — the one intentional crack in [loadOngoingMarker]'s privacy,
     * added for [LockdownGracePeriod]'s cancel UI so it can read exactly
     * when the live session started (and, for a scheduled one, which
     * schedule it came from) without duplicating this persisted state
     * anywhere else. Every other consumer should keep going through
     * [markManualSessionStarted] / [maybeMarkScheduledSessionStarted] /
     * [recordCompletionIfNeeded] instead of reaching in here.
     */
    data class OngoingSessionSnapshot(
        val startedAtMillis: Long,
        val sessionKind: SessionKind,
        val scheduleId: String?,
        /** See [OngoingSessionMarker.firstObservedAtMillis]. */
        val firstObservedAtMillis: Long
    )

    /** See [OngoingSessionSnapshot]. Null if no session is currently being tracked. */
    fun currentOngoingSession(): OngoingSessionSnapshot? {
        if (!initialized) return null
        val m = loadOngoingMarker() ?: return null
        return OngoingSessionSnapshot(m.startedAtMillis, m.sessionKind, m.scheduleId, m.firstObservedAtMillis)
    }

    /** Convenience accessor for just the start time of whatever session is currently being tracked — see [currentOngoingSession]. */
    fun currentSessionStartedAtMillis(): Long? = currentOngoingSession()?.startedAtMillis

    /**
     * Convenience accessor for [OngoingSessionMarker.firstObservedAtMillis] —
     * i.e. when the app actually first noticed the currently-tracked session,
     * which is what [LockdownGracePeriod] anchors its safety-net countdown to.
     * Deliberately separate from [currentSessionStartedAtMillis]; see that
     * field's kdoc for why the two aren't the same thing.
     */
    fun currentSessionFirstObservedAtMillis(): Long? = currentOngoingSession()?.firstObservedAtMillis

    private lateinit var prefs: SharedPreferences
    @Volatile private var initialized = false

    private val _pendingCelebration = MutableStateFlow<CompletedSession?>(null)
    /** The most recently completed session the UI hasn't shown a celebration for yet, or null. */
    val pendingCelebration: StateFlow<CompletedSession?> = _pendingCelebration

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Re-hydrate a celebration that was queued but never shown (e.g. the
        // app was killed right after the watchdog recorded it) so reopening
        // the app later still shows it exactly once — not lost, not stale.
        prefs.getString(KEY_PENDING_CELEBRATION, null)?.let { raw ->
            runCatching { _pendingCelebration.value = CompletedSession.fromJson(JSONObject(raw)) }
        }
        initialized = true
    }

    val isInitialized: Boolean get() = initialized

    /** Call once the celebration screen has actually been shown/dismissed, so it never re-appears on a later app open. */
    fun consumePendingCelebration() {
        if (!initialized) return
        _pendingCelebration.value = null
        prefs.edit().remove(KEY_PENDING_CELEBRATION).apply()
    }

    // ═══════════════════════════════ Session START ═══════════════════════════════

    /**
     * Called from [BlockerRepository.startManualLock] / [BlockerRepository.startManualLockIndefinite]
     * the moment a manual session actually begins.
     *
     * @param indefinite true for [BlockerRepository.startManualLockIndefinite] sessions
     *   (`manualLockUntil = Long.MAX_VALUE`). These never end on their own — there is
     *   deliberately no `endManualLock()` — so this marker's `plannedEndAtMillis` is
     *   set to Long.MAX_VALUE too, and [recordCompletionIfNeeded] hard-refuses to ever
     *   treat one as completed (see the guard there). [plannedMinutes] is ignored when true.
     */
    fun markManualSessionStarted(startedAtMillis: Long, plannedMinutes: Int, indefinite: Boolean) {
        if (!initialized) return
        closeOutStaleMarkerIfReplaced(startedAtMillis)
        saveOngoingMarker(
            OngoingSessionMarker(
                startedAtMillis = startedAtMillis,
                plannedEndAtMillis = if (indefinite) Long.MAX_VALUE else startedAtMillis + plannedMinutes * 60_000L,
                plannedMinutes = if (indefinite) -1 else plannedMinutes,
                sessionKind = SessionKind.MANUAL,
                reasonLabel = "Manual lockdown",
                // A manual session is always started by a real, in-the-moment tap —
                // startedAtMillis already IS "now" here, so this is just that same
                // value again (see firstObservedAtMillis's kdoc for why the two
                // fields exist separately at all).
                firstObservedAtMillis = startedAtMillis
            )
        )
    }

    /**
     * Called from [LockdownEngine.evaluate] every time it resolves a currently-running
     * SCHEDULED window, right alongside the existing
     * [BlockerRepository.maybeResetBreaksForScheduledSession] call it already makes for
     * the same purpose. Cheap to call on every single `evaluate()` tick (which happens a
     * lot — screen recomposition, the watchdog, the accessibility loop) because it's a
     * no-op unless [plannedEndAtMillis] doesn't match what's already being tracked, i.e.
     * unless this is genuinely a NEW occurrence (today's window vs. yesterday's, or a
     * different schedule entirely) — this is exactly what makes a recurring daily
     * schedule count each day's occurrence as its own completed session instead of one
     * continuous blob.
     */
    fun maybeMarkScheduledSessionStarted(
        startedAtMillis: Long,
        plannedEndAtMillis: Long,
        label: String,
        scheduleId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!initialized) return
        val current = loadOngoingMarker()
        if (current != null && current.sessionKind == SessionKind.SCHEDULED && current.plannedEndAtMillis == plannedEndAtMillis) {
            return // already tracking this exact occurrence — nothing to do
        }
        closeOutStaleMarkerIfReplaced(startedAtMillis)
        val plannedMinutes = ((plannedEndAtMillis - startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
        saveOngoingMarker(
            OngoingSessionMarker(
                startedAtMillis = startedAtMillis,
                plannedEndAtMillis = plannedEndAtMillis,
                plannedMinutes = plannedMinutes,
                sessionKind = SessionKind.SCHEDULED,
                reasonLabel = label.ifBlank { "Scheduled lockdown" },
                scheduleId = scheduleId,
                // BUGFIX: this is the one place a SCHEDULED session's marker gets
                // created, and startedAtMillis here is the schedule's own
                // start-of-window time — which, unlike a manual session's, can
                // already be minutes (or more) in the past by the time this line
                // actually runs (a schedule saved/enabled while its window already
                // covers right now; a window only picked up late after the phone
                // was asleep). Recording nowMillis here — the real moment this
                // occurrence was first noticed — instead of reusing startedAtMillis
                // is what makes LockdownGracePeriod's 1-minute safety net always
                // start counting down from the moment the session actually begins
                // being enforced, instead of sometimes starting pre-expired.
                firstObservedAtMillis = nowMillis
            )
        )
    }

    /**
     * A manual session can, without ever going through a visible "not live" moment,
     * hand straight off into an already-active scheduled window the instant its timer
     * runs out (e.g. a manual session that was running during a recurring overnight
     * schedule). In that case none of [recordCompletionIfNeeded]'s callers ever see
     * `sessionLive == false` — LockdownEngine just keeps reporting `active = true`,
     * seamlessly, for the scheduled window that was there the whole time. Without this,
     * that manual session's completion (and its reward) would be silently lost the
     * instant the new marker overwrote the old one. So: whenever a NEW session is about
     * to start being tracked, first check whether the marker it's about to replace was
     * a genuinely different, still-unrecorded session — and if so, close it out (using
     * "now" as its end time) before moving on. A no-op in the overwhelmingly common
     * case where there's nothing stale to flush.
     */
    private fun closeOutStaleMarkerIfReplaced(newStartedAtMillis: Long) {
        val stale = loadOngoingMarker() ?: return
        if (stale.startedAtMillis == newStartedAtMillis) return // the same session re-announcing itself
        if (stale.plannedEndAtMillis == Long.MAX_VALUE) return // indefinite sessions are never recorded as completed
        if (prefs.getLong(KEY_LAST_RECORDED_START, -1L) == stale.startedAtMillis) return // already recorded elsewhere
        closeOutMarker(stale, endedAtMillis = newStartedAtMillis)
    }

    // ═══════════════════════════════ Session END ═══════════════════════════════

    /**
     * Call this from anywhere that has just independently confirmed, via
     * [LockdownEngine], that `!decision.active && !decision.onBreak` right now — i.e.
     * the session is truly over, not merely paused for an emergency break. Deliberately
     * safe (and expected) to be called from several independent places for the exact
     * same real-world transition — [LockdownWatchdogReceiver] (background/killed-app
     * case, ~45s cadence), [LockdownLauncherActivity.onResume] (foreground case),
     * [com.allinone.blocker.service.AppBlockerAccessibilityService]'s guard loop
     * (fastest — ~3s while a session is live), and
     * [com.allinone.blocker.service.BlockerForegroundService]'s independent self-check.
     * Only the first of these to run for a given session actually writes anything; every
     * later call for that same session (keyed off its own start time) is a no-op.
     */
    fun recordCompletionIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        if (!initialized) return
        val marker = loadOngoingMarker() ?: return

        // Defensive only: an indefinite session's plannedEndAtMillis is
        // Long.MAX_VALUE and LockdownEngine.evaluate() can never report one
        // as inactive on its own (there is no endManualLock()) — so this
        // should never actually be reached for one. Guarded anyway rather
        // than trusting every future caller to get that invariant right.
        if (marker.plannedEndAtMillis == Long.MAX_VALUE) return

        if (prefs.getLong(KEY_LAST_RECORDED_START, -1L) == marker.startedAtMillis) {
            // Another detection path already won the race for this session —
            // just clear the marker so nobody keeps re-checking it forever.
            clearOngoingMarker()
            return
        }

        closeOutMarker(marker, endedAtMillis = nowMillis)
    }

    /**
     * Clears the ongoing-session marker WITHOUT ever recording a completion —
     * used only by [LockdownGracePeriod]'s cancel path, for a session being
     * treated as if it never meaningfully started at all (see that file's
     * header comment): no [CompletedSession] record, no lifetime-stat bump,
     * no celebration. [startedAtMillis] must match the marker currently
     * tracked — the same keyed-by-start-time guard [recordCompletionIfNeeded]
     * and [closeOutStaleMarkerIfReplaced] already use — so a slow or delayed
     * call can never wipe out a DIFFERENT, already-real session that has
     * since taken its place.
     */
    fun discardOngoingSession(startedAtMillis: Long) {
        if (!initialized) return
        val marker = loadOngoingMarker() ?: return
        if (marker.startedAtMillis != startedAtMillis) return
        clearOngoingMarker()
    }

    private fun closeOutMarker(marker: OngoingSessionMarker, endedAtMillis: Long) {
        val breaksUsed = BlockerRepository.breakUsesThisSession.value
        val previousLifetimeMinutes = prefs.getLong(KEY_LIFETIME_MINUTES, 0L)
        val lifetimeSessions = prefs.getInt(KEY_LIFETIME_SESSIONS, 0) + 1
        val lifetimeMinutes = previousLifetimeMinutes + marker.plannedMinutes.coerceAtLeast(0)
        val milestone = lifetimeSessions in MILESTONE_SESSION_COUNTS ||
            (previousLifetimeMinutes / MILESTONE_HOUR_STEP_MINUTES) < (lifetimeMinutes / MILESTONE_HOUR_STEP_MINUTES)

        val completed = CompletedSession(
            startedAtMillis = marker.startedAtMillis,
            endedAtMillis = endedAtMillis,
            plannedMinutes = marker.plannedMinutes,
            sessionKind = marker.sessionKind,
            reasonLabel = marker.reasonLabel,
            breaksUsed = breaksUsed,
            completedCleanly = breaksUsed == 0,
            isMilestone = milestone,
            lifetimeSessionsCompleted = lifetimeSessions,
            lifetimeMinutesLocked = lifetimeMinutes
        )

        prefs.edit()
            .putInt(KEY_LIFETIME_SESSIONS, lifetimeSessions)
            .putLong(KEY_LIFETIME_MINUTES, lifetimeMinutes)
            .putLong(KEY_LAST_RECORDED_START, marker.startedAtMillis)
            .putString(KEY_PENDING_CELEBRATION, completed.toJson().toString())
            .remove(KEY_ONGOING_SESSION)
            .apply()
        _pendingCelebration.value = completed
    }

    // ═══════════════════════════════ Lifetime stats ═══════════════════════════════

    fun lifetimeSessionsCompleted(): Int = if (!initialized) 0 else prefs.getInt(KEY_LIFETIME_SESSIONS, 0)
    fun lifetimeMinutesLocked(): Long = if (!initialized) 0L else prefs.getLong(KEY_LIFETIME_MINUTES, 0L)

    // ═══════════════════════════════ Storage helpers ═══════════════════════════════

    private fun loadOngoingMarker(): OngoingSessionMarker? {
        val raw = prefs.getString(KEY_ONGOING_SESSION, null) ?: return null
        return runCatching { OngoingSessionMarker.fromJson(JSONObject(raw)) }.getOrNull()
    }

    private fun saveOngoingMarker(marker: OngoingSessionMarker) {
        prefs.edit().putString(KEY_ONGOING_SESSION, marker.toJson().toString()).apply()
    }

    private fun clearOngoingMarker() {
        prefs.edit().remove(KEY_ONGOING_SESSION).apply()
    }
}
