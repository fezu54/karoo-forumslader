package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class GradientProfileTest : ShouldSpec({

    should("blend speed frequency and dynamo power when updatedWith is called") {
        // given
        val initialProfile = GradientProfile(
            gradientPct = 5,
            speedMetersPerSecond = 5.0f,
            frequencyHz = 36.0f,
            dynamoPowerWatts = 2.0f
        )

        // when
        val updated = initialProfile.updatedWith(
            measuredSpeedMs = 10.0f,
            measuredFrequencyHz = 72.0f,
            measuredDynamoWatts = 6.0f
        )

        // then (EMA_ALPHA = 0.05)
        updated.speedMetersPerSecond shouldBe (5.25f plusOrMinus 0.001f)
        updated.frequencyHz shouldBe (37.8f plusOrMinus 0.001f)
        updated.dynamoPowerWatts shouldBe (2.2f plusOrMinus 0.001f)
        updated.gradientPct shouldBe 5
    }

    should("reduce speed and frequency proportionally when adjustedForHeadwind is called") {
        // given
        val profile = GradientProfile(
            gradientPct = 4,
            speedMetersPerSecond = 5.0f,
            frequencyHz = 36.0f,
            dynamoPowerWatts = 3.0f
        )

        // when
        val adjusted = profile.adjustedForHeadwind(headwindSpeedMs = 4.0f)

        // then
        adjusted.speedMetersPerSecond shouldBe (4.0f plusOrMinus 0.001f)
        adjusted.frequencyHz shouldBe (28.8f plusOrMinus 0.001f)
        adjusted.dynamoPowerWatts shouldBe 3.0f
    }

    should("return sensible default profile when defaultFor is called") {
        // given
        val expectedSpeedsByGradient = mapOf(
            -6 to 9.7f,
            0 to 6.7f,
            2 to 4.4f,
            5 to 3.1f,
            8 to 2.2f,
            12 to 1.7f
        )

        // when / then
        assertSoftly {
            expectedSpeedsByGradient.forEach { (gradient, expectedSpeed) ->
                val profile = GradientProfile.defaultFor(gradient)

                profile.gradientPct shouldBe gradient
                profile.speedMetersPerSecond shouldBe expectedSpeed
                profile.frequencyHz shouldBe (expectedSpeed * 7.2f)
                profile.dynamoPowerWatts shouldBe 0f
            }
        }
    }

    should("preserve learned dynamo power when hysteresis cutoff passes null but blend genuine zero watt readings") {
        // given
        val profile = GradientProfile(
            gradientPct = 0,
            speedMetersPerSecond = 5.0f,
            frequencyHz = 36.0f,
            dynamoPowerWatts = 4.0f
        )

        // when: hysteresis cutoff passes null
        val afterHysteresis = profile.updatedWith(
            measuredSpeedMs = 6.0f,
            measuredFrequencyHz = 43.2f,
            measuredDynamoWatts = null
        )

        // and when: genuine 0 W reading below cut-in is measured
        val afterZeroWatts = profile.updatedWith(
            measuredSpeedMs = 1.5f,
            measuredFrequencyHz = 10.8f,
            measuredDynamoWatts = 0.0f
        )

        // then
        afterHysteresis.dynamoPowerWatts shouldBe 4.0f
        afterHysteresis.speedMetersPerSecond shouldBe (5.05f plusOrMinus 0.001f)
        afterZeroWatts.dynamoPowerWatts shouldBe (3.8f plusOrMinus 0.001f)
    }

    should("scale headwind speed reduction down on steep slow climbs") {
        // given: steep slow climb at 1.8 m/s under 4 m/s headwind
        val climbProfile = GradientProfile(
            gradientPct = 10,
            speedMetersPerSecond = 1.8f,
            frequencyHz = 13.0f,
            dynamoPowerWatts = 1.0f
        )

        // when
        val adjusted = climbProfile.adjustedForHeadwind(headwindSpeedMs = 4.0f)

        // then: scaled penalty = 4.0 * 0.25 * (1.8 / 5.0) = 0.36 m/s -> 1.44 m/s (above 1.2 m/s floor)
        adjusted.speedMetersPerSecond shouldBe (1.44f plusOrMinus 0.01f)
        adjusted.speedMetersPerSecond shouldBeGreaterThan 1.2f
    }
})
