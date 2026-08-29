package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
 * Keeps a draggable bubble parked on the screen edge, above every app rather than only inside
 * this one. It is the in-app Swiss-army rail promoted to a system overlay, which is the only way
 * a tool like this is one tap away while a call ends in the dialer.
 *
 * A foreground service because the window has to survive the app being backgrounded - exactly
 * what HiOS is aggressive about.
 */
class FloatingRailService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private var windowManager: WindowManager? = null
    private var railView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val appViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = appViewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        if (intent?.action == ACTION_HIDE) {
            AppPrefs.setFloatingRailEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
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

        showRail()
        return START_STICKY
    }

    private fun showRail() {
        if (railView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 320
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingRailService)
            setViewTreeSavedStateRegistryOwner(this@FloatingRailService)
            setViewTreeViewModelStoreOwner(this@FloatingRailService)
            setContent {
                val engineOn by KeepAliveForegroundService.isRunning.collectAsState()
                FloatingRail(
                    isEngineOn = engineOn,
                    onAction = ::handleAction,
                    onDragVertically = ::moveBy
                )
            }
        }

        try {
            windowManager?.addView(view, params)
            railView = view
            _isShowing.value = true
            AppPrefs.setFloatingRailEnabled(this, true)
            Log.i(TAG, "Floating rail added to the window manager")
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Floating Rail Shown",
                details = "System-wide bubble docked to the screen edge"
            )
        } catch (e: Exception) {
            Log.e(TAG, "addView failed for the floating rail", e)
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Floating Rail Failed",
                details = "addView threw ${e.javaClass.simpleName}: ${e.message}",
                isSuccess = false
            )
            stopSelf()
        }
    }

    private fun moveBy(deltaY: Float) {
        val params = layoutParams ?: return
        val view = railView ?: return
        params.y = (params.y + deltaY).toInt().coerceAtLeast(0)
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "Could not move the floating rail: ${e.message}")
        }
    }

    private fun handleAction(action: FloatingAction) {
        Log.i(TAG, "Floating rail action: $action")
        when (action) {
            FloatingAction.LAST_CALL -> openApp(MainActivity.EXTRA_OPEN_LAST_CALL)
            FloatingAction.ARM_EVERYTHING -> openApp(MainActivity.EXTRA_START_QUICK_ARM)
            FloatingAction.SHOW_POPUP -> KeepAliveForegroundService.showOverlayDirect(this, "floating_rail")
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

    private fun startAsForeground() {
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

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating IPC tools")
            .setContentText("Bubble docked on the screen edge")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(launchIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide", hidePendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (inner: Exception) {
                Log.e(TAG, "legacy startForeground also failed: ${inner.message}")
            }
        }
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

    private fun removeRail() {
        railView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "removeView warning: ${e.message}")
            }
            railView = null
        }
        _isShowing.value = false
    }

    override fun onDestroy() {
        removeRail()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        appViewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
