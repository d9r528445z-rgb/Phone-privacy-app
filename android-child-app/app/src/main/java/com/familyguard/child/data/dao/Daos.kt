package com.familyguard.child.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.familyguard.child.data.model.AppUsageRecord
import com.familyguard.child.data.model.LocationRecord
import com.familyguard.child.data.model.WebVisitRecord

@Dao
interface UsageDao {
    /** Upsert a day's usage for an app (unique on dayEpoch+packageName). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(records: List<AppUsageRecord>)

    @Query("SELECT * FROM app_usage WHERE dayEpoch >= :sinceDay ORDER BY totalForegroundMs DESC")
    suspend fun since(sinceDay: Long): List<AppUsageRecord>

    @Query("SELECT * FROM app_usage WHERE dayEpoch = :dayEpoch ORDER BY totalForegroundMs DESC")
    suspend fun forDay(dayEpoch: Long): List<AppUsageRecord>

    @Query("DELETE FROM app_usage WHERE dayEpoch < :beforeDay")
    suspend fun pruneBefore(beforeDay: Long): Int
}

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(record: LocationRecord)

    @Query("SELECT * FROM locations WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int = 500): List<LocationRecord>

    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): LocationRecord?

    @Query("DELETE FROM locations WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long): Int
}

@Dao
interface WebVisitDao {
    @Insert
    suspend fun insert(record: WebVisitRecord)

    /** Returns the most recent URL for a browser so we can de-dupe rapid repeats. */
    @Query("SELECT * FROM web_visits WHERE browserPackage = :pkg ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastForBrowser(pkg: String): WebVisitRecord?

    @Query("SELECT * FROM web_visits WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int = 1000): List<WebVisitRecord>

    @Query("DELETE FROM web_visits WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long): Int
}
