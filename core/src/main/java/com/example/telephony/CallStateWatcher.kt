package com.example.telephony

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log

/** What just happened to the phone call, as far as anything outside the dialer can tell. */
enum class CallEvent { RINGING, ANSWERED, ENDED }

/**
 * The state machine, kept apart from the platform so the transitions can be tested without a
 * radio. Returns null when nothing meaningful changed.
 */
internal object CallTransition {

    fun classify(previous: Int, next: Int): CallEvent? {
        if (previous == next) return null
        return when {
            next == TelephonyManager.CALL_STATE_RINGING -> CallEvent.RINGING
            next == TelephonyManager.CALL_STATE_OFFHOOK -> CallEvent.ANSWERED
            // Back to idle from either a ringing or a connected call: it is over either way, and
            // a rejected call is as much an event worth reacting to as an answered one.
            next == TelephonyManager.CALL_STATE_IDLE -> CallEvent.ENDED
            else -> null
        }
    }
}

/**
 * Watches the call state and reports transitions.
 *
 * Deliberately says nothing about *who* is calling: from API 31 the platform stopped handing the
 * number to a plain listener, and only a CallScreeningService or the default dialer can see it.
 * The number is knowable once the call ends and the provider has the record, which is what
 * callers of this class use [CallEvent.ENDED] for.
 *
 * Registration happens on a dedicated HandlerThread, because on Transsion builds the main looper
 * is throttled by the freezer while the app is in the background - the whole reason the
 * diagnostic app exists.
 */
class CallStateWatcher(
    private val context: Context,
    private val onEvent: (CallEvent) -> Unit
) {

    private val thread = HandlerThread("BubbleCallWatcher").apply { start() }
    private val handler = Handler(thread.looper)

    private var telephony: TelephonyManager? = null
    private var callback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    private var previous = TelephonyManager.CALL_STATE_IDLE

    fun start() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephony = tm
        if (tm == null) {
            Log.w(TAG, "No TelephonyManager; call watching is off")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onState(state)
                }
                callback = cb
                tm.registerTelephonyCallback({ command -> handler.post(command) }, cb)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
                }
                legacyListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            Log.i(TAG, "Call state watcher registered")
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE not granted, not watching calls: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not register the call state watcher", e)
        }
    }

    private fun onState(state: Int) {
        val event = CallTransition.classify(previous, state)
        previous = state
        if (event != null) {
            Log.i(TAG, "Call event: $event")
            onEvent(event)
        }
    }

    fun stop() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (callback as? TelephonyCallback)?.let { telephony?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                legacyListener?.let { telephony?.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering the call state watcher: ${e.message}")
        } finally {
            thread.quitSafely()
        }
    }

    companion object {
        private const val TAG = "CallStateWatcher"

        /**
         * The provider writes the finished call a moment after the state goes idle, so anything
         * reading the log on ENDED has to wait or it reads the call before last.
         */
        const val CALL_LOG_SETTLE_MS = 1_500L
    }
}
