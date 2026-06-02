package org.happycode.karoo.forumslader.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should display disconnected status when not connected`() {
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(connected = false, metrics = emptyMap())
            }
        }

        composeTestRule.onNodeWithText("Disconnected").assertIsDisplayed()
    }

    @Test
    fun `should display connected status when connected`() {
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(connected = true, metrics = emptyMap())
            }
        }

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `should display metric value when provided`() {
        val metrics = mapOf(DataFieldId.BATTERY_LEVEL to 85.0)
        
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(connected = true, metrics = metrics)
            }
        }

        // The exact text depends on localization, assuming "85.0" is shown
        // Since we used String.format(Locale.getDefault(), "%.1f", it), it might be "85.0" or "85,0"
        // Let's use a regex or check for the numeric part
        composeTestRule.onNodeWithText("85.0", substring = true).assertIsDisplayed()
    }
}
