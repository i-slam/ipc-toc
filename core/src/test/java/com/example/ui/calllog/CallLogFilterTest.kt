package com.example.ui.calllog

import com.example.telephony.CallDirection
import com.example.telephony.CallRecord
import com.example.telephony.WhatsAppLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which call lands under which tab is a judgement call, not a mechanical mapping - a rejected
 * call is a missed one as far as anyone reading the list is concerned - so it is pinned here
 * rather than left to be rediscovered by scrolling the app.
 */
class CallLogFilterTest {

    @Test
    fun `every direction shows under All`() {
        CallDirection.entries.forEach { direction ->
            assertTrue(direction.name, CallLogFilter.matches(CallLogTab.ALL, direction))
        }
    }

    @Test
    fun `calls the user did not take share the missed tab`() {
        listOf(CallDirection.MISSED, CallDirection.REJECTED, CallDirection.BLOCKED).forEach {
            assertTrue(it.name, CallLogFilter.matches(CallLogTab.MISSED, it))
        }
        assertFalse(CallLogFilter.matches(CallLogTab.MISSED, CallDirection.INCOMING))
        assertFalse(CallLogFilter.matches(CallLogTab.MISSED, CallDirection.OUTGOING))
    }

    @Test
    fun `voicemail counts as incoming`() {
        assertTrue(CallLogFilter.matches(CallLogTab.INCOMING, CallDirection.INCOMING))
        assertTrue(CallLogFilter.matches(CallLogTab.INCOMING, CallDirection.VOICEMAIL))
        assertFalse(CallLogFilter.matches(CallLogTab.INCOMING, CallDirection.MISSED))
    }

    @Test
    fun `outgoing is only outgoing`() {
        assertTrue(CallLogFilter.matches(CallLogTab.OUTGOING, CallDirection.OUTGOING))
        CallDirection.entries.filter { it != CallDirection.OUTGOING }.forEach {
            assertFalse(it.name, CallLogFilter.matches(CallLogTab.OUTGOING, it))
        }
    }

    @Test
    fun `an unknown call is visible under All and nowhere else`() {
        assertTrue(CallLogFilter.matches(CallLogTab.ALL, CallDirection.UNKNOWN))
        listOf(CallLogTab.MISSED, CallLogTab.INCOMING, CallLogTab.OUTGOING).forEach {
            assertFalse(it.name, CallLogFilter.matches(it, CallDirection.UNKNOWN))
        }
    }

    @Test
    fun `filtering and counting agree with each other`() {
        val rows = sampleRows()

        CallLogTab.entries.forEach { tab ->
            assertEquals(
                tab.name,
                CallLogFilter.filter(tab, rows).size,
                CallLogFilter.counts(rows)[tab] ?: -1
            )
        }
        assertEquals(6, CallLogFilter.counts(rows)[CallLogTab.ALL] ?: -1)
        assertEquals(2, CallLogFilter.counts(rows)[CallLogTab.MISSED] ?: -1)
        assertEquals(2, CallLogFilter.counts(rows)[CallLogTab.INCOMING] ?: -1)
        assertEquals(1, CallLogFilter.counts(rows)[CallLogTab.OUTGOING] ?: -1)
    }

    @Test
    fun `counts of an empty log are zero rather than absent`() {
        val counts = CallLogFilter.counts(emptyList())
        CallLogTab.entries.forEach { assertEquals(it.name, 0, counts[it] ?: -1) }
    }

    /**
     * The WhatsApp button is enabled off the same decision the launcher makes, so a number the
     * launcher refuses must arrive at the row already marked unusable.
     */
    @Test
    fun `a number with no country code cannot be messaged`() {
        val local = row(1, "07700 900123", CallDirection.MISSED, e164 = null)
        val international = row(2, "+234 803 123 4567", CallDirection.MISSED, e164 = null)

        assertFalse(local.canWhatsApp)
        assertTrue(international.canWhatsApp)
        assertEquals("2348031234567", international.waDigits)
    }

    @Test
    fun `a withheld number cannot be messaged`() {
        assertFalse(row(3, "", CallDirection.INCOMING, e164 = null).canWhatsApp)
    }
}

private fun sampleRows(): List<CallRow> = listOf(
    row(1, "+2348031234567", CallDirection.MISSED),
    row(2, "+2348031234568", CallDirection.REJECTED),
    row(3, "+2348031234569", CallDirection.INCOMING),
    row(4, "+2348031234570", CallDirection.VOICEMAIL),
    row(5, "+2348031234571", CallDirection.OUTGOING),
    row(6, "+2348031234572", CallDirection.UNKNOWN)
)

private fun row(
    id: Long,
    number: String,
    direction: CallDirection,
    e164: String? = null
): CallRow = CallRow(
    record = CallRecord(
        id = id,
        number = number,
        cachedName = null,
        direction = direction,
        timestamp = 1_700_000_000_000L,
        durationSeconds = 42,
        phoneAccountId = null,
        geocodedLocation = null,
        isNew = false,
        viaNumber = null
    ),
    waDigits = WhatsAppLauncher.normalise(number, e164)
)
