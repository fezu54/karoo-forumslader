package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertsConfigCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should display alert thresholds correctly`() {
        // given / when
        composeTestRule.setContent {
            AppTheme {
                AlertsConfigCard(
                    batteryLowThreshold = 25,
                    highTempThreshold = 55f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {}
                )
            }
        }

        // then
        composeTestRule.onNodeWithText("Alerts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Low Battery Threshold", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("25 %").assertIsDisplayed()
        composeTestRule.onNodeWithText("High Temperature Threshold", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("55 °C").assertIsDisplayed()
    }

    @Test
    fun `should decrease battery threshold when decrease button is clicked`() {
        // given
        var updatedThreshold = 0
        composeTestRule.setContent {
            AppTheme {
                AlertsConfigCard(
                    batteryLowThreshold = 25,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = { updatedThreshold = it },
                    onHighTempThresholdChange = {}
                )
            }
        }

        // when
        composeTestRule.onAllNodesWithContentDescription("Decrease").onFirst().performClick()

        // then
        updatedThreshold shouldBe 24
    }

    @Test
    fun `should increase battery threshold when increase button is clicked`() {
        // given
        var updatedThreshold = 0
        composeTestRule.setContent {
            AppTheme {
                AlertsConfigCard(
                    batteryLowThreshold = 25,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = { updatedThreshold = it },
                    onHighTempThresholdChange = {}
                )
            }
        }

        // when
        composeTestRule.onAllNodesWithContentDescription("Increase").onFirst().performClick()

        // then
        updatedThreshold shouldBe 26
    }

    @Test
    fun `should increase temperature threshold when increase button is clicked`() {
        // given
        var updatedTemp = 0f
        composeTestRule.setContent {
            AppTheme {
                AlertsConfigCard(
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = { updatedTemp = it }
                )
            }
        }

        // when (second stepper on the card is temperature)
        val increaseButtons = composeTestRule.onAllNodesWithContentDescription("Increase")
        increaseButtons[1].performClick()

        // then
        updatedTemp shouldBe 51f
    }
}
