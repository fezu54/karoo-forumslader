package org.happycode.karoo.forumslader.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Assert.assertEquals
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
                MainScreenContent(
                    connected = false,
                    sensorState = StreamState.Idle,
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onNodeWithText("2.50 km", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display consumer current`() {
        val metrics = mapOf(DataFieldId.CONSUMER_CURRENT to 1250.0)

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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1250 mA", substring = true).performScrollTo().assertIsDisplayed()
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onNodeWithText("48.2 V", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery current`() {
        val metrics = mapOf(DataFieldId.BATTERY_CURRENT to -1500.0)

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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onNodeWithText("-1500 mA", substring = true).performScrollTo().assertIsDisplayed()
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("Configuration").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2150 mm", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("28").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display warning banner when there are missing streams`() {
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Searching,
                    hasMissingStreams = true,
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = 2200,
                    poles = 14,
                    versionKey = "v6",
                    speedMultiplier = 1.0f,
                    lockedMacAddress = null,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        // Verify the warning text is displayed using substring matching because the full string is long
        composeTestRule.onNodeWithText("Some data fields are unsupported", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should invoke onForgetDevice when Forget is clicked`() {
        val config = ForumsladerConfig(ApplicationProvider.getApplicationContext())
        var forgetClicked = false
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = true,
                    sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
                    metrics = emptyMap(),
                    userProfile = null,
                    wheelsize = config.wheelsize,
                    poles = config.poles,
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = "00:11:22:33:44:55",
                    onSpeedMultiplierChange = {},
                    onForgetDevice = { forgetClicked = true },
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = {}
                )
            }
        }

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("Forget").performScrollTo().performClick()
        assert(forgetClicked)
    }

    @Test
    fun `should send reset day distance command when reset button is clicked and confirmed`() = runTest {
        val metrics = mapOf(DataFieldId.DAY_DISTANCE to 1000.0)

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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = { org.happycode.karoo.forumslader.domain.CommandBus.sendCommand(
                        $$"$FLT,7*41\r\n"
                    ) },
                    onResetTourDistance = {}
                )
            }
        }

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            org.happycode.karoo.forumslader.domain.CommandBus.commands.first()
        }

        composeTestRule.onNodeWithContentDescription("Reset Day Distance", substring = true).performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals($$"$FLT,7*41\r\n", deferred.await())
    }

    @Test
    fun `should send reset tour distance command when reset button is clicked and confirmed`() = runTest {
        val metrics = mapOf(DataFieldId.TOUR_DISTANCE to 2000.0)

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
                    versionKey = config.version.key,
                    speedMultiplier = config.speedMultiplier,
                    lockedMacAddress = config.lockedMacAddress,
                    onSpeedMultiplierChange = {},
                    onForgetDevice = {},
                    batteryLowThreshold = 20,
                    highTempThreshold = 50f,
                    onBatteryLowThresholdChange = {},
                    onHighTempThresholdChange = {},
                    onResetDayDistance = {},
                    onResetTourDistance = { org.happycode.karoo.forumslader.domain.CommandBus.sendCommand(
                        $$"$FLT,6*42\r\n"
                    ) }
                )
            }
        }

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            org.happycode.karoo.forumslader.domain.CommandBus.commands.first()
        }

        composeTestRule.onNodeWithContentDescription("Reset Tour Distance", substring = true).performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals($$"$FLT,6*42\r\n", deferred.await())
    }
}
