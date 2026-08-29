package com.example.bubble

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The standalone app is one screen and one overlay service, so what is worth pinning is that the
 * on/off state survives a reboot and that the boot receiver honours it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class BubbleSetupTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun reset() {
    AppPrefs.setFloatingRailEnabled(context, false)
  }

  @Test
  fun `bubble state round trips`() {
    AppPrefs.setFloatingRailEnabled(context, true)
    assertTrue(AppPrefs.isFloatingRailEnabled(context))
  }

  @Test
  fun `boot receiver stays idle when the bubble was off`() {
    BubbleBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

    assertEquals(
      null,
      shadowOf(context as android.app.Application).nextStartedService
    )
  }

  @Test
  fun `boot receiver ignores unrelated broadcasts`() {
    AppPrefs.setFloatingRailEnabled(context, true)

    BubbleBootReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

    assertEquals(
      null,
      shadowOf(context as android.app.Application).nextStartedService
    )
  }

  @Test
  fun `the diagnostics package it hands off to is the other app in this repo`() {
    assertEquals("com.aistudio.ipcsolution.poc", BubbleOverlayService.DIAGNOSTICS_PACKAGE)
  }
}
