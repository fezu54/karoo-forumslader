package org.happycode.karoo.forumslader.domain

/**
 * Represents a prediction of battery range based on current power draw and route context.
 */
data class BatteryEstimate(
    val remainingCapacityPct: Int,
    val avgDischargeRatePctPerKm: Float,
    val estimatedRangeKm: Float?,
    val routeRemainingKm: Float?,
    val isSufficientForRoute: Boolean?
)
