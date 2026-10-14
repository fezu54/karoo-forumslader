package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify

class BatteryEstimatorTest : ShouldSpec({

    fun createMetrics(
        distance: Double = 0.0,
        batteryPct: Int? = 100,
        batteryCurrent: Float = -0.5f,
        chargeState: ChargeState = ChargeState.DISCHARGING
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = 12.0f,
            batteryCurrent = batteryCurrent,
            consumerCurrent = 0.5f,
            batteryLevelPercentage = batteryPct,
            chargeState = chargeState,
            dynamoPowerWatts = 0f,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = 0f,
            speedMetersPerSecond = 0f,
            generatorGear = 0
        ),
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = 20f,
            altitudeMeters = 0f
        ),
        energy = ForumsladerMetrics.Energy(
            tripWattHours = 0.0,
            tourWattHours = 0.0
        ),
        distance = ForumsladerMetrics.Distance(
            tripMeters = distance,
            dayMeters = distance,
            tourMeters = distance,
            odometerMeters = distance
        )
    )

    should("return null when no samples exist") {
        // given
        val estimator = BatteryEstimator()

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldBeNull()
    }

    should("return null range when distance is less than minimum window") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 400.0, batteryPct = 99))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 99
            estimatedRangeKm.shouldBeNull()
        }
    }

    should("calculate estimated range when discharging over minimum window") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95)) // 5% per km
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 95
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 19.0f // 95 / 5 = 19
        }
    }

    should("indicate sufficient route capacity when charging") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 52, chargeState = ChargeState.CHARGING)) // charged 2%
            onRouteRemaining(distanceMeters = 10000.0, upcomingElevation = 0.0)
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 52
            estimatedRangeKm.shouldBeNull()
            routeRemainingKm shouldBe 10.0f
            isSufficientForRoute shouldBe true
            chargeState shouldBe ChargeState.CHARGING
        }
    }

    should("deduct penalty for upcoming elevation") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, elevationPenaltyPctPer100m = 2.0f, maxCredibleDischargeRate = 20.0f).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 90)) // 10% per km discharge
            onRouteRemaining(distanceMeters = 5000.0, upcomingElevation = 500.0) // 500m elevation -> 10% penalty
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        // Base capacity: 90. Effective capacity: 90 - 10 = 80
        // Discharge rate: 10% / 1km = 10% per km
        // Range: 80 / 10 = 8.0 km
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 90
            avgDischargeRatePctPerKm shouldBe 10.0f
            estimatedRangeKm shouldBe 8.0f
            routeRemainingKm shouldBe 5.0f
            isSufficientForRoute shouldBe true // 8.0 >= 5.0
        }
    }

    should("deduct penalty for headwind") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, headwindPenaltyPctPerKmPerMs = 1.0f, maxCredibleDischargeRate = 20.0f).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 90)) // 10% per km discharge
            onHeadwindSpeed(5.0f) // 5 m/s -> +5% per km penalty
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        // Total discharge rate: 10 + 5 = 15% per km
        // Capacity: 90. Range: 90 / 15 = 6.0 km
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 15.0f
            estimatedRangeKm shouldBe 6.0f
        }
    }

    should("handle missing battery level in metrics") {
        // given
        val estimator = BatteryEstimator().apply {
            onMetrics(createMetrics(batteryPct = null))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldBeNull()
    }

    should("handle null values in onRouteRemaining and onHeadwindSpeed") {
        // given
        val estimator = BatteryEstimator().apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))
            onRouteRemaining(distanceMeters = null, upcomingElevation = null)
            onHeadwindSpeed(null)
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            routeRemainingKm.shouldBeNull()
            estimatedRangeKm shouldBe 19.0f
        }
    }

    should("handle negative elevation and headwind values") {
        // given
        val estimator = BatteryEstimator(elevationPenaltyPctPer100m = 1.0f, headwindPenaltyPctPerKmPerMs = 1.0f, maxCredibleDischargeRate = 20.0f).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 90))
            onRouteRemaining(distanceMeters = 1000.0, upcomingElevation = -100.0) // should be coerced to 0
            onHeadwindSpeed(-5.0f) // should be coerced to 0
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 10.0f // no headwind penalty
            estimatedRangeKm shouldBe 9.0f // no elevation penalty (90/10)
        }
    }

    should("handle extreme elevation penalty exceeding battery level") {
        // given
        val estimator = BatteryEstimator(elevationPenaltyPctPer100m = 10.0f).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))
            onRouteRemaining(distanceMeters = 1000.0, upcomingElevation = 1000.0) // 100% penalty
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        estimate.estimatedRangeKm shouldBe 0f // (95 - 100) -> 0 / rate -> 0
    }

    should("maintain sliding window and drop old samples") {
        // given
        val estimator = BatteryEstimator(windowMeters = 1000.0, minMetersForEstimate = 500.0, emaAlpha = 1.0f, maxCredibleDischargeRate = 10.0f).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 500.0, batteryPct = 98))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        // Window is 1000m. Range is 0 to 1000.
        estimate?.avgDischargeRatePctPerKm shouldBe 5.0f // 5% per km

        // when
        estimator.onMetrics(createMetrics(distance = 1500.0, batteryPct = 90))

        // then
        // Window now 500 to 1500.
        // Diff = 98 - 90 = 8%. Distance = 1km. Rate = 8% per km.
        estimator.getEstimate()?.avgDischargeRatePctPerKm shouldBe 8.0f
    }

    should("handle battery level increase as standing still or charging") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 55, chargeState = ChargeState.CHARGING)) // +5%
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm.shouldBeNull()
            isSufficientForRoute.shouldBeNull()
            chargeState shouldBe ChargeState.CHARGING
        }
    }

    should("handle FULL charge state exactly like CHARGING") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.FULL))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 100, chargeState = ChargeState.FULL))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm.shouldBeNull()
            isSufficientForRoute.shouldBeNull()
            chargeState shouldBe ChargeState.FULL
        }
    }

    should("handle STANDBY state by hiding range but not assuming sufficient route") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.STANDBY))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 50, chargeState = ChargeState.STANDBY))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm.shouldBeNull()
            isSufficientForRoute.shouldBeNull()
            chargeState shouldBe ChargeState.STANDBY
        }
    }

    should("handle DISCHARGING state with no level drop gracefully") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 50, chargeState = ChargeState.DISCHARGING))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm.shouldBeNull()
            isSufficientForRoute.shouldBeNull()
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("handle routeRemainingKm as 0") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 90))
            onRouteRemaining(distanceMeters = 0.0, upcomingElevation = 0.0)
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            routeRemainingKm shouldBe 0.0f
            isSufficientForRoute shouldBe true // 9.0 >= 0.0
        }
    }

    should("reset window when trip distance decreases") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 50000.0, batteryPct = 80))
            onMetrics(createMetrics(distance = 51000.0, batteryPct = 75))
        }
        estimator.getEstimate()?.estimatedRangeKm.shouldNotBeNull()

        // when trip distance resets to 0 (new trip)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 75))
        estimator.onMetrics(createMetrics(distance = 600.0, batteryPct = 70))

        // then
        val estimate = estimator.getEstimate()
        estimate.shouldNotBeNull()
        estimate.remainingCapacityPct shouldBe 70
        estimate.estimatedRangeKm.shouldNotBeNull()
    }

    should("preserve last discharge rate when charging occurs") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        }
        estimator.getEstimate()?.avgDischargeRatePctPerKm shouldBe 5.0f

        // when charging occurs
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 96, chargeState = ChargeState.CHARGING))
        estimator.onMetrics(createMetrics(distance = 3000.0, batteryPct = 98, chargeState = ChargeState.CHARGING))

        // then lastDischargeRate is preserved for standby
        estimator.onMetrics(createMetrics(distance = 3000.0, batteryPct = 98, chargeState = ChargeState.STANDBY))
        val estimate = estimator.getEstimate()
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 19.6f
            chargeState shouldBe ChargeState.STANDBY
        }
    }

    should("exclude charging distance when post charge battery level remains below initial level") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, maxStepMeters = 5000.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 90, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 85, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 17000.0, batteryPct = 88, chargeState = ChargeState.CHARGING))

        // when
        estimator.onMetrics(createMetrics(distance = 17000.0, batteryPct = 88, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 17500.0, batteryPct = 88, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 88
            estimatedRangeKm shouldBe (35.2f plusOrMinus 1.0f)
            (estimatedRangeKm!! < 100.0f) shouldBe true
        }
    }

    should("freeze last discharge rate when charging occurs") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 70, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 65, chargeState = ChargeState.DISCHARGING))
        }
        val estimateBefore = estimator.getEstimate()
        estimateBefore?.avgDischargeRatePctPerKm shouldBe 5.0f

        // when net charge over the window becomes positive (charged to 72% over next 1km)
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 72, chargeState = ChargeState.CHARGING))
        val estimateAfter = estimator.getEstimate()

        // then last discharge rate is frozen (5.0f) and avg discharge rate is 0f while charging
        estimateAfter.shouldNotBeNull()
        with(estimateAfter) {
            remainingCapacityPct shouldBe 72
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm shouldBe 14.4f // 72 / 5.0
        }
    }

    should("prune pre-charging samples when entering discharging without prior discharge rate") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 2000.0, batteryPct = 70, chargeState = ChargeState.CHARGING))
        }

        // when starts discharging without prior discharge rate
        estimator.onMetrics(createMetrics(distance = 2100.0, batteryPct = 70, chargeState = ChargeState.DISCHARGING))
        estimator.getEstimate()?.estimatedRangeKm.shouldBeNull()

        // and completes 1km of discharging from 70% to 68%
        estimator.onMetrics(createMetrics(distance = 3100.0, batteryPct = 68, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then calculates new discharge rate using discharging samples
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 68
            avgDischargeRatePctPerKm shouldBe 2.0f
            estimatedRangeKm shouldBe 34.0f
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("retain last known discharge rate and calculate range in standby when previously discharging") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        }
        val activeEstimate = estimator.getEstimate()
        activeEstimate?.estimatedRangeKm shouldBe 19.0f

        // when stops at red light (STANDBY)
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.STANDBY))
        val standbyEstimate = estimator.getEstimate()

        // then
        standbyEstimate.shouldNotBeNull()
        with(standbyEstimate) {
            remainingCapacityPct shouldBe 95
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 19.0f
            chargeState shouldBe ChargeState.STANDBY
        }
    }

    should("calculate route sufficiency in standby when route remaining is provided") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onRouteRemaining(distanceMeters = 10000.0, upcomingElevation = 0.0)
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
            getEstimate()
        }

        // when enters standby
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.STANDBY))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            estimatedRangeKm shouldBe 19.0f
            routeRemainingKm shouldBe 10.0f
            isSufficientForRoute shouldBe true
            chargeState shouldBe ChargeState.STANDBY
        }
    }

    should("return null for isSufficientForRoute when route remaining is provided but estimated range is null") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, initialDischargeRate = null).apply {
            onRouteRemaining(distanceMeters = 10000.0, upcomingElevation = 0.0)
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 400.0, batteryPct = 49, chargeState = ChargeState.DISCHARGING))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            routeRemainingKm shouldBe 10.0f
            estimatedRangeKm.shouldBeNull()
            isSufficientForRoute.shouldBeNull()
        }
    }

    should("evaluate isSufficientForRoute as false when estimated range is less than route remaining") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onRouteRemaining(distanceMeters = 50000.0, upcomingElevation = 0.0)
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))
        }

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            estimatedRangeKm shouldBe 19.0f
            routeRemainingKm shouldBe 50.0f
            isSufficientForRoute shouldBe false
        }
    }

    should("provide range estimate while charging when previous discharge rate exists") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING)) // 5% per km
        }

        // when charging occurs
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 98, chargeState = ChargeState.CHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 98
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm shouldBe 19.6f // 98 / 5.0
            chargeState shouldBe ChargeState.CHARGING
        }
    }

    should("combine discharging samples across charging phase when new discharge completes") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, emaAlpha = 1.0f).apply {
            // First discharge phase: establish lastDischargeRate = 5% per km
            onMetrics(createMetrics(distance = 0.0, batteryPct = 50, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 45, chargeState = ChargeState.DISCHARGING))
            // Charging phase: level charges to 55% (net charging over window: 50 -> 55)
            onMetrics(createMetrics(distance = 2000.0, batteryPct = 55, chargeState = ChargeState.CHARGING))
        }

        // when entering discharging again at 55% (initialLevelDiff = 50 - 55 = -5 <= 0)
        estimator.onMetrics(createMetrics(distance = 2100.0, batteryPct = 55, chargeState = ChargeState.DISCHARGING))

        // then charging samples are excluded, and until 500m window is satisfied it uses lastDischargeRate
        val transitionEstimate = estimator.getEstimate()
        transitionEstimate.shouldNotBeNull()
        transitionEstimate.estimatedRangeKm shouldBe 11.0f // 55 / 5.0

        // when new 1km discharging window completes from 55% to 53% (cumulative: 2km, 7% drop -> 3.5% per km)
        estimator.onMetrics(createMetrics(distance = 3100.0, batteryPct = 53, chargeState = ChargeState.DISCHARGING))
        val newEstimate = estimator.getEstimate()

        // then fresh rate is calculated cleanly across discharging samples
        newEstimate.shouldNotBeNull()
        with(newEstimate) {
            remainingCapacityPct shouldBe 53
            avgDischargeRatePctPerKm shouldBe 3.5f
            estimatedRangeKm shouldBe (53f / 3.5f)
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("fall back to lastDischargeRate when calculated rate is below minimum credible threshold") {
        // given: initial rate of 5.0% per km established
        val estimator = BatteryEstimator(windowMeters = 20000.0, minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        }
        estimator.getEstimate()?.avgDischargeRatePctPerKm shouldBe 5.0f

        // when: 1% drop over 20km = 0.05% per km (< MIN_CREDIBLE_DISCHARGE_RATE 0.1f)
        estimator.onMetrics(createMetrics(distance = 21000.0, batteryPct = 94, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then: falls back to lastDischargeRate (5.0f) and does not overwrite it with 0.05f
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 18.8f // 94 / 5.0
        }
    }

    should("update lastDischargeRate only when rate meets or exceeds minimum credible threshold") {
        // given: initial rate of 5.0% per km established
        val estimator = BatteryEstimator(windowMeters = 20000.0, minMetersForEstimate = 500.0, maxStepMeters = 15000.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        }
        estimator.getEstimate()?.avgDischargeRatePctPerKm shouldBe 5.0f

        // when: new trip with 2% drop over 10km = 0.2% per km (>= MIN_CREDIBLE_DISCHARGE_RATE 0.1f)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 10000.0, batteryPct = 98, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then: updates lastDischargeRate to 0.2f
        estimate.shouldNotBeNull()
        with(estimate) {
            avgDischargeRatePctPerKm shouldBe 0.2f
            estimatedRangeKm shouldBe 490.0f // 98 / 0.2
        }
    }

    should("compute isSufficientForRoute while charging based on lastDischargeRate and route remaining") {
        // given: established rate = 5.0% per km (100% -> 95% over 1km)
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
            // Route remaining is 15 km
            onRouteRemaining(distanceMeters = 15000.0, upcomingElevation = 0.0)
        }

        // when charging at 90% (estimated range = 90 / 5.0 = 18.0 km >= 15 km)
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 90, chargeState = ChargeState.CHARGING))
        val sufficientEstimate = estimator.getEstimate()

        // then isSufficientForRoute should be true
        sufficientEstimate.shouldNotBeNull()
        with(sufficientEstimate) {
            estimatedRangeKm shouldBe 18.0f
            isSufficientForRoute shouldBe true
        }

        // when route remaining increases to 25 km (exceeds 18.0 km)
        estimator.onRouteRemaining(distanceMeters = 25000.0, upcomingElevation = 0.0)
        val insufficientEstimate = estimator.getEstimate()

        // then isSufficientForRoute should be false
        insufficientEstimate.shouldNotBeNull()
        with(insufficientEstimate) {
            estimatedRangeKm shouldBe 18.0f
            isSufficientForRoute shouldBe false
        }
    }

    should("provide realistic range after repeated charge-discharge cycling") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, emaAlpha = 1.0f)

        // Cycle 1: Discharging (80% to 76% over 1km -> 4% per km)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 80, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 76, chargeState = ChargeState.DISCHARGING))
        estimator.getEstimate()?.estimatedRangeKm shouldBe 19.0f // 76 / 4

        // Cycle 1: Charging (76% to 82% over next 1km)
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 82, chargeState = ChargeState.CHARGING))
        estimator.getEstimate()?.estimatedRangeKm shouldBe 20.5f // 82 / 4

        // Cycle 2: Discharging starts again
        estimator.onMetrics(createMetrics(distance = 2100.0, batteryPct = 82, chargeState = ChargeState.DISCHARGING))
        estimator.getEstimate()?.estimatedRangeKm shouldBe 20.5f // falls back to 4% per km

        // Cycle 2: Discharges 82% to 79% over 1km (3% drop; cumulative: 2km, 7% drop -> 3.5% per km)
        estimator.onMetrics(createMetrics(distance = 3100.0, batteryPct = 79, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then calculates realistic range with combined rate
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 79
            avgDischargeRatePctPerKm shouldBe 3.5f
            estimatedRangeKm shouldBe (79f / 3.5f)
        }
    }

    should("provide range estimate in FULL state when previous discharge rate exists") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING)) // 5% per km
        }

        // when battery is fully charged (FULL)
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 100, chargeState = ChargeState.FULL))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 100
            avgDischargeRatePctPerKm shouldBe 0f
            estimatedRangeKm shouldBe 20.0f // 100 / 5.0
            chargeState shouldBe ChargeState.FULL
        }
    }

    should("accumulate discharging distance across multiple short climbs separated by charging segments") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 1000.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 90, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 600.0, batteryPct = 90, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 5600.0, batteryPct = 95, chargeState = ChargeState.CHARGING))

        // when
        estimator.onMetrics(createMetrics(distance = 5600.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 6200.0, batteryPct = 94, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 94
            estimatedRangeKm shouldBe (112.8f plusOrMinus 0.1f)
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("calculate valid discharge rate when climbing after initial discharge and long charging phase") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 1000.0, maxStepMeters = 2000.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 85, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 200.0, batteryPct = 85, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 10200.0, batteryPct = 92, chargeState = ChargeState.CHARGING))

        // when
        estimator.onMetrics(createMetrics(distance = 10200.0, batteryPct = 92, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 11700.0, batteryPct = 89, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 89
            estimatedRangeKm shouldBe (50.43f plusOrMinus 1.0f)
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("not double-count battery drops when integer level bounces up by 1% during discharging") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 1000.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 85, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 500.0, batteryPct = 84, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 85, chargeState = ChargeState.DISCHARGING))

        // when
        estimator.onMetrics(createMetrics(distance = 1500.0, batteryPct = 84, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 84
            avgDischargeRatePctPerKm shouldBe (0.6667f plusOrMinus 0.05f)
            estimatedRangeKm shouldBe (126.0f plusOrMinus 2.0f)
        }
    }

    should("hold discharge rate steady between integer percentage drops") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 1000.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 90, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 89, chargeState = ChargeState.DISCHARGING))

        val initialEstimate = estimator.getEstimate()
        initialEstimate.shouldNotBeNull()
        initialEstimate.avgDischargeRatePctPerKm shouldBe 1.0f

        // when
        estimator.onMetrics(createMetrics(distance = 1500.0, batteryPct = 89, chargeState = ChargeState.DISCHARGING))
        val midEstimate = estimator.getEstimate()

        // then
        midEstimate.shouldNotBeNull()
        midEstimate.avgDischargeRatePctPerKm shouldBe 1.0f

        // when
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 89, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 2500.0, batteryPct = 89, chargeState = ChargeState.DISCHARGING))
        val decayedEstimate = estimator.getEstimate()

        // then
        decayedEstimate.shouldNotBeNull()
        decayedEstimate.avgDischargeRatePctPerKm shouldBe 0.4f
    }

    should("clamp extreme discharge rates to credible bounds") {
        // Rate below MIN_CREDIBLE_DISCHARGE_RATE (0.15f) is clamped to 0.15f
        val lowEstimator = BatteryEstimator(minMetersForEstimate = 1000.0, maxStepMeters = 25000.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            // 1% drop over 20 km = 0.05 %/km -> clamped to 0.15 %/km
            onMetrics(createMetrics(distance = 20000.0, batteryPct = 99, chargeState = ChargeState.DISCHARGING))
        }
        val lowEstimate = lowEstimator.getEstimate()
        lowEstimate.shouldNotBeNull()
        lowEstimate.avgDischargeRatePctPerKm shouldBe BatteryEstimator.MIN_CREDIBLE_DISCHARGE_RATE

        // Rate above MAX_CREDIBLE_DISCHARGE_RATE (5.0f) is clamped to 5.0f
        val highEstimator = BatteryEstimator(minMetersForEstimate = 1000.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            // 10% drop over 1 km = 10.0 %/km -> clamped to 5.0 %/km
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 90, chargeState = ChargeState.DISCHARGING))
        }
        val highEstimate = highEstimator.getEstimate()
        highEstimate.shouldNotBeNull()
        highEstimate.avgDischargeRatePctPerKm shouldBe BatteryEstimator.MAX_CREDIBLE_DISCHARGE_RATE
    }

    should("provide range estimate and accurate isSufficientForRoute while CHARGING and FULL when prior rate exists") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING)) // 5.0 %/km
            onRouteRemaining(distanceMeters = 15000.0, upcomingElevation = 0.0) // 15 km remaining
        }

        // when CHARGING at 90%: range = 90 / 5.0 = 18 km >= 15 km
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 90, chargeState = ChargeState.CHARGING))
        val chargingEstimate = estimator.getEstimate()

        // then
        chargingEstimate.shouldNotBeNull()
        with(chargingEstimate) {
            estimatedRangeKm shouldBe 18.0f
            avgDischargeRatePctPerKm shouldBe 0f
            isSufficientForRoute shouldBe true
            chargeState shouldBe ChargeState.CHARGING
        }

        // when route remaining increases to 20 km (exceeds 18 km range)
        estimator.onRouteRemaining(distanceMeters = 20000.0, upcomingElevation = 0.0)
        val insufficientCharging = estimator.getEstimate()
        insufficientCharging.shouldNotBeNull()
        insufficientCharging.isSufficientForRoute shouldBe false

        // when FULL at 100%: range = 100 / 5.0 = 20 km == 20 km
        estimator.onMetrics(createMetrics(distance = 3000.0, batteryPct = 100, chargeState = ChargeState.FULL))
        val fullEstimate = estimator.getEstimate()
        fullEstimate.shouldNotBeNull()
        with(fullEstimate) {
            estimatedRangeKm shouldBe 20.0f
            avgDischargeRatePctPerKm shouldBe 0f
            isSufficientForRoute shouldBe true
            chargeState shouldBe ChargeState.FULL
        }
    }

    should("provide immediate range estimate on cold start using default discharge rate") {
        // given
        val estimator = BatteryEstimator()

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 80, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 80
            avgDischargeRatePctPerKm shouldBe BatteryEstimator.DEFAULT_DISCHARGE_RATE
            estimatedRangeKm shouldBe 80.0f
        }
    }

    should("provide immediate range estimate on cold start using persisted initial discharge rate") {
        // given
        val persistedRate = 2.0f
        val estimator = BatteryEstimator(initialDischargeRate = persistedRate)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 80, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 80
            avgDischargeRatePctPerKm shouldBe 2.0f
            estimatedRangeKm shouldBe 40.0f
        }
    }

    should("persist updated discharge rate to store when credible rate is calculated") {
        // given
        val store = mockk<DischargeRateStore>(relaxed = true)
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, dischargeRateStore = store)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        estimate.avgDischargeRatePctPerKm shouldBe 5.0f
        verify { store.save(5.0f) }
    }

    should("not persist unchanged discharge rate to store on repeated ticks") {
        // given
        val store = mockk<DischargeRateStore>(relaxed = true)
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, dischargeRateStore = store)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))

        // when
        estimator.onMetrics(createMetrics(distance = 1001.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        verify(exactly = 1) { store.save(any()) }
    }

    should("return estimate with initialDischargeRate when single metrics sample recorded") {
        // given
        val estimator = BatteryEstimator(initialDischargeRate = 2.0f)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        estimate.avgDischargeRatePctPerKm shouldBe 2.0f
        estimate.estimatedRangeKm shouldBe 50.0f
    }

    should("return null estimated range when initial discharge rate is zero") {
        // given
        val estimator = BatteryEstimator(initialDischargeRate = 0f)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))

        // when
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        estimate.estimatedRangeKm.shouldBeNull()
    }
})
