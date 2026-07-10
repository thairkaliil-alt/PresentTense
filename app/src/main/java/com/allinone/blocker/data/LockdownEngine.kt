package com.allinone.blocker.data

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager
import java.util.Calendar

/**
 * Decides whether full-phone lockdown (README section 3) is currently active,
 * and whether a given package is exempt from it (phone/SMS, README 3.1).
 */
object LockdownEngine {

    private const val DAY_MS = 24 * 60 * 60_000L

    fun evaluate(
        manualLockUntil: Long,
        schedules: List<LockdownSchedule>,
        nowMillis: Long = System.currentTimeMillis(),
        breakUntilMillis: Long = BlockerRepository.breakUntil.value
    ): LockdownDecision {
        // A manual session always wins, and can run "until turned off" (Long.MAX_VALUE).
        if (manualLockUntil > nowMillis) {
            if (breakUntilMillis > nowMillis) {
                // A break is running: lockdown is NOT enforced right now (active=false),
                // but we still report the underlying session so the UI can show
                // "lockdown paused, resumes when break ends" instead of "off".
                return LockdownDecision(
                    active = false,
                    reason = "Manual lockdown",
                    endsAtMillis = manualLockUntil,
                    onBreak = true,
                    breakEndsAtMillis = breakUntilMillis
                )
            }
            return LockdownDecision(true, "Manual lockdown", manualLockUntil)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val today = cal.get(Calendar.DAY_OF_WEEK)
        val yesterday = if (today == Calendar.SUNDAY) Calendar.SATURDAY else today - 1
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        for (s in schedules) {
            if (!s.enabled) continue
            val overnight = s.startMinutes > s.endMinutes

            val startsToday = today in s.daysOfWeek &&
                if (overnight) nowMinutes >= s.startMinutes else nowMinutes in s.startMinutes until s.endMinutes
            if (startsToday) {
                val endMillis = startOfDay(nowMillis) + s.endMinutes * 60_000L + if (overnight) DAY_MS else 0L
                BlockerRepository.maybeResetBreaksForScheduledSession(endMillis)
                // This window started TODAY at s.startMinutes (whether or not
                // it also runs past midnight) — see the sibling branch below
                // for the window that started YESTERDAY and is still running.
                val startedAtMillis = startOfDay(nowMillis) + s.startMinutes * 60_000L
                LockdownCompletionRepository.maybeMarkScheduledSessionStarted(startedAtMillis, endMillis, s.label)
                if (breakUntilMillis > nowMillis) {
                    return LockdownDecision(
                        active = false,
                        reason = s.label.ifBlank { "Scheduled lockdown" },
                        endsAtMillis = endMillis,
                        onBreak = true,
                        breakEndsAtMillis = breakUntilMillis
                    )
                }
                return LockdownDecision(true, s.label.ifBlank { "Scheduled lockdown" }, endMillis)
            }

            // Overnight window that started yesterday and is still running into today.
            if (overnight && yesterday in s.daysOfWeek && nowMinutes < s.endMinutes) {
                val endMillis = startOfDay(nowMillis) + s.endMinutes * 60_000L
                BlockerRepository.maybeResetBreaksForScheduledSession(endMillis)
                // This window started YESTERDAY at s.startMinutes and is
                // still running into today — a distinct occurrence from the
                // "starts today" branch above, keyed by its own end time.
                val startedAtMillis = startOfDay(nowMillis) - DAY_MS + s.startMinutes * 60_000L
                LockdownCompletionRepository.maybeMarkScheduledSessionStarted(startedAtMillis, endMillis, s.label)
                if (breakUntilMillis > nowMillis) {
                    return LockdownDecision(
                        active = false,
                        reason = s.label.ifBlank { "Scheduled lockdown" },
                        endsAtMillis = endMillis,
                        onBreak = true,
                        breakEndsAtMillis = breakUntilMillis
                    )
                }
                return LockdownDecision(true, s.label.ifBlank { "Scheduled lockdown" }, endMillis)
            }
        }

        return LockdownDecision(false)
    }

    private fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * True if [pkg] should never be blocked by lockdown: our own app, the
     * phone's current default dialer, and the default SMS app. Wrapped safely —
     * if Android won't tell us the default dialer/SMS app for any reason, this
     * just returns false for that check instead of crashing, so you can always
     * add your phone/messaging app to the whitelist manually as a backup.
     */
    fun isAlwaysExempt(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return true
        val dialer = runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        }.getOrNull()
        if (dialer != null && pkg == dialer) return true
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        if (sms != null && pkg == sms) return true
        return false
    }

    /**
     * True for the phone's own system Settings app — every AOSP-based
     * Android build (stock, Samsung, Pixel, MIUI, etc.) ships this under the
     * same package name. This is where someone would turn off this app's
     * Accessibility Service or Device Admin — i.e. disable enforcement
     * itself — so it's treated as never-exempt during lockdown regardless
     * of whitelist status (see AppBlockerAccessibilityService.shouldCorralDuringLockdown
     * and BlockerRepository.addToWhitelist).
     */
    fun isSystemSettingsPackage(pkg: String): Boolean = pkg == "com.android.settings"
}
