package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.KeepAliveForegroundService
import com.example.ui.QuickArm

/**
 * Invisible entry point behind the launcher shortcuts and the quick-settings tiles. It performs
 * the action and finishes without ever drawing, so a long-press on the app icon is one tap from
 * a popup or from an armed engine.
 *
 * Arming can need permission dialogs, which an invisible activity cannot host, so that case is
 * handed to MainActivity to run the chain there.
 */
class QuickActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        Log.i(TAG, "QuickActionActivity: $action")

        when (action) {
            ACTION_SHOW_POPUP -> {
                LogEventBus.log(
                    source = EventSource.DIRECT_SERVICE,
                    action = "Shortcut: Show Popup",
                    details = "Overlay requested from a launcher shortcut or quick-settings tile"
                )
                KeepAliveForegroundService.showOverlayDirect(this, "quick_action")
            }

            ACTION_ARM -> {
                val needsDialogs = QuickArm.missingRuntimePermissions(this).isNotEmpty() ||
                        QuickArm.needsOverlay(this) ||
                        QuickArm.needsBatteryExemption(this)

                if (needsDialogs) {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MainActivity.EXTRA_START_QUICK_ARM, true)
                        }
                    )
                } else {
                    QuickArm.finish(this)
                }
            }

            ACTION_LAST_CALL -> {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_OPEN_LAST_CALL, true)
                    }
                )
            }

            else -> {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            }
        }

        finish()
    }

    companion object {
        private const val TAG = "QuickActionActivity"

        const val ACTION_SHOW_POPUP = "com.example.action.QUICK_SHOW_POPUP"
        const val ACTION_ARM = "com.example.action.QUICK_ARM"
        const val ACTION_LAST_CALL = "com.example.action.QUICK_LAST_CALL"
    }
}
