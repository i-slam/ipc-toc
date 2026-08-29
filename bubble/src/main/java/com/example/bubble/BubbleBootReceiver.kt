package com.example.bubble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.data.AppPrefs

/**
 * Puts the button back after a reboot when it was on, so the phone needs no taps at all.
 * Transsion's power manager can drop this broadcast, and newer releases restrict starting a
 * foreground service from it, so a failure is logged rather than fatal.
 */
class BubbleBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        if (!AppPrefs.isFloatingRailEnabled(context)) {
            Log.i(TAG, "Bubble was off before the reboot, staying idle")
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission is gone, cannot restore the bubble")
            return
        }

        try {
            BubbleOverlayService.show(context)
            Log.i(TAG, "Bubble restored after $action")
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore the bubble after boot: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BubbleBootReceiver"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
