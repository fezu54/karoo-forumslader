package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatusCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should display connected status`() {
        composeTestRule.setContent {
            AppTheme {
                StatusCard(connected = true, sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")))
            }
        }
        composeTestRule.onNodeWithContentDescription("Connected").assertIsDisplayed()
    }

    @Test
    fun `should display disconnected status`() {
        composeTestRule.setContent {
            AppTheme {
                StatusCard(connected = false, sensorState = StreamState.Idle)
            }
        }
        composeTestRule.onNodeWithContentDescription("Disconnected").assertIsDisplayed()
    }

    @Test
    fun `should display searching status`() {
        composeTestRule.setContent {
            AppTheme {
                StatusCard(connected = true, sensorState = StreamState.Searching)
            }
        }
        composeTestRule.onNodeWithContentDescription("Searching").assertIsDisplayed()
    }
}
