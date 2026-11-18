package org.happycode.karoo.forumslader.domain.estimation

import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

/**
 * Models electrical consumer load (12V lighting and USB outputs) and converts load shifts
 * into bounded adjustments to the battery discharge rate (%/km).
 */
class ConsumerLoadModel(
    private val batteryCapacityWh: Float
) {
    /**
     * Computes total consumer load in watts using the battery bus voltage when available.
     * Falls back to battery discharge drain when a direct-wired 12V load bypasses consumer current sensing.
     */
    fun calculateConsumerPowerWatts(power: ForumsladerMetrics.Power): Float {
        val busVoltage = power.batteryVoltage.takeIf { it > 0f } ?: DEFAULT_BUS_VOLTAGE
        val railPowerWatts = busVoltage * power.consumerCurrent.coerceAtLeast(0f)
        val batteryDrainWatts = when (power.chargeState) {
            ChargeState.DISCHARGING -> (-power.batteryCurrent * busVoltage).coerceAtLeast(0f)
            else -> 0f
        }
        return maxOf(railPowerWatts, batteryDrainWatts)
    }

    /**
     * Converts the difference between current and baseline consumer power into a %/km rate delta.
     * Negative relief is bounded relative to [baseRatePctPerKm] so turning off a heavy load cannot
     * collapse the effective rate to unrealistic triple-digit range spikes.
     */
    fun calculateRateAdjustmentPctPerKm(
        currentPowerWatts: Float,
        baselinePowerWatts: Float?,
        speedMetersPerSecond: Float?,
        baseRatePctPerKm: Float
    ): Float {
        val baselineWatts = baselinePowerWatts ?: return 0f
        val powerDeltaWatts = currentPowerWatts - baselineWatts
        if (powerDeltaWatts == 0f) return 0f

        val speedMs = speedMetersPerSecond?.takeIf { it >= MIN_MOVING_SPEED_MS } ?: DEFAULT_SPEED_MS
        val speedKmh = speedMs * MS_TO_KMH
        val whPerKmDelta = powerDeltaWatts / speedKmh
        val rawDeltaPctPerKm = (whPerKmDelta / batteryCapacityWh) * PERCENT_SCALE
        val maxNegativeRelief = -baseRatePctPerKm * MAX_NEGATIVE_RELIEF_FRACTION
        return rawDeltaPctPerKm.coerceAtLeast(maxNegativeRelief)
    }

    companion object {
        private const val DEFAULT_BUS_VOLTAGE = 12.0f
        private const val MIN_MOVING_SPEED_MS = 1.2f
        private const val DEFAULT_SPEED_MS = 5.0f
        private const val MS_TO_KMH = 3.6f
        private const val PERCENT_SCALE = 100f
        private const val MAX_NEGATIVE_RELIEF_FRACTION = 0.45f
    }
}
