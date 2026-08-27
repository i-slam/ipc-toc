package com.example.telephony

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.EventSource
import com.example.data.LogEventBus
import com.example.service.SeparateProcessOverlayService

class CallStateMonitor(private val context: Context) {

    private val handlerThread = HandlerThread("CallMonitorBackgroundThread").apply { start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var contentObserver: ContentObserver? = null

    private var lastState = TelephonyManager.CALL_STATE_IDLE

    fun startListening() {
        Log.i(TAG, "Starting CallStateMonitor on background looper ${handlerThread.name}")
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        registerTelephonyListener()
        registerCallLogObserver()

        LogEventBus.log(
            source = EventSource.SYSTEM_DIAGNOSTIC,
            action = "CallStateMonitor Started",
            details = "Active on background HandlerThread '${handlerThread.name}'"
        )
    }

    private fun registerTelephonyListener() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChanged(state)
                    }
                }
                telephonyCallback = callback
                tm.registerTelephonyCallback(
                    { command -> backgroundHandler.post(command) },
                    callback
                )
                Log.i(TAG, "Registered modern TelephonyCallback with background executor")
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChanged(state)
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.i(TAG, "Registered legacy PhoneStateListener")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted: ${e.message}")
            LogEventBus.log(
                source = EventSource.TELEPHONY_CALLBACK,
                action = "Permission Missing",
                details = "Cannot listen to real telephony state without READ_PHONE_STATE permission",
                isSuccess = false
            )
        }
    }

    private fun registerCallLogObserver() {
        try {
            contentObserver = object : ContentObserver(backgroundHandler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.i(TAG, "ContentObserver onChange fired for URI: $uri")
                    LogEventBus.log(
                        source = EventSource.CONTENT_OBSERVER,
                        action = "CallLog Changed",
                        details = "ContentObserver detected new/updated call log record ($uri)"
                    )
                }
            }
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                contentObserver!!
            )
            Log.i(TAG, "Registered CallLog ContentObserver on background handler")
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CALL_LOG permission not granted for ContentObserver: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver", e)
        }
    }

    private fun handleCallStateChanged(state: Int) {
        val stateName = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK (In Call)"
            else -> "UNKNOWN ($state)"
        }

        Log.i(TAG, "Call state transition: $lastState -> $stateName")
        LogEventBus.log(
            source = EventSource.TELEPHONY_CALLBACK,
            action = "Call State Changed",
            details = "State: $stateName (Previous: $lastState)"
        )

        // If transitioning from OFFHOOK to IDLE (Call Ended)
        if (lastState == TelephonyManager.CALL_STATE_OFFHOOK && state == TelephonyManager.CALL_STATE_IDLE) {
            Log.i(TAG, "Call ended! Triggering overlay popup...")
            LogEventBus.log(
                source = EventSource.TELEPHONY_CALLBACK,
                action = "Call Ended Trigger",
                details = "Detected call end via TelephonyCallback, launching overlay popup"
            )
            SeparateProcessOverlayService.show(context, "telephony_callback")
        }

        lastState = state
    }

    fun simulateCallEnd() {
        LogEventBus.log(
            source = EventSource.TELEPHONY_CALLBACK,
            action = "Simulated Call End",
            details = "Manual test simulation: OFFHOOK -> IDLE transition triggered"
        )
        SeparateProcessOverlayService.show(context, "simulated_call_end")
    }

    fun stopListening() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    (it as? TelephonyCallback)?.let { cb ->
                        telephonyManager?.unregisterTelephonyCallback(cb)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let {
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
            contentObserver?.let {
                context.contentResolver.unregisterContentObserver(it)
            }
            handlerThread.quitSafely()
            Log.i(TAG, "CallStateMonitor stopped cleanly")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping CallStateMonitor: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CallStateMonitor"
    }
}
