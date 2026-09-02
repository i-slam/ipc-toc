package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.MainActivity
import com.example.data.AppPrefs
import com.example.data.EventSource
import com.example.data.LogEventBus

/**
 * Toggles the keep-alive engine straight from the notification shade - no launcher, no app.
 * The armed flag is the tile's source of truth because it survives the app process being killed,
 * which is exactly what happens on the ROMs this app exists to diagnose.
 */
class KeepAliveTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val armed = AppPrefs.isArmed(this)

        if (armed) {
            KeepAliveForegroundService.stop(this)
            AppPrefs.setArmed(this, false)
            LogEventBus.log(
                source = EventSource.SYSTEM_DIAGNOSTIC,
                action = "Quick Settings Tile",
                details = "Keep-alive toggled off from the shade"
            )
            refresh()
            return
        }

        // Starting a foreground service from a tile is a background start, which the platform can
        // refuse. Fall back to opening the app, which arms from the foreground where it is allowed.
        try {
            KeepAliveForegroundService.start(this)
            KeepAliveForegroundService.toggleWakeLock(this, true)
            AppPrefs.setArmed(this, true)
            LogEventBus.log(
                source = EventSource.SYSTEM_DIAGNOSTIC,
                action = "Quick Settings Tile",
                details = "Keep-alive toggled on from the shade"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Tile could not start the service directly: ${e.message}")
            LogEventBus.log(
                source = EventSource.SYSTEM_DIAGNOSTIC,
                action = "Tile Start Refused",
                details = "${e.javaClass.simpleName}: falling back to the app",
                isSuccess = false
            )
            openAppToArm()
        }

        refresh()
    }

    private fun openAppToArm() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_QUICK_ARM, true)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        1,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the app from the tile: ${e.message}")
        }
    }

    private fun refresh() {
        val armed = AppPrefs.isArmed(this)
        qsTile?.apply {
            state = if (armed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (armed) "IPC engine on" else "IPC engine off"
            updateTile()
        }
    }

    private companion object {
        const val TAG = "KeepAliveTileService"
    }
}

/** Opens the Last Call Info page directly from the shade. */
class LastCallTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Last call"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_LAST_CALL, true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the last call page from the tile: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "LastCallTileService"
    }
}
