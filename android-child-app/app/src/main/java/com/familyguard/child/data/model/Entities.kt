package com.familyguard.child.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per app, per day — total foreground time in milliseconds.
 * We store aggregates (not every raw event) to keep the DB small.
 * `dayEpoch` is the start-of-day (local) in epoch millis, used as the bucket key.
 */
@Entity(
    tableName = "app_usage",
    indices = [Index(value = ["dayEpoch", "packageName"], unique = true), Index("dayEpoch")]
)
data class AppUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayEpoch: Long,
    val packageName: String,
    val appLabel: String,
    val totalForegroundMs: Long,
    val lastUsedEpoch: Long,
    val launchCount: Int
)

/** A single location fix. */
@Entity(tableName = "locations", indices = [Index("timestamp")])
data class LocationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val speedMps: Float,
    val provider: String
)

/**
 * A visited web address, captured from the browser's address bar via the
 * accessibility service. Only the URL/host + time are kept — never page
 * content, form data, or passwords.
 */
@Entity(tableName = "web_visits", indices = [Index("timestamp"), Index("host")])
data class WebVisitRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val url: String,
    val host: String,
    val browserPackage: String
)
