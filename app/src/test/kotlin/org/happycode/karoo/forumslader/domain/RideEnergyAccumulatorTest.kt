package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Distance
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Dynamics
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Energy
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Environment
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Power

class RideEnergyAccumulatorTest : ShouldSpec({

    var currentTimeMs: Long
    lateinit var accumulator: RideEnergyAccumulator

    fun createMetrics(
        dynamoPowerWatts: Float = 5.0f,
        chargeState: ChargeState = ChargeState.CHARGING,
        batteryLevelPercentage: Int? = 80
    ) = ForumsladerMetrics(
        power = Power(
            batteryVoltage = 12.5f,
            batteryCurrent = 0.5f,
            consumerCurrent = 0.2f,
            batteryLevelPercentage = batteryLevelPercentage,
            chargeState = chargeState,
            dynamoPowerWatts = dynamoPowerWatts,
            statusMask = 0
        ),
        dynamics = Dynamics(
            frequency = 50.0f,
            speedMetersPerSecond = 8.0f,
            generatorGear = 2
        ),
        environment = Environment(
            temperatureCelsius = 20.0f,
            altitudeMeters = 100.0f
        ),
        energy = Energy(
            tripWattHours = 10.0,
            tourWattHours = 50.0
        ),
        distance = Distance(
            tripMeters = 1000.0,
            dayMeters = 2000.0,
            tourMeters = 10000.0,
            odometerMeters = 50000.0
        )
    )

    beforeEach {
        currentTimeMs = 1000L
        accumulator = RideEnergyAccumulator(timeProvider = { currentTimeMs })
    }

    should("accumulate energy, average power, peak power, and state durations when update is called") {
        // given
        val chargingMetrics = createMetrics(
            dynamoPowerWatts = 3.6f,
            chargeState = ChargeState.CHARGING,
            batteryLevelPercentage = 70
        )
        val dischargingMetrics = createMetrics(
            dynamoPowerWatts = 7.2f,
            chargeState = ChargeState.DISCHARGING,
            batteryLevelPercentage = 85
        )
        val standbyMetrics = createMetrics(
            dynamoPowerWatts = 0.0f,
            chargeState = ChargeState.STANDBY,
            batteryLevelPercentage = 82
        )

        // when
        accumulator.update(chargingMetrics, deltaTimeSec = 1000f) // 3.6W * 1000s / 3600 = 1.0 Wh
        accumulator.update(dischargingMetrics, deltaTimeSec = 1000f) // 7.2W * 1000s / 3600 = 2.0 Wh
        accumulator.update(standbyMetrics, deltaTimeSec = 1000f) // 0.0W * 1000s / 3600 = 0.0 Wh

        // then
        val summary = accumulator.toSummary()
        summary.hasData shouldBe true
        summary.totalEnergyWh shouldBe (3.0f plusOrMinus 0.01f)
        summary.avgPowerW shouldBe (3.6f plusOrMinus 0.01f)
        summary.peakPowerW shouldBe (7.2f plusOrMinus 0.01f)
        summary.chargingTimeSec shouldBe 1000L
        summary.dischargingTimeSec shouldBe 1000L
        summary.standbyTimeSec shouldBe 1000L
        summary.batteryStartPct shouldBe 70
        summary.batteryEndPct shouldBe 82
        summary.netBatteryChangePct shouldBe 12
    }

    should("count FULL charge state as standby duration when update is called") {
        // given
        val fullMetrics = createMetrics(
            dynamoPowerWatts = 1.8f,
            chargeState = ChargeState.FULL,
            batteryLevelPercentage = 100
        )

        // when
        accumulator.update(fullMetrics, deltaTimeSec = 60f)

        // then
        val summary = accumulator.toSummary()
        summary.standbyTimeSec shouldBe 60L
        summary.chargingTimeSec shouldBe 0L
        summary.dischargingTimeSec shouldBe 0L
    }

    should("ignore negative deltaTimeSec and coerce negative power to zero") {
        // given
        val negativePowerMetrics = createMetrics(dynamoPowerWatts = -5.0f)

        // when
        accumulator.update(negativePowerMetrics, deltaTimeSec = -10f)
        accumulator.update(negativePowerMetrics, deltaTimeSec = 10f)

        // then
        val summary = accumulator.toSummary()
        summary.hasData shouldBe true
        summary.totalEnergyWh shouldBe 0f
        summary.avgPowerW shouldBe 0f
        summary.peakPowerW shouldBe 0f
    }

    should("return null netBatteryChangePct when battery level is null") {
        // given
        val noBatteryMetrics = createMetrics(batteryLevelPercentage = null)

        // when
        accumulator.update(noBatteryMetrics, deltaTimeSec = 10f)

        // then
        val summary = accumulator.toSummary()
        summary.batteryStartPct.shouldBeNull()
        summary.batteryEndPct.shouldBeNull()
        summary.netBatteryChangePct.shouldBeNull()
    }

    should("not accumulate metrics when ride has not started or is paused") {
        // given
        val metrics = createMetrics(dynamoPowerWatts = 6.0f, batteryLevelPercentage = 50)

        // when - before ride start
        accumulator.onMetrics(metrics)

        // then
        accumulator.toSummary().hasData shouldBe false

        // when - ride started, then paused
        accumulator.onRideStart()
        currentTimeMs = 1000L
        accumulator.onMetrics(metrics)
        currentTimeMs = 2000L
        accumulator.onMetrics(metrics) // +1s

        accumulator.onRidePause()
        currentTimeMs = 60_000L // 58s elapsed while paused
        accumulator.onMetrics(metrics) // ignored while paused

        accumulator.onRideResume()
        currentTimeMs = 61_000L
        accumulator.onMetrics(metrics) // first sample after resume has 0 deltaTimeSec
        currentTimeMs = 63_000L
        accumulator.onMetrics(metrics) // +2s

        // then - only 3 seconds of active recording counted
        val summary = accumulator.toSummary()
        summary.chargingTimeSec shouldBe 3L
        summary.avgPowerW shouldBe (6.0f plusOrMinus 0.01f)
    }

    should("clamp deltaTimeSec to MAX_DELTA_TIME_SEC when BLE dropout occurs during recording") {
        // given
        accumulator.onRideStart()
        val metrics = createMetrics(dynamoPowerWatts = 3.6f)

        // when
        currentTimeMs = 1000L
        accumulator.onMetrics(metrics)
        currentTimeMs = 121_000L // 120 seconds gap
        accumulator.onMetrics(metrics)

        // then
        val summary = accumulator.toSummary()
        summary.chargingTimeSec shouldBe 30L
    }

    should("reset all accumulated statistics when reset is called") {
        // given
        accumulator.onRideStart()
        accumulator.update(createMetrics(dynamoPowerWatts = 10f, batteryLevelPercentage = 90), deltaTimeSec = 100f)

        // when
        accumulator.reset()

        // then
        val summary = accumulator.toSummary()
        accumulator.isRecording shouldBe false
        summary.hasData shouldBe false
        summary.totalEnergyWh shouldBe 0f
        summary.avgPowerW shouldBe 0f
        summary.peakPowerW shouldBe 0f
        summary.chargingTimeSec shouldBe 0L
        summary.dischargingTimeSec shouldBe 0L
        summary.standbyTimeSec shouldBe 0L
        summary.batteryStartPct.shouldBeNull()
        summary.batteryEndPct.shouldBeNull()
        summary.netBatteryChangePct.shouldBeNull()
    }
})
