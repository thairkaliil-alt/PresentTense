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
        nowMillis: Long = System.currentTimeMillis(),
        sessionStart: Long = 0L
    ): BlockDecision {
        if (!app.enabled) return BlockDecision(false)

        // NOTE: the Reels/Shorts kill switch is intentionally NOT handled
        // here. It used to short-circuit here with a blanket "block the
        // whole app" whenever the app was reels-capable (Instagram,
        // YouTube, Facebook, Snapchat, TikTok) — but that's not what the
        // kill switch is supposed to do; it should only block the
        // Reels/Shorts SCREEN inside the app, not the whole app. That
        // screen-level check needs the live accessibility node tree (see
        // ReelsDetector), which this function doesn't have access to, so
        // it's handled directly in AppBlockerAccessibilityService instead —
        // and only AFTER this function has already had first say on
        // whether the app is fully blocked by its own rules (schedule,
        // permanent block, limits, etc.). See the BUGFIX note over there
        // for why that order matters.

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

    /**
     * Same rule-checking as [evaluate], but used only to drive Strict Mode's
     * Active Plan auto-lock (see BlockerRepository.hasActiveTimedBlock).
     * Only rules that have a built-in expiry are considered:
     *  - TIME_INTERVAL ends at its window's end time
     *  - DAILY_LIMIT / OPEN_COUNT reset automatically at midnight
     *  - COOLDOWN ends once its timer runs out
     *  - SESSION_LIMIT ends once the session ends
     *
     * PERMANENT rules are intentionally skipped, and an app with an empty
     * rule list (which [evaluate] treats as "blocked, always") is treated
     * as not blocked here for the same reason. An app that is blocked
     * forever must never be able to keep Active Plan locked forever too.
     * The Reels/Shorts kill switch is skipped for the same reason — it's a
     * plain on/off toggle with no scheduled end, so it doesn't count here
     * either.
     */
    fun evaluateTimeBound(
        context: Context,
        app: BlockedApp,
        nowMillis: Long = System.currentTimeMillis(),
        sessionStart: Long = 0L
    ): BlockDecision {
        if (!app.enabled || app.rules.isEmpty()) return BlockDecision(false)

        val nowMinutes = minutesOfDay(nowMillis)

        for (rule in app.rules) {
            when (rule.type) {
                BlockRuleType.PERMANENT -> {
                    // Never counts toward Active Plan — see doc comment above.
                }

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
                    // NOTE: sessionStart is only known while this app is the
                    // one currently in the foreground (tracked by the
                    // accessibility service). Active Plan's repository-wide
                    // check below always passes 0 here, so a SESSION_LIMIT
                    // rule can't trigger the auto-lock while the app isn't
                    // the one you're actively using. That's an accepted gap,
                    // not a bug — the other four rule types cover the vast
                    // majority of real cases.
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
