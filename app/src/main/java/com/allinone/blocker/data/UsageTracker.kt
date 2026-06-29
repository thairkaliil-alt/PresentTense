package com.allinone.blocker.data

import android.content.Context

/**
 * Kept as a thin wrapper so BlockEngine.kt (the daily-limit rule) doesn't need to
 * change at all. The real tracking now lives in ScreenTimeTracker, backed by
 * queryEvents() instead of the old queryUsageStats() call - same usage, more
 * accurate numbers.
 */
object UsageTracker {
    fun todayUsageMinutes(context: Context, pkg: String): Int =
        ScreenTimeTracker.todayUsageMinutes(context, pkg)
}
