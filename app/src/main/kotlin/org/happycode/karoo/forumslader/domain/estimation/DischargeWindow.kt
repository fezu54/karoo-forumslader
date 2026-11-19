package org.happycode.karoo.forumslader.domain.estimation

import kotlin.collections.ArrayDeque
import org.happycode.karoo.forumslader.domain.ChargeState

/**
 * Tracks virtual discharging distance, voltage recovery bounce protection, EMA-smoothed discharge rate,
 * and windowed consumer power and headwind baselines over a sliding distance window.
 */
class DischargeWindow(
    private val windowMeters: Double,
    private val minMetersForEstimate: Double,
    private val maxStepMeters: Double,
    private val minCredibleDischargeRate: Float,
    private val maxCredibleDischargeRate: Float,
    private val emaAlpha: Float,
    private val initialDischargeRate: Float?,
    private val dischargeRateStore: DischargeRateStore
) {
    private data class VirtualDischargeSample(
        val virtualDistanceMeters: Double,
        val cumulativeDropPct: Int,
        val consumerPowerWatts: Float,
        val headwindSpeedMs: Float?
    )

    private val samples = ArrayDeque<VirtualDischargeSample>()
    private var prevDistanceMeters: Double? = null
    private var prevChargeState: ChargeState? = null
    private var streakMinLevel: Int? = null

    private var cumulativeDischargeMeters: Double = 0.0
    private var cumulativeDischargeDropPct: Int = 0
    private var lastDropVirtualDistanceMeters: Double = 0.0

    var lastDischargeRate: Float? = initialDischargeRate
        private set
    var baselineConsumerPowerWatts: Float? = null
        private set
    var baselineHeadwindSpeedMs: Float? = null
        private set
    private var hasCalculatedRate: Boolean = false

    /**
     * Records a telemetry sample and updates the discharge rate and windowed baselines when discharging.
     */
    fun recordSample(
        chargeState: ChargeState,
        batteryLevelPct: Int,
        tripDistanceMeters: Double,
        consumerPowerWatts: Float,
        headwindSpeedMs: Float?
    ) {
        prevDistanceMeters?.let { prev ->
            if (tripDistanceMeters < prev) reset()
        }

        if (baselineConsumerPowerWatts == null) {
            baselineConsumerPowerWatts = consumerPowerWatts
            baselineHeadwindSpeedMs = headwindSpeedMs?.coerceAtLeast(0f)
        }

        when (chargeState) {
            ChargeState.DISCHARGING -> handleDischargingSample(
                batteryLevelPct,
                tripDistanceMeters,
                consumerPowerWatts,
                headwindSpeedMs
            )
            else -> streakMinLevel = null
        }

        prevDistanceMeters = tripDistanceMeters
        prevChargeState = chargeState
    }

    /**
     * Clears all accumulated samples and resets rate and baseline state back to initial values.
     */
    fun reset() {
        samples.clear()
        lastDischargeRate = initialDischargeRate
        hasCalculatedRate = false
        cumulativeDischargeMeters = 0.0
        cumulativeDischargeDropPct = 0
        streakMinLevel = null
        lastDropVirtualDistanceMeters = 0.0
        prevDistanceMeters = null
        prevChargeState = null
        baselineConsumerPowerWatts = null
        baselineHeadwindSpeedMs = null
    }

    private fun handleDischargingSample(
        currentLevel: Int,
        currentDistance: Double,
        consumerPowerWatts: Float,
        headwindSpeedMs: Float?
    ) {
        if (prevChargeState != ChargeState.DISCHARGING || streakMinLevel == null) {
            initDischargeStreak(currentLevel, consumerPowerWatts, headwindSpeedMs)
        }

        advanceVirtualDischargeDistance(currentDistance)
        val droppedThisStep = recordStreakDrop(currentLevel)
        appendDischargeSample(consumerPowerWatts, headwindSpeedMs)
        pruneSlidingWindow()
        updateDischargeRate(currentLevel, droppedThisStep)
    }

    private fun initDischargeStreak(
        currentLevel: Int,
        consumerPowerWatts: Float,
        headwindSpeedMs: Float?
    ) {
        streakMinLevel = currentLevel
        lastDropVirtualDistanceMeters = cumulativeDischargeMeters
        if (samples.isEmpty()) {
            samples.addLast(
                VirtualDischargeSample(
                    cumulativeDischargeMeters,
                    cumulativeDischargeDropPct,
                    consumerPowerWatts,
                    headwindSpeedMs
                )
            )
        }
    }

    private fun advanceVirtualDischargeDistance(currentDistance: Double) {
        val deltaMeters = prevDistanceMeters?.takeIf { prevChargeState == ChargeState.DISCHARGING }
            ?.let { currentDistance - it } ?: 0.0

        if (deltaMeters in MIN_STEP_METERS..maxStepMeters) {
            cumulativeDischargeMeters += deltaMeters
        }
    }

    private fun recordStreakDrop(currentLevel: Int): Boolean {
        val minLvl = streakMinLevel ?: currentLevel
        val dropped = currentLevel < minLvl
        if (dropped) {
            cumulativeDischargeDropPct += (minLvl - currentLevel)
            streakMinLevel = currentLevel
            lastDropVirtualDistanceMeters = cumulativeDischargeMeters
        }
        return dropped
    }

    private fun appendDischargeSample(consumerPowerWatts: Float, headwindSpeedMs: Float?) {
        val shouldAppend = samples.isEmpty() || samples.last().let { last ->
            last.virtualDistanceMeters != cumulativeDischargeMeters ||
                last.cumulativeDropPct != cumulativeDischargeDropPct
        }
        if (shouldAppend) {
            samples.addLast(
                VirtualDischargeSample(
                    cumulativeDischargeMeters,
                    cumulativeDischargeDropPct,
                    consumerPowerWatts,
                    headwindSpeedMs
                )
            )
        }
    }

    private fun pruneSlidingWindow() {
        while (samples.size > 2 && (cumulativeDischargeMeters - samples.first().virtualDistanceMeters) > windowMeters) {
            samples.removeFirst()
        }
    }

    private fun isVoltageRecoveryBounce(currentLevel: Int): Boolean =
        streakMinLevel?.let { currentLevel > it } ?: false

    private fun updateDischargeRate(currentLevel: Int, droppedThisStep: Boolean) {
        if (samples.size < 2 || isVoltageRecoveryBounce(currentLevel)) return

        val oldest = samples.first()
        val newest = samples.last()
        val distanceDiffMeters = newest.virtualDistanceMeters - oldest.virtualDistanceMeters
        val levelDiff = newest.cumulativeDropPct - oldest.cumulativeDropPct

        resolveUpdatedRate(levelDiff, distanceDiffMeters, droppedThisStep)?.let { rate ->
            if (!hasCalculatedRate || rate != lastDischargeRate) {
                lastDischargeRate = rate
                hasCalculatedRate = true
                refreshWindowBaselines()
                dischargeRateStore.save(rate)
            }
        }
    }

    private fun refreshWindowBaselines() {
        baselineConsumerPowerWatts = samples.map { it.consumerPowerWatts.toDouble() }
            .average()
            .toFloat()
        val windSamples = samples.mapNotNull { it.headwindSpeedMs?.coerceAtLeast(0f) }
        baselineHeadwindSpeedMs = when {
            windSamples.isNotEmpty() -> windSamples.map { it.toDouble() }.average().toFloat()
            else -> null
        }
    }

    private fun resolveUpdatedRate(
        levelDiff: Int,
        distanceDiffMeters: Double,
        droppedOnCurrentStep: Boolean
    ): Float? =
        takeIf { distanceDiffMeters >= minMetersForEstimate && levelDiff > 0 }
            ?.let { (levelDiff.toFloat() / (distanceDiffMeters / METERS_PER_KM).toFloat()) }
            ?.coerceIn(minCredibleDischargeRate, maxCredibleDischargeRate)
            ?.let { candidateRate -> blendWithPriorRate(candidateRate, droppedOnCurrentStep) }

    private fun blendWithPriorRate(candidateRate: Float, droppedOnCurrentStep: Boolean): Float =
        when {
            !hasCalculatedRate -> candidateRate
            else -> lastDischargeRate?.let { priorRate ->
                when {
                    droppedOnCurrentStep -> (emaAlpha * candidateRate) + ((1f - emaAlpha) * priorRate)
                    shouldDecayRate(candidateRate, priorRate) -> candidateRate
                    else -> priorRate
                }
            } ?: candidateRate
        }

    private fun shouldDecayRate(candidateRate: Float, priorRate: Float): Boolean {
        val impliedStepMeters = maxOf(minMetersForEstimate, (1.0f / priorRate) * METERS_PER_KM)
        val distanceSinceLastDrop = cumulativeDischargeMeters - lastDropVirtualDistanceMeters
        return distanceSinceLastDrop > impliedStepMeters && candidateRate < priorRate
    }

    companion object {
        private const val MIN_STEP_METERS = 0.0001
        private const val METERS_PER_KM = 1000.0
    }
}
