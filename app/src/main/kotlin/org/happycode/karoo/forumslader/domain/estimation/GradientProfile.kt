package org.happycode.karoo.forumslader.domain.estimation

import kotlin.math.roundToInt

/**
 * Learned rider speed, dynamo frequency, and dynamo power output for a 1% gradient bucket.
 */
data class GradientProfile(
    val gradientPct: Int,
    val speedMetersPerSecond: Float,
    val frequencyHz: Float,
    val dynamoPowerWatts: Float
) {
    /**
     * Blends new measurements with a fixed distance-gated EMA.
     * Pass null for [measuredDynamoWatts] during full-battery hysteresis cutoffs to preserve
     * learned dynamo power while still allowing genuine 0 W readings below cut-in to blend.
     */
    fun updatedWith(
        measuredSpeedMs: Float,
        measuredFrequencyHz: Float,
        measuredDynamoWatts: Float?
    ): GradientProfile = copy(
        speedMetersPerSecond = blend(speedMetersPerSecond, measuredSpeedMs),
        frequencyHz = blend(frequencyHz, measuredFrequencyHz),
        dynamoPowerWatts = measuredDynamoWatts?.let { blend(dynamoPowerWatts, it) } ?: dynamoPowerWatts
    )

    /**
     * Adjusts climbing speed and dynamo frequency for headwind, scaled to the bucket's base speed
     * so steep slow climbs are not over-penalized by aerodynamic drag.
     */
    fun adjustedForHeadwind(headwindSpeedMs: Float): GradientProfile {
        val speedScale = (speedMetersPerSecond / REFERENCE_SPEED_MS)
            .coerceIn(MIN_HEADWIND_SPEED_SCALE, MAX_HEADWIND_SPEED_SCALE)
        val penalty = headwindSpeedMs.coerceAtLeast(0f) * HEADWIND_SPEED_PENALTY_FACTOR * speedScale
        return (speedMetersPerSecond - penalty)
            .coerceAtLeast(MIN_CLIMBING_SPEED_MS)
            .let { adjustedSpeedMs ->
                val speedRatio = adjustedSpeedMs / speedMetersPerSecond.coerceAtLeast(MIN_CLIMBING_SPEED_MS)
                copy(
                    speedMetersPerSecond = adjustedSpeedMs,
                    frequencyHz = frequencyHz * speedRatio
                )
            }
    }

    private fun blend(current: Float, measured: Float): Float =
        (current * (1f - EMA_ALPHA)) + (measured * EMA_ALPHA)

    companion object {
        private const val EMA_ALPHA = 0.05f
        private const val HEADWIND_SPEED_PENALTY_FACTOR = 0.25f
        private const val REFERENCE_SPEED_MS = 5.0f
        private const val MIN_HEADWIND_SPEED_SCALE = 0.35f
        private const val MAX_HEADWIND_SPEED_SCALE = 1.2f
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
