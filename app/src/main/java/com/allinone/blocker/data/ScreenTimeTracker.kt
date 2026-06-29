package com.allinone.blocker.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Precise, fully local screen-time tracking.
 *
 * Source of truth is UsageStatsManager.queryEvents() - the raw log of when each
 * app's window actually came to the front and left it. We do NOT use
 * queryUsageStats(); Android caches/buckets that and it can be stale by hours.
 * Background audio/media never raises a "foreground" event, so it's already
 * excluded automatically - no extra logic needed for that.
 *
 * reconcile() is the only thing that ever writes data. It gets called from the
 * accessibility service (instant), the foreground service (every ~60s), and a
 * WorkManager job (every 15 min, as a safety net). All three just feed the same
 * pipeline, so there's no separate "live counter" to keep in sync with the saved
 * data - reconcile() IS the live counter.
 *
 * PERFORMANCE NOTES:
 *   1. [reconcile] is throttled - calls that arrive less than
 *      [MIN_RECONCILE_GAP_MS] after the last *actual* DB pass are skipped
 *      immediately, instead of opening the DB every time.
 *   2. Today's per-app usage is kept in an in-memory cache that's only
 *      recomputed when reconcile() actually runs, so BlockEngine's daily-limit
 *      check now just reads a number from memory instead of hitting SQLite.
 *   3. When there's actually a backlog to catch up on (e.g. the background
 *      service was asleep for a while), all of that gets written inside ONE
 *      database transaction instead of one small transaction per app-switch
 *      event. IMPORTANT: that transaction is only opened when there's real
 *      work to do (checkpoint < now) - the very common "nothing changed
 *      since the last check" case stays exactly as cheap as a plain read,
 *      with no transaction overhead at all.
 */
object ScreenTimeTracker {

    private const val CHECKPOINT_KEY = "last_reconciled_millis"

    // Don't actually touch the database more often than this, no matter how
    // many callers ask for a reconcile. Real app switches are still caught
    // because the *next* event after this window passes will trigger a pass
    // that catches up on everything that happened in between.
    private const val MIN_RECONCILE_GAP_MS = 4_000L

    @Volatile private var openPackage: String? = null
    @Volatile private var openStartMillis: Long = 0L
    @Volatile private var lastReconcileAtMillis: Long = 0L
    private val reconciling = AtomicBoolean(false)

    // Live tracking for the domain currently open inside a browser. Separate
    // from openPackage/openStartMillis above (which track the *app*, i.e.
    // "Chrome is foreground") — this tracks *which site* inside that app, and
    // is driven directly by the accessibility service each time it reads a
    // new URL, rather than by reconcile()'s UsageStatsManager polling (the
    // OS has no concept of "which website", only "which app").
    @Volatile private var openDomain: String? = null
    @Volatile private var openDomainStartMillis: Long = 0L
    private val domainTodayCache = ConcurrentHashMap<String, Long>()
    @Volatile private var domainCacheDayKey: Int = -1


    // In-memory cache of "today's total ms per package", rebuilt every time
    // reconcile() actually runs. This is what makes repeated lookups (e.g. the
    // daily-limit rule check) cheap - no DB hit per lookup.
    private val todayCache = ConcurrentHashMap<String, Long>()
    @Volatile private var todayCacheDayKey: Int = -1

    /**
     * Reads new usage events since the last checkpoint and saves them.
     * Safe (and cheap) to call often - throttled internally, see class doc.
     *
     * @param force bypass the throttle window. Use this for the rare spots
     *   that genuinely need fresh numbers right now (e.g. opening the Stats
     *   screen), not for routine event-driven calls.
     */
    fun reconcile(context: Context, force: Boolean = false) {
        if (!hasUsageAccess(context)) return

        val now = System.currentTimeMillis()
        if (!force && now - lastReconcileAtMillis < MIN_RECONCILE_GAP_MS) return
        if (!reconciling.compareAndSet(false, true)) return // another call is already running

        try {
            // Re-check under the lock: another thread may have just finished
            // a pass while we were waiting, making ours redundant.
            val now2 = System.currentTimeMillis()
            if (!force && now2 - lastReconcileAtMillis < MIN_RECONCILE_GAP_MS) return

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val db = ScreenTimeDatabase.get(context).writableDatabase

            var checkpoint = readCheckpoint(db)
            if (checkpoint <= 0L) checkpoint = now2 - 60_000L // first run ever: just start tracking from "now"

            if (checkpoint < now2) {
                // Only open a transaction when there's actually new data to
                // write - see class doc, point 3.
                db.beginTransaction()
                try {
                    val events = usm.queryEvents(checkpoint, now2)
                    val event = UsageEvents.Event()

                    var pendingPkg: String? = null
                    var pendingStart = 0L

                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val ts = event.timeStamp

                        // MOVE_TO_FOREGROUND/BACKGROUND and ACTIVITY_RESUMED/PAUSED are the
                        // same underlying values under different names added later - checking
                        // both just keeps this working on every Android version we support.
                        val isResume = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                            event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                        val isPause = event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                            event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
                        if (!isResume && !isPause) continue

                        if (isResume) {
                            // A new app coming forward also closes out a previous app that
                            // never got a clean "paused" event (covers missed events).
                            if (pendingPkg != null && pendingPkg != event.packageName) {
                                writeSpan(db, pendingPkg!!, pendingStart, ts)
                            }
                            pendingPkg = event.packageName
                            pendingStart = ts
                        } else if (isPause && event.packageName == pendingPkg) {
                            writeSpan(db, pendingPkg!!, pendingStart, ts)
                            pendingPkg = null
                        }
                    }

                    // Whatever's left open has no "paused" event yet - it's the app on
                    // screen right now. Remember it for live reads, but don't save it as a
                    // finished session, and don't move the checkpoint past its start. Next
                    // call will see this same "resume" event again and close it out properly
                    // once it actually does end - this is what prevents double counting.
                    openPackage = pendingPkg
                    openStartMillis = pendingStart
                    writeCheckpoint(db, if (pendingPkg != null) pendingStart else now2)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            refreshTodayCache(db)
            lastReconcileAtMillis = now2
        } finally {
            reconciling.set(false)
        }
    }

    /**
     * Saves [pkg]'s usage between [startMillis] and [endMillis], splitting at
     * midnight if the span crosses a day boundary.
     *
     * NOTE: must always be called from inside an already-open transaction.
     * reconcile() owns one that covers all writeSpan calls in a single pass.
     * This function must NOT open its own transaction — SQLite on Android does
     * not support true nested transactions. An inner beginTransaction() inside
     * an outer one causes the inner endTransaction() to commit/rollback the
     * outer transaction early, before the checkpoint is written. That was the
     * bug: sessions could be saved but the checkpoint left un-advanced, so the
     * same events got replayed on the next reconcile and screen time was
     * double-counted.
     */
    private fun writeSpan(db: SQLiteDatabase, pkg: String, startMillis: Long, endMillis: Long) {
        if (endMillis <= startMillis) return
        var spanStart = startMillis
        while (spanStart < endMillis) {
            val dayEnd = startOfNextDay(spanStart)
            val spanEnd = minOf(endMillis, dayEnd)
            val dayKey = dayKeyFor(spanStart)
            val durationMillis = spanEnd - spanStart

            db.insert(
                "app_sessions", null,
                ContentValues().apply {
                    put("package_name", pkg)
                    put("day_key", dayKey)
                    put("start_millis", spanStart)
                    put("end_millis", spanEnd)
                }
            )
            upsertDailyTotal(db, dayKey, pkg, durationMillis)
            writeHourlySpan(db, pkg, dayKey, spanStart, spanEnd)

            spanStart = spanEnd
        }
    }

    /**
     * Breaks a usage span into per-hour buckets and upserts each into hourly_totals.
     * This is what powers the 24-bar hourly chart on the Stats screen.
     * The span is already guaranteed to be within a single calendar day by writeSpan().
     */
    private fun writeHourlySpan(db: SQLiteDatabase, pkg: String, dayKey: Int, spanStart: Long, spanEnd: Long) {
        var cursor = spanStart
        while (cursor < spanEnd) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = cursor
            val hour = cal.get(Calendar.HOUR_OF_DAY)

            // Find end of this hour slot
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val hourEnd = minOf(spanEnd, cal.timeInMillis)

            val sliceMillis = hourEnd - cursor
            if (sliceMillis > 0) {
                upsertHourlyTotal(db, dayKey, hour, pkg, sliceMillis)
            }
            cursor = hourEnd
        }
    }

    private fun upsertHourlyTotal(db: SQLiteDatabase, dayKey: Int, hour: Int, pkg: String, deltaMillis: Long) {
        val stmt = db.compileStatement(
            "UPDATE hourly_totals SET total_millis = total_millis + ? WHERE day_key = ? AND hour_of_day = ? AND package_name = ?"
        )
        val updatedRows: Int
        try {
            stmt.bindLong(1, deltaMillis)
            stmt.bindLong(2, dayKey.toLong())
            stmt.bindLong(3, hour.toLong())
            stmt.bindString(4, pkg)
            updatedRows = stmt.executeUpdateDelete()
        } finally {
            stmt.close()
        }
        if (updatedRows == 0) {
            db.insert(
                "hourly_totals", null,
                ContentValues().apply {
                    put("day_key", dayKey)
                    put("hour_of_day", hour)
                    put("package_name", pkg)
                    put("total_millis", deltaMillis)
                }
            )
        }
    }

    // "Update, and if nothing was there to update, insert" - written this way on
    // purpose instead of SQL's "ON CONFLICT ... DO UPDATE" shortcut, because that
    // shortcut isn't supported by the older SQLite version that ships on Android
    // 8.0/8.1/9, and this app supports those (minSdk 26).
    private fun upsertDailyTotal(db: SQLiteDatabase, dayKey: Int, pkg: String, deltaMillis: Long) {
        val stmt = db.compileStatement(
            "UPDATE daily_totals SET total_millis = total_millis + ? WHERE day_key = ? AND package_name = ?"
        )
        val updatedRows: Int
        try {
            stmt.bindLong(1, deltaMillis)
            stmt.bindLong(2, dayKey.toLong())
            stmt.bindString(3, pkg)
            updatedRows = stmt.executeUpdateDelete()
        } finally {
            stmt.close()
        }
        if (updatedRows == 0) {
            db.insert(
                "daily_totals", null,
                ContentValues().apply {
                    put("day_key", dayKey)
                    put("package_name", pkg)
                    put("total_millis", deltaMillis)
                }
            )
        }
    }

    private fun readCheckpoint(db: SQLiteDatabase): Long {
        db.query(
            "sync_state", arrayOf("value"), "key = ?", arrayOf(CHECKPOINT_KEY), null, null, null
        ).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    private fun writeCheckpoint(db: SQLiteDatabase, value: Long) {
        val stmt = db.compileStatement("UPDATE sync_state SET value = ? WHERE key = ?")
        val updatedRows: Int
        try {
            stmt.bindLong(1, value)
            stmt.bindString(2, CHECKPOINT_KEY)
            updatedRows = stmt.executeUpdateDelete()
        } finally {
            stmt.close()
        }
        if (updatedRows == 0) {
            db.insert("sync_state", null, ContentValues().apply {
                put("key", CHECKPOINT_KEY)
                put("value", value)
            })
        }
    }

    /**
     * Rebuilds [todayCache] from the daily_totals table in one query. Called at
     * the end of every reconcile() pass so cached reads always reflect the
     * latest saved data (plus whatever's still open gets added on top at read
     * time in [todayMillisFor]/[todayUsageMinutes]).
     */
    private fun refreshTodayCache(db: SQLiteDatabase) {
        val key = dayKeyFor(System.currentTimeMillis())
        if (key != todayCacheDayKey) {
            todayCache.clear()
            todayCacheDayKey = key
        }
        db.query(
            "daily_totals", arrayOf("package_name", "total_millis"),
            "day_key = ?", arrayOf(key.toString()), null, null, null
        ).use { c ->
            val fresh = HashMap<String, Long>()
            while (c.moveToNext()) fresh[c.getString(0)] = c.getLong(1)
            todayCache.clear()
            todayCache.putAll(fresh)
        }
    }

    // ---------- Reads (call these from anywhere, including UI later) ----------

    fun todayUsageMinutes(context: Context, pkg: String): Int =
        (todayMillisFor(context, pkg) / 60_000L).toInt()

    fun todayMillisFor(context: Context, pkg: String): Long {
        ensureCacheFreshEnough(context)
        var total = todayCache[pkg] ?: 0L
        if (openPackage == pkg) total += (System.currentTimeMillis() - openStartMillis).coerceAtLeast(0)
        return total
    }

    fun todayTotalMinutes(context: Context): Int {
        ensureCacheFreshEnough(context)
        var total = todayCache.values.sum()
        openPackage?.let { total += (System.currentTimeMillis() - openStartMillis).coerceAtLeast(0) }
        return (total / 60_000L).toInt()
    }

    private fun ensureCacheFreshEnough(context: Context) {
        if (todayCacheDayKey == -1) reconcile(context)
    }

    fun weeklyTotals(context: Context, days: Int = 7): Map<Int, Map<String, Long>> {
        val db = ScreenTimeDatabase.get(context).readableDatabase
        val today = dayKeyFor(System.currentTimeMillis())
        val dayKeys = (0 until days).map { offsetDayKey(today, -it) }

        val result = linkedMapOf<Int, MutableMap<String, Long>>()
        dayKeys.forEach { result[it] = mutableMapOf() }

        val placeholders = dayKeys.joinToString(",") { "?" }
        val args = dayKeys.map { it.toString() }.toTypedArray()
        db.query(
            "daily_totals", arrayOf("day_key", "package_name", "total_millis"),
            "day_key IN ($placeholders)", args, null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                val dayKey = c.getInt(0)
                val pkg = c.getString(1)
                val millis = c.getLong(2)
                result[dayKey]?.put(pkg, millis)
            }
        }
        return result
    }

    /**
     * Returns a 24-element list of total screen time in millis for each hour of today.
     * Index 0 = midnight–1am, index 23 = 11pm–midnight.
     * Adds the currently-open app's live time into the correct hour bucket.
     */
    fun hourlyTotalsToday(context: Context): List<Long> {
        val db = ScreenTimeDatabase.get(context).readableDatabase
        val todayKey = dayKeyFor(System.currentTimeMillis())
        val buckets = LongArray(24) { 0L }

        db.query(
            "hourly_totals", arrayOf("hour_of_day", "total_millis"),
            "day_key = ?", arrayOf(todayKey.toString()), null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                val hour = c.getInt(0)
                val millis = c.getLong(1)
                if (hour in 0..23) buckets[hour] += millis
            }
        }

        // Add live time for the currently open app into the right hour bucket
        val currentPkg = openPackage
        if (currentPkg != null && openStartMillis > 0) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = openStartMillis
            val startHour = cal.get(Calendar.HOUR_OF_DAY)
            val now = System.currentTimeMillis()
            val liveMillis = (now - openStartMillis).coerceAtLeast(0L)
            if (startHour in 0..23) buckets[startHour] += liveMillis
        }

        return buckets.toList()
    }

    /**
     * Returns blocked-attempt counts for today: a map of packageName -> attempt count.
     * Used by the Stats screen to show which app you tried to open the most.
     */
    fun blockedAttemptsToday(context: Context): Map<String, Int> {
        val db = ScreenTimeDatabase.get(context).readableDatabase
        val todayKey = dayKeyFor(System.currentTimeMillis())
        val result = HashMap<String, Int>()

        db.query(
            "blocked_attempts", arrayOf("package_name", "attempts"),
            "day_key = ?", arrayOf(todayKey.toString()), null, null, null
        ).use { c ->
            while (c.moveToNext()) result[c.getString(0)] = c.getInt(1)
        }
        return result
    }

    /**
     * Records one blocked attempt for [pkg] today. Called by the accessibility
     * service whenever BlockEngine returns blocked = true.
     */
    fun recordBlockedAttempt(context: Context, pkg: String) {
        val db = ScreenTimeDatabase.get(context).writableDatabase
        val todayKey = dayKeyFor(System.currentTimeMillis())

        val stmt = db.compileStatement(
            "UPDATE blocked_attempts SET attempts = attempts + 1 WHERE day_key = ? AND package_name = ?"
        )
        val updatedRows: Int
        try {
            stmt.bindLong(1, todayKey.toLong())
            stmt.bindString(2, pkg)
            updatedRows = stmt.executeUpdateDelete()
        } finally {
            stmt.close()
        }
        if (updatedRows == 0) {
            db.insert("blocked_attempts", null, ContentValues().apply {
                put("day_key", todayKey)
                put("package_name", pkg)
                put("attempts", 1)
            })
        }
    }

    /**
     * Returns the streak (consecutive blocked days) for each package.
     * A "streak day" = a day where the app was never successfully used past the block.
     * The streak resets the day the app is actually launched successfully.
     * We calculate this fresh from the DB each time it's asked for — it's only
     * called when opening the Stats screen, so performance isn't a concern.
     */
    fun streaksForPackages(context: Context, packages: List<String>): Map<String, Int> {
        if (packages.isEmpty()) return emptyMap()
        val db = ScreenTimeDatabase.get(context).readableDatabase
        val result = HashMap<String, Int>()

        // For each package: walk back day by day from yesterday. A day "counts"
        // toward the streak if the app had blocked_attempts > 0 but daily_totals
        // shows 0 (or no row) — meaning we tried to open it but never got through.
        // The streak stops the moment a day has actual usage time > 0.
        val today = dayKeyFor(System.currentTimeMillis())

        for (pkg in packages) {
            var streak = 0
            var dayOffset = 1 // start from yesterday (today isn't over yet)
            while (dayOffset <= 365) { // cap at a year to avoid infinite loops
                val dayKey = offsetDayKey(today, -dayOffset)

                // Check if this day had any actual usage
                val usageMillis = db.query(
                    "daily_totals", arrayOf("total_millis"),
                    "day_key = ? AND package_name = ?",
                    arrayOf(dayKey.toString(), pkg), null, null, null
                ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

                if (usageMillis > 0L) {
                    // App was actually used this day — streak broken
                    break
                }

                // Check if this day had any blocked attempts (meaning the app was
                // blocked and the user tried to open it — counts toward streak)
                val hadAttempts = db.query(
                    "blocked_attempts", arrayOf("attempts"),
                    "day_key = ? AND package_name = ?",
                    arrayOf(dayKey.toString(), pkg), null, null, null
                ).use { c -> if (c.moveToFirst()) c.getInt(0) > 0 else false }

                if (hadAttempts) {
                    streak++
                    dayOffset++
                } else {
                    // No attempts and no usage — day is irrelevant, keep walking back
                    // but only if streak is already > 0 (gap before we even started tracking)
                    if (streak > 0) break
                    dayOffset++
                    // If no data yet (streak == 0), keep looking back up to 30 days
                    if (dayOffset > 30) break
                }
            }
            if (streak > 0) result[pkg] = streak
        }
        return result
    }

    // ── Per-domain (website) tracking ────────────────────────────────────────
    //
    // Unlike app tracking, there's no OS-level API that tells us "the user
    // switched from reddit.com to gmail.com" — UsageStatsManager only knows
    // about apps. Instead, the accessibility service calls onDomainChanged()
    // every time it reads a new URL out of the browser's address bar, and we
    // do the same "close out the previous span, open a new one" bookkeeping
    // ourselves instead of replaying OS events like reconcile() does.

    /**
     * Call this from the accessibility service whenever the URL bar shows a
     * new domain (or the browser closes / leaves foreground — pass null).
     * Closes out time accumulated on the previous domain and starts the
     * clock for the new one. Safe to call repeatedly with the same domain;
     * it's a no-op in that case.
     */
    fun onDomainChanged(context: Context, newDomain: String?) {
        val now = System.currentTimeMillis()
        val previous = openDomain
        val previousStart = openDomainStartMillis

        if (previous == newDomain) return // same site, nothing to close out

        if (previous != null && previousStart > 0L) {
            writeDomainSpan(context, previous, previousStart, now)
        }

        openDomain = newDomain
        openDomainStartMillis = if (newDomain != null) now else 0L
    }

    /** Saves [domain]'s usage between [startMillis] and [endMillis], splitting at midnight if needed. */
    private fun writeDomainSpan(context: Context, domain: String, startMillis: Long, endMillis: Long) {
        if (endMillis <= startMillis) return
        val db = ScreenTimeDatabase.get(context).writableDatabase
        var spanStart = startMillis
        while (spanStart < endMillis) {
            val dayEnd = startOfNextDay(spanStart)
            val spanEnd = minOf(endMillis, dayEnd)
            val dayKey = dayKeyFor(spanStart)
            val durationMillis = spanEnd - spanStart

            db.beginTransaction()
            try {
                upsertDomainDailyTotal(db, dayKey, domain, durationMillis)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            spanStart = spanEnd
        }
        refreshDomainTodayCache(db)
    }

    private fun upsertDomainDailyTotal(db: SQLiteDatabase, dayKey: Int, domain: String, deltaMillis: Long) {
        val stmt = db.compileStatement(
            "UPDATE domain_daily_totals SET total_millis = total_millis + ? WHERE day_key = ? AND domain = ?"
        )
        val updatedRows: Int
        try {
            stmt.bindLong(1, deltaMillis)
            stmt.bindLong(2, dayKey.toLong())
            stmt.bindString(3, domain)
            updatedRows = stmt.executeUpdateDelete()
        } finally {
            stmt.close()
        }
        if (updatedRows == 0) {
            db.insert(
                "domain_daily_totals", null,
                ContentValues().apply {
                    put("day_key", dayKey)
                    put("domain", domain)
                    put("total_millis", deltaMillis)
                }
            )
        }
    }

    private fun refreshDomainTodayCache(db: SQLiteDatabase) {
        val key = dayKeyFor(System.currentTimeMillis())
        if (key != domainCacheDayKey) {
            domainTodayCache.clear()
            domainCacheDayKey = key
        }
        db.query(
            "domain_daily_totals", arrayOf("domain", "total_millis"),
            "day_key = ?", arrayOf(key.toString()), null, null, null
        ).use { c ->
            val fresh = HashMap<String, Long>()
            while (c.moveToNext()) fresh[c.getString(0)] = c.getLong(1)
            domainTodayCache.clear()
            domainTodayCache.putAll(fresh)
        }
    }

    /**
     * Records one blocked attempt for [domain] today — call this when the
     * accessibility service detects the user landed on a blocked site.
     */
    fun recordDomainBlockedAttempt(context: Context, domain: String) {
        val db = ScreenTimeDatabase.get(context).writableDatabase
        val todayKey = dayKeyFor(System.currentTimeMillis())

        val stmt = db.compileStatement(
            "UPDATE domain_blocked_attempts SET attempts = attempts + 1 WHERE day_key = ? AND domain = ?"
        )
        val updatedRows: Int
        try {
            stmt.bindLong(1, todayKey.toLong())
            stmt.bindString(2, domain)
            updatedRows = stmt.executeUpdateDelete()
        } finally {
            stmt.close()
        }
        if (updatedRows == 0) {
            db.insert("domain_blocked_attempts", null, ContentValues().apply {
                put("day_key", todayKey)
                put("domain", domain)
                put("attempts", 1)
            })
        }
    }

    /** Today's total time on [domain], in millis, including whatever's still live right now. */
    fun todayMillisForDomain(context: Context, domain: String): Long {
        ensureDomainCacheFreshEnough(context)
        var total = domainTodayCache[domain] ?: 0L
        if (openDomain == domain) total += (System.currentTimeMillis() - openDomainStartMillis).coerceAtLeast(0)
        return total
    }

    private fun ensureDomainCacheFreshEnough(context: Context) {
        if (domainCacheDayKey == -1) {
            val db = ScreenTimeDatabase.get(context).readableDatabase
            refreshDomainTodayCache(db)
        }
    }

    /**
     * Returns today's screen time per domain, for every domain that has any
     * recorded time OR any blocked attempts today — the per-domain
     * equivalent of how appStats is built on the Stats screen.
     */
    fun domainStatsToday(context: Context): Map<String, Long> {
        ensureDomainCacheFreshEnough(context)
        val result = HashMap<String, Long>(domainTodayCache)
        val liveDomain = openDomain
        if (liveDomain != null) {
            val live = (System.currentTimeMillis() - openDomainStartMillis).coerceAtLeast(0)
            result[liveDomain] = (result[liveDomain] ?: 0L) + live
        }
        return result
    }

    /** Returns blocked-attempt counts for today: domain -> attempt count. */
    fun domainBlockedAttemptsToday(context: Context): Map<String, Int> {
        val db = ScreenTimeDatabase.get(context).readableDatabase
        val todayKey = dayKeyFor(System.currentTimeMillis())
        val result = HashMap<String, Int>()

        db.query(
            "domain_blocked_attempts", arrayOf("domain", "attempts"),
            "day_key = ?", arrayOf(todayKey.toString()), null, null, null
        ).use { c ->
            while (c.moveToNext()) result[c.getString(0)] = c.getInt(1)
        }
        return result
    }

    // ---------- Small helpers ----------

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun dayKeyFor(millis: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun startOfNextDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun offsetDayKey(dayKey: Int, offsetDays: Int): Int {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, dayKey / 1000)
        c.set(Calendar.DAY_OF_YEAR, dayKey % 1000)
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }
}
