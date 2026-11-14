package com.famille.rappelpipi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if ((a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED ||
                a == "android.intent.action.QUICKBOOT_POWERON") &&
            Prefs.enabled(context) && Settings.canDrawOverlays(context)
        ) {
            context.startForegroundService(Intent(context, ReminderService::class.java))
        }
    }
}
