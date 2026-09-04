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
import org.happycode.karoo.forumslader.domain.BatteryEstimate
import org.happycode.karoo.forumslader.domain.CommandBus
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

    private val config by lazy { ForumsladerConfig(ApplicationProvider.getApplicationContext()) }

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    private fun showMainScreen(
        connected: Boolean = true,
        configLoaded: Boolean = true,
        sensorState: StreamState = StreamState.Idle,
        metrics: Map<String, Double> = emptyMap(),
        userProfile: UserProfile? = null,
        estimate: BatteryEstimate? = null,
        wheelsize: Int = config.wheelsize,
        poles: Int = config.poles,
        versionKey: String = config.version.key,
        speedMultiplier: Float = config.speedMultiplier,
        lockedMacAddress: String? = config.lockedMacAddress,
        onSpeedMultiplierChange: (Float) -> Unit = {},
        onForgetDevice: () -> Unit = {},
        batteryLowThreshold: Int = 20,
        highTempThreshold: Float = 50f,
        onBatteryLowThresholdChange: (Int) -> Unit = {},
        onHighTempThresholdChange: (Float) -> Unit = {},
        onResetDayDistance: () -> Unit = {},
        onResetTourDistance: () -> Unit = {},
        hasMissingStreams: Boolean = false,
    ) {
        composeTestRule.setContent {
            AppTheme {
                MainScreenContent(
                    connected = connected,
                    configLoaded = configLoaded,
                    sensorState = sensorState,
                    metrics = metrics,
                    userProfile = userProfile,
                    estimate = estimate,
                    wheelsize = wheelsize,
                    poles = poles,
                    versionKey = versionKey,
                    speedMultiplier = speedMultiplier,
                    lockedMacAddress = lockedMacAddress,
                    onSpeedMultiplierChange = onSpeedMultiplierChange,
                    onForgetDevice = onForgetDevice,
                    batteryLowThreshold = batteryLowThreshold,
                    highTempThreshold = highTempThreshold,
                    onBatteryLowThresholdChange = onBatteryLowThresholdChange,
                    onHighTempThresholdChange = onHighTempThresholdChange,
                    onResetDayDistance = onResetDayDistance,
                    onResetTourDistance = onResetTourDistance,
                    hasMissingStreams = hasMissingStreams,
                    onSaveToUsb = {},
                    onToggleServer = {},
                    onClearLogs = {}
                )
            }
        }
    }

    @Test
    fun `should display disconnected status when not connected`() {
        // when
        showMainScreen(connected = false)

        // then
        composeTestRule.onNodeWithContentDescription("Disconnected").assertIsDisplayed()
    }

    @Test
    fun `should display battery estimate when available`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 85,
            avgDischargeRatePctPerKm = 0.5f,
            estimatedRangeKm = 170.0f,
            routeRemainingKm = 50.0f,
            isSufficientForRoute = true
        )

        // when
        showMainScreen(estimate = estimate)

        // then
        composeTestRule.onNodeWithText("0.5%/km").assertIsDisplayed()
        composeTestRule.onNodeWithText("~170 km remaining").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sufficient for route", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should display insufficient text when battery estimate is not sufficient for route`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 10,
            avgDischargeRatePctPerKm = 1.0f,
            estimatedRangeKm = 10.0f,
            routeRemainingKm = 50.0f,
            isSufficientForRoute = false
        )

        // when
        showMainScreen(estimate = estimate)

        // then
        composeTestRule.onNodeWithText("Battery may run out early", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should display calculating text when estimate is null`() {
        // when
        showMainScreen(estimate = null)

        // then
        composeTestRule.onNodeWithText("Calculating…").assertIsDisplayed()
    }

    @Test
    fun `should display charging text when battery estimate state is CHARGING`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 50,
            avgDischargeRatePctPerKm = 0f,
            estimatedRangeKm = null,
            routeRemainingKm = null,
            isSufficientForRoute = null,
            chargeState = org.happycode.karoo.forumslader.domain.ChargeState.CHARGING
        )

        // when
        showMainScreen(estimate = estimate)

        // then
        composeTestRule.onNodeWithText("Charging — range unlimited").assertIsDisplayed()
    }

    @Test
    fun `should display standby text when battery estimate state is STANDBY`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 50,
            avgDischargeRatePctPerKm = 0f,
            estimatedRangeKm = null,
            routeRemainingKm = null,
            isSufficientForRoute = null,
            chargeState = org.happycode.karoo.forumslader.domain.ChargeState.STANDBY
        )

        // when
        showMainScreen(estimate = estimate)

        // then
        composeTestRule.onNodeWithText("Standby").assertIsDisplayed()
    }

    @Test
    fun `should display not enough data when discharging but no range available`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 50,
            avgDischargeRatePctPerKm = 0f,
            estimatedRangeKm = null,
            routeRemainingKm = null,
            isSufficientForRoute = null,
            chargeState = org.happycode.karoo.forumslader.domain.ChargeState.DISCHARGING
        )

        // when
        showMainScreen(estimate = estimate)

        // then
        composeTestRule.onNodeWithText("Not enough data…").assertIsDisplayed()
    }

    @Test
    fun `should display connected status when connected`() {
        // when
        showMainScreen(sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")))

        // then
        composeTestRule.onNodeWithContentDescription("Connected").assertIsDisplayed()
    }

    @Test
    fun `should display metric value when provided`() {
        // given
        val metrics = mapOf(DataFieldId.BATTERY_LEVEL to 85.0)

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics
        )

        // then
        composeTestRule.onNodeWithText("85%", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display speed in mph when system unit is Imperial`() {
        // given
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = imperialProfile
        )

        // then
        composeTestRule.onNodeWithText("22.4 mph", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display speed in kmh when system unit is Metric`() {
        // given
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = metricProfile
        )

        // then
        composeTestRule.onNodeWithText("36.0 km/h", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display searching status when searching`() {
        // when
        showMainScreen(sensorState = StreamState.Searching)

        // then
        composeTestRule.onNodeWithContentDescription("Searching").assertIsDisplayed()
    }

    @Test
    fun `should display not available status when not available`() {
        // when
        showMainScreen(sensorState = StreamState.NotAvailable)

        // then
        composeTestRule.onNodeWithContentDescription("Not Available").assertIsDisplayed()
    }

    @Test
    fun `should display trip distance in miles when system unit is Imperial`() {
        // given
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = imperialProfile
        )

        // then
        composeTestRule.onNodeWithText("1.00 mi", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display trip distance in km when system unit is Metric`() {
        // given
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = metricProfile
        )

        // then
        composeTestRule.onNodeWithText("2.50 km", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery range in km when system unit is Metric`() {
        // given
        val metrics = mapOf(DataFieldId.BATTERY_RANGE to 50000.0) // 50 km
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = metricProfile
        )

        // then
        composeTestRule.onNodeWithText("50.00 km", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery range in miles when system unit is Imperial`() {
        // given
        val metrics = mapOf(DataFieldId.BATTERY_RANGE to 80467.2) // 50 miles
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = imperialProfile
        )

        // then
        composeTestRule.onNodeWithText("50.00 mi", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display consumer current`() {
        // given
        val metrics = mapOf(DataFieldId.CONSUMER_CURRENT to 1250.0)

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics
        )

        // then
        composeTestRule.onNodeWithText("1250 mA", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery voltage`() {
        // given
        val metrics = mapOf(DataFieldId.BATTERY_VOLTAGE to 48.2)

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics
        )

        // then
        composeTestRule.onNodeWithText("48.2 V", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display battery current`() {
        // given
        val metrics = mapOf(DataFieldId.BATTERY_CURRENT to -1500.0)

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics
        )

        // then
        composeTestRule.onNodeWithText("-1500 mA", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display temperature in C when metric`() {
        // given
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = metricProfile
        )

        // then
        composeTestRule.onNodeWithText("22.5 °C", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display temperature in F when imperial`() {
        // given
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

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            userProfile = imperialProfile
        )

        // then
        // (22.5 * 9/5) + 32 = 72.5
        composeTestRule.onNodeWithText("72.5 °F", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display configuration values`() {
        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            wheelsize = 2150,
            poles = 28
        )

        // then
        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("Configuration").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2150 mm", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("28").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should display warning banner when there are missing streams`() {
        // when
        showMainScreen(
            sensorState = StreamState.Searching,
            hasMissingStreams = true
        )

        // then
        // Verify the warning text is displayed using substring matching because the full string is long
        composeTestRule.onNodeWithText("Some data fields are unsupported", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should invoke onForgetDevice when Forget is clicked`() {
        // given
        var forgetClicked = false

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            lockedMacAddress = "00:11:22:33:44:55",
            onForgetDevice = { forgetClicked = true }
        )

        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("Forget").performScrollTo().performClick()

        // then
        assert(forgetClicked)
    }

    @Test
    fun `should send reset day distance command when reset button is clicked and confirmed`() = runTest {
        // given
        val metrics = mapOf(DataFieldId.DAY_DISTANCE to 1000.0)

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            onResetDayDistance = { CommandBus.sendCommand($$"$FLT,7*45\n") }
        )

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            CommandBus.commands.first()
        }

        composeTestRule.onNodeWithContentDescription("Reset Day Distance", substring = true).performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        // then
        assertEquals($$"$FLT,7*45\n", deferred.await())
    }

    @Test
    fun `should send reset tour distance command when reset button is clicked and confirmed`() = runTest {
        // given
        val metrics = mapOf(DataFieldId.TOUR_DISTANCE to 2000.0)

        // when
        showMainScreen(
            sensorState = StreamState.Streaming(DataPoint("", emptyMap(), "")),
            metrics = metrics,
            onResetTourDistance = { CommandBus.sendCommand($$"$FLT,6*44\n") }
        )

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            CommandBus.commands.first()
        }

        composeTestRule.onNodeWithContentDescription("Reset Tour Distance", substring = true).performScrollTo().performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        // then
        assertEquals($$"$FLT,6*44\n", deferred.await())
    }

    @Test
    fun `should display battery low threshold slider`() {
        // when
        showMainScreen()

        composeTestRule.onRoot().performTouchInput { swipeLeft() }

        // then
        // The slider for battery low threshold is in AlertsConfigCard
        composeTestRule.onNodeWithText("Low Battery Threshold", substring = true).assertIsDisplayed()
    }

    @Test
    fun `should display diagnostics card on config page`() {
        // when
        showMainScreen()

        composeTestRule.onRoot().performTouchInput { swipeLeft() }

        // then
        composeTestRule.onNodeWithText("Diagnostics & Logs").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Save to USB Storage").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `should render MainScreenPreview without errors`() {
        composeTestRule.setContent {
            MainScreenPreview()
        }
        composeTestRule.onNodeWithText("85%", substring = true).performScrollTo().assertIsDisplayed()
    }
}
