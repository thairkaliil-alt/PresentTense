package com.allinone.blocker.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Safety net only. Normal tracking happens because BlockerForegroundService calls
 * ScreenTimeTracker.reconcile() about once a minute while it's alive. This worker
 * exists for the rare case a phone manufacturer's battery manager kills that
 * service anyway - WorkManager negotiates with Android's own job scheduler instead
 * of trying to keep a raw background thread alive, so it can still run under
 * Doze/App Standby where a plain Service would get frozen.
 */
class ScreenTimeSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        runCatching { ScreenTimeTracker.reconcile(applicationContext) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        private const val UNIQUE_NAME = "screen_time_sync"

        /** Safe to call repeatedly (app start, boot, etc.) - it won't double-schedule. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenTimeSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
