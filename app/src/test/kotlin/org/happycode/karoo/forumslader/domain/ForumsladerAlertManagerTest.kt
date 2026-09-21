package org.happycode.karoo.forumslader.domain

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ForumsladerAlertManagerTest : ShouldSpec({

    lateinit var alertManager: ForumsladerAlertManager

    beforeEach {
        alertManager = ForumsladerAlertManager(
            listOf(
                BatteryLowRule({ 20 }),
                HighTemperatureRule({ 50f }),
                StatusBitmaskRule(0x8, ForumsladerAlert.ShortCircuit),
                StatusBitmaskRule(0x800000, ForumsladerAlert.SystemInterrupt)
            )
        )
    }

    fun createMetrics(
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
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = temperatureCelsius,
            altitudeMeters = 0f
        ),
        energy = ForumsladerMetrics.Energy(tripWattHours = 0.0, tourWattHours = 0.0),
        distance = ForumsladerMetrics.Distance(
            tripMeters = 0.0,
            dayMeters = 0.0,
            tourMeters = 0.0,
            odometerMeters = 0.0
        )
    )

    should("fire low battery alert when below threshold") {
        // given
        val metrics = createMetrics(batteryLevelPercentage = 15)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        alerts shouldBe listOf(ForumsladerAlert.BatteryLow(15))
    }

    should("not fire low battery alert when battery level equals threshold") {
        // given
        val metricsExactThreshold = createMetrics(batteryLevelPercentage = 20)
        val metricsBelowThreshold = createMetrics(batteryLevelPercentage = 19)

        // when & then
        alertManager.evaluate(metricsExactThreshold, currentTime = Duration.ZERO).shouldBeEmpty()
        alertManager.evaluate(metricsBelowThreshold, currentTime = Duration.ZERO) shouldBe listOf(
            ForumsladerAlert.BatteryLow(19)
        )
    }

    should("not fire low battery alert repeatedly unless re-armed") {
        // given
        val metrics1 = createMetrics(batteryLevelPercentage = 15)
        alertManager.evaluate(metrics1, currentTime = Duration.ZERO)

        // when
        val metrics2 = createMetrics(batteryLevelPercentage = 14)
        val alerts = alertManager.evaluate(metrics2, currentTime = 60.seconds)

        // then
        alerts.shouldBeEmpty()
    }

    should("fire low battery alert again after re-arming") {
        // given
        val metrics1 = createMetrics(batteryLevelPercentage = 15)
        alertManager.evaluate(metrics1, currentTime = Duration.ZERO)

        val metrics2 = createMetrics(batteryLevelPercentage = 28)
        alertManager.evaluate(metrics2, currentTime = 60.seconds)

        // when
        val metrics3 = createMetrics(batteryLevelPercentage = 15)
        val alerts = alertManager.evaluate(metrics3, currentTime = 120.seconds)

        // then
        alerts shouldBe listOf(ForumsladerAlert.BatteryLow(15))
    }

    should("fire short circuit alert on status bit and mute for 60 seconds") {
        // given
        val metrics = createMetrics(statusMask = 0x8)

        // when
        val alerts1 = alertManager.evaluate(metrics, currentTime = Duration.ZERO)
        val alerts2 = alertManager.evaluate(metrics, currentTime = 30.seconds)
        val alerts3 = alertManager.evaluate(metrics, currentTime = 60.seconds)

        // then
        assertSoftly {
            alerts1 shouldBe listOf(ForumsladerAlert.ShortCircuit)
            alerts2.shouldBeEmpty()
            alerts3 shouldBe listOf(ForumsladerAlert.ShortCircuit)
        }
    }

    should("fire short circuit alert when status bitmask has multiple flags active") {
        // given
        val metrics = createMetrics(statusMask = 0x8 or 0x1 or 0x100)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        alerts shouldBe listOf(ForumsladerAlert.ShortCircuit)
    }

    should("fire system interrupt alert on status bit") {
        // given
        val metrics = createMetrics(statusMask = 0x800000)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        alerts shouldBe listOf(ForumsladerAlert.SystemInterrupt)
    }

    should("fire high temperature alert at or above threshold") {
        // given
        val metricsAtThreshold = createMetrics(temperatureCelsius = 50f)
        val metricsBelowThreshold = createMetrics(temperatureCelsius = 49.9f)

        // when & then
        alertManager.evaluate(metricsBelowThreshold, currentTime = Duration.ZERO).shouldBeEmpty()
        alertManager.evaluate(metricsAtThreshold, currentTime = Duration.ZERO) shouldBe listOf(
            ForumsladerAlert.HighTemperature(50f)
        )
    }

    should("handle null battery level in BatteryLowRule") {
        // given
        val metrics = createMetrics(batteryLevelPercentage = null)

        // when
        val alerts = alertManager.evaluate(metrics, currentTime = Duration.ZERO)

        // then
        alerts.shouldBeEmpty()
    }

    should("respect muting across all rules") {
        // given
        val metrics = createMetrics(
            batteryLevelPercentage = 10,
            temperatureCelsius = 60f,
            statusMask = 0x8
        )

        // when
        val alerts1 = alertManager.evaluate(metrics, currentTime = Duration.ZERO)
        val alerts2 = alertManager.evaluate(metrics, currentTime = 1.seconds)
        val alerts3 = alertManager.evaluate(metrics, currentTime = 61.seconds)

        // then
        assertSoftly {
            alerts1 shouldHaveSize 3
            alerts2.shouldBeEmpty()
            alerts3 shouldHaveSize 2
        }
    }

    should("have correct alert titles and details") {
        // given & when & then
        assertSoftly {
            ForumsladerAlert.ShortCircuit.title shouldBe "Forumslader"
            ForumsladerAlert.ShortCircuit.detail shouldBe "Short Circuit!"
            ForumsladerAlert.BatteryLow(15).detail shouldBe "Battery Low (15%)"
            ForumsladerAlert.HighTemperature(55.5f).detail shouldBe "High Temperature (55°C)"
            ForumsladerAlert.SystemInterrupt.detail shouldBe "System Interrupt!"
        }
    }

    should("handle empty rules list") {
        // given
        val manager = ForumsladerAlertManager(emptyList())

        // when
        val alerts = manager.evaluate(createMetrics())

        // then
        alerts.shouldBeEmpty()
    }

    should("use system time by default") {
        // when
        val alerts = alertManager.evaluate(createMetrics())

        // then
        alerts.shouldBeEmpty()
    }

    should("re-arm when battery goes above threshold plus offset in BatteryLowRule") {
        // given
        val rule = BatteryLowRule({ 20 })
        val metricsLow = createMetrics(batteryLevelPercentage = 15)
        val metricsHigh = createMetrics(batteryLevelPercentage = 29) // 20 + 8 + 1

        // when & then
        assertSoftly {
            rule.evaluate(metricsLow, Duration.ZERO) shouldBe ForumsladerAlert.BatteryLow(15)
            rule.evaluate(metricsLow, 10.seconds).shouldBeNull()
            rule.evaluate(metricsHigh, 20.seconds).shouldBeNull()
            rule.evaluate(metricsLow, 120.seconds) shouldBe ForumsladerAlert.BatteryLow(15)
        }
    }

    should("respect changing threshold in BatteryLowRule") {
        // given
        var threshold = 20
        val rule = BatteryLowRule({ threshold })
        val metrics = createMetrics(batteryLevelPercentage = 25)

        // when & then
        rule.evaluate(metrics, Duration.ZERO).shouldBeNull()

        threshold = 30
        rule.evaluate(metrics, 10.seconds) shouldBe ForumsladerAlert.BatteryLow(25)
    }

    should("respect changing threshold in HighTemperatureRule") {
        // given
        var threshold = 50f
        val rule = HighTemperatureRule({ threshold })
        val metrics = createMetrics(temperatureCelsius = 45f)

        // when & then
        rule.evaluate(metrics, Duration.ZERO).shouldBeNull()

        threshold = 40f
        rule.evaluate(metrics, 10.seconds) shouldBe ForumsladerAlert.HighTemperature(45f)
    }

    should("NOT re-arm when battery goes up but not enough in BatteryLowRule") {
        // given
        val rule = BatteryLowRule({ 20 })
        val metricsLow = createMetrics(batteryLevelPercentage = 15)
        val metricsMid = createMetrics(batteryLevelPercentage = 25) // 20 + 5 < 20 + 8

        // when
        rule.evaluate(metricsLow, Duration.ZERO)
        val resultMid = rule.evaluate(metricsMid, 10.seconds)
        val resultLowAgain = rule.evaluate(metricsLow, 120.seconds)

        // then
        assertSoftly {
            resultMid.shouldBeNull()
            resultLowAgain.shouldBeNull()
        }
    }
})
