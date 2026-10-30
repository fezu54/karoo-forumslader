package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
            DataFieldId.BATTERY_VOLTAGE to 48.0,
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

        composeTestRule.onNodeWithText("48.0 V", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("18.0 km/h", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display power and dynamo metrics on PowerMetricsCard`() {
        val metrics = mapOf(
            DataFieldId.DYNAMO_POWER to 12.5,
            DataFieldId.BATTERY_VOLTAGE to 48.0,
            DataFieldId.BATTERY_CURRENT to 1500.0,
            DataFieldId.TEMPERATURE to 23.0
        )

        composeTestRule.setContent {
            AppTheme {
                PowerMetricsCard(
                    metrics = metrics,
                    userProfile = null
                )
            }
        }

        composeTestRule.onNodeWithText("Power & Dynamo").assertIsDisplayed()
        composeTestRule.onNodeWithText("12.5 W").assertIsDisplayed()
        composeTestRule.onNodeWithText("48.0 V").assertIsDisplayed()
        composeTestRule.onNodeWithText("1500 mA").assertIsDisplayed()
        composeTestRule.onNodeWithText("23.0 °C").assertIsDisplayed()
    }

    @Test
    fun `should display distance and energy metrics on DistanceMetricsCard`() {
        val metrics = mapOf(
            DataFieldId.SPEED to 10.0,
            DataFieldId.TRIP_DISTANCE to 15000.0,
            DataFieldId.TRIP_ENERGY to 45.0
        )

        composeTestRule.setContent {
            AppTheme {
                DistanceMetricsCard(
                    metrics = metrics,
                    userProfile = null,
                    connected = true,
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Distance & Energy").assertIsDisplayed()
        composeTestRule.onNodeWithText("36.0 km/h").assertIsDisplayed()
        composeTestRule.onNodeWithText("15.00 km").assertIsDisplayed()
        composeTestRule.onNodeWithText("45.0 Wh").assertIsDisplayed()
    }
}
