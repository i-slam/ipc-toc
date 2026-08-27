package com.example.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
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
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.ui.OverlayCard

abstract class BaseOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    protected abstract val variantLabel: String
    protected abstract val tag: String

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val appViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = appViewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Process.myProcessName() else "pid ${Process.myPid()}"
        Log.i(tag, "onCreate (process=$processName)")
        LogEventBus.log(
            source = EventSource.OVERLAY_WINDOW,
            action = "OverlayService.onCreate",
            details = "Initialized overlay service ($variantLabel) in process $processName"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: "direct_intent"
        val canDraw = Settings.canDrawOverlays(this)
        Log.i(tag, "onStartCommand: source=$source canDrawOverlays=$canDraw")

        if (!canDraw) {
            Log.w(tag, "Overlay permission not granted, stopping self")
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Overlay Blocked",
                details = "Permission SYSTEM_ALERT_WINDOW not granted for source: $source",
                isSuccess = false
            )
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(source)
        return START_NOT_STICKY
    }

    private fun showOverlay(source: String) {
        removeOverlay()

        val layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val shownAt = System.currentTimeMillis()
        val currentProcess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Process.myProcessName() else "pid_${Process.myPid()}"
        val currentPid = Process.myPid()

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BaseOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BaseOverlayService)
            setViewTreeViewModelStoreOwner(this@BaseOverlayService)
            setContent {
                OverlayCard(
                    variantLabel = variantLabel,
                    triggerSource = source,
                    processName = currentProcess,
                    pid = currentPid,
                    shownAtMillis = shownAt,
                    onDismiss = {
                        LogEventBus.log(
                            source = EventSource.OVERLAY_WINDOW,
                            action = "Overlay Dismissed",
                            details = "User dismissed overlay ($variantLabel)"
                        )
                        removeOverlay()
                        stopSelf()
                    },
                    onSaveCallLog = { tag, notes ->
                        LogEventBus.log(
                            source = EventSource.OVERLAY_WINDOW,
                            action = "Call Log Saved",
                            details = "Tag: '$tag', Notes: '$notes' via $variantLabel overlay"
                        )
                    }
                )
            }
        }

        Log.i(tag, "calling windowManager.addView() now")
        try {
            windowManager?.addView(composeView, params)
            overlayView = composeView
            Log.i(tag, "addView succeeded")
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Overlay Rendered",
                details = "Successfully displayed overlay ($variantLabel) triggered via $source",
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e(tag, "addView failed", e)
            LogEventBus.log(
                source = EventSource.OVERLAY_WINDOW,
                action = "Overlay Failed",
                details = "addView threw ${e.javaClass.simpleName}: ${e.message}",
                isSuccess = false
            )
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(tag, "removeView warning: ${e.message}")
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        appViewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }
}
