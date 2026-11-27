package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ConsumerLoadModelTest : ShouldSpec({

    val model = ConsumerLoadModel(batteryCapacityWh = 13.3f)

    fun createPower(
        voltage: Float = 12.0f,
        batteryCurrent: Float = -0.1f,
        consumerCurrent: Float = 0.8f,
        chargeState: ChargeState = ChargeState.DISCHARGING
    ) = ForumsladerMetrics.Power(
        batteryVoltage = voltage,
        batteryCurrent = batteryCurrent,
        consumerCurrent = consumerCurrent,
        batteryLevelPercentage = 80,
        chargeState = chargeState,
        dynamoPowerWatts = 0f,
        statusMask = 0
    )

    should("calculate consumer power at battery bus voltage for 12V lighting load") {
        // given
        val power = createPower(voltage = 12.0f, batteryCurrent = -0.5f, consumerCurrent = 0.8f)

        // when
        val watts = model.calculateConsumerPowerWatts(power)

        // then
        watts shouldBe (9.6f plusOrMinus 0.01f)
    }

    should("fall back to battery discharge drain when consumer current is zero during discharging") {
        // given
        val power = createPower(voltage = 12.0f, batteryCurrent = -0.75f, consumerCurrent = 0.0f)

        // when
        val watts = model.calculateConsumerPowerWatts(power)

        // then
        watts shouldBe (9.0f plusOrMinus 0.01f)
    }

    should("use default 12V bus voltage when battery voltage reading is zero") {
        // given
        val power = createPower(voltage = 0f, batteryCurrent = 0.5f, consumerCurrent = 0.5f, chargeState = ChargeState.CHARGING)

        // when
        val watts = model.calculateConsumerPowerWatts(power)

        // then
        watts shouldBe (6.0f plusOrMinus 0.01f)
    }

    should("bound negative rate relief when heavy load disconnects so effective rate stays realistic") {
        // given: heavy 10W baseline drops to 1W at slow 10 km/h speed (raw relief would be -6.77 %/km)
        val baseRate = 1.5f

        // when
        val adjustment = model.calculateRateAdjustmentPctPerKm(
            currentPowerWatts = 1.0f,
            baselinePowerWatts = 10.0f,
            speedMetersPerSecond = 10.0f / 3.6f,
            baseRatePctPerKm = baseRate
        )

        // then: capped at -45% of baseRate (-0.675 %/km)
        adjustment shouldBe (-0.675f plusOrMinus 0.01f)
    }

    should("return zero rate adjustment when baseline power is missing or unchanged") {
        // given / when / then
        model.calculateRateAdjustmentPctPerKm(5.0f, null, 5.0f, 1.0f) shouldBe 0f
        model.calculateRateAdjustmentPctPerKm(5.0f, 5.0f, 5.0f, 1.0f) shouldBe 0f
    }

    should("apply full positive rate increase when consumer load spikes") {
        // given: +9W jump at 18 km/h -> +0.5 Wh/km -> +3.76 %/km on 13.3Wh pack
        val adjustment = model.calculateRateAdjustmentPctPerKm(
            currentPowerWatts = 10.0f,
            baselinePowerWatts = 1.0f,
            speedMetersPerSecond = 5.0f,
            baseRatePctPerKm = 1.0f
        )

        // then
        adjustment shouldBe (3.759f plusOrMinus 0.02f)
    }

    should("increase rate adjustment when dynamic batteryCapacityWh override reflects a degraded pack") {
        // given: +9W jump at 18 km/h -> +0.5 Wh/km on a degraded 10.0 Wh pack
        // when
        val degradedAdjustment = model.calculateRateAdjustmentPctPerKm(
            currentPowerWatts = 10.0f,
            baselinePowerWatts = 1.0f,
            speedMetersPerSecond = 5.0f,
            baseRatePctPerKm = 1.0f,
            batteryCapacityWh = 10.0f
        )

        // then: 0.5 / 10.0 * 100 = 5.0 %/km
        degradedAdjustment shouldBe (5.0f plusOrMinus 0.01f)
    }

    should("decrease rate adjustment when dynamic batteryCapacityWh override reflects a larger battery pack") {
        // given: +9W jump at 18 km/h -> +0.5 Wh/km on a large 33.3 Wh 18650 pack
        // when
        val largePackAdjustment = model.calculateRateAdjustmentPctPerKm(
            currentPowerWatts = 10.0f,
            baselinePowerWatts = 1.0f,
            speedMetersPerSecond = 5.0f,
            baseRatePctPerKm = 1.0f,
            batteryCapacityWh = 33.3f
        )

        // then: 0.5 / 33.3 * 100 = 1.502 %/km
        largePackAdjustment shouldBe (1.502f plusOrMinus 0.01f)
    }
})
