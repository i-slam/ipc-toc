package com.example.telephony

import android.telephony.TelephonyManager.CALL_STATE_IDLE
import android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
import android.telephony.TelephonyManager.CALL_STATE_RINGING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which state changes count as which event. The one worth being explicit about is a call that
 * rings and is never answered: it goes RINGING -> IDLE without ever being OFFHOOK, and treating
 * that as "nothing happened" would miss every missed call - the exact rows this app exists to
 * act on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class CallStateWatcherTest {

    @Test
    fun `an incoming call starts ringing`() {
        assertEquals(CallEvent.RINGING, CallTransition.classify(CALL_STATE_IDLE, CALL_STATE_RINGING))
    }

    @Test
    fun `picking up is an answer`() {
        assertEquals(
            CallEvent.ANSWERED,
            CallTransition.classify(CALL_STATE_RINGING, CALL_STATE_OFFHOOK)
        )
    }

    @Test
    fun `dialling out is an answer too`() {
        // No ringing state on the calling side; it goes straight off-hook.
        assertEquals(
            CallEvent.ANSWERED,
            CallTransition.classify(CALL_STATE_IDLE, CALL_STATE_OFFHOOK)
        )
    }

    @Test
    fun `hanging up ends the call`() {
        assertEquals(CallEvent.ENDED, CallTransition.classify(CALL_STATE_OFFHOOK, CALL_STATE_IDLE))
    }

    @Test
    fun `a call that rings out and is never answered still ends`() {
        assertEquals(CallEvent.ENDED, CallTransition.classify(CALL_STATE_RINGING, CALL_STATE_IDLE))
    }

    @Test
    fun `a repeated state is not an event`() {
        listOf(CALL_STATE_IDLE, CALL_STATE_RINGING, CALL_STATE_OFFHOOK).forEach {
            assertNull(CallTransition.classify(it, it))
        }
    }
}
