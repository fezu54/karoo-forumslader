package org.happycode.karoo.forumslader.domain.estimation

import org.happycode.karoo.forumslader.domain.ChargeState

data class BatteryEstimate(
    val remainingCapacityPct: Int,
    val avgDischargeRatePctPerKm: Float,
    val estimatedRangeKm: Float?,
    val routeRemainingKm: Float?,
    val isSufficientForRoute: Boolean?,
    val chargeState: ChargeState = ChargeState.DISCHARGING
)
