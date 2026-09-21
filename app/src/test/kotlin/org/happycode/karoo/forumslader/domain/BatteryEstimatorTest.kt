package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, elevationPenaltyPctPer100m = 2.0f).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, headwindPenaltyPctPerKmPerMs = 1.0f).apply {
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
        val estimator = BatteryEstimator(elevationPenaltyPctPer100m = 1.0f, headwindPenaltyPctPerKmPerMs = 1.0f).apply {
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
        val estimator = BatteryEstimator(windowMeters = 1000.0, minMetersForEstimate = 500.0).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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

    should("purge samples accumulated during charging when entering discharging") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 80, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 2000.0, batteryPct = 85, chargeState = ChargeState.CHARGING))
        }

        // when enters discharging (charging samples are purged, window starts fresh at 85%)
        estimator.onMetrics(createMetrics(distance = 2100.0, batteryPct = 85, chargeState = ChargeState.DISCHARGING))

        // then initial estimate without prior rate has no range
        val initialEstimate = estimator.getEstimate()
        initialEstimate.shouldNotBeNull()
        initialEstimate.estimatedRangeKm.shouldBeNull()

        // and when 1km of discharging completes from 85% to 83%
        estimator.onMetrics(createMetrics(distance = 3100.0, batteryPct = 83, chargeState = ChargeState.DISCHARGING))
        val activeEstimate = estimator.getEstimate()
        activeEstimate.shouldNotBeNull()
        with(activeEstimate) {
            remainingCapacityPct shouldBe 83
            avgDischargeRatePctPerKm shouldBe 2.0f
            estimatedRangeKm shouldBe 41.5f
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("fall back to last discharge rate in discharging state when distance is under threshold") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
            getEstimate()
            onMetrics(createMetrics(distance = 2000.0, batteryPct = 98, chargeState = ChargeState.CHARGING))
        }

        // when enters discharging with distance under threshold
        estimator.onMetrics(createMetrics(distance = 2200.0, batteryPct = 98, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 98
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 19.6f
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("fall back to last discharge rate in discharging state when battery level has not dropped yet") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
            getEstimate()
            onMetrics(createMetrics(distance = 2000.0, batteryPct = 98, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 2100.0, batteryPct = 98, chargeState = ChargeState.DISCHARGING))
        }

        // when riding past minMetersForEstimate but battery level has not dropped
        estimator.onMetrics(createMetrics(distance = 3100.0, batteryPct = 98, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 98
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 19.6f
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("update last discharge rate to new rate when distance and level drop thresholds are met during discharge") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 100, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 95, chargeState = ChargeState.DISCHARGING))
            getEstimate()
            onMetrics(createMetrics(distance = 2000.0, batteryPct = 98, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 2100.0, batteryPct = 98, chargeState = ChargeState.DISCHARGING))
        }

        // when distance and level drop thresholds are met (1000m and 2% drop -> 2.0% per km)
        estimator.onMetrics(createMetrics(distance = 3100.0, batteryPct = 96, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 96
            avgDischargeRatePctPerKm shouldBe 2.0f
            estimatedRangeKm shouldBe 48.0f
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
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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

    should("purge lower battery level samples when battery level increases during charging") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 70, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 500.0, batteryPct = 72, chargeState = ChargeState.CHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 75, chargeState = ChargeState.CHARGING))
        }

        // when starts discharging
        estimator.onMetrics(createMetrics(distance = 1500.0, batteryPct = 75, chargeState = ChargeState.DISCHARGING))
        estimator.onMetrics(createMetrics(distance = 2500.0, batteryPct = 70, chargeState = ChargeState.DISCHARGING))
        val estimate = estimator.getEstimate()

        // then
        estimate.shouldNotBeNull()
        with(estimate) {
            remainingCapacityPct shouldBe 70
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 14.0f
        }
    }
})
