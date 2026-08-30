package com.example.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The jid is the undocumented part, so what is pinned here is that an unresolved number produces
 * no jid at all rather than a malformed one - a bad jid would send the photos to nobody, where
 * no jid falls back to WhatsApp's own picker.
 */
class WhatsAppSenderTest {

    @Test
    fun `resolved digits become a chat address`() {
        assertEquals("2348031234567@s.whatsapp.net", WhatsAppSender.jidFor("2348031234567"))
    }

    @Test
    fun `no digits, no jid`() {
        assertNull(WhatsAppSender.jidFor(null))
        assertNull(WhatsAppSender.jidFor(""))
        assertNull(WhatsAppSender.jidFor("   "))
    }
}
