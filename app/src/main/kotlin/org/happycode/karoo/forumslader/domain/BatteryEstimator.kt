package org.happycode.karoo.forumslader.domain

import kotlin.collections.ArrayDeque

/**
 * Estimates battery range based on a sliding window of distance/percentage samples.
 * Accounts for environmental factors like elevation and headwind.
 */
class BatteryEstimator(
    private val windowMeters: Double = 20000.0,
    private val minMetersForEstimate: Double = 1000.0,
    private val elevationPenaltyPctPer100m: Float = 1.5f,
    private val headwindPenaltyPctPerKmPerMs: Float = 0.5f
) {
    private val samples = ArrayDeque<Sample>()
    private var routeRemainingKm: Float? = null
    private var upcomingElevationMeters: Double? = null
    private var headwindSpeedMs: Float? = null
    private var lastDischargeRate: Float? = null

    private data class Sample(
        val distanceMeters: Double,
        val batteryLevelPct: Int,
        val chargeState: ChargeState
    )

    fun onMetrics(metrics: ForumsladerMetrics) {
        val level = metrics.power.batteryLevelPercentage ?: return
        val dist = metrics.distance.tripMeters
        val state = metrics.power.chargeState

        if (samples.isNotEmpty() && dist < samples.last().distanceMeters) {
            samples.clear()
            lastDischargeRate = null
        }

        samples.addLast(Sample(dist, level, state))

        // Maintain sliding window
        while (samples.isNotEmpty() && (dist - samples.first().distanceMeters > windowMeters)) {
            samples.removeFirst()
        }
    }

    fun onRouteRemaining(distanceMeters: Double?, upcomingElevation: Double?) {
        routeRemainingKm = distanceMeters?.let { (it / 1000.0).toFloat() }
        upcomingElevationMeters = upcomingElevation
    }

    fun onHeadwindSpeed(speedMs: Float?) {
        headwindSpeedMs = speedMs
    }

    fun getEstimate(): BatteryEstimate? {
        val lastSample = samples.lastOrNull() ?: return null
        val currentState = lastSample.chargeState
        val currentLevel = lastSample.batteryLevelPct

        val initialFirst = samples.first()
        val initialLevelDiff = (initialFirst.batteryLevelPct - currentLevel).toFloat()

        val isNewDischargeWithoutLevelDrop =
            currentState == ChargeState.DISCHARGING && lastDischargeRate == null && initialLevelDiff <= 0

        if (isNewDischargeWithoutLevelDrop) {
            pruneNonDischargingSamples()
        }

        val firstSample =
            samples.firstOrNull() ?: return buildEstimate(rate = null, currentLevel, currentState)
        val distanceDiffMeters = lastSample.distanceMeters - firstSample.distanceMeters
        val levelDiff = (firstSample.batteryLevelPct - currentLevel).toFloat()

        return when {
            currentState == ChargeState.CHARGING || currentState == ChargeState.FULL -> BatteryEstimate(
                remainingCapacityPct = currentLevel,
                avgDischargeRatePctPerKm = 0f,
                estimatedRangeKm = null,
                routeRemainingKm = routeRemainingKm,
                isSufficientForRoute = routeRemainingKm?.let { true },
                chargeState = currentState
            )

            currentState == ChargeState.STANDBY || distanceDiffMeters < minMetersForEstimate || levelDiff <= 0 ->
                buildEstimate(rate = lastDischargeRate, currentLevel, currentState)

            else -> {
                val rate = calculateAdjustedDischargeRate(levelDiff, distanceDiffMeters)
                lastDischargeRate = rate
                buildEstimate(rate, currentLevel, currentState)
            }
        }
    }

    private fun buildEstimate(
        rate: Float?,
        currentLevel: Int,
        currentState: ChargeState
    ): BatteryEstimate {
        val estimatedRangeKm = rate?.takeIf { it > 0f }?.let { dischargeRate ->
            val adjustedCapacity = calculateElevationAdjustedCapacity(currentLevel.toFloat())
            (adjustedCapacity / dischargeRate).takeIf { range -> range.isFinite() }
        }
        return BatteryEstimate(
            remainingCapacityPct = currentLevel,
            avgDischargeRatePctPerKm = rate ?: 0f,
            estimatedRangeKm = estimatedRangeKm,
            routeRemainingKm = routeRemainingKm,
            isSufficientForRoute = routeRemainingKm?.let { remaining ->
                estimatedRangeKm?.let { range -> range >= remaining }
            },
            chargeState = currentState
        )
    }

    private fun pruneNonDischargingSamples() =
        samples.indexOfLast { it.chargeState != ChargeState.DISCHARGING }
            .takeIf { it >= 0 }
            ?.let { lastNonDischargingIndex ->
                repeat(lastNonDischargingIndex + 1) { samples.removeFirst() }
            }

    private fun calculateAdjustedDischargeRate(
        levelDiff: Float,
        distanceDiffMeters: Double
    ): Float {
        val distanceDiffKm = (distanceDiffMeters / 1000.0).toFloat()
        val baseRate = levelDiff / distanceDiffKm
        val headwind = headwindSpeedMs?.coerceAtLeast(0f) ?: 0f

        return baseRate + (headwind * headwindPenaltyPctPerKmPerMs)
    }

    private fun calculateElevationAdjustedCapacity(currentLevel: Float): Float {
        val elevation = upcomingElevationMeters?.coerceAtLeast(0.0) ?: 0.0
        val elevationPenalty = (elevation / 100.0).toFloat() * elevationPenaltyPctPer100m

        return (currentLevel - elevationPenalty).coerceAtLeast(0f)
    }
}
