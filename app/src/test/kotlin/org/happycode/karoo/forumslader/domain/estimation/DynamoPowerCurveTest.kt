package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class DynamoPowerCurveTest : ShouldSpec({

    val curve = DynamoPowerCurve()

    should("return zero watts when frequency is below cut-in threshold") {
        // given
        val lowFrequencyHz = 10.0f

        // when
        val powerWatts = curve.predictFromFrequency(lowFrequencyHz)

        // then
        powerWatts shouldBe 0.0f
    }

    should("scale learned dynamo power proportionally when headwind reduces frequency") {
        // given
        val baseProfile = GradientProfile(
            gradientPct = 6,
            speedMetersPerSecond = 4.0f,
            frequencyHz = 30.0f,
            dynamoPowerWatts = 4.0f
        )
        val headwindProfile = baseProfile.adjustedForHeadwind(headwindSpeedMs = 4.0f)

        // when
        val predictedWatts = curve.predictPowerWatts(profile = headwindProfile, baseProfile = baseProfile)

        // then
        val expectedRatio = headwindProfile.frequencyHz / baseProfile.frequencyHz
        predictedWatts shouldBe (4.0f * expectedRatio plusOrMinus 0.01f)
    }

    should("interpolate power across all frequency stages when predicting from frequency") {
        // given / when / then
        curve.predictFromFrequency(20.0f) shouldBe (0.5f + ((20.0f - 12.0f) / (25.0f - 12.0f)) * (3.2f - 0.5f) plusOrMinus 0.01f)
        curve.predictFromFrequency(35.0f) shouldBe (3.2f + ((35.0f - 25.0f) / (40.0f - 25.0f)) * (7.0f - 3.2f) plusOrMinus 0.01f)
        curve.predictFromFrequency(50.0f) shouldBe (7.0f + ((50.0f - 40.0f) / (60.0f - 40.0f)) * (10.2f - 7.0f) plusOrMinus 0.01f)
        curve.predictFromFrequency(70.0f) shouldBe 11.0f
    }

    should("fallback to frequency prediction when baseProfile has zero frequency or zero watts") {
        // given
        val profile = GradientProfile(
            gradientPct = 5,
            speedMetersPerSecond = 3.0f,
            frequencyHz = 50.0f,
            dynamoPowerWatts = 0f
        )
        val zeroBase = GradientProfile(
            gradientPct = 5,
            speedMetersPerSecond = 3.0f,
            frequencyHz = 0f,
            dynamoPowerWatts = 0f
        )

        // when
        val predictedWatts = curve.predictPowerWatts(profile = profile, baseProfile = zeroBase)

        // then
        predictedWatts shouldBe (curve.predictFromFrequency(50.0f) plusOrMinus 0.01f)
    }
})
