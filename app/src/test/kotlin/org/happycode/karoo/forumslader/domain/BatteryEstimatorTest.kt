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

    should("calculate net discharge rate over window including charging periods") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 80, chargeState = ChargeState.DISCHARGING))
            // Discharged to 75% over 1km
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 75, chargeState = ChargeState.DISCHARGING))
        }

        // when charging occurs for 1km (+3%)
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 78, chargeState = ChargeState.CHARGING))
        
        // then net discharge from 80% to 78% over 2km is 1.0% per km
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 78, chargeState = ChargeState.DISCHARGING))
        val activeEstimate = estimator.getEstimate()
        activeEstimate.shouldNotBeNull()
        with(activeEstimate) {
            remainingCapacityPct shouldBe 78
            avgDischargeRatePctPerKm shouldBe 1.0f
            estimatedRangeKm shouldBe 78.0f
            chargeState shouldBe ChargeState.DISCHARGING
        }
    }

    should("freeze last discharge rate when window has net positive charge") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
            onMetrics(createMetrics(distance = 0.0, batteryPct = 70, chargeState = ChargeState.DISCHARGING))
            onMetrics(createMetrics(distance = 1000.0, batteryPct = 65, chargeState = ChargeState.DISCHARGING))
        }
        val estimateBefore = estimator.getEstimate()
        estimateBefore?.avgDischargeRatePctPerKm shouldBe 5.0f

        // when net charge over the window becomes positive (charged to 72% over next 1km)
        estimator.onMetrics(createMetrics(distance = 2000.0, batteryPct = 72, chargeState = ChargeState.DISCHARGING)) // Net +2%
        val estimateAfter = estimator.getEstimate()

        // then last discharge rate is frozen (5.0f) instead of using the negative rate
        estimateAfter.shouldNotBeNull()
        with(estimateAfter) {
            remainingCapacityPct shouldBe 72
            avgDischargeRatePctPerKm shouldBe 5.0f
            estimatedRangeKm shouldBe 14.4f // 72 / 5.0
        }
    }

    should("prune pre-charging samples when entering discharging without prior discharge rate") {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0).apply {
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
})
