package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPrefs
import com.example.receiver.BootCompletedReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The tap-saving entry points: the armed flag that survives a reboot, and the trampoline behind
 * the launcher shortcuts and quick-settings tiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class QuickActionsTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun reset() {
    AppPrefs.setArmed(context, false)
  }

  @Test
  fun `armed flag round trips`() {
    assertFalse(AppPrefs.isArmed(context))
    AppPrefs.setArmed(context, true)
    assertTrue(AppPrefs.isArmed(context))
    AppPrefs.setArmed(context, false)
    assertFalse(AppPrefs.isArmed(context))
  }

  @Test
  fun `boot receiver stays idle when the engine was never armed`() {
    BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

    assertEquals(
      "nothing should be started when disarmed",
      null,
      shadowOf(context as android.app.Application).nextStartedService
    )
  }

  @Test
  fun `boot receiver restarts the engine when armed`() {
    AppPrefs.setArmed(context, true)

    BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

    val started = shadowOf(context as android.app.Application).nextStartedService
    assertTrue(
      "the keep-alive service should be started after boot",
      started?.component?.className?.contains("KeepAliveForegroundService") == true
    )
  }

  @Test
  fun `boot receiver ignores unrelated broadcasts`() {
    AppPrefs.setArmed(context, true)

    BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_ON))

    assertEquals(
      null,
      shadowOf(context as android.app.Application).nextStartedService
    )
  }

  @Test
  fun `quick action shortcut ids are the ones the launcher xml declares`() {
    // The shortcut XML targets these action strings by name; a rename in code without a matching
    // rename in res/xml/shortcuts.xml would silently produce dead shortcuts.
    assertEquals("com.example.action.QUICK_SHOW_POPUP", QuickActionActivity.ACTION_SHOW_POPUP)
    assertEquals("com.example.action.QUICK_ARM", QuickActionActivity.ACTION_ARM)
    assertEquals("com.example.action.QUICK_LAST_CALL", QuickActionActivity.ACTION_LAST_CALL)
  }
}
