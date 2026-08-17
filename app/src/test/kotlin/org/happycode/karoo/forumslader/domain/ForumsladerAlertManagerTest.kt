package org.happycode.karoo.forumslader.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ForumsladerAlertManagerTest {

    private val alertManager = ForumsladerAlertManager(
        listOf(
            BatteryLowRule({ 20 }),
            HighTemperatureRule({ 50f }),
            StatusBitmaskRule(0x8, ForumsladerAlert.ShortCircuit),
            StatusBitmaskRule(0x800000, ForumsladerAlert.SystemInterrupt)
        )
    )

    private fun mockMetrics(
        batteryLevelPercentage: Int? = 50,
        statusMask: Int = 0,
        temperatureCelsius: Float = 20f
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = 12f,
            batteryCurrent = 0f,
            consumerCurrent = 0f,
            batteryLevelPercentage = batteryLevelPercentage,
            chargeState = ChargeState.STANDBY,
            dynamoPowerWatts = 0f,
            statusMask = statusMask
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = 0f,
            speedMetersPerSecond = 0f,
            generatorGear = 0
        ),
        environment = ForumsladerMetrics.Environment(temperatureCelsius, altitudeMeters = 0f),
        energy = ForumsladerMetrics.Energy(tripWattHours = 0.0, tourWattHours = 0.0),
        distance = ForumsladerMetrics.Distance(
            tripMeters = 0.0,
            dayMeters = 0.0,
            tourMeters = 0.0,
            odometerMeters = 0.0
        )
    )

    @Test
    fun `should fire low battery alert when below threshold`() {
        // given
        val metrics = mockMetrics(batteryLevelPercentage = 15)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        assertEquals(listOf(ForumsladerAlert.BatteryLow(15)), alerts)
    }

    @Test
    fun `should not fire low battery alert repeatedly unless re-armed`() {
        // given
        val metrics1 = mockMetrics(batteryLevelPercentage = 15)
        alertManager.evaluate(metrics1, currentTime = Duration.ZERO)

        // when
        val metrics2 = mockMetrics(batteryLevelPercentage = 14)
        val alerts = alertManager.evaluate(metrics2, currentTime = 60.seconds)

        // then
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `should fire low battery alert again after re-arming`() {
        // given
        val metrics1 = mockMetrics(batteryLevelPercentage = 15)
        alertManager.evaluate(metrics1, currentTime = Duration.ZERO)

        val metrics2 = mockMetrics(batteryLevelPercentage = 28)
        alertManager.evaluate(metrics2, currentTime = 60.seconds)

        // when
        val metrics3 = mockMetrics(batteryLevelPercentage = 15)
        val alerts = alertManager.evaluate(metrics3, currentTime = 120.seconds)

        // then
        assertEquals(listOf(ForumsladerAlert.BatteryLow(15)), alerts)
    }

    @Test
    fun `should fire short circuit alert on status bit and mute for 60 seconds`() {
        // given
        val metrics = mockMetrics(statusMask = 0x8)

        // when
        val alerts1 = alertManager.evaluate(metrics, currentTime = Duration.ZERO)
        val alerts2 = alertManager.evaluate(metrics, currentTime = 30.seconds)
        val alerts3 = alertManager.evaluate(metrics, currentTime = 60.seconds)

        // then
        assertAll(
            { assertEquals(listOf(ForumsladerAlert.ShortCircuit), alerts1) },
            { assertTrue(alerts2.isEmpty()) },
            { assertEquals(listOf(ForumsladerAlert.ShortCircuit), alerts3) }
        )
    }

    @Test
    fun `should fire system interrupt alert on status bit`() {
        // given
        val metrics = mockMetrics(statusMask = 0x800000)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        assertEquals(listOf(ForumsladerAlert.SystemInterrupt), alerts)
    }

    @Test
    fun `should fire high temperature alert above threshold`() {
        // given
        val metrics = mockMetrics(temperatureCelsius = 55f)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        assertEquals(listOf(ForumsladerAlert.HighTemperature(55f)), alerts)
    }

    @Test
    fun `should handle null battery level in BatteryLowRule`() {
        // given
        val metrics = mockMetrics(batteryLevelPercentage = null)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `should respect muting across all rules`() {
        // given
        val localAlertManager = ForumsladerAlertManager(
            listOf(
                BatteryLowRule({ 20 }),
                HighTemperatureRule({ 50f }),
                StatusBitmaskRule(0x8, ForumsladerAlert.ShortCircuit)
            )
        )
        val metrics = mockMetrics(batteryLevelPercentage = 10, temperatureCelsius = 60f, statusMask = 0x8)
        
        // when
        val alerts1 = localAlertManager.evaluate(metrics, currentTime = Duration.ZERO)
        val alerts2 = localAlertManager.evaluate(metrics, currentTime = 1.seconds)
        val alerts3 = localAlertManager.evaluate(metrics, currentTime = 61.seconds)
        
        // then
        assertAll(
            { assertEquals(3, alerts1.size) },
            { assertTrue(alerts2.isEmpty()) },
            { assertEquals(2, alerts3.size) }
        )
    }

    @Test
    fun `should have correct alert titles and details`() {
        assertAll(
            { assertEquals("Forumslader", ForumsladerAlert.ShortCircuit.title) },
            { assertEquals("Short Circuit!", ForumsladerAlert.ShortCircuit.detail) },
            { assertEquals("Battery Low (15%)", ForumsladerAlert.BatteryLow(15).detail) },
            { assertEquals("High Temperature (55°C)", ForumsladerAlert.HighTemperature(55.5f).detail) },
            { assertEquals("System Interrupt!", ForumsladerAlert.SystemInterrupt.detail) }
        )
    }

    @Test
    fun `should handle empty rules list`() {
        // given
        val manager = ForumsladerAlertManager(emptyList())

        // when
        val alerts = manager.evaluate(mockMetrics())

        // then
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `should use system time by default`() {
        // when
        val alerts = alertManager.evaluate(mockMetrics())

        // then
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `BatteryLowRule should re-arm when battery goes above threshold plus offset`() {
        // given
        val rule = BatteryLowRule({ 20 })
        val metricsLow = mockMetrics(batteryLevelPercentage = 15)
        val metricsHigh = mockMetrics(batteryLevelPercentage = 29) // 20 + 8 + 1
        
        // when & then
        assertAll(
            { assertEquals(ForumsladerAlert.BatteryLow(15), rule.evaluate(metricsLow, Duration.ZERO)) },
            { assertNull(rule.evaluate(metricsLow, 10.seconds)) },
            { assertNull(rule.evaluate(metricsHigh, 20.seconds)) },
            { assertEquals(ForumsladerAlert.BatteryLow(15), rule.evaluate(metricsLow, 120.seconds)) }
        )
    }

    @Test
    fun `BatteryLowRule should respect changing threshold`() {
        // given
        var threshold = 20
        val rule = BatteryLowRule({ threshold })
        val metrics = mockMetrics(batteryLevelPercentage = 25)
        
        // when & then
        assertNull(rule.evaluate(metrics, Duration.ZERO))
        
        threshold = 30
        assertEquals(ForumsladerAlert.BatteryLow(25), rule.evaluate(metrics, 10.seconds))
    }

    @Test
    fun `HighTemperatureRule should respect changing threshold`() {
        // given
        var threshold = 50f
        val rule = HighTemperatureRule({ threshold })
        val metrics = mockMetrics(temperatureCelsius = 45f)
        
        // when & then
        assertNull(rule.evaluate(metrics, Duration.ZERO))
        
        threshold = 40f
        assertEquals(ForumsladerAlert.HighTemperature(45f), rule.evaluate(metrics, 10.seconds))
    }

    @Test
    fun `BatteryLowRule should NOT re-arm when battery goes up but not enough`() {
        // given
        val rule = BatteryLowRule({ 20 })
        val metricsLow = mockMetrics(batteryLevelPercentage = 15)
        val metricsMid = mockMetrics(batteryLevelPercentage = 25) // 20 + 5 < 20 + 8
        
        // when
        rule.evaluate(metricsLow, Duration.ZERO)
        val resultMid = rule.evaluate(metricsMid, 10.seconds)
        val resultLowAgain = rule.evaluate(metricsLow, 120.seconds)
        
        // then
        assertAll(
            { assertNull(resultMid) },
            { assertNull(resultLowAgain) }
        )
    }
}
