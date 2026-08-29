package com.example.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.R
import com.example.service.KeepAliveTileService
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Puts the engine toggle in the notification shade. From Android 13 the app can ask for the tile
 * to be added with a single confirmation; below that the tile still exists, it just has to be
 * dragged in from the shade's edit screen.
 */
object QuickTiles {

    private const val TAG = "QuickTiles"

    fun requestAddEngineTile(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestOnTiramisu(context)
        } else {
            Toast.makeText(
                context,
                "Pull down the shade, tap edit, and drag in the IPC engine tile",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestOnTiramisu(context: Context) {
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            Log.w(TAG, "StatusBarManager unavailable")
            return
        }

        try {
            manager.requestAddTileService(
                ComponentName(context, KeepAliveTileService::class.java),
                context.getString(R.string.tile_keep_alive),
                Icon.createWithResource(context, R.drawable.ic_tile_bolt),
                Executor { it.run() },
                Consumer<Int> { result -> Log.i(TAG, "Tile add request result: $result") }
            )
        } catch (e: Exception) {
            Log.w(TAG, "requestAddTileService failed: ${e.message}")
            Toast.makeText(
                context,
                "Could not ask for the tile - add it from the shade's edit screen",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
