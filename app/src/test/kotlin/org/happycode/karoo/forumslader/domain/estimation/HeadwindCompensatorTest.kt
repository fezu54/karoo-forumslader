package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class HeadwindCompensatorTest : ShouldSpec({

    val compensator = HeadwindCompensator(headwindPenaltyPctPerKmPerMs = 0.5f)

    should("return zero penalty when current headwind is null or below baseline") {
        // given / when / then
        compensator.calculateRatePenaltyPctPerKm(null, 2.0f, 1.0f) shouldBe 0f
        compensator.calculateRatePenaltyPctPerKm(-3.0f, 0.0f, 1.0f) shouldBe 0f
        compensator.calculateRatePenaltyPctPerKm(4.0f, 4.0f, 1.0f) shouldBe 0f
        compensator.calculateRatePenaltyPctPerKm(3.0f, 5.0f, 1.0f) shouldBe 0f
    }

    should("scale excess headwind penalty by non-climb fraction of remaining route") {
        // given: 6 m/s current wind vs 2 m/s baseline -> 4 m/s excess across 75% non-climb route
        // when
        val penalty = compensator.calculateRatePenaltyPctPerKm(
            currentHeadwindMs = 6.0f,
            baselineHeadwindMs = 2.0f,
            nonClimbRouteFraction = 0.75f
        )

        // then: 4.0 * 0.5 * 0.75 = 1.5 %/km
        penalty shouldBe (1.5f plusOrMinus 0.001f)
    }

    should("clamp non-climb route fraction between zero and one") {
        // given / when / then
        compensator.calculateRatePenaltyPctPerKm(4.0f, null, -0.5f) shouldBe 0f
        compensator.calculateRatePenaltyPctPerKm(4.0f, null, 1.5f) shouldBe (2.0f plusOrMinus 0.001f)
    }
})
