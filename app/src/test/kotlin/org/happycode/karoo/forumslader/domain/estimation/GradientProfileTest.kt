package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
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

        // then
        updated.speedMetersPerSecond shouldBe (5.5f plusOrMinus 0.001f)
        updated.frequencyHz shouldBe (39.6f plusOrMinus 0.001f)
        updated.dynamoPowerWatts shouldBe (2.4f plusOrMinus 0.001f)
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
    }})
