package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppPrefs
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.KeepAliveForegroundService

/**
 * Brings the keep-alive engine back after a reboot, so an armed phone needs no taps at all.
 *
 * HiOS is the reason for the defensive shape: Transsion's power manager can drop this broadcast
 * outright, and newer platform releases restrict starting a foreground service from it. A failure
 * here is logged, never fatal - the user can still arm from the tile or the rail.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "BootCompletedReceiver: $action")

        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        if (!AppPrefs.isArmed(context)) {
            Log.i(TAG, "Not armed before reboot, staying idle")
            return
        }

        try {
            KeepAliveForegroundService.start(context)
            LogEventBus.log(
                source = EventSource.SYSTEM_DIAGNOSTIC,
                action = "Restarted After Boot",
                details = "Keep-alive service restarted from $action"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not restart the keep-alive service after boot: ${e.message}")
            LogEventBus.log(
                source = EventSource.SYSTEM_DIAGNOSTIC,
                action = "Boot Restart Blocked",
                details = "${e.javaClass.simpleName}: ${e.message}",
                isSuccess = false
            )
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"

        /** Transsion and several other OEMs send this instead of the standard boot broadcast. */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
