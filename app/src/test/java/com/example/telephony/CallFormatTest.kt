package com.example.telephony

import org.junit.Assert.assertEquals
import org.junit.Test

class CallFormatTest {

    @Test
    fun `zero duration is reported as not connected`() {
        assertEquals("0s (not connected)", CallFormat.duration(0))
        assertEquals("0s (not connected)", CallFormat.duration(-5))
    }

    @Test
    fun `duration is split into hours minutes and seconds`() {
        assertEquals("45s", CallFormat.duration(45))
        assertEquals("2m 5s", CallFormat.duration(125))
        assertEquals("1h 1m 1s", CallFormat.duration(3661))
    }

    @Test
    fun `relative time buckets by age`() {
        val now = 1_000_000_000_000L
        assertEquals("just now", CallFormat.relative(now - 30_000, now))
        assertEquals("5 min ago", CallFormat.relative(now - 5 * 60_000, now))
        assertEquals("3 h ago", CallFormat.relative(now - 3 * 3_600_000, now))
        assertEquals("2 d ago", CallFormat.relative(now - 2 * 86_400_000L, now))
        assertEquals("2 w ago", CallFormat.relative(now - 15 * 86_400_000L, now))
        assertEquals("in the future", CallFormat.relative(now + 60_000, now))
    }

    @Test
    fun `call log types map to directions`() {
        assertEquals(CallDirection.INCOMING, CallDirection.fromCallLogType(1))
        assertEquals(CallDirection.OUTGOING, CallDirection.fromCallLogType(2))
        assertEquals(CallDirection.MISSED, CallDirection.fromCallLogType(3))
        assertEquals(CallDirection.VOICEMAIL, CallDirection.fromCallLogType(4))
        assertEquals(CallDirection.REJECTED, CallDirection.fromCallLogType(5))
        assertEquals(CallDirection.BLOCKED, CallDirection.fromCallLogType(6))
        assertEquals(CallDirection.UNKNOWN, CallDirection.fromCallLogType(99))
    }

    @Test
    fun `share text carries the identifying fields`() {
        val record = CallRecord(
            id = 7L,
            number = "+2348012345678",
            cachedName = "Field Agent",
            direction = CallDirection.INCOMING,
            timestamp = 1_700_000_000_000L,
            durationSeconds = 125,
            phoneAccountId = "SIM1",
            geocodedLocation = "Lagos",
            isNew = true,
            viaNumber = null
        )

        val text = record.toShareText()
        assertEquals("Field Agent", record.displayName)
        assert(text.contains("+2348012345678"))
        assert(text.contains("Incoming"))
        assert(text.contains("2m 5s"))
        assert(text.contains("Lagos"))
        assert(text.contains("SIM1"))
    }
}
