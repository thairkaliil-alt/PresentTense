package com.allinone.blocker.data

/**
 * Option C: a short, no-friction window right at the START of any new
 * lockdown session (manual or scheduled) where the person can back out
 * completely — no PIN, no Strict Mode challenge, no burned emergency break.
 *
 * WHY THIS EXISTS: [LockdownEngine.evaluate] treats "is the current moment
 * inside a schedule's day/time window" as "lockdown is active right now" —
 * so saving or toggling on a schedule that happens to cover this exact
 * moment (or tapping a manual-lock preset) instantly starts a real session
 * with zero warning. Option A (see the confirmation dialogs in
 * LockdownSchedulesScreen.kt) catches the schedule/toggle case BEFORE it
 * happens. This object is the safety net for everything else — including
 * the manual-lock hold-to-void gesture in LockdownScreen.kt, which is a
 * deliberate action but still just as capable of being a "oh, I was just
 * testing the UI" mistake.
 *
 * This is NOT a general-purpose escape hatch. It only ever looks at
 * whichever session [LockdownCompletionRepository] is CURRENTLY tracking
 * via its [LockdownCompletionRepository.OngoingSessionMarker] (never a
 * separately-computed timer of its own — see [currentOngoingSession]'s
 * kdoc), and only acts while `now - startedAtMillis < GRACE_PERIOD_MS`. The
 * instant that window closes, cancelling stops being possible and the
 * session runs exactly as strict as configured, completely unchanged from
 * today — Strict Mode, the emergency-break limit, corralling, and the
 * watchdog are never touched by this file.
 *
 * WHAT "CANCEL" MEANS PER SESSION KIND:
 *   - MANUAL: the one sanctioned place `manualLockUntil` is ever set back to
 *     0 before it would naturally end — see the big comment in
 *     [BlockerRepository] right above [BlockerRepository.clearManualLockForGraceCancel]
 *     for why this doesn't reopen the "no endManualLock()" door.
 *   - SCHEDULED: schedules recur, so disabling the whole [LockdownSchedule]
 *     would wrongly cancel every future occurrence too. Instead this marks
 *     ONLY the one occurrence that's currently live — keyed by the
 *     schedule's id plus the exact minute it started — as cancelled (see
 *     [BlockerRepository.markScheduleOccurrenceCancelled]), so
 *     [LockdownEngine.evaluate] skips just that window and the schedule
 *     resumes normally at its next scheduled time.
 *
 * Either way, cancelling within the grace period is treated as if the
 * session never meaningfully started at all: [LockdownCompletionRepository.discardOngoingSession]
 * clears the tracked marker WITHOUT ever building a [LockdownCompletionRepository.CompletedSession]
 * record, so it can't bump the lifetime stats, trigger a celebration, or
 * (since [BlockerRepository.startEmergencyBreak] is never called) consume
 * an emergency break. It also never goes through [StrictModeGate], so it
 * can't count as a broken streak either — see [StrictModeGate.guard]'s own
 * comment on what does and doesn't count against the streak.
 */
object LockdownGracePeriod {

    /** How long after a session genuinely begins the no-friction "Cancel lockdown" option stays available. */
    const val GRACE_PERIOD_MS = 60_000L

    /**
     * Milliseconds left in the grace window for whichever session
     * [LockdownCompletionRepository] is currently tracking, clamped to
     * never go below 0. 0 both when there's nothing being tracked and when
     * a real session's window has simply closed — callers that only care
     * about "should I show the cancel option" want [isWithinGrace] instead,
     * but this is handy for a UI countdown ("cancel for the next 43s").
     */
    fun remainingMs(nowMillis: Long = System.currentTimeMillis()): Long {
        val startedAt = LockdownCompletionRepository.currentSessionStartedAtMillis() ?: return 0L
        return (GRACE_PERIOD_MS - (nowMillis - startedAt)).coerceAtLeast(0L)
    }

    /** True while the no-friction cancel option should still be shown for whatever session is currently tracked. */
    fun isWithinGrace(nowMillis: Long = System.currentTimeMillis()): Boolean = remainingMs(nowMillis) > 0L

    /**
     * Cancels whatever session is currently being tracked, IF it's still
     * within its grace period — a no-op (returns false) otherwise, so a
     * stray/late call (e.g. a delayed tap right as the window closes) can
     * never do anything. Returns true if a session was actually cancelled.
     *
     * Safe to call from any screen; every field this touches is a
     * StateFlow/SharedPreferences write the rest of the app already
     * observes (manualLockUntil, or the persisted cancelled-occurrence set
     * LockdownEngine.evaluate() reads), so the UI updates on its own —
     * callers don't need to manually navigate anywhere afterward, though
     * LockdownLauncherActivity does still exit back to the main app since
     * it's the one screen a "no lockdown active" state can't stay on.
     */
    fun cancelCurrentSession(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val session = LockdownCompletionRepository.currentOngoingSession() ?: return false
        if (!isWithinGrace(nowMillis)) return false

        // Treat it as if it never meaningfully started — no completion
        // record, no lifetime-stat bump, no celebration. Done FIRST, before
        // either branch below, so there's no window where the marker is
        // still around but the underlying lock has already been lifted.
        LockdownCompletionRepository.discardOngoingSession(session.startedAtMillis)

        when (session.sessionKind) {
            LockdownCompletionRepository.SessionKind.MANUAL ->
                BlockerRepository.clearManualLockForGraceCancel()

            LockdownCompletionRepository.SessionKind.SCHEDULED -> {
                val scheduleId = session.scheduleId
                // Defensive only: every SCHEDULED marker is created via
                // LockdownEngine.evaluate(), which always supplies a
                // scheduleId — this should never actually be null.
                if (scheduleId != null) {
                    BlockerRepository.markScheduleOccurrenceCancelled(scheduleId, session.startedAtMillis)
                }
            }
        }
        return true
    }
}
