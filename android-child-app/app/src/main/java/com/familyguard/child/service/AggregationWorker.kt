package com.familyguard.child.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.familyguard.child.collect.UsageCollector
import com.familyguard.child.data.AppDatabase
import com.familyguard.child.util.TimeUtil
import java.util.concurrent.TimeUnit

/**
 * Backup periodic aggregation + retention pruning, independent of the service
 * process so usage data keeps updating even if the service is killed.
 * Keeps 30 days of history.
 */
class AggregationWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        UsageCollector(applicationContext).collectAndStore()
        val db = AppDatabase.get(applicationContext)
        val cutoffDay = TimeUtil.daysAgoStart(RETENTION_DAYS)
        val cutoffMs = cutoffDay
        db.usageDao().pruneBefore(cutoffDay)
        db.locationDao().pruneBefore(cutoffMs)
        db.webVisitDao().pruneBefore(cutoffMs)
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }

    companion object {
        private const val RETENTION_DAYS = 30
        private const val UNIQUE = "familyguard_aggregation"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AggregationWorker>(
                30, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
