package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.BatteryEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryEstimateStoreTest {

    @Test
    fun `should update and clear estimate`() {
        val estimate = BatteryEstimate(
            remainingCapacityPct = 80,
            avgDischargeRatePctPerKm = 1.0f,
            estimatedRangeKm = 80.0f,
            routeRemainingKm = null,
            isSufficientForRoute = null
        )

        BatteryEstimateStore.updateEstimate(estimate)
        assertEquals(estimate, BatteryEstimateStore.estimateFlow.value)

        BatteryEstimateStore.clear()
        assertNull(BatteryEstimateStore.estimateFlow.value)
    }
}
