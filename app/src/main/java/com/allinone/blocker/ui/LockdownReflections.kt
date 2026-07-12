package com.allinone.blocker.ui

/**
 * The quiet, rotating reflective line shown under the countdown on the
 * active lockdown screen (see [LockdownLauncherActivity]). This is the
 * screen's one line of "why", not just "how long" — see the UX-audit
 * backlog item this implements.
 *
 * DESIGN INTENT — read this before adding lines:
 *   - Quiet, dignified, present-tense. Never a productivity-poster shout,
 *     never gamified (no "you got this!", no exclamation points, no streak
 *     talk). This app's whole voice is calm and matter-of-fact — see the
 *     header comments on GraceCancelBanner and LockdownCompletionRepository
 *     for the same tone applied elsewhere.
 *   - Short. One breath, not a sentence you have to parse.
 *   - [genericLines] is the fallback for manual locks and any schedule
 *     without a label ("Manual lockdown", "Scheduled lockdown" from
 *     LockdownEngine never match a keyword below, which is intentional —
 *     an unlabeled lock gets the calm generic line, not a guess).
 *
 * MATCHING: [LockdownDecision.reason] is already the schedule's label when
 * one exists (see LockdownEngine.evaluate), so no new field or plumbing is
 * needed — this just keyword-matches that existing string.
 */
internal object LockdownReflections {

    /** How often the visible line changes during a single session. */
    private const val ROTATE_INTERVAL_MS = 4 * 60_000L

    private val sleepKeywords = listOf("sleep", "night", "bed", "wind down", "wind-down")
    private val familyKeywords = listOf("family", "kids", "kid", "children", "home", "dinner")
    private val focusKeywords = listOf("study", "work", "focus", "deep work", "class")

    private val sleepLines = listOf(
        "Tomorrow starts with tonight.",
        "Rest is not time lost.",
        "The night doesn't need company.",
        "Let today end here.",
        "Nothing here is worth losing sleep over."
    )

    private val familyLines = listOf(
        "They're on the other side of this.",
        "This is time you don't get back.",
        "Presence is the whole point.",
        "They notice when you're really there.",
        "This is who you're choosing to be today."
    )

    private val focusLines = listOf(
        "This is what you showed up to do.",
        "One thing, done well.",
        "The work is still worth it.",
        "Attention is the only thing you can't get back.",
        "This is who you're choosing to be today."
    )

    private val genericLines = listOf(
        "This moment is the only one you actually have.",
        "This is who you're choosing to be today.",
        "Not gone. Just not now.",
        "Nothing here needs you right now.",
        "Being unreachable is allowed."
    )

    private fun linesFor(reason: String): List<String> {
        val r = reason.lowercase()
        return when {
            sleepKeywords.any { r.contains(it) } -> sleepLines
            familyKeywords.any { r.contains(it) } -> familyLines
            focusKeywords.any { r.contains(it) } -> focusLines
            else -> genericLines
        }
    }

    /**
     * The line to show right now: picked from the pool matching [reason],
     * stable for [ROTATE_INTERVAL_MS] at a time, then quietly swapped for
     * another line from the same pool. Deterministic given the same inputs
     * (no mutable state, nothing stored) — every recomposition with the
     * same [sessionStartedAtMillis]/[nowMillis] window gets the same line,
     * so it doesn't flicker between recompositions within that window.
     *
     * [sessionStartedAtMillis] anchors the rotation to when THIS session
     * began (falls back to [nowMillis] if unavailable, e.g. right at
     * startup before the tracker has written its marker) so a session that
     * starts mid-hour still gets a clean rotation cadence from its own
     * beginning rather than from the wall clock.
     */
    fun currentLine(
        reason: String,
        sessionStartedAtMillis: Long?,
        nowMillis: Long
    ): String {
        val lines = linesFor(reason)
        val anchor = sessionStartedAtMillis ?: nowMillis
        val windowIndex = (nowMillis - anchor).coerceAtLeast(0L) / ROTATE_INTERVAL_MS
        val hashed = mix(anchor / 1000L + windowIndex * 104_729L)
        val index = (((hashed % lines.size) + lines.size) % lines.size).toInt()
        return lines[index]
    }

    /** Cheap 64-bit integer hash (splitmix64) — just needs to scatter, not to be cryptographic. */
    private fun mix(input: Long): Long {
        var z = input + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0xbf58476d1ce4e5b9L
        z = (z xor (z ushr 27)) * -0x94d049bb133111ebL
        return z xor (z ushr 31)
    }
}
