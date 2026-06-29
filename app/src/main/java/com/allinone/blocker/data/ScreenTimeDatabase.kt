package com.allinone.blocker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite store for screen-time history. Deliberately NOT Room - this is
 * one file, no annotation processor, nothing extra to configure in the build.
 * Everything stays on-device; nothing here ever touches the network.
 */
class ScreenTimeDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // Raw log of every app session, mainly useful for future history/debug views.
        db.execSQL(
            """
            CREATE TABLE app_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                day_key INTEGER NOT NULL,
                start_millis INTEGER NOT NULL,
                end_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_day_pkg ON app_sessions(day_key, package_name)")

        // Fast-to-read rollup: one row per app per day. This is what the UI will
        // query later - no need to re-add up raw sessions every time.
        db.execSQL(
            """
            CREATE TABLE daily_totals (
                day_key INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                total_millis INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (day_key, package_name)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_daily_totals_day ON daily_totals(day_key)")

        // Single row tracking how far into Android's event log we've already read.
        db.execSQL(
            """
            CREATE TABLE sync_state (
                key TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Counts how many times each app was blocked today (attempted but denied).
        // One row per app per day; incremented every time the overlay fires.
        db.execSQL(
            """
            CREATE TABLE blocked_attempts (
                day_key INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (day_key, package_name)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_blocked_attempts_day ON blocked_attempts(day_key)")

        // Screen time broken down by hour-of-day, for the hourly bar chart.
        // hour_of_day is 0–23. One row per app per hour per day.
        db.execSQL(
            """
            CREATE TABLE hourly_totals (
                day_key INTEGER NOT NULL,
                hour_of_day INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                total_millis INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (day_key, hour_of_day, package_name)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_hourly_totals_day ON hourly_totals(day_key)")

        // Consecutive-days streak per app: how many days in a row that app was
        // never successfully opened (i.e. it was blocked or just not used at all).
        // streak_days resets to 0 the day an app is successfully launched past the block.
        db.execSQL(
            """
            CREATE TABLE app_streaks (
                package_name TEXT PRIMARY KEY,
                streak_days INTEGER NOT NULL DEFAULT 0,
                last_updated_day_key INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        createDomainTables(db)
    }

    /**
     * Per-domain equivalent of daily_totals/blocked_attempts, kept in their
     * own tables rather than folded into the package_name-keyed ones above.
     * Reasoning: every existing table assumes "package_name" identifies an
     * installed app (used for icons, labels, streaks, etc.) — a domain like
     * "reddit.com" is a different kind of thing and mixing the two would
     * make every existing query need a "is this actually an app?" check.
     */
    private fun createDomainTables(db: SQLiteDatabase) {
        // One row per domain per day — today's (and history's) time-on-site.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS domain_daily_totals (
                day_key INTEGER NOT NULL,
                domain TEXT NOT NULL,
                total_millis INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (day_key, domain)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_domain_daily_totals_day ON domain_daily_totals(day_key)")

        // Mirrors blocked_attempts, but for domains the user tried to open
        // while they were on the blocklist.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS domain_blocked_attempts (
                day_key INTEGER NOT NULL,
                domain TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (day_key, domain)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_domain_blocked_attempts_day ON domain_blocked_attempts(day_key)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_totals_day ON daily_totals(day_key)")
        }
        if (oldVersion < 3) {
            // Blocked attempts counter — how many times each app was denied per day
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS blocked_attempts (
                    day_key INTEGER NOT NULL,
                    package_name TEXT NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (day_key, package_name)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_blocked_attempts_day ON blocked_attempts(day_key)")

            // Hourly breakdown for the 24-bar chart
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hourly_totals (
                    day_key INTEGER NOT NULL,
                    hour_of_day INTEGER NOT NULL,
                    package_name TEXT NOT NULL,
                    total_millis INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (day_key, hour_of_day, package_name)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hourly_totals_day ON hourly_totals(day_key)")

            // Per-app streak tracking
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_streaks (
                    package_name TEXT PRIMARY KEY,
                    streak_days INTEGER NOT NULL DEFAULT 0,
                    last_updated_day_key INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            // Website blocking: per-domain screen time + blocked-attempt tables.
            createDomainTables(db)
        }
    }

    companion object {
        private const val DB_NAME = "screen_time.db"
        private const val DB_VERSION = 4

        @Volatile private var instance: ScreenTimeDatabase? = null

        fun get(context: Context): ScreenTimeDatabase =
            instance ?: synchronized(this) {
                instance ?: ScreenTimeDatabase(context).also { instance = it }
            }
    }
}
