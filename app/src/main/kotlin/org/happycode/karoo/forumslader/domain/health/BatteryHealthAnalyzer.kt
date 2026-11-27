package org.happycode.karoo.forumslader.domain.health

import kotlin.math.ceil
import org.happycode.karoo.forumslader.domain.estimation.BatteryCapacity

/**
 * Pure domain analyzer calculating baseline capacity, State of Health (%), degradation slope,
 * remaining cycles to 60% SoH, and automatic battery-replacement history resets.
 */
class BatteryHealthAnalyzer(
    private val cycleDropResetThreshold: Int = DEFAULT_CYCLE_DROP_RESET_THRESHOLD
) {
    fun analyze(snapshots: List<BatteryHealthSnapshot>): BatteryHealthSummary? {
        if (snapshots.isEmpty()) return null

        val sorted = snapshots.sortedBy { it.timestampEpochMs }
        val activeHistory = extractActivePackHistory(sorted)
        val baselineCapacityMah = activeHistory.maxOf { it.fullChargeCapacityMah }
        if (baselineCapacityMah <= 0) return null
        val latest = activeHistory.last()
        val stateOfHealthPct = ((latest.fullChargeCapacityMah.toFloat() / baselineCapacityMah.toFloat()) * PERCENT_SCALE)
            .coerceIn(0f, PERCENT_SCALE)
        val status = classifyStatus(stateOfHealthPct)
        val slope = calculateSlopeMahPerCycle(activeHistory)
        val cyclesRemaining = estimateCyclesRemaining(
            latestCapacityMah = latest.fullChargeCapacityMah,
            baselineCapacityMah = baselineCapacityMah,
            stateOfHealthPct = stateOfHealthPct,
            slopeMahPerCycle = slope
        )

        return BatteryHealthSummary(
            currentCapacityMah = latest.fullChargeCapacityMah,
            currentCapacityWh = BatteryCapacity.toWh(latest.fullChargeCapacityMah),
            baselineCapacityMah = baselineCapacityMah,
            stateOfHealthPct = stateOfHealthPct,
            cycleCount = latest.cycleCount,
            status = status,
            slopeMahPerCycle = slope,
            estimatedCyclesRemaining = cyclesRemaining,
            history = activeHistory
        )
    }

    private fun extractActivePackHistory(sorted: List<BatteryHealthSnapshot>): List<BatteryHealthSnapshot> {
        var startIndex = 0
        for (i in 1 until sorted.size) {
            if (sorted[i].cycleCount <= sorted[i - 1].cycleCount - cycleDropResetThreshold) {
                startIndex = i
            }
        }
        return sorted.subList(startIndex, sorted.size)
    }

    private fun classifyStatus(stateOfHealthPct: Float): BatteryHealthStatus = when {
        stateOfHealthPct >= GOOD_SOH_THRESHOLD_PCT -> BatteryHealthStatus.GOOD
        stateOfHealthPct >= DEGRADING_SOH_THRESHOLD_PCT -> BatteryHealthStatus.DEGRADING
        else -> BatteryHealthStatus.REPLACE_SOON
    }

    private fun calculateSlopeMahPerCycle(history: List<BatteryHealthSnapshot>): Float? {
        if (history.asSequence().map { it.cycleCount }.distinct().count() < 2) return null

        val n = history.size.toDouble()
        val sumX = history.sumOf { it.cycleCount.toDouble() }
        val sumY = history.sumOf { it.fullChargeCapacityMah.toDouble() }
        val meanX = sumX / n
        val meanY = sumY / n

        var numerator = 0.0
        var denominator = 0.0
        history.forEach { point ->
            val dx = point.cycleCount - meanX
            val dy = point.fullChargeCapacityMah - meanY
            numerator += dx * dy
            denominator += dx * dx
        }
        if (denominator <= 0.0) return null
        return (numerator / denominator).toFloat()
    }

    private fun estimateCyclesRemaining(
        latestCapacityMah: Int,
        baselineCapacityMah: Int,
        stateOfHealthPct: Float,
        slopeMahPerCycle: Float?
    ): Int? {
        val thresholdMah = baselineCapacityMah * (DEGRADING_SOH_THRESHOLD_PCT / PERCENT_SCALE)
        return when {
            stateOfHealthPct < DEGRADING_SOH_THRESHOLD_PCT || latestCapacityMah <= thresholdMah -> 0
            slopeMahPerCycle == null || slopeMahPerCycle >= MIN_DEGRADATION_SLOPE -> null
            else -> ceil((latestCapacityMah - thresholdMah) / -slopeMahPerCycle).toInt().coerceAtLeast(0)
        }
    }

    companion object {
        const val GOOD_SOH_THRESHOLD_PCT = 80f
        const val DEGRADING_SOH_THRESHOLD_PCT = 60f
        const val DEFAULT_CYCLE_DROP_RESET_THRESHOLD = 5
        private const val PERCENT_SCALE = 100f
        private const val MIN_DEGRADATION_SLOPE = -0.01f
    }
}
