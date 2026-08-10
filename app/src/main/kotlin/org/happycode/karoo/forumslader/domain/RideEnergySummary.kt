package org.happycode.karoo.forumslader.domain

import java.time.Instant

data class RideEnergySummary(
    val rideDate: Instant,
    val durationSec: Long,
    val distanceKm: Float,
    val totalEnergyWh: Float,
    val avgPowerW: Float,
    val peakPowerW: Float,
    val chargingTimeSec: Long,
    val dischargingTimeSec: Long,
    val standbyTimeSec: Long,
    val batteryStartPct: Int,
    val batteryEndPct: Int
)
