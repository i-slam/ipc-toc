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
import org.robolectric.annotation.Config

/**
 * Call log numbers arrive however they were dialled, while wa.me wants bare digits with a country
 * code, so the normalisation is where this breaks in practice.
 *
 * The country resolution itself is `PhoneNumberUtils`, which behaves differently under Robolectric
 * than on a device - so the decision is tested directly by feeding in what the platform would have
 * returned, rather than asserting on a call that only works on real hardware.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class WhatsAppLauncherTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `a resolved number keeps its digits and loses the plus`() {
    assertEquals(
      "2348031234567",
      WhatsAppLauncher.normalise("+234 803 123 4567", "+2348031234567")
    )
  }

  @Test
  fun `a local number resolved by the platform becomes international`() {
    // What PhoneNumberUtils returns on a device with a GB SIM.
    assertEquals("447700900123", WhatsAppLauncher.normalise("07700 900123", "+447700900123"))
  }

  @Test
  fun `an unresolvable local number is refused rather than sent as-is`() {
    // No SIM country, so the platform gave up. Passing 07700900123 to wa.me opens WhatsApp only
    // to say the number is invalid, which reads as the app being broken.
    assertNull(WhatsAppLauncher.normalise("07700 900123", null))
    assertNull(WhatsAppLauncher.normalise("0803 123 4567", null))
  }

  @Test
  fun `an already international number survives without the platform`() {
    assertEquals("2348031234567", WhatsAppLauncher.normalise("2348031234567", null))
    assertEquals("2348031234567", WhatsAppLauncher.normalise("+234 803 123 4567", null))
  }

  @Test
  fun `nothing usable comes back as null rather than a broken link`() {
    assertNull(WhatsAppLauncher.normalise(null, null))
    assertNull(WhatsAppLauncher.normalise("", null))
    assertNull(WhatsAppLauncher.normalise("   ", null))
    // Service and emergency codes are not WhatsApp accounts.
    assertNull(WhatsAppLauncher.normalise("999", null))
    assertNull(WhatsAppLauncher.normalise("12345", null))
  }

  @Test
  fun `the chat intent points at wa dot me`() {
    val intent = WhatsAppLauncher.chatIntent(context, "+2348031234567")
    assertTrue(
      "unexpected url: ${intent?.data}",
      intent?.data.toString().startsWith("https://wa.me/2348031234567")
    )
  }

  @Test
  fun `a message is url encoded into the link`() {
    val url = WhatsAppLauncher.chatIntent(context, "+2348031234567", "hi there & thanks")
      ?.data
      .toString()
    assertTrue("spaces should be encoded, got $url", url.contains("hi%20there"))
    assertTrue("ampersand should be encoded, got $url", url.contains("%26"))
  }

  @Test
  fun `no number yields no intent`() {
    assertNull(WhatsAppLauncher.chatIntent(context, null))
    assertNull(WhatsAppLauncher.chatIntent(context, "0803 123 4567"))
  }
}
