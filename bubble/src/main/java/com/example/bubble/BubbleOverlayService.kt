package com.example.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.data.AppPrefs
import com.example.service.OverlayWindowService
import com.example.telephony.CallRecord
import com.example.telephony.WhatsAppLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The whole of this app: a bubble on the screen edge, above every other app. There is no
 * diagnostic UI behind it - the launcher activity exists only to grant the overlay permission
 * and switch this on.
 */
class BubbleOverlayService : OverlayWindowService() {

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
            Log.w(TAG, "Overlay permission missing")
            AppPrefs.setFloatingRailEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        val attached = attachOverlay { onDragVertically ->
            BubblePanel(
                onAction = ::handleAction,
                onDragVertically = onDragVertically
            )
        }

        if (!attached) {
            stopSelf()
            return START_NOT_STICKY
        }

        _isShowing.value = true
        AppPrefs.setFloatingRailEnabled(this, true)
        return START_STICKY
    }

    // The panel hands over the record it already loaded, so no action re-queries the provider on
    // the main thread.
    private fun handleAction(action: BubbleAction, record: CallRecord?) {
        Log.i(TAG, "Bubble action: $action")
        when (action) {
            BubbleAction.WHATSAPP_LAST_CALL -> whatsAppLastCall(record)
            BubbleAction.OPEN_CALL_LOG -> openCallLog()
            BubbleAction.COPY_LAST_CALL -> copyLastCall(record)
            BubbleAction.OPEN_DIALER -> openDialer()
            BubbleAction.OPEN_DIAGNOSTICS -> openDiagnosticsApp()
            BubbleAction.HIDE -> {
                AppPrefs.setFloatingRailEnabled(this, false)
                stopSelf()
            }
        }
    }

    private fun whatsAppLastCall(record: CallRecord?) {
        val number = record?.number
        if (number.isNullOrBlank()) {
            toast("No number on the last call to message")
            return
        }

        if (!WhatsAppLauncher.isInstalled(this)) {
            toast("WhatsApp is not installed")
            return
        }

        // A local-format number with no SIM country cannot be turned into a wa.me link, and
        // WhatsApp would just show "phone number shared via url is invalid".
        val failure = WhatsAppLauncher.openChat(this, number)
        if (failure != null) {
            toast("$failure: $number")
        }
    }

    private fun copyLastCall(record: CallRecord?) {
        if (record == null) {
            toast("No call to copy - grant call log access in the app first")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Last call", record.toShareText()))
        toast("Last call copied")
    }

    /**
     * The full list, where every call gets its own WhatsApp button. Starting an activity from a
     * service is a background start the platform normally refuses, but an app holding
     * SYSTEM_ALERT_WINDOW is exempt - which this one does, or the bubble would not be on screen.
     */
    private fun openCallLog() {
        try {
            startActivity(BubbleActivity.callLogIntent(this))
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the call log: ${e.message}")
            toast("Could not open the call log")
        }
    }

    private fun openDialer() {
        try {
            startActivity(
                Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the dialer: ${e.message}")
        }
    }

    /** Opens the full diagnostic app when it happens to be installed alongside this one. */
    private fun openDiagnosticsApp() {
        val launch = packageManager.getLaunchIntentForPackage(DIAGNOSTICS_PACKAGE)
        if (launch == null) {
            toast("The IPC Solution PoC app is not installed")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launch)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the diagnostics app: ${e.message}")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BubbleActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hideIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BubbleOverlayService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating button")
            .setContentText("Docked on the screen edge")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(launchIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide", hideIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating button",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the floating button on screen"
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
        private const val TAG = "BubbleOverlayService"
        private const val CHANNEL_ID = "bubble_channel"
        private const val NOTIFICATION_ID = 5001

        const val ACTION_HIDE = "com.example.bubble.ACTION_HIDE"

        /** The full diagnostic app, if the user also has it installed. */
        const val DIAGNOSTICS_PACKAGE = "com.aistudio.ipcsolution.poc"

        private val _isShowing = MutableStateFlow(false)
        val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

        fun show(context: Context) {
            val intent = Intent(context, BubbleOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            try {
                context.startService(
                    Intent(context, BubbleOverlayService::class.java).apply { action = ACTION_HIDE }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop the bubble: ${e.message}")
            }
        }
    }
}
