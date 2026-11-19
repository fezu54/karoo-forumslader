package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.ChargeState

class DischargeWindowTest : ShouldSpec({

    fun createWindow(
        initialRate: Float? = null,
        windowMeters: Double = 20_000.0,
        minMeters: Double = 1_000.0,
        emaAlpha: Float = 0.35f
    ) = DischargeWindow(
        windowMeters = windowMeters,
        minMetersForEstimate = minMeters,
        maxStepMeters = 1_000.0,
        minCredibleDischargeRate = 0.15f,
        maxCredibleDischargeRate = 5.0f,
        emaAlpha = emaAlpha,
        initialDischargeRate = initialRate,
        dischargeRateStore = DischargeRateStore {}
    )

    should("average consumer power and headwind baselines across samples in sliding window") {
        // given
        val window = createWindow(emaAlpha = 1.0f)

        // when: 0m to 1000m with varying consumer power (2W, 4W, 6W) and wind (2, 4, 6 m/s)
        window.recordSample(ChargeState.DISCHARGING, 100, 0.0, consumerPowerWatts = 2.0f, headwindSpeedMs = 2.0f)
        window.recordSample(ChargeState.DISCHARGING, 100, 500.0, consumerPowerWatts = 4.0f, headwindSpeedMs = 4.0f)
        window.recordSample(ChargeState.DISCHARGING, 98, 1_000.0, consumerPowerWatts = 6.0f, headwindSpeedMs = 6.0f)

        // then
        window.lastDischargeRate.shouldNotBeNull() shouldBe (2.0f plusOrMinus 0.01f)
        window.baselineConsumerPowerWatts.shouldNotBeNull() shouldBe (4.0f plusOrMinus 0.01f)
        window.baselineHeadwindSpeedMs.shouldNotBeNull() shouldBe (4.0f plusOrMinus 0.01f)
    }

    should("reset accumulated state when trip distance decreases") {
        // given
        val window = createWindow(initialRate = 1.0f)
        window.recordSample(ChargeState.DISCHARGING, 100, 5_000.0, consumerPowerWatts = 2.0f, headwindSpeedMs = 3.0f)
        window.recordSample(ChargeState.DISCHARGING, 96, 6_000.0, consumerPowerWatts = 2.0f, headwindSpeedMs = 3.0f)
        window.lastDischargeRate shouldBe (4.0f plusOrMinus 0.01f)

        // when: trip distance restarts at 0m
        window.recordSample(ChargeState.DISCHARGING, 96, 0.0, consumerPowerWatts = 8.0f, headwindSpeedMs = null)

        // then
        window.lastDischargeRate shouldBe 1.0f
        window.baselineConsumerPowerWatts shouldBe 8.0f
        window.baselineHeadwindSpeedMs.shouldBeNull()
    }
})
