package org.happycode.karoo.forumslader.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryEstimatorTest {

    private fun createMetrics(
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

    @Test
    fun `should return null when no samples exist`() {
        // given
        val estimator = BatteryEstimator()

        // when
        val estimate = estimator.getEstimate()

        // then
        assertNull(estimate)
    }

    @Test
    fun `should return null range when distance is less than minimum window`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 400.0, batteryPct = 99))
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(99, remainingCapacityPct)
            assertNull(estimatedRangeKm)
        }
    }

    @Test
    fun `should calculate estimated range when discharging over minimum window`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95)) // 5% per km
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(95, remainingCapacityPct)
            assertEquals(5.0f, avgDischargeRatePctPerKm)
            assertEquals(19.0f, estimatedRangeKm) // 95 / 5 = 19
        }
    }

    @Test
    fun `should indicate sufficient route capacity when charging`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 50))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 52)) // charged 2%
        estimator.onRouteRemaining(distanceMeters = 10000.0, upcomingElevation = 0.0)
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(52, remainingCapacityPct)
            assertNull(estimatedRangeKm)
            assertEquals(10.0f, routeRemainingKm)
            assertEquals(true, isSufficientForRoute)
        }
    }

    @Test
    fun `should deduct penalty for upcoming elevation`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, elevationPenaltyPctPer100m = 2.0f)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 90)) // 10% per km discharge
        estimator.onRouteRemaining(distanceMeters = 5000.0, upcomingElevation = 500.0) // 500m elevation -> 10% penalty
        val estimate = estimator.getEstimate()

        // then
        // Base capacity: 90. Effective capacity: 90 - 10 = 80
        // Discharge rate: 10% / 1km = 10% per km
        // Range: 80 / 10 = 8.0 km
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(90, remainingCapacityPct)
            assertEquals(10.0f, avgDischargeRatePctPerKm)
            assertEquals(8.0f, estimatedRangeKm)
            assertEquals(5.0f, routeRemainingKm)
            assertEquals(true, isSufficientForRoute) // 8.0 >= 5.0
        }
    }

    @Test
    fun `should deduct penalty for headwind`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, headwindPenaltyPctPerKmPerMs = 1.0f)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 90)) // 10% per km discharge
        estimator.onHeadwindSpeed(5.0f) // 5 m/s -> +5% per km penalty
        val estimate = estimator.getEstimate()

        // then
        // Total discharge rate: 10 + 5 = 15% per km
        // Capacity: 90. Range: 90 / 15 = 6.0 km
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(15.0f, avgDischargeRatePctPerKm)
            assertEquals(6.0f, estimatedRangeKm)
        }
    }

    @Test
    fun `should handle missing battery level in metrics`() {
        // given
        val estimator = BatteryEstimator()
        val metricsWithoutBattery = createMetrics(batteryPct = null)

        // when
        estimator.onMetrics(metricsWithoutBattery)
        val estimate = estimator.getEstimate()

        // then
        assertNull(estimate)
    }

    @Test
    fun `should handle null values in onRouteRemaining and onHeadwindSpeed`() {
        // given
        val estimator = BatteryEstimator()
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))

        // when
        estimator.onRouteRemaining(distanceMeters = null, upcomingElevation = null)
        estimator.onHeadwindSpeed(null)
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertNull(routeRemainingKm)
            assertEquals(19.0f, estimatedRangeKm)
        }
    }

    @Test
    fun `should handle negative elevation and headwind values`() {
        // given
        val estimator = BatteryEstimator(elevationPenaltyPctPer100m = 1.0f, headwindPenaltyPctPerKmPerMs = 1.0f)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 90))

        // when
        estimator.onRouteRemaining(distanceMeters = 1000.0, upcomingElevation = -100.0) // should be coerced to 0
        estimator.onHeadwindSpeed(-5.0f)           // should be coerced to 0
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(10.0f, avgDischargeRatePctPerKm) // no headwind penalty
            assertEquals(9.0f, estimatedRangeKm)        // no elevation penalty (90/10)
        }
    }

    @Test
    fun `should handle extreme elevation penalty exceeding battery level`() {
        // given
        val estimator = BatteryEstimator(elevationPenaltyPctPer100m = 10.0f)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))

        // when
        estimator.onRouteRemaining(distanceMeters = 1000.0, upcomingElevation = 1000.0) // 100% penalty
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        assertEquals(0f, estimate?.estimatedRangeKm) // (95 - 100) -> 0 / rate -> 0
    }

    @Test
    fun `should maintain sliding window and drop old samples`() {
        // given
        val estimator = BatteryEstimator(windowMeters = 1000.0, minMetersForEstimate = 500.0)

        // when
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 500.0, batteryPct = 98))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 95))

        // then
        // Window is 1000m. Range is 0 to 1000.
        assertEquals(5.0f, estimator.getEstimate()?.avgDischargeRatePctPerKm) // 5% per km

        // when
        estimator.onMetrics(createMetrics(distance = 1500.0, batteryPct = 90))

        // then
        // Window now 500 to 1500.
        // Diff = 98 - 90 = 8%. Distance = 1km. Rate = 8% per km.
        assertEquals(8.0f, estimator.getEstimate()?.avgDischargeRatePctPerKm)
    }

    @Test
    fun `should handle battery level increase as standing still or charging`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 50))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 55)) // +5%

        // when
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(0f, avgDischargeRatePctPerKm)
            assertNull(estimatedRangeKm)
            assertEquals(true, isSufficientForRoute)
        }
    }

    @Test
    fun `should handle routeRemainingKm as 0`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)
        estimator.onMetrics(createMetrics(distance = 0.0, batteryPct = 100))
        estimator.onMetrics(createMetrics(distance = 1000.0, batteryPct = 90))

        // when
        estimator.onRouteRemaining(distanceMeters = 0.0, upcomingElevation = 0.0)
        val estimate = estimator.getEstimate()

        // then
        assertNotNull(estimate)
        with(estimate!!) {
            assertEquals(0.0f, routeRemainingKm)
            assertEquals(true, isSufficientForRoute) // 9.0 >= 0.0
        }
    }
}
