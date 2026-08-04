package org.happycode.karoo.forumslader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
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
        batteryLevelPercentage: Int = 50,
        statusMask: Int = 0,
        temperatureCelsius: Float = 20f
    ): ForumsladerMetrics {
        return ForumsladerMetrics(
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
    }

    @Test
    fun `should fire low battery alert when below threshold`() {
        val metrics = mockMetrics(batteryLevelPercentage = 15)
        val alerts = alertManager.evaluate(metrics, currentTime = 0.milliseconds)

        assertEquals(listOf(ForumsladerAlert.BatteryLow(15)), alerts)
    }

    @Test
    fun `should not fire low battery alert repeatedly unless re-armed`() {
        val metrics1 = mockMetrics(batteryLevelPercentage = 15)
        alertManager.evaluate(metrics1, currentTime = 0.milliseconds)

        val metrics2 = mockMetrics(batteryLevelPercentage = 14)
        val alerts = alertManager.evaluate(metrics2, currentTime = 60.seconds)

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `should fire low battery alert again after re-arming`() {
        val metrics1 = mockMetrics(batteryLevelPercentage = 15)
        alertManager.evaluate(metrics1, currentTime = 0.milliseconds)

        val metrics2 = mockMetrics(batteryLevelPercentage = 28)
        alertManager.evaluate(metrics2, currentTime = 60.seconds)

        val metrics3 = mockMetrics(batteryLevelPercentage = 15)
        val alerts = alertManager.evaluate(metrics3, currentTime = 120.seconds)

        assertEquals(listOf(ForumsladerAlert.BatteryLow(15)), alerts)
    }

    @Test
    fun `should fire short circuit alert on status bit and mute for 60 seconds`() {
        val metrics = mockMetrics(statusMask = 0x8)

        val alerts1 = alertManager.evaluate(metrics, currentTime = 0.milliseconds)
        assertEquals(listOf(ForumsladerAlert.ShortCircuit), alerts1)

        val alerts2 = alertManager.evaluate(metrics, currentTime = 30.seconds)
        assertTrue(alerts2.isEmpty())

        val alerts3 = alertManager.evaluate(metrics, currentTime = 60.seconds)
        assertEquals(listOf(ForumsladerAlert.ShortCircuit), alerts3)
    }

    @Test
    fun `should fire system interrupt alert on status bit`() {
        val metrics = mockMetrics(statusMask = 0x800000)

        val alerts = alertManager.evaluate(metrics, currentTime = 0.milliseconds)
        assertEquals(listOf(ForumsladerAlert.SystemInterrupt), alerts)
    }

    @Test
    fun `should fire high temperature alert above threshold`() {
        val metrics = mockMetrics(temperatureCelsius = 55f)

        val alerts = alertManager.evaluate(metrics, currentTime = 0.milliseconds)
        assertEquals(listOf(ForumsladerAlert.HighTemperature(55f)), alerts)
    }
}
