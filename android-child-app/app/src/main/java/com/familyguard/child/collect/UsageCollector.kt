package com.familyguard.child.collect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.familyguard.child.data.AppDatabase
import com.familyguard.child.data.model.AppUsageRecord
import com.familyguard.child.util.TimeUtil

/**
 * Computes real per-app foreground time by replaying UsageEvents and pairing
 * RESUMED/PAUSED (foreground/background) transitions, bucketed per day.
 * Intervals that cross local midnight are split so each day bucket is correct.
 * This is more accurate than UsageStats.getTotalTimeInForeground.
 */
class UsageCollector(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = context.packageManager

    /** Mutable per (day, package) accumulator. */
    private class Acc {
        var totalMs: Long = 0
        var launches: Int = 0
        var lastUsed: Long = 0
    }

    /** True if the parent granted "Usage access" in system settings. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Collect usage from [fromMs] to now and write day-bucketed aggregates.
     * Safe to call repeatedly — upsert replaces each affected day's rows.
     */
    suspend fun collectAndStore(fromMs: Long = TimeUtil.daysAgoStart(7)) {
        if (!hasUsageAccess()) return
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(fromMs, now)

        val acc = HashMap<String, Acc>()            // key = "dayEpoch|package"
        val resumedAt = HashMap<String, Long>()     // package -> resume timestamp

        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    resumedAt[pkg] = e.timeStamp
                    val day = TimeUtil.startOfDay(e.timeStamp)
                    acc.getOrPut("$day|$pkg") { Acc() }.apply {
                        launches += 1
                        lastUsed = maxOf(lastUsed, e.timeStamp)
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = resumedAt.remove(pkg) ?: continue
                    addInterval(acc, pkg, start, e.timeStamp)
                }
            }
        }
        // Close any app still in the foreground at "now".
        for ((pkg, start) in resumedAt) addInterval(acc, pkg, start, now)

        if (acc.isEmpty()) return

        val labelCache = HashMap<String, String>()
        val records = acc.entries.mapNotNull { (key, v) ->
            if (v.totalMs <= 0 && v.launches <= 0) return@mapNotNull null
            val sep = key.indexOf('|')
            val day = key.substring(0, sep).toLong()
            val pkg = key.substring(sep + 1)
            AppUsageRecord(
                dayEpoch = day,
                packageName = pkg,
                appLabel = labelCache.getOrPut(pkg) { resolveLabel(pkg) },
                totalForegroundMs = v.totalMs,
                lastUsedEpoch = v.lastUsed,
                launchCount = v.launches
            )
        }
        AppDatabase.get(context).usageDao().upsert(records)
    }

    /** Add a foreground interval, splitting across midnight so day buckets stay correct. */
    private fun addInterval(acc: HashMap<String, Acc>, pkg: String, startMs: Long, endMs: Long) {
        if (endMs <= startMs) return
        var cursor = startMs
        while (cursor < endMs) {
            val day = TimeUtil.startOfDay(cursor)
            val dayEnd = day + 86_400_000L
            val segmentEnd = minOf(endMs, dayEnd)
            acc.getOrPut("$day|$pkg") { Acc() }.apply {
                totalMs += (segmentEnd - cursor)
                lastUsed = maxOf(lastUsed, segmentEnd)
            }
            cursor = segmentEnd
        }
    }

    private fun resolveLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg
    }
}
