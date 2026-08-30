package com.example.bubble

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How the fan and the panel share one small screen.
 *
 * The bug this pins: opening a panel used to leave the fan spread out underneath it, so the two
 * competed for a phone-width overlay. The fan is a launcher - once it has been used it belongs
 * out of the way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp", sdk = [30, 36])
class BubblePanelTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun setContent() {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BubblePanel(onAction = { _, _ -> }, onDragVertically = {})
            }
        }
    }

    @Test
    fun `closed, the bubble is one button and not six`() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Open the menu").assertIsDisplayed()
        // The blobs sit at zero offset stacked under the toggle when closed. Composing them there
        // would leave five invisible buttons piled on the bubble for a screen reader to find.
        composeTestRule.onNodeWithContentDescription("All calls").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Send from inventory").assertDoesNotExist()
    }

    @Test
    fun `the toggle fans the actions out`() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Open the menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("All calls").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send from inventory").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Last call and WhatsApp").assertIsDisplayed()
    }

    @Test
    fun `opening the last call panel puts the fan away`() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Open the menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Last call and WhatsApp").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("WhatsApp this number").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("All calls").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Send from inventory").assertDoesNotExist()
    }

    @Test
    fun `with a panel up the toggle closes it rather than reopening the fan`() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Open the menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Last call and WhatsApp").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Open the menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("WhatsApp this number").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("All calls").assertDoesNotExist()
    }

    @Test
    fun `the close button on the panel closes it`() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Open the menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Last call and WhatsApp").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("WhatsApp this number").assertDoesNotExist()
    }
}
