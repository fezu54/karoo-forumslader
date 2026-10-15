package org.happycode.karoo.forumslader.domain.estimation

import kotlin.math.roundToInt

data class GradientProfile(
    val gradientPct: Int,
    val speedMetersPerSecond: Float,
    val frequencyHz: Float,
    val dynamoPowerWatts: Float
) {
    fun updatedWith(
        measuredSpeedMs: Float,
        measuredFrequencyHz: Float,
        measuredDynamoWatts: Float
    ): GradientProfile = copy(
        speedMetersPerSecond = blend(speedMetersPerSecond, measuredSpeedMs),
        frequencyHz = blend(frequencyHz, measuredFrequencyHz),
        dynamoPowerWatts = blend(dynamoPowerWatts, measuredDynamoWatts)
    )

    fun adjustedForHeadwind(headwindSpeedMs: Float): GradientProfile =
        (speedMetersPerSecond - (headwindSpeedMs.coerceAtLeast(0f) * HEADWIND_SPEED_PENALTY_FACTOR))
            .coerceAtLeast(MIN_CLIMBING_SPEED_MS)
            .let { adjustedSpeedMs ->
                val speedRatio = adjustedSpeedMs / speedMetersPerSecond.coerceAtLeast(MIN_CLIMBING_SPEED_MS)
                copy(
                    speedMetersPerSecond = adjustedSpeedMs,
                    frequencyHz = frequencyHz * speedRatio
                )
            }

    private fun blend(current: Float, measured: Float): Float =
        (current * (1f - EMA_ALPHA)) + (measured * EMA_ALPHA)

    companion object {
        private const val EMA_ALPHA = 0.1f
        private const val HEADWIND_SPEED_PENALTY_FACTOR = 0.25f
        private const val MIN_CLIMBING_SPEED_MS = 1.2f
        private const val MIN_GRADIENT_BUCKET = -15
        private const val MAX_GRADIENT_BUCKET = 25
        private const val DEFAULT_HZ_PER_MS = 7.2f

        fun bucketFor(gradientPct: Float): Int =
            gradientPct.roundToInt().coerceIn(MIN_GRADIENT_BUCKET, MAX_GRADIENT_BUCKET)

        fun defaultFor(gradientPct: Int): GradientProfile =
            defaultSpeedMsFor(gradientPct).let { speedMs ->
                GradientProfile(
                    gradientPct,
                    speedMetersPerSecond = speedMs,
                    frequencyHz = speedMs * DEFAULT_HZ_PER_MS,
                    dynamoPowerWatts = 0f
                )
            }

        private fun defaultSpeedMsFor(gradientPct: Int): Float = when {
            gradientPct <= -5 -> 9.7f
            gradientPct <= 0 -> 6.7f
            gradientPct <= 3 -> 4.4f
            gradientPct <= 6 -> 3.1f
            gradientPct <= 9 -> 2.2f
            else -> 1.7f
        }
    }
}
