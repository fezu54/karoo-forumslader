package org.happycode.karoo.forumslader.domain

import kotlin.collections.ArrayDeque

class BatteryEstimator(
    private val windowMeters: Double = 20000.0,
    private val minMetersForEstimate: Double = 1000.0,
    private val elevationPenaltyPctPer100m: Float = 1.5f,
    private val headwindPenaltyPctPerKmPerMs: Float = 0.5f,
    private val maxStepMeters: Double = MAX_STEP_METERS,
    private val minCredibleDischargeRate: Float = MIN_CREDIBLE_DISCHARGE_RATE,
    private val maxCredibleDischargeRate: Float = MAX_CREDIBLE_DISCHARGE_RATE,
    private val emaAlpha: Float = EMA_ALPHA,
    private val initialDischargeRate: Float? = DEFAULT_DISCHARGE_RATE,
    private val dischargeRateStore: DischargeRateStore = DischargeRateStore {}
) {
    private data class VirtualDischargeSample(
        val virtualDistanceMeters: Double,
        val cumulativeDropPct: Int,
        val elevationMeters: Double? = null
    )

    private val samples = ArrayDeque<VirtualDischargeSample>()
    private var prevDistanceMeters: Double? = null
    private var prevElevationMeters: Double? = null
    private var prevChargeState: ChargeState? = null
    private var streakMinLevel: Int? = null

    private var cumulativeDischargeMeters: Double = 0.0
    private var cumulativeDischargeDropPct: Int = 0
    private var lastDischargeRate: Float? = initialDischargeRate
    private var hasCalculatedRate: Boolean = false
    private var lastDropVirtualDistanceMeters: Double = 0.0

    private var lastBatteryLevelPct: Int? = null
    private var lastChargeState: ChargeState? = null

    private var routeRemainingKm: Float? = null
    private var upcomingElevationMeters: Double? = null
    private var headwindSpeedMs: Float? = null

    fun onMetrics(metrics: ForumsladerMetrics) {
        val currentLevel = metrics.power.batteryLevelPercentage ?: return
        val currentDistance = metrics.distance.tripMeters
        val currentState = metrics.power.chargeState
        val elevation = metrics.environment.altitudeMeters.toDouble()

        prevDistanceMeters?.let {
            if (currentDistance < it) reset()
        }

        lastBatteryLevelPct = currentLevel
        lastChargeState = currentState

        if (currentState == ChargeState.DISCHARGING) {
            handleDischargingMetric(currentLevel, currentDistance, elevation)
        } else {
            streakMinLevel = null
        }

        prevDistanceMeters = currentDistance
        prevElevationMeters = elevation
        prevChargeState = currentState
    }

    private fun handleDischargingMetric(
        currentLevel: Int,
        currentDistance: Double,
        elevation: Double
    ) {
        if (prevChargeState != ChargeState.DISCHARGING || streakMinLevel == null) {
            initDischargeStreak(currentLevel, elevation)
        }

        advanceVirtualDischargeDistance(currentDistance)
        val droppedThisStep = recordStreakDrop(currentLevel)
        appendDischargeSample(elevation)
        pruneSlidingWindow()
        updateDischargeRate(currentLevel, droppedThisStep)
    }

    private fun initDischargeStreak(currentLevel: Int, elevation: Double) {
        streakMinLevel = currentLevel
        lastDropVirtualDistanceMeters = cumulativeDischargeMeters
        if (samples.isEmpty()) {
            samples.addLast(
                VirtualDischargeSample(
                    cumulativeDischargeMeters,
                    cumulativeDischargeDropPct,
                    elevation
                )
            )
        }
    }

    private fun advanceVirtualDischargeDistance(currentDistance: Double) {
        val deltaMeters = prevDistanceMeters?.takeIf { prevChargeState == ChargeState.DISCHARGING }
            ?.let { currentDistance - it } ?: 0.0

        if (deltaMeters in 0.0001..maxStepMeters) {
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

    private fun appendDischargeSample(elevation: Double) {
        val shouldAppend = samples.isEmpty() || samples.last().let { last ->
            last.virtualDistanceMeters != cumulativeDischargeMeters || last.cumulativeDropPct != cumulativeDischargeDropPct
        }
        if (shouldAppend) {
            samples.addLast(
                VirtualDischargeSample(
                    cumulativeDischargeMeters,
                    cumulativeDischargeDropPct,
                    elevation
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
                dischargeRateStore.save(rate)
            }
        }
    }

    private fun resolveUpdatedRate(
        levelDiff: Int,
        distanceDiffMeters: Double,
        droppedOnCurrentStep: Boolean
    ): Float? =
        takeIf { distanceDiffMeters >= minMetersForEstimate && levelDiff > 0 }
            ?.let { calculateAdjustedDischargeRate(levelDiff.toFloat(), distanceDiffMeters) }
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
        val impliedStepMeters = maxOf(minMetersForEstimate, (1.0f / priorRate) * 1000.0)
        val distanceSinceLastDrop = cumulativeDischargeMeters - lastDropVirtualDistanceMeters
        return distanceSinceLastDrop > impliedStepMeters && candidateRate < priorRate
    }

    private fun reset() {
        samples.clear()
        lastDischargeRate = initialDischargeRate
        hasCalculatedRate = false
        cumulativeDischargeMeters = 0.0
        cumulativeDischargeDropPct = 0
        streakMinLevel = null
        lastDropVirtualDistanceMeters = 0.0
        prevDistanceMeters = null
        prevElevationMeters = null
        prevChargeState = null
    }

    fun onRouteRemaining(distanceMeters: Double?, upcomingElevation: Double?) {
        routeRemainingKm = distanceMeters?.let { (it / 1000.0).toFloat() }
        upcomingElevationMeters = upcomingElevation
    }

    fun onHeadwindSpeed(speedMs: Float?) {
        headwindSpeedMs = speedMs
    }

    fun getEstimate(): BatteryEstimate? {
        val currentLevel = lastBatteryLevelPct ?: return null
        val currentState = lastChargeState ?: return null
        return buildEstimate(rate = lastDischargeRate, currentLevel, currentState)
    }

    private fun buildEstimate(
        rate: Float?,
        currentLevel: Int,
        currentState: ChargeState
    ): BatteryEstimate {
        val effectiveRate = calculateEffectiveRate(rate)
        val estimatedRangeKm = effectiveRate?.takeIf { it > 0f }?.let { dischargeRate ->
            val adjustedCapacity = calculateElevationAdjustedCapacity(currentLevel.toFloat())
            (adjustedCapacity / dischargeRate).takeIf { range -> range.isFinite() }
        }
        val avgDischargeRatePctPerKm = when (currentState) {
            ChargeState.CHARGING, ChargeState.FULL -> 0f
            else -> effectiveRate ?: 0f
        }
        val isSufficientForRoute = routeRemainingKm?.let { remaining ->
            estimatedRangeKm?.let { it >= remaining }
                ?: when (currentState) {
                    ChargeState.CHARGING, ChargeState.FULL -> true
                    else -> null
                }
        }
        return BatteryEstimate(
            currentLevel,
            avgDischargeRatePctPerKm,
            estimatedRangeKm,
            routeRemainingKm,
            isSufficientForRoute,
            chargeState = currentState
        )
    }

    private fun calculateEffectiveRate(rate: Float?): Float? =
        rate?.takeIf { it > 0f }?.let { baseRate ->
            val headwind = headwindSpeedMs?.coerceAtLeast(0f) ?: 0f
            (baseRate + (headwind * headwindPenaltyPctPerKmPerMs))
                .coerceIn(minCredibleDischargeRate, maxCredibleDischargeRate)
        }

    private fun calculateAdjustedDischargeRate(
        levelDiff: Float,
        distanceDiffMeters: Double
    ): Float {
        val distanceDiffKm = (distanceDiffMeters / 1000.0).toFloat()
        return levelDiff / distanceDiffKm
    }

    private fun calculateElevationAdjustedCapacity(currentLevel: Float): Float {
        val elevation = upcomingElevationMeters?.coerceAtLeast(0.0) ?: 0.0
        val elevationPenalty = (elevation / 100.0).toFloat() * elevationPenaltyPctPer100m

        return (currentLevel - elevationPenalty).coerceAtLeast(0f)
    }

    companion object {
        const val DEFAULT_DISCHARGE_RATE = 1.0f
        const val MIN_CREDIBLE_DISCHARGE_RATE = 0.15f
        const val MAX_CREDIBLE_DISCHARGE_RATE = 5.0f
        private const val EMA_ALPHA = 0.35f
        const val MAX_STEP_METERS = 1_000.0
    }
}
