package com.familyguard.child.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

object TimeUtil {
    /** Start-of-day (local midnight) epoch millis for a given instant. */
    fun startOfDay(epochMs: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = epochMs
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun todayStart(): Long = startOfDay(System.currentTimeMillis())

    fun daysAgoStart(days: Int): Long =
        startOfDay(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))

    fun formatDuration(ms: Long): String {
        val totalMin = TimeUnit.MILLISECONDS.toMinutes(ms)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
