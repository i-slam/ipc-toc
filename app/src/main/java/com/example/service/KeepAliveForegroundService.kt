package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.AppPrefs
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.telephony.CallStateMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeepAliveForegroundService : Service() {

    private var callStateMonitor: CallStateMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var dynamicReceiver: DynamicTriggerReceiver? = null

    // Messenger for bound IPC
    private val messenger = Messenger(IncomingHandler())

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_SHOW_OVERLAY -> {
                    val variant = msg.data?.getString(EXTRA_VARIANT) ?: "separate"
                    Log.i(TAG, "Received Messenger IPC MSG_SHOW_OVERLAY (variant=$variant)")
                    LogEventBus.log(
                        source = EventSource.MESSENGER_IPC,
                        action = "Messenger IPC Received",
                        details = "Command MSG_SHOW_OVERLAY received via Bound Service Messenger (variant=$variant)"
                    )
                    triggerOverlay(variant, "messenger_ipc")
                }
                MSG_PING -> {
                    LogEventBus.log(
                        source = EventSource.MESSENGER_IPC,
                        action = "Messenger Ping",
                        details = "Ping received via Bound Service Messenger"
                    )
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing KeepAliveForegroundService")
        createNotificationChannel()
        registerDynamicReceiver()
        callStateMonitor = CallStateMonitor(this).apply { startListening() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: intent?.getStringExtra("action") ?: ACTION_START
        Log.i(TAG, "onStartCommand: action=$action")

        when (action) {
            ACTION_STOP -> {
                LogEventBus.log(
                    source = EventSource.DIRECT_SERVICE,
                    action = "Stop Requested",
                    details = "Stopping KeepAliveForegroundService"
                )
                // Only an explicit stop disarms: a system kill must still restart after boot.
                AppPrefs.setArmed(this, false)
                stopForegroundService()
                return START_NOT_STICKY
            }

            ACTION_SHOW_OVERLAY_DIRECT -> {
                val variant = intent?.getStringExtra(EXTRA_VARIANT) ?: "separate"
                LogEventBus.log(
                    source = EventSource.DIRECT_SERVICE,
                    action = "Direct Service Trigger",
                    details = "Bypassed BroadcastQueue! Executing show overlay directly via Service onStartCommand (variant=$variant)"
                )
                triggerOverlay(variant, "direct_service_intent")
            }

            ACTION_SIMULATE_CALL -> {
                LogEventBus.log(
                    source = EventSource.DIRECT_SERVICE,
                    action = "Simulate Call Action",
                    details = "Direct service intent received to simulate call end"
                )
                callStateMonitor?.simulateCallEnd()
            }

            ACTION_TOGGLE_WAKELOCK -> {
                val enable = intent?.getBooleanExtra("enable", !isWakeLockHeld.value) ?: true
                setWakeLock(enable)
            }

            ACTION_PING -> {
                LogEventBus.log(
                    source = EventSource.DIRECT_SERVICE,
                    action = "Service Ping",
                    details = "Direct service ping received on alive process"
                )
            }

            else -> {
                startForegroundWithNotification()
                _isRunning.value = true
                AppPrefs.setArmed(this, true)
                LogEventBus.log(
                    source = EventSource.DIRECT_SERVICE,
                    action = "Service Started",
                    details = "KeepAliveForegroundService is now active in foreground with notification"
                )
            }
        }

        return START_STICKY
    }

    private fun triggerOverlay(variant: String, source: String) {
        if (variant == "same") {
            SameProcessOverlayService.show(this, source)
        } else {
            SeparateProcessOverlayService.show(this, source)
        }
    }

    private fun registerDynamicReceiver() {
        if (dynamicReceiver == null) {
            dynamicReceiver = DynamicTriggerReceiver()
            val filter = IntentFilter().apply {
                addAction(ACTION_DYNAMIC_POPUP)
                addAction(ACTION_SHOW_POPUP)
                addAction(ACTION_PING)
                priority = 1000
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    this,
                    dynamicReceiver!!,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(dynamicReceiver, filter)
            }
            Log.i(TAG, "Dynamically registered DynamicTriggerReceiver in active service process")
            LogEventBus.log(
                source = EventSource.DYNAMIC_BROADCAST,
                action = "Dynamic Receiver Registered",
                details = "Registered in active foreground service process (RECEIVER_EXPORTED)"
            )
        }
    }

    private fun unregisterDynamicReceiver() {
        dynamicReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i(TAG, "Unregistered dynamic receiver")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            dynamicReceiver = null
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun setWakeLock(enable: Boolean) {
        if (enable) {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "IPCSolutionPoC::KeepAliveWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                _isWakeLockHeld.value = true
                LogEventBus.log(
                    source = EventSource.SYSTEM_DIAGNOSTIC,
                    action = "WakeLock Acquired",
                    details = "PARTIAL_WAKE_LOCK acquired to prevent CPU/binder freeze on HiOS"
                )
            }
        } else {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            _isWakeLockHeld.value = false
            LogEventBus.log(
                source = EventSource.SYSTEM_DIAGNOSTIC,
                action = "WakeLock Released",
                details = "PARTIAL_WAKE_LOCK released"
            )
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}, falling back to legacy startForeground", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val directTriggerIntent = Intent(this, KeepAliveForegroundService::class.java).apply {
            putExtra("action", ACTION_SHOW_OVERLAY_DIRECT)
            putExtra(EXTRA_VARIANT, "separate")
        }
        val directTriggerPendingIntent = PendingIntent.getService(
            this,
            1,
            directTriggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lastCallIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_OPEN_LAST_CALL, true)
        }
        val lastCallPendingIntent = PendingIntent.getActivity(
            this,
            2,
            lastCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IPC Keep-Alive & Call Monitor Active")
            .setContentText("Listening for background events & phone calls")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_dialog_info,
                "Test Popup",
                directTriggerPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "Last Call",
                lastCallPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IPC Keep-Alive Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps IPC listener and call monitor service active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        unregisterDynamicReceiver()
        callStateMonitor?.stopListening()
        callStateMonitor = null
        setWakeLock(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        _isRunning.value = false
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    inner class DynamicTriggerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.i(TAG, "DynamicTriggerReceiver received: $action")

            when (action) {
                ACTION_DYNAMIC_POPUP, ACTION_SHOW_POPUP -> {
                    val variant = intent.getStringExtra(EXTRA_VARIANT) ?: "separate"
                    LogEventBus.log(
                        source = EventSource.DYNAMIC_BROADCAST,
                        action = "Dynamic Receiver Fired",
                        details = "Action: $action, Variant: $variant (Delivered via runtime registered receiver)"
                    )
                    triggerOverlay(variant, "dynamic_broadcast")
                }
                ACTION_PING -> {
                    LogEventBus.log(
                        source = EventSource.DYNAMIC_BROADCAST,
                        action = "Dynamic Receiver Ping",
                        details = "Ping broadcast acknowledged by dynamic receiver"
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "KeepAliveService"
        const val CHANNEL_ID = "ipc_keep_alive_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.example.ACTION_START_KEEPALIVE"
        const val ACTION_STOP = "com.example.ACTION_STOP_KEEPALIVE"
        const val ACTION_SHOW_OVERLAY_DIRECT = "com.example.ACTION_SHOW_OVERLAY_DIRECT"
        const val ACTION_SIMULATE_CALL = "com.example.ACTION_SIMULATE_CALL"
        const val ACTION_TOGGLE_WAKELOCK = "com.example.ACTION_TOGGLE_WAKELOCK"
        const val ACTION_DYNAMIC_POPUP = "com.example.ACTION_DYNAMIC_POPUP"
        const val ACTION_SHOW_POPUP = "com.example.ACTION_SHOW_POPUP"
        const val ACTION_PING = "com.example.ACTION_PING"

        const val EXTRA_VARIANT = "variant"

        const val MSG_SHOW_OVERLAY = 1
        const val MSG_PING = 2

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isWakeLockHeld = MutableStateFlow(false)
        val isWakeLockHeld: StateFlow<Boolean> = _isWakeLockHeld.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun showOverlayDirect(context: Context, variant: String = "separate") {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply {
                action = ACTION_SHOW_OVERLAY_DIRECT
                putExtra(EXTRA_VARIANT, variant)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && _isRunning.value) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun toggleWakeLock(context: Context, enable: Boolean) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply {
                action = ACTION_TOGGLE_WAKELOCK
                putExtra("enable", enable)
            }
            context.startService(intent)
        }

        fun simulateCall(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply {
                action = ACTION_SIMULATE_CALL
            }
            context.startService(intent)
        }
    }
}
