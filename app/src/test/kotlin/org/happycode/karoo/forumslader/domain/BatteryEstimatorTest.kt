package org.happycode.karoo.forumslader.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryEstimatorTest {

    private fun createMetrics(distance: Double, batteryPct: Int): ForumsladerMetrics {
        val power = ForumsladerMetrics.Power(
            batteryVoltage = 12.0f,
            batteryCurrent = -0.5f,
            consumerCurrent = 0.5f,
            batteryLevelPercentage = batteryPct,
            chargeState = ChargeState.DISCHARGING,
            dynamoPowerWatts = 0f,
            statusMask = 0
        )
        val distanceObj = ForumsladerMetrics.Distance(
            tripMeters = distance,
            dayMeters = distance,
            tourMeters = distance,
            odometerMeters = distance
        )
        return ForumsladerMetrics(
            power = power,
            dynamics = ForumsladerMetrics.Dynamics(0f, 0f, 0),
            environment = ForumsladerMetrics.Environment(20f, 0f),
            energy = ForumsladerMetrics.Energy(0.0, 0.0),
            distance = distanceObj
        )
    }

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
        estimator.onMetrics(createMetrics(0.0, 100))
        estimator.onMetrics(createMetrics(400.0, 99))
        val estimate = estimator.getEstimate()

        // then
        assertEquals(99, estimate?.remainingCapacityPct)
        assertNull(estimate?.estimatedRangeKm)
    }

    @Test
    fun `should calculate estimated range when discharging over minimum window`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)
        
        // when
        estimator.onMetrics(createMetrics(0.0, 100))
        estimator.onMetrics(createMetrics(1000.0, 95)) // 5% per km
        val estimate = estimator.getEstimate()

        // then
        assertEquals(95, estimate?.remainingCapacityPct)
        assertEquals(5.0f, estimate?.avgDischargeRatePctPerKm)
        assertEquals(19.0f, estimate?.estimatedRangeKm) // 95 / 5 = 19
    }

    @Test
    fun `should indicate sufficient route capacity when charging`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0)
        
        // when
        estimator.onMetrics(createMetrics(0.0, 50))
        estimator.onMetrics(createMetrics(1000.0, 52)) // charged 2%
        estimator.onRouteRemaining(10000.0, 0.0)
        val estimate = estimator.getEstimate()

        // then
        assertEquals(52, estimate?.remainingCapacityPct)
        assertNull(estimate?.estimatedRangeKm)
        assertEquals(10.0f, estimate?.routeRemainingKm)
        assertEquals(true, estimate?.isSufficientForRoute)
    }

    @Test
    fun `should deduct penalty for upcoming elevation`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, elevationPenaltyPctPer100m = 2.0f)
        
        // when
        estimator.onMetrics(createMetrics(0.0, 100))
        estimator.onMetrics(createMetrics(1000.0, 90)) // 10% per km discharge
        estimator.onRouteRemaining(5000.0, 500.0) // 500m elevation -> 10% penalty
        val estimate = estimator.getEstimate()

        // then
        // Base capacity: 90. Effective capacity: 90 - 10 = 80
        // Discharge rate: 10% / 1km = 10% per km
        // Range: 80 / 10 = 8.0 km
        assertEquals(90, estimate?.remainingCapacityPct)
        assertEquals(10.0f, estimate?.avgDischargeRatePctPerKm)
        assertEquals(8.0f, estimate?.estimatedRangeKm)
        assertEquals(5.0f, estimate?.routeRemainingKm)
        assertEquals(true, estimate?.isSufficientForRoute) // 8.0 >= 5.0
    }
    
    @Test
    fun `should deduct penalty for headwind`() {
        // given
        val estimator = BatteryEstimator(minMetersForEstimate = 500.0, headwindPenaltyPctPerKmPerMs = 1.0f)
        
        // when
        estimator.onMetrics(createMetrics(0.0, 100))
        estimator.onMetrics(createMetrics(1000.0, 90)) // 10% per km discharge
        estimator.onHeadwindSpeed(5.0f) // 5 m/s -> +5% per km penalty
        val estimate = estimator.getEstimate()

        // then
        // Total discharge rate: 10 + 5 = 15% per km
        // Capacity: 90. Range: 90 / 15 = 6.0 km
        assertEquals(15.0f, estimate?.avgDischargeRatePctPerKm)
        assertEquals(6.0f, estimate?.estimatedRangeKm)
    }
}
