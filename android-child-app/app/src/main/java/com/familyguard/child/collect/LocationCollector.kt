package com.familyguard.child.collect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.familyguard.child.data.AppDatabase
import com.familyguard.child.data.model.LocationRecord
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Periodic location sampling via the fused provider. Fixes are written to the
 * local DB; the parent reads them over the LAN. Interval is deliberately
 * modest (default 5 min) to conserve battery.
 */
class LocationCollector(
    private val context: Context,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 5 * 60 * 1000L
) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch(Dispatchers.IO) {
                AppDatabase.get(context).locationDao().insert(
                    LocationRecord(
                        timestamp = System.currentTimeMillis(),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyM = loc.accuracy,
                        speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                        provider = loc.provider ?: "fused"
                    )
                )
            }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(25f)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (se: SecurityException) {
            // Permission revoked between check and request — ignore.
        }
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }
}
