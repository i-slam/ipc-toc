package com.example.data

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Process.myProcessName()` only exists from API 33. Reading it behind an API 28 guard threw
 * NoSuchMethodError and killed the app on launch on every device below Android 13, so each
 * branch of the resolver is exercised here across the SDK range the app supports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 30, 33, 36])
class ProcessInfoTest {

  @Test
  fun `process name resolves on every supported api level`() {
    val name = ProcessInfo.currentProcessName()
    assertTrue("process name should not be blank", name.isNotBlank())
  }

  @Test
  fun `process label carries the pid`() {
    val label = ProcessInfo.currentProcessLabel()
    assertTrue("label '$label' should contain a pid in brackets", Regex("""\(\d+\)$""").containsMatchIn(label))
  }
}
