package org.happycode.karoo.forumslader.extension

internal data class BatteryFieldTextSizes(
    val digitSizeSp: Float,
    val unitSizeSp: Float,
    val textSizeSp: Float
)

internal object BatteryFieldTextSizer {
    private const val HORIZONTAL_OVERHEAD_DP = 10f
    private const val MIN_HALF_WIDTH_DP = 20f
    private const val UNIT_MARGIN_OVERHEAD_DP = 4f
    private const val PLAIN_OVERHEAD_DP = 2f
    private const val MIN_AVAILABLE_WIDTH_DP = 16f

    private const val COMPACT_DIGIT_SCALE = 0.55f
    private const val WIDE_DIGIT_SCALE = 0.70f
    private const val COMPACT_TEXT_SCALE = 0.40f
    private const val WIDE_TEXT_SCALE = 0.50f

    private const val DIGIT_HEIGHT_RATIO = 0.52f
    private const val TEXT_HEIGHT_RATIO = 0.38f
    private const val DIGIT_CHAR_EM = 0.58f
    private const val UNIT_CHAR_EM = 0.34f
    private const val TEXT_CHAR_EM = 0.58f
    private const val UNIT_TO_DIGIT_RATIO = 0.5f

    private const val MIN_DIGIT_SP = 12f
    private const val MAX_DIGIT_SP = 40f
    private const val MIN_UNIT_SP = 9f
    private const val MAX_UNIT_SP = 20f
    private const val MIN_TEXT_SP = 10f
    private const val MAX_TEXT_SP = 22f

    fun calculate(
        viewWidthDp: Float,
        viewHeightDp: Float,
        configTextSize: Int,
        isCompact: Boolean,
        levelText: String,
        levelUnitText: String,
        rangeText: String,
        rangeUnitText: String,
        isRangeNumeric: Boolean
    ): BatteryFieldTextSizes {
        val halfWidthDp = (viewWidthDp - HORIZONTAL_OVERHEAD_DP).coerceAtLeast(MIN_HALF_WIDTH_DP) / 2f
        val digitScale = if (isCompact) COMPACT_DIGIT_SCALE else WIDE_DIGIT_SCALE
        val textScale = if (isCompact) COMPACT_TEXT_SCALE else WIDE_TEXT_SCALE

        val maxDigitHeightSp = viewHeightDp * DIGIT_HEIGHT_RATIO
        val maxTextHeightSp = viewHeightDp * TEXT_HEIGHT_RATIO

        val maxLeftDigitSp = maxNumericHalfSp(halfWidthDp, levelText, levelUnitText)
        val maxRightDigitSp = if (isRangeNumeric) {
            maxNumericHalfSp(halfWidthDp, rangeText, rangeUnitText)
        } else {
            Float.MAX_VALUE
        }
        val maxDigitWidthSp = minOf(maxLeftDigitSp, maxRightDigitSp)

        val digitSizeSp = minOf(configTextSize * digitScale, maxDigitHeightSp, maxDigitWidthSp)
            .coerceIn(MIN_DIGIT_SP, MAX_DIGIT_SP)
        val unitSizeSp = (digitSizeSp * UNIT_TO_DIGIT_RATIO).coerceIn(MIN_UNIT_SP, MAX_UNIT_SP)

        val rangeTextLen = rangeText.length.coerceAtLeast(1)
        val maxTextWidthSp = halfWidthDp / (rangeTextLen * TEXT_CHAR_EM)
        val textSizeSp = minOf(configTextSize * textScale, maxTextHeightSp, maxTextWidthSp)
            .coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)

        return BatteryFieldTextSizes(
            digitSizeSp = digitSizeSp,
            unitSizeSp = unitSizeSp,
            textSizeSp = textSizeSp
        )
    }

    private fun maxNumericHalfSp(halfWidthDp: Float, valueText: String, unitText: String): Float {
        val overhead = if (unitText.isNotEmpty()) UNIT_MARGIN_OVERHEAD_DP else PLAIN_OVERHEAD_DP
        val availableWidthDp = (halfWidthDp - overhead).coerceAtLeast(MIN_AVAILABLE_WIDTH_DP)
        val requiredEm = (valueText.length * DIGIT_CHAR_EM + unitText.length * UNIT_CHAR_EM).coerceAtLeast(1f)
        return availableWidthDp / requiredEm
    }
}
