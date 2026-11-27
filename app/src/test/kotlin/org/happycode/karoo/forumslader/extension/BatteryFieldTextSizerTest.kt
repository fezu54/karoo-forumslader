package org.happycode.karoo.forumslader.extension

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual

class BatteryFieldTextSizerTest : ShouldSpec({

    fun estimatedHalfWidthDp(valueText: String, unitText: String, digitSizeSp: Float, unitSizeSp: Float): Float {
        val marginDp = if (unitText.isNotEmpty()) 2f else 0f
        val valueWidthDp = valueText.length * digitSizeSp * 0.58f
        val unitWidthDp = unitText.length * unitSizeSp * 0.60f
        return valueWidthDp + marginDp + unitWidthDp
    }

    should("fit two-digit battery and range with route suffix inside Karoo 3 two-column half-cell") {
        // given
        val viewWidthDp = 126f
        val viewHeightDp = 56f
        val halfWidthDp = (viewWidthDp - 10f) / 2f

        // when
        val sizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "65",
            levelUnitText = "%",
            rangeText = "65",
            rangeUnitText = "km ✓",
            isRangeNumeric = true
        )

        // then
        val rangeWidthDp = estimatedHalfWidthDp("65", "km ✓", sizes.digitSizeSp, sizes.unitSizeSp)
        rangeWidthDp shouldBeLessThanOrEqual halfWidthDp
        sizes.digitSizeSp shouldBeGreaterThanOrEqual 12f
        sizes.unitSizeSp shouldBeGreaterThanOrEqual 9f
    }

    should("shrink digit size when range has three or four digits on Karoo 3 two-column layout") {
        // given
        val viewWidthDp = 126f
        val viewHeightDp = 56f
        val halfWidthDp = (viewWidthDp - 10f) / 2f

        // when
        val twoDigitSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "65",
            levelUnitText = "%",
            rangeText = "65",
            rangeUnitText = "km ✓",
            isRangeNumeric = true
        )
        val threeDigitSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "85",
            levelUnitText = "%",
            rangeText = "667",
            rangeUnitText = "km ✓",
            isRangeNumeric = true
        )
        val fourDigitSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "100",
            levelUnitText = "%",
            rangeText = "1200",
            rangeUnitText = "km ✓",
            isRangeNumeric = true
        )

        // then
        threeDigitSizes.digitSizeSp shouldBeLessThan twoDigitSizes.digitSizeSp
        fourDigitSizes.digitSizeSp shouldBeLessThan threeDigitSizes.digitSizeSp
        estimatedHalfWidthDp("667", "km ✓", threeDigitSizes.digitSizeSp, threeDigitSizes.unitSizeSp) shouldBeLessThanOrEqual halfWidthDp
        estimatedHalfWidthDp("1200", "km ✓", fourDigitSizes.digitSizeSp, fourDigitSizes.unitSizeSp) shouldBeLessThanOrEqual halfWidthDp
    }

    should("fit three-digit battery level with charging icon inside Karoo 3 two-column half-cell") {
        // given
        val viewWidthDp = 126f
        val viewHeightDp = 56f
        val halfWidthDp = (viewWidthDp - 10f) / 2f

        // when
        val sizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "100",
            levelUnitText = "⚡%",
            rangeText = "Laden",
            rangeUnitText = "",
            isRangeNumeric = false
        )

        // then
        val levelWidthDp = estimatedHalfWidthDp("100", "⚡%", sizes.digitSizeSp, sizes.unitSizeSp)
        levelWidthDp shouldBeLessThanOrEqual halfWidthDp
        sizes.textSizeSp shouldBeGreaterThanOrEqual 10f
    }

    should("allow larger digits on full-width Karoo 3 data field") {
        // given
        val compactSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = 126f,
            viewHeightDp = 56f,
            configTextSize = 36,
            isCompact = true,
            levelText = "85",
            levelUnitText = "%",
            rangeText = "120",
            rangeUnitText = "km ✓",
            isRangeNumeric = true
        )

        // when
        val fullWidthSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = 256f,
            viewHeightDp = 70f,
            configTextSize = 48,
            isCompact = false,
            levelText = "85",
            levelUnitText = "%",
            rangeText = "120",
            rangeUnitText = "km ✓",
            isRangeNumeric = true
        )

        // then
        fullWidthSizes.digitSizeSp shouldBeGreaterThan compactSizes.digitSizeSp
        fullWidthSizes.digitSizeSp shouldBeLessThanOrEqual 40f
        fullWidthSizes.unitSizeSp shouldBeLessThanOrEqual 20f
    }

    should("scale non-numeric status text to fit half-cell width") {
        // given
        val viewWidthDp = 126f
        val viewHeightDp = 56f

        // when
        val shortTextSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "---",
            levelUnitText = "%",
            rangeText = "Voll",
            rangeUnitText = "",
            isRangeNumeric = false
        )
        val longTextSizes = BatteryFieldTextSizer.calculate(
            viewWidthDp = viewWidthDp,
            viewHeightDp = viewHeightDp,
            configTextSize = 36,
            isCompact = true,
            levelText = "---",
            levelUnitText = "%",
            rangeText = "Berechne…",
            rangeUnitText = "",
            isRangeNumeric = false
        )

        // then
        longTextSizes.textSizeSp shouldBeLessThan shortTextSizes.textSizeSp
        longTextSizes.textSizeSp shouldBeGreaterThanOrEqual 10f
        shortTextSizes.textSizeSp shouldBeLessThanOrEqual 22f
    }
})
