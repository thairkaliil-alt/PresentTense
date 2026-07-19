package com.allinone.blocker.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.allinone.blocker.receiver.LockdownScheduleTriggerReceiver
import java.util.Calendar

/**
 * Wakes the device at the exact moment a scheduled (or manual) lockdown
 * session is due to start, end, or resume after a break — even if the
 * phone has been asleep the whole time and Present Tense hasn't been
 * opened at all.
 *
 * THE BUG THIS FIXES ("scheduled lockdown doesn't start on its own — only
 * kicks in once I open the app"): before this file existed, the ONLY thing
 * that ever noticed a schedule's window opening was
 * AppBlockerAccessibilityService's own background loop (see its
 * tickLockdownGuard/startLockdownGuardLoop) — and that loop only runs while
 * the app's process actually has CPU time. When the phone is genuinely
 * asleep (screen off, deep Doze), a plain coroutine delay() does NOT fire
 * in real wall-clock time — it only catches up the next time SOMETHING
 * wakes the CPU. In practice, that "something" was always you physically
 * opening the app: that first touch is what wakes the phone up and lets
 * everything else run, which is why the schedule looked "correct" the
 * instant you opened the app but never on its own before that. The
 * schedule math itself (LockdownEngine.evaluate) was always right — it
 * just never got asked at the right moment without your help.
 *
 * THE FIX: don't poll — ask AlarmManager to wake the device at the EXACT
 * instant something needs to change, via setExactAndAllowWhileIdle. Unlike
 * a coroutine delay(), this fires even during deep Doze (this is the same
 * mechanism [LockdownGuard]'s 45-second watchdog already relies on, and the
 * same general technique dedicated digital-detox apps like Opal/Forest/One
 * Sec use to start sessions on their own). [LockdownScheduleTriggerReceiver]
 * handles what happens when it fires, including re-arming the NEXT one —
 * so once this is armed the first time, the chain keeps itself going
 * forever with no polling and no battery cost in between.
 */
object LockdownScheduleAlarms {

    private const val REQUEST_CODE = 71_101

    /**
     * Cancels whatever was pending and arms a single exact alarm for the
     * next moment [LockdownEngine.evaluate]'s answer would change: a
     * schedule's window opening or closing, a break ending, or a manual
     * session's timer running out. If nothing is scheduled, active, or on
     * a break right now, this simply cancels any pending alarm and leaves
     * it at that — there's nothing to wait for.
     *
     * Safe to call as often as you like, from anywhere: schedule edits,
     * manual lock start/stop, break start/end, app start, boot, and every
     * time the alarm itself fires.
     */
    fun rearm(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(appContext)
        runCatching { am.cancel(pendingIntent) }

        val next = nextTransitionMillis(nowMillis) ?: return
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
        }
    }

    /** Cancels the pending transition alarm without scheduling a new one. */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pendingIntent(appContext)) }
    }

    /**
     * The earliest moment strictly after [nowMillis] at which
     * [LockdownEngine.evaluate]'s decision would flip to something
     * different — or null if there's genuinely nothing to wait for (no
     * enabled schedules, no manual session, no break).
     */
    private fun nextTransitionMillis(nowMillis: Long): Long? {
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value,
            nowMillis = nowMillis
        )

        val candidates = mutableListOf<Long>()

        // A session (manual or scheduled) is enforced right now — wake up
        // right when it's due to end. (endsAtMillis can be Long.MAX_VALUE
        // for a manual "lock until I turn it off" session with no natural
        // end — nothing to wait for in that case, so it's skipped.)
        if (decision.active && decision.endsAtMillis in nowMillis until Long.MAX_VALUE) {
            candidates += decision.endsAtMillis
        }
        // On an emergency break inside a session — wake up when the break
        // ends (so enforcement resumes exactly on time) and also, in case
        // that isn't the end of the whole session, when the session itself
        // is due to end.
        if (decision.onBreak) {
            if (decision.breakEndsAtMillis > nowMillis) candidates += decision.breakEndsAtMillis
            if (decision.endsAtMillis in nowMillis until Long.MAX_VALUE) candidates += decision.endsAtMillis
        }

        // Every enabled schedule's own next upcoming start. This is the one
        // that matters most — it's what makes a lockdown that ISN'T
        // running yet start on its own, right on time, even from a phone
        // that's been asleep for hours.
        BlockerRepository.schedules.value.forEach { schedule ->
            nextScheduleStartMillis(schedule, nowMillis)?.let { candidates += it }
        }

        return candidates.filter { it > nowMillis }.minOrNull()
    }

    /**
     * Next time [schedule]'s window is due to START after [nowMillis], or
     * null if it's disabled or has no repeat days selected at all (a
     * schedule with no days can never start — see repeatSummary() in
     * LockdownSchedulesScreen.kt, which shows "Never" for exactly this
     * case). Mirrors StrictAlarmEntry.nextTriggerMillis's repeating-alarm
     * search in StrictAlarmEntry.kt, just driven by minutes-since-midnight
     * instead of separate hour/minute fields.
     */
    private fun nextScheduleStartMillis(schedule: LockdownSchedule, nowMillis: Long): Long? {
        if (!schedule.enabled || schedule.daysOfWeek.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        for (dayOffset in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, schedule.startMinutes / 60)
                set(Calendar.MINUTE, schedule.startMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val triggerAt = candidate.timeInMillis
            val dayMatches = candidate.get(Calendar.DAY_OF_WEEK) in schedule.daysOfWeek
            if (dayMatches && triggerAt > nowMillis) return triggerAt
        }
        return null
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LockdownScheduleTriggerReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
