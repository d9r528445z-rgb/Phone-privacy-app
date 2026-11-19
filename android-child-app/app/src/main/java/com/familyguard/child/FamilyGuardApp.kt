package com.familyguard.child

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class FamilyGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            // IMPORTANCE_LOW keeps it quiet, but it stays permanently visible — by design.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "familyguard_monitor"
        const val NOTIF_ID = 1001
    }
}
