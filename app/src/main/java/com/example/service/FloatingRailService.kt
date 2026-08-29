package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppPrefs
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.ui.FloatingAction
import com.example.ui.FloatingRail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The diagnostic app's floating bubble: the in-app Swiss-army rail promoted to a system overlay,
 * so the tools are one tap away while a call ends in the dialer rather than only on this app's
 * own screens.
 */
class FloatingRailService : OverlayWindowService() {

    override val logTag: String = TAG

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        markStarted()

        if (intent?.action == ACTION_HIDE) {
            AppPrefs.setFloatingRailEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        goForeground(NOTIFICATION_ID, buildNotification())

        if (!canDrawOverlays()) {
            Log.w(TAG, "Overlay permission missing, cannot show the floating rail")
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Floating Rail Blocked",
                details = "SYSTEM_ALERT_WINDOW not granted - grant it, then switch the floating button on again",
                isSuccess = false
            )
            AppPrefs.setFloatingRailEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        val attached = attachOverlay { onDragVertically ->
            val engineOn by KeepAliveForegroundService.isRunning.collectAsState()
            FloatingRail(
                isEngineOn = engineOn,
                onAction = ::handleAction,
                onDragVertically = onDragVertically
            )
        }

        if (attached) {
            _isShowing.value = true
            AppPrefs.setFloatingRailEnabled(this, true)
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Floating Rail Shown",
                details = "System-wide bubble docked to the screen edge"
            )
        } else {
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Floating Rail Failed",
                details = "WindowManager refused the overlay window",
                isSuccess = false
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun handleAction(action: FloatingAction) {
        Log.i(TAG, "Floating rail action: $action")
        when (action) {
            FloatingAction.LAST_CALL -> openApp(MainActivity.EXTRA_OPEN_LAST_CALL)
            FloatingAction.ARM_EVERYTHING -> openApp(MainActivity.EXTRA_START_QUICK_ARM)
            FloatingAction.SHOW_POPUP ->
                KeepAliveForegroundService.showOverlayDirect(this, "floating_rail")
            FloatingAction.SIMULATE_CALL_END -> KeepAliveForegroundService.simulateCall(this)
            FloatingAction.TOGGLE_ENGINE -> {
                if (KeepAliveForegroundService.isRunning.value) {
                    KeepAliveForegroundService.stop(this)
                } else {
                    KeepAliveForegroundService.start(this)
                    KeepAliveForegroundService.toggleWakeLock(this, true)
                }
            }
            FloatingAction.HIDE -> {
                AppPrefs.setFloatingRailEnabled(this, false)
                stopSelf()
            }
        }
    }

    private fun openApp(extra: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(extra, true)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the app from the floating rail: ${e.message}")
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hidePendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatingRailService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating IPC tools")
            .setContentText("Bubble docked on the screen edge")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(launchIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide", hidePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating IPC tools",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the floating tool bubble on screen"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        _isShowing.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingRailService"
        private const val CHANNEL_ID = "floating_rail_channel"
        private const val NOTIFICATION_ID = 4002

        const val ACTION_HIDE = "com.example.ACTION_HIDE_FLOATING_RAIL"

        private val _isShowing = MutableStateFlow(false)
        val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

        fun show(context: Context) {
            val intent = Intent(context, FloatingRailService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingRailService::class.java).apply {
                action = ACTION_HIDE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop the floating rail: ${e.message}")
            }
        }
    }
}
