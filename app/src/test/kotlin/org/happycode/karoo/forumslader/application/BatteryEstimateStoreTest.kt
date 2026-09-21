package org.happycode.karoo.forumslader.application

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.BatteryEstimate

class BatteryEstimateStoreTest : ShouldSpec({

    should("update and clear estimate") {
        //given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 80,
            avgDischargeRatePctPerKm = 1.0f,
            estimatedRangeKm = 80.0f,
            routeRemainingKm = null,
            isSufficientForRoute = null
        )

        with(BatteryEstimateStore) {
            // when
            updateEstimate(estimate)

            // then
            estimateFlow.value shouldBe estimate

            // when
            clear()

            // then
            estimateFlow.value.shouldBeNull()
        }
    }
})
