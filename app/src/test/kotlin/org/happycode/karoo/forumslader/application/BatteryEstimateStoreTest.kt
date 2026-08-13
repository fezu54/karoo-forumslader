package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.BatteryEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BatteryEstimateStoreTest {

    @Before
    fun setUp() {
        BatteryEstimateStore.clear()
    }

    @Test
    fun `should update estimate when updateEstimate called`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 80,
            avgDischargeRatePctPerKm = 1.5f,
            estimatedRangeKm = 53.3f,
            routeRemainingKm = 40.0f,
            isSufficientForRoute = true
        )

        // when
        BatteryEstimateStore.updateEstimate(estimate)

        // then
        assertEquals(estimate, BatteryEstimateStore.estimateFlow.value)
    }

    @Test
    fun `should reset estimate to null when clear called`() {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 50,
            avgDischargeRatePctPerKm = 2.0f,
            estimatedRangeKm = 25.0f,
            routeRemainingKm = null,
            isSufficientForRoute = null
        )
        BatteryEstimateStore.updateEstimate(estimate)

        // when
        BatteryEstimateStore.clear()

        // then
        assertNull(BatteryEstimateStore.estimateFlow.value)
    }
}
