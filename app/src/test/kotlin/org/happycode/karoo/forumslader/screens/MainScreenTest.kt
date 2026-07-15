package org.happycode.karoo.forumslader.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
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
                MainScreenContent(connected = false, sensorState = StreamState.Idle, metrics = emptyMap(), userProfile = null)
            }
        }

        composeTestRule.onNodeWithText("Disconnected").assertIsDisplayed()
    }

    @Test
    fun `should display connected status when connected`() {
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = emptyMap(),
                    userProfile = null
                )
            }
        }

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `should display metric value when provided`() {
        val metrics = mapOf(DataFieldId.BATTERY_LEVEL to 85.0)
        
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = null
                )
            }
        }

        composeTestRule.onNodeWithText("85%", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should display speed in mph when system unit is Imperial`() {
        val metrics = mapOf(DataFieldId.SPEED to 10.0) // 10 m/s = 36 km/h = 22.37 mph
        val imperialProfile = UserProfile(
            weight = 70f,
            preferredUnit = PreferredUnit(
                distance = PreferredUnit.UnitType.IMPERIAL,
                elevation = PreferredUnit.UnitType.IMPERIAL,
                temperature = PreferredUnit.UnitType.IMPERIAL,
                weight = PreferredUnit.UnitType.IMPERIAL
            ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList()
        )
        
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = imperialProfile
                )
            }
        }

        composeTestRule.onNodeWithText("22.4 mph", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should display speed in kmh when system unit is Metric`() {
        val metrics = mapOf(DataFieldId.SPEED to 10.0) // 10 m/s = 36 km/h
        val metricProfile = UserProfile(
            weight = 70f,
            preferredUnit = PreferredUnit(
                distance = PreferredUnit.UnitType.METRIC,
                elevation = PreferredUnit.UnitType.METRIC,
                temperature = PreferredUnit.UnitType.METRIC,
                weight = PreferredUnit.UnitType.METRIC
            ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList()
        )
        
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = metricProfile
                )
            }
        }

        composeTestRule.onNodeWithText("36.0 km/h", substring = true).assertIsDisplayed()
    }
}
