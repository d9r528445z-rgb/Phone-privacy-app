package com.familyguard.child.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyguard.child.data.Prefs

/** Restart monitoring after the phone reboots, if the parent completed setup. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            // Only auto-start once the guardian has accepted the consent/setup.
            if (Prefs.get(context).consentAccepted) {
                MonitorService.start(context)
            }
        }
    }
}
