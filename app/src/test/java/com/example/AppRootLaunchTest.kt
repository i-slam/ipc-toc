package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.AppRoot
import com.example.ui.AppRoute
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Composes and measures what MainActivity actually shows. A layout that throws during measure -
 * nested scrolling in an unbounded parent, for instance - kills the app on launch, and that is
 * invisible to the compiler, so it is pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// sdk 30 as well as 36: the first launch crash only reproduced below API 33.
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [30, 36])
class AppRootLaunchTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun diagnostics_route_composes_and_measures() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        AppRoot(initialRoute = AppRoute.DIAGNOSTICS)
      }
    }

    composeTestRule.onRoot().assertExists()
  }

  @Test
  fun call_log_route_composes_and_measures() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        AppRoot(initialRoute = AppRoute.CALL_LOG)
      }
    }

    composeTestRule.onRoot().assertExists()
  }

  @Test
  fun last_call_route_composes_and_measures() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        AppRoot(initialRoute = AppRoute.LAST_CALL)
      }
    }

    composeTestRule.onRoot().assertExists()
  }
}
