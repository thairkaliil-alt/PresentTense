package com.allinone.blocker.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import org.json.JSONArray
import org.json.JSONObject

/** Friction / protection level for a block (README section 5.4). */
enum class ProtectionLevel { SOFT, NORMAL, STRICT, HARDCORE, LOCKED }

/**
 * High-level intent preset chosen by the user. Each preset maps to a
 * sensible default [ProtectionLevel] and a default [BlockRule] list.
 * CUSTOM means the user has manually configured their own rules.
 */
enum class BlockPreset { MINDFUL, HARD_LIMITS, FULLY_BLOCKED, CUSTOM }

/** The available per-app block modes (README section 1.1). */
enum class BlockRuleType {
    TIME_INTERVAL,
    DAILY_LIMIT,
    SESSION_LIMIT,
    OPEN_COUNT,
    COOLDOWN,
    PERMANENT
}

/**
 * A single rule. Only the fields relevant to [type] are used; the rest keep
 * sensible defaults so a rule round-trips cleanly through JSON.
 *
 * @Immutable tells Compose that all public properties are stable and will
 * never change after construction — this allows Compose to skip recomposing
 * any composable whose only input is this object, as long as the reference
 * hasn't changed. Without this, Compose treats the class as unstable and
 * recomposes unconditionally on every scroll frame.
 */
@Immutable
data class BlockRule(
    val type: BlockRuleType,
    val startMinutes: Int = 9 * 60,
    val endMinutes: Int = 18 * 60,
    val limitMinutes: Int = 30,
    val count: Int = 3,
    val cooldownMinutes: Int = 45,
    // Only used by SESSION_LIMIT. This is the length of the "session" that
    // limitMinutes is measured against — e.g. limitMinutes=15 with
    // sessionWindowMinutes=60 means "15 minutes of use, out of every 60-minute
    // stretch". Usage accumulates across closes/reopens within that stretch
    // (see BlockerRepository.addSessionStint/sessionWindowUsedMs) — only once
    // the full window has passed does the count start over.
    // BUGFIX ("close and reopen the app resets the session block"): before
    // this field existed there was no window at all — a "session" was just
    // "since the app was last brought to the foreground", so leaving and
    // coming straight back always looked like a brand-new session. Always
    // whole hours (adjustable in 1h steps in the UI), 60 minimum.
    val sessionWindowMinutes: Int = 60
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("startMinutes", startMinutes)
        put("endMinutes", endMinutes)
        put("limitMinutes", limitMinutes)
        put("count", count)
        put("cooldownMinutes", cooldownMinutes)
        put("sessionWindowMinutes", sessionWindowMinutes)
    }

    companion object {
        fun fromJson(o: JSONObject): BlockRule = BlockRule(
            type = runCatching { BlockRuleType.valueOf(o.getString("type")) }
                .getOrDefault(BlockRuleType.PERMANENT),
            startMinutes = o.optInt("startMinutes", 9 * 60),
            endMinutes = o.optInt("endMinutes", 18 * 60),
            limitMinutes = o.optInt("limitMinutes", 30),
            count = o.optInt("count", 3),
            cooldownMinutes = o.optInt("cooldownMinutes", 45),
            // Missing on every rule saved before this feature existed —
            // optInt defaults those to 60 (1 hour), which matches the
            // feature's own default, so old SESSION_LIMIT rules behave
            // exactly the way a freshly-created one would.
            sessionWindowMinutes = o.optInt("sessionWindowMinutes", 60)
        )
    }
}

/**
 * An app the user has chosen to block, with its stacked rules.
 *
 * @Stable tells Compose that:
 *  1. equals() is stable and consistent (data class guarantees this).
 *  2. When a property changes, Compose will be notified (guaranteed since
 *     BlockerRepository always replaces the whole object via upsertApp()).
 * This is the key fix for list scroll jank — without @Stable, Compose
 * recomposes every visible BlockedAppRow on every scroll frame because it
 * can't prove the row's input hasn't changed. With it, Compose compares by
 * equals() and skips rows whose data is identical.
 *
 * Note: fields are kept as var for JSON deserialization compatibility, but
 * in practice they are only mutated during construction (fromJson) and then
 * replaced wholesale via upsertApp(). @Stable is still correct here because
 * the Repository always emits a new list when anything changes.
 */
@Stable
data class BlockedApp(
    val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val isReels: Boolean = false,
    val protection: ProtectionLevel = ProtectionLevel.NORMAL,
    val rules: List<BlockRule> = emptyList(),
    val preset: BlockPreset = BlockPreset.FULLY_BLOCKED
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("appName", appName)
        put("enabled", enabled)
        put("isReels", isReels)
        put("protection", protection.name)
        put("rules", JSONArray().apply { rules.forEach { put(it.toJson()) } })
        put("preset", preset.name)
    }

    companion object {
        fun fromJson(o: JSONObject): BlockedApp {
            val rules = mutableListOf<BlockRule>()
            val arr = o.optJSONArray("rules")
            if (arr != null) {
                for (i in 0 until arr.length()) rules.add(BlockRule.fromJson(arr.getJSONObject(i)))
            }
            return BlockedApp(
                packageName = o.getString("packageName"),
                appName = o.optString("appName", o.getString("packageName")),
                enabled = o.optBoolean("enabled", true),
                isReels = o.optBoolean("isReels", false),
                protection = runCatching { ProtectionLevel.valueOf(o.getString("protection")) }
                    .getOrDefault(ProtectionLevel.NORMAL),
                rules = rules,
                preset = runCatching { BlockPreset.valueOf(o.getString("preset")) }
                    .getOrDefault(BlockPreset.FULLY_BLOCKED)
            )
        }
    }
}

/**
 * A website the user has chosen to block, identified by its domain
 * (e.g. "reddit.com"). Deliberately much simpler than [BlockedApp] — no
 * stacked rules for v1, just "blocked or not". Rules can be added later by
 * following the same pattern as BlockRule if needed.
 *
 * [domain] is always stored lowercase and without "www." so that
 * "www.Reddit.com" and "reddit.com" are treated as the same entry. See
 * UrlExtractor.normalizeDomain() — every domain that goes in or out of this
 * class should already be normalized by that function.
 */
@Stable
data class BlockedWebsite(
    val domain: String,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("domain", domain)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject): BlockedWebsite = BlockedWebsite(
            domain = o.getString("domain"),
            enabled = o.optBoolean("enabled", true)
        )
    }
}

/** Result of evaluating whether an app should be blocked right now. */
@Immutable
data class BlockDecision(val blocked: Boolean, val reason: String = "", val remainingSeconds: Long = -1)

/**
 * A recurring daily lockdown window (README 3.2), e.g. "every night 11pm-7am".
 * [daysOfWeek] uses java.util.Calendar values: Calendar.SUNDAY(1) .. Calendar.SATURDAY(7),
 * representing the day the window *starts* on.
 */
@Stable
data class LockdownSchedule(
    val id: String,
    val label: String = "",
    val startMinutes: Int = 23 * 60,
    val endMinutes: Int = 7 * 60,
    val daysOfWeek: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true,
    // Off by default, and per-schedule — NOT the same thing as the global
    // Strict Mode switch. Strict Mode is deliberately global for blocked
    // apps/websites (see AppRulesScreen's StrictModeLinkCard comment), but
    // a schedule opting into it here is a separate, individual choice. Only
    // schedules with this turned on route their "disable" and "delete"
    // actions through StrictModeGate.guard (see LockdownSchedulesScreen) —
    // every other schedule disables/deletes freely regardless of whether
    // Strict Mode is on globally.
    val strictModeProtected: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("startMinutes", startMinutes)
        put("endMinutes", endMinutes)
        put("daysOfWeek", JSONArray(daysOfWeek.toList()))
        put("enabled", enabled)
        put("strictModeProtected", strictModeProtected)
    }

    companion object {
        fun fromJson(o: JSONObject): LockdownSchedule {
            val days = mutableSetOf<Int>()
            val arr = o.optJSONArray("daysOfWeek")
            if (arr != null) {
                for (i in 0 until arr.length()) days.add(arr.getInt(i))
            } else {
                days.addAll(1..7)
            }
            return LockdownSchedule(
                id = o.getString("id"),
                label = o.optString("label", ""),
                startMinutes = o.optInt("startMinutes", 23 * 60),
                endMinutes = o.optInt("endMinutes", 7 * 60),
                daysOfWeek = days,
                enabled = o.optBoolean("enabled", true),
                // Missing on every schedule saved before this feature existed —
                // optBoolean defaults those to false, i.e. unprotected, which is
                // exactly right: old schedules should NOT suddenly start
                // demanding a Strict Mode challenge just because this field
                // now exists.
                strictModeProtected = o.optBoolean("strictModeProtected", false)
            )
        }
    }
}

/**
 * Result of evaluating whether full-phone lockdown is active right now.
 */
@Immutable
data class LockdownDecision(
    val active: Boolean,
    val reason: String = "",
    val endsAtMillis: Long = -1,
    val onBreak: Boolean = false,
    val breakEndsAtMillis: Long = -1
)
