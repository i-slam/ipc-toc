package com.example.ui.calllog

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.telephony.CallDirection
import com.example.telephony.CallRecord
import com.example.telephony.WhatsAppLauncher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list with its data supplied directly, so the states that matter - a tab with nothing in it,
 * a locked call log, a number WhatsApp cannot take - can be composed without a device, a provider
 * or a SIM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A fixed phone-sized screen: the list is lazy, so a row scrolled off a tiny default display
// would never be composed and the assertions would fail for the wrong reason.
@Config(qualifiers = "w411dp-h891dp", sdk = [30, 36])
class CallLogContentTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val noActions = CallLogActions({ _, _ -> }, {}, {}, {})

    private fun setContent(
        rows: List<CallRow>,
        hasPermission: Boolean = true,
        whatsAppInstalled: Boolean = true
    ) {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var tab by remember { mutableStateOf(CallLogTab.ALL) }
                CallLogContent(
                    rows = rows,
                    selectedTab = tab,
                    onSelectTab = { tab = it },
                    isLoading = false,
                    hasPermission = hasPermission,
                    whatsAppInstalled = whatsAppInstalled,
                    onGrantPermission = {},
                    actions = noActions
                )
            }
        }
    }

    @Test
    fun `all calls show under the All tab`() {
        setContent(sampleRows())

        composeTestRule.onNodeWithText("Ada Missed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grace Incoming").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alan Outgoing").assertIsDisplayed()
    }

    @Test
    fun `the missed tab hides everything the user answered`() {
        setContent(sampleRows())

        composeTestRule.onNodeWithTag(tabTestTag(CallLogTab.MISSED)).performClick()

        composeTestRule.onNodeWithText("Ada Missed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grace Incoming").assertDoesNotExist()
        composeTestRule.onNodeWithText("Alan Outgoing").assertDoesNotExist()
    }

    @Test
    fun `a tab with nothing in it says so instead of going blank`() {
        setContent(listOf(row(1, "Alan Outgoing", "+2348031234571", CallDirection.OUTGOING)))

        composeTestRule.onNodeWithTag(tabTestTag(CallLogTab.MISSED)).performClick()

        composeTestRule.onNodeWithText("Nothing under Missed").assertIsDisplayed()
    }

    @Test
    fun `a number with a country code gets a working WhatsApp button`() {
        setContent(sampleRows())

        composeTestRule.onNodeWithContentDescription("WhatsApp Ada Missed").assertIsEnabled()
    }

    /** The case that used to open WhatsApp only for it to say the number is invalid. */
    @Test
    fun `a local number gets a disabled button that explains itself`() {
        setContent(listOf(row(9, "Local caller", "07700 900123", CallDirection.MISSED)))

        composeTestRule
            .onNodeWithContentDescription(
                "WhatsApp unavailable: 07700 900123 has no country code"
            )
            .assertIsNotEnabled()
    }

    @Test
    fun `without the permission the list offers the grant instead of rows`() {
        setContent(sampleRows(), hasPermission = false)

        composeTestRule.onNodeWithText("Grant call log access").assertIsDisplayed()
    }

    @Test
    fun `a missing WhatsApp is called out once rather than per row`() {
        setContent(sampleRows(), whatsAppInstalled = false)

        composeTestRule
            .onNodeWithText(
                "WhatsApp is not installed, so the green buttons will fall back to a browser link."
            )
            .assertIsDisplayed()
    }

    @Test
    fun `an empty log says so`() {
        setContent(emptyList())

        composeTestRule.onNodeWithText("No calls in the log yet").assertIsDisplayed()
    }
}

private fun sampleRows(): List<CallRow> = listOf(
    row(1, "Ada Missed", "+2348031234567", CallDirection.MISSED),
    row(2, "Grace Incoming", "+2348031234569", CallDirection.INCOMING),
    row(3, "Alan Outgoing", "+2348031234571", CallDirection.OUTGOING)
)

private fun row(id: Long, name: String, number: String, direction: CallDirection): CallRow =
    CallRow(
        record = CallRecord(
            id = id,
            number = number,
            cachedName = name,
            direction = direction,
            timestamp = 1_700_000_000_000L,
            durationSeconds = 42,
            phoneAccountId = null,
            geocodedLocation = null,
            isNew = false,
            viaNumber = null
        ),
        waDigits = WhatsAppLauncher.normalise(number, null)
    )
