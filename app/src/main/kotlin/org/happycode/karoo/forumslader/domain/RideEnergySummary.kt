package org.happycode.karoo.forumslader.domain

data class RideEnergySummary(
    val totalEnergyWh: Float,
    val avgPowerW: Float,
    val peakPowerW: Float,
    val chargingTimeSec: Long,
    val dischargingTimeSec: Long,
    val standbyTimeSec: Long,
    val batteryStartPct: Int?,
    val batteryEndPct: Int?,
    val hasData: Boolean = false
) {
    val netBatteryChangePct: Int?
        get() = batteryStartPct?.let { start ->
            batteryEndPct?.let { end -> end - start }
        }
}
