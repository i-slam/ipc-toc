package com.example.bubble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.telephony.WhatsAppLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Call log numbers arrive however they were dialled. wa.me wants bare digits with a country code,
 * so the normalisation is where this feature breaks in practice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class WhatsAppLauncherTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun withCountry(iso: String) {
    shadowOf(context.getSystemService(android.telephony.TelephonyManager::class.java))
      .setNetworkCountryIso(iso)
  }

  @Test
  fun `international numbers keep their digits and lose the punctuation`() {
    withCountry("gb")
    assertEquals("2348031234567", WhatsAppLauncher.toWaMeDigits(context, "+234 803 123 4567"))
    assertEquals("2348031234567", WhatsAppLauncher.toWaMeDigits(context, "+234-803-123-4567"))
  }

  @Test
  fun `a local number is resolved against the sim country`() {
    withCountry("gb")
    // 07700 900123 is a UK number; with GB context it should come back as 447700900123.
    assertEquals("447700900123", WhatsAppLauncher.toWaMeDigits(context, "07700 900123"))
  }

  @Test
  fun `nothing usable comes back as null rather than a broken link`() {
    withCountry("gb")
    assertNull(WhatsAppLauncher.toWaMeDigits(context, null))
    assertNull(WhatsAppLauncher.toWaMeDigits(context, ""))
    assertNull(WhatsAppLauncher.toWaMeDigits(context, "   "))
    // Service codes are too short to be a WhatsApp account.
    assertNull(WhatsAppLauncher.toWaMeDigits(context, "999"))
  }

  @Test
  fun `the chat intent points at wa dot me`() {
    withCountry("gb")
    val intent = WhatsAppLauncher.chatIntent(context, "+2348031234567")
    assertTrue(intent?.data.toString().startsWith("https://wa.me/2348031234567"))
  }

  @Test
  fun `a message is url encoded into the link`() {
    withCountry("gb")
    val intent = WhatsAppLauncher.chatIntent(context, "+2348031234567", "hi there & thanks")
    val url = intent?.data.toString()
    assertTrue("spaces should be encoded, got $url", url.contains("hi%20there") || url.contains("hi+there"))
    assertTrue("ampersand should be encoded, got $url", url.contains("%26"))
  }

  @Test
  fun `no number yields no intent`() {
    withCountry("gb")
    assertNull(WhatsAppLauncher.chatIntent(context, null))
  }
}
