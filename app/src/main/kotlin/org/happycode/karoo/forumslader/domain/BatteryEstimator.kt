package org.happycode.karoo.forumslader.domain

import kotlin.collections.ArrayDeque

/**
 * Estimates battery range based on a sliding window of distance/percentage samples.
 * Accounts for environmental factors like elevation and headwind.
 */
class BatteryEstimator(
    private val windowMeters: Double = 10000.0,
    private val minMetersForEstimate: Double = 500.0,
    private val elevationPenaltyPctPer100m: Float = 1.5f,
    private val headwindPenaltyPctPerKmPerMs: Float = 0.5f
) {
    private val samples = ArrayDeque<Sample>()
    private var routeRemainingKm: Float? = null
    private var upcomingElevationMeters: Double? = null
    private var headwindSpeedMs: Float? = null

    private data class Sample(val distanceMeters: Double, val batteryLevelPct: Int, val chargeState: ChargeState)

    fun onMetrics(metrics: ForumsladerMetrics) {
        val level = metrics.power.batteryLevelPercentage ?: return
        val dist = metrics.distance.tripMeters
        val state = metrics.power.chargeState

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
        val firstSample = samples.first()

        val currentLevel = lastSample.batteryLevelPct
        val distanceDiffMeters = lastSample.distanceMeters - firstSample.distanceMeters
        val currentState = lastSample.chargeState
        val levelDiff = (firstSample.batteryLevelPct - currentLevel).toFloat()

        return when {
            currentState == ChargeState.CHARGING || currentState == ChargeState.FULL -> BatteryEstimate(
                remainingCapacityPct = currentLevel,
                avgDischargeRatePctPerKm = 0f,
                estimatedRangeKm = null,
                routeRemainingKm = routeRemainingKm,
                isSufficientForRoute = true,
                chargeState = currentState
            )
            currentState == ChargeState.STANDBY || distanceDiffMeters < minMetersForEstimate || levelDiff <= 0 -> BatteryEstimate(
                remainingCapacityPct = currentLevel,
                avgDischargeRatePctPerKm = 0f,
                estimatedRangeKm = null,
                routeRemainingKm = routeRemainingKm,
                isSufficientForRoute = null,
                chargeState = currentState
            )
            else -> calculateDischargingEstimate(levelDiff, distanceDiffMeters, currentLevel, currentState)
        }
    }

    private fun calculateDischargingEstimate(
        levelDiff: Float, 
        distanceDiffMeters: Double, 
        currentLevel: Int, 
        currentState: ChargeState
    ): BatteryEstimate {
        val dischargeRate = calculateAdjustedDischargeRate(levelDiff, distanceDiffMeters)
        val adjustedCapacity = calculateElevationAdjustedCapacity(currentLevel.toFloat())

        val estimatedRangeKm = (adjustedCapacity / dischargeRate).takeIf { it.isFinite() }
        val isSufficient = routeRemainingKm?.let { remaining ->
            estimatedRangeKm?.let { range -> range >= remaining }
        }

        return BatteryEstimate(
            remainingCapacityPct = currentLevel,
            avgDischargeRatePctPerKm = dischargeRate,
            estimatedRangeKm = estimatedRangeKm,
            routeRemainingKm = routeRemainingKm,
            isSufficientForRoute = isSufficient,
            chargeState = currentState
        )
    }

    private fun calculateAdjustedDischargeRate(levelDiff: Float, distanceDiffMeters: Double): Float {
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
