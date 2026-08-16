package org.happycode.karoo.forumslader.ui.main.components

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
class MetricsListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should display all provided metrics`() {
        val metrics = mapOf(
            DataFieldId.BATTERY_LEVEL to 80.0,
            DataFieldId.SPEED to 5.0
        )

        composeTestRule.setContent {
            AppTheme {
                MetricsList(
                    metrics = metrics,
                    userProfile = null,
                    connected = true,
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onNodeWithText("80%", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("18.0 km/h", substring = true).assertIsDisplayed()
    }
}
