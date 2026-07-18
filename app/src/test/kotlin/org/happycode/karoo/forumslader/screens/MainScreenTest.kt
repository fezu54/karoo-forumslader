package org.happycode.karoo.forumslader.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import androidx.test.core.app.ApplicationProvider
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `should display disconnected status when not connected`() {
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(connected = false, sensorState = StreamState.Idle, metrics = emptyMap(), userProfile = null, wheelsize = config.wheelsize, poles = config.poles, versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Disconnected").assertIsDisplayed()
    }

    @Test
    fun `should display connected status when connected`() {
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Connected").assertIsDisplayed()
    }

    @Test
    fun `should display metric value when provided`() {
        val metrics = mapOf(DataFieldId.BATTERY_LEVEL to 85.0)
        
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("85%", substring = true).performScrollTo().assertIsDisplayed()
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
        
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = imperialProfile,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("22.4 mph", substring = true).performScrollTo().assertIsDisplayed()
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
        
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = metricProfile,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("36.0 km/h", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display searching status when searching`() {
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Searching,
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Searching").assertIsDisplayed()
    }

    @Test
    fun `should display not available status when not available`() {
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.NotAvailable,
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Not Available").assertIsDisplayed()
    }

    @Test
    fun `should display trip distance in miles when system unit is Imperial`() {
        val metrics = mapOf(DataFieldId.TRIP_DISTANCE to 1609.34) // 1 mile = 1609.34 meters
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
        
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = imperialProfile,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1.00 mi", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display trip distance in km when system unit is Metric`() {
        val metrics = mapOf(DataFieldId.TRIP_DISTANCE to 2500.0) // 2.5 km
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
        
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = metricProfile,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("2.50 km", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display consumer current`() {
        val metrics = mapOf(DataFieldId.CONSUMER_CURRENT to 1.25)
        
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1.3 A", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery voltage`() {
        val metrics = mapOf(DataFieldId.BATTERY_VOLTAGE to 48.2)

        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("48.2 V", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery current`() {
        val metrics = mapOf(DataFieldId.BATTERY_CURRENT to -1.5)

        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("-1.5 A", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display temperature in C when metric`() {
        val metrics = mapOf(DataFieldId.TEMPERATURE to 22.5)
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

        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = metricProfile,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("22.5 °C", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display temperature in F when imperial`() {
        val metrics = mapOf(DataFieldId.TEMPERATURE to 22.5)
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

        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = metrics,
                    userProfile = imperialProfile,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        // (22.5 * 9/5) + 32 = 72.5
        composeTestRule.onNodeWithText("72.5 °F", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display configuration values`() {
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        config.wheelsize = 2150
        config.poles = 28
        
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key, speedMultiplier = config.speedMultiplier, onSpeedMultiplierChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Configuration").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2150 mm", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("28").performScrollTo().assertIsDisplayed()
    }
}
