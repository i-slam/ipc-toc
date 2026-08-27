package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.KeepAliveForegroundService
import com.example.service.SameProcessOverlayService
import com.example.service.SeparateProcessOverlayService

const val ACTION_SHOW_POPUP = "com.example.ACTION_SHOW_POPUP"
const val ACTION_SET_KEEP_ALIVE = "com.example.ACTION_SET_KEEP_ALIVE"
const val EXTRA_VARIANT = "variant" // "same" or "separate"
const val EXTRA_ON = "on" // "1" or "0"

/**
 * Static BroadcastReceiver declared in AndroidManifest.xml.
 *
 * Used for testing ADB commands such as:
 * adb shell am broadcast -n com.example/.receiver.TriggerReceiver -a com.example.ACTION_SHOW_POPUP --es variant separate
 * adb shell am broadcast -n com.example/.receiver.TriggerReceiver -a com.example.ACTION_SHOW_POPUP --es variant separate -f 0x10000000
 */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val flags = intent.flags
        Log.i("TriggerReceiver", "onReceive fired: action=$action (flags=0x${Integer.toHexString(flags)})")

        LogEventBus.log(
            source = EventSource.STATIC_BROADCAST,
            action = "Static Receiver Fired",
            details = "Action: $action, Flags: 0x${Integer.toHexString(flags)}"
        )

        when (action) {
            ACTION_SHOW_POPUP -> {
                val variant = intent.getStringExtra(EXTRA_VARIANT) ?: "separate"
                Log.i("TriggerReceiver", "Handling ACTION_SHOW_POPUP (variant=$variant)")
                if (variant == "same") {
                    SameProcessOverlayService.show(context, "static_broadcast")
                } else {
                    SeparateProcessOverlayService.show(context, "static_broadcast")
                }
            }
            ACTION_SET_KEEP_ALIVE -> {
                val on = intent.getStringExtra(EXTRA_ON) == "1"
                if (on) {
                    KeepAliveForegroundService.start(context)
                } else {
                    KeepAliveForegroundService.stop(context)
                }
            }
        }
    }
}
