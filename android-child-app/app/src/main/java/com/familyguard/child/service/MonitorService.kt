package com.familyguard.child.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.familyguard.child.FamilyGuardApp
import com.familyguard.child.R
import com.familyguard.child.collect.LocationCollector
import com.familyguard.child.collect.UsageCollector
import com.familyguard.child.data.Prefs
import com.familyguard.child.server.LocalApiServer
import com.familyguard.child.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Always-on monitoring service. Shows a permanent notification (required and
 * intentional), samples location, periodically aggregates app usage, and runs
 * the PIN-protected LAN server the parent connects to.
 */
class MonitorService : LifecycleService() {

    private var locationCollector: LocationCollector? = null
    private var apiServer: LocalApiServer? = null

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        startCollectors()
        startServer()
        startUsageLoop()
        AggregationWorker.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Re-assert foreground in case we were restarted.
        startAsForeground()
        return START_STICKY
    }

    private fun startAsForeground() {
        val tapIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, FamilyGuardApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dataSync = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            // Only attach the location type when "all the time" location is granted,
            // otherwise starting a location FGS (esp. from boot on Android 14+) throws.
            val canLocation = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    hasPerm(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            val type = if (canLocation) dataSync or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else dataSync
            try {
                startForeground(FamilyGuardApp.NOTIF_ID, notification, type)
            } catch (t: Throwable) {
                // Fall back to data-sync only if the OS refuses the location type.
                runCatching { startForeground(FamilyGuardApp.NOTIF_ID, notification, dataSync) }
            }
        } else {
            startForeground(FamilyGuardApp.NOTIF_ID, notification)
        }
    }

    private fun hasPerm(perm: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, perm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startCollectors() {
        locationCollector = LocationCollector(this, lifecycleScope).also { it.start() }
    }

    private fun startServer() {
        val prefs = Prefs.get(this)
        if (!prefs.serverEnabled) return
        apiServer = LocalApiServer(applicationContext, prefs.serverPort).also {
            try {
                it.startSafe()
            } catch (t: Throwable) {
                // Port busy or network down — the status screen will show server as offline.
            }
        }
    }

    /** Aggregate usage shortly after start, then every 15 minutes while alive. */
    private fun startUsageLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            val collector = UsageCollector(this@MonitorService)
            while (isActive) {
                runCatching { collector.collectAndStore() }
                delay(15 * 60 * 1000L)
            }
        }
    }

    override fun onDestroy() {
        locationCollector?.stop()
        apiServer?.stop()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
