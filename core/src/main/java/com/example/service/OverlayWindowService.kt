package com.example.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
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

/**
 * Hosts a Compose window in the system overlay layer, above every app.
 *
 * A Service is not a LifecycleOwner and Compose refuses to run without one, so this supplies the
 * three owners a ComposeView looks for on its view tree, plus the WindowManager plumbing to
 * attach, move and detach the window. Subclasses decide what to draw and when.
 */
abstract class OverlayWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner,
    ViewModelStoreOwner {

    protected abstract val logTag: String

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val overlayViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = overlayViewModelStore

    protected val isOverlayAttached: Boolean get() = overlayView != null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    /** Call from onStartCommand before touching the window. */
    protected fun markStarted() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    protected fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    /**
     * Attaches the overlay window. [content] receives a callback that moves the window vertically,
     * which is how a drag gesture inside Compose reaches the WindowManager.
     *
     * Returns true when the window is on screen.
     */
    protected fun attachOverlay(
        initialY: Int = 320,
        gravity: Int = Gravity.TOP or Gravity.END,
        content: @Composable (onDragVertically: (Float) -> Unit) -> Unit
    ): Boolean {
        if (overlayView != null) return true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            x = 0
            y = initialY
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayWindowService)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowService)
            setViewTreeViewModelStoreOwner(this@OverlayWindowService)
            setContent { content(::moveOverlayBy) }
        }

        return try {
            windowManager?.addView(view, params)
            overlayView = view
            Log.i(logTag, "Overlay window attached")
            true
        } catch (e: Exception) {
            Log.e(logTag, "addView failed for the overlay window", e)
            layoutParams = null
            false
        }
    }

    protected fun moveOverlayBy(deltaY: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        params.y = (params.y + deltaY).toInt().coerceAtLeast(0)
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(logTag, "Could not move the overlay window: ${e.message}")
        }
    }

    protected fun detachOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(logTag, "removeView warning: ${e.message}")
            }
            overlayView = null
        }
        layoutParams = null
    }

    /**
     * Foreground promotion with the type dance the platform expects, falling back rather than
     * crashing when a release refuses the typed call.
     */
    protected fun goForeground(notificationId: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(notificationId, notification, type)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(logTag, "startForeground failed: ${e.message}")
            try {
                startForeground(notificationId, notification)
            } catch (inner: Exception) {
                Log.e(logTag, "legacy startForeground also failed: ${inner.message}")
            }
        }
    }

    override fun onDestroy() {
        detachOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayViewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null
}
