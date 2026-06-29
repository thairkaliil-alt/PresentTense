package com.allinone.blocker.data

import android.content.Context
import java.util.Calendar

/**
 * Pure-ish decision logic: given a [BlockedApp] and the current moment,
 * decide whether it should be blocked and why. An app is blocked if ANY of
 * its stacked rules trigger (README 1.1 combination semantics).
 */
object BlockEngine {

    /**
     * @param sessionStart epoch millis the app most recently entered the
     *        foreground (for SESSION_LIMIT), or 0 if not currently foreground.
     */
    fun evaluate(
        context: Context,
        app: BlockedApp,
        reelsKillSwitch: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        sessionStart: Long = 0L
    ): BlockDecision {
        if (!app.enabled) return BlockDecision(false)

        // Reels / Shorts kill switch (README 4.1) forces known short-form apps off.
        if (reelsKillSwitch && app.isReels) {
            return BlockDecision(true, "Reels / Shorts kill switch is on")
        }

        // No explicit rules but the app is enabled -> treat as a simple block.
        if (app.rules.isEmpty()) {
            return BlockDecision(true, "App is blocked")
        }

        val nowMinutes = minutesOfDay(nowMillis)

        for (rule in app.rules) {
            when (rule.type) {
                BlockRuleType.PERMANENT ->
                    return BlockDecision(true, "Permanently blocked")

                BlockRuleType.TIME_INTERVAL -> {
                    if (inWindow(nowMinutes, rule.startMinutes, rule.endMinutes)) {
                        return BlockDecision(
                            true,
                            "Blocked until ${formatMinutes(rule.endMinutes)}"
                        )
                    }
                }

                BlockRuleType.DAILY_LIMIT -> {
                    val used = UsageTracker.todayUsageMinutes(context, app.packageName)
                    if (used >= rule.limitMinutes) {
                        return BlockDecision(
                            true,
                            "Daily limit reached (${rule.limitMinutes} min)"
                        )
                    }
                }

                BlockRuleType.OPEN_COUNT -> {
                    if (BlockerRepository.opensToday(app.packageName) >= rule.count) {
                        return BlockDecision(
                            true,
                            "Open limit reached (${rule.count}/day)"
                        )
                    }
                }

                BlockRuleType.COOLDOWN -> {
                    val last = BlockerRepository.lastUse(app.packageName)
                    if (last > 0) {
                        val elapsed = nowMillis - last
                        val cooldownMs = rule.cooldownMinutes * 60_000L
                        if (elapsed < cooldownMs) {
                            val remaining = (cooldownMs - elapsed) / 1000
                            return BlockDecision(
                                true,
                                "Cooling down (${rule.cooldownMinutes} min)",
                                remaining
                            )
                        }
                    }
                }

                BlockRuleType.SESSION_LIMIT -> {
                    if (sessionStart > 0) {
                        val sessionMs = nowMillis - sessionStart
                        val limitMs = rule.limitMinutes * 60_000L
                        if (sessionMs >= limitMs) {
                            return BlockDecision(
                                true,
                                "Session limit reached (${rule.limitMinutes} min)"
                            )
                        }
                    }
                }
            }
        }

        return BlockDecision(false)
    }

    private fun inWindow(now: Int, start: Int, end: Int): Boolean =
        if (start <= end) now in start until end else now >= start || now < end

    private fun minutesOfDay(millis: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun formatMinutes(m: Int): String {
        val h = (m / 60) % 24
        val min = m % 60
        val period = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, min, period)
    }
}
