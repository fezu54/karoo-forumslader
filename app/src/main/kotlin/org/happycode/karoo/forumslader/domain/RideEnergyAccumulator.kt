package org.happycode.karoo.forumslader.domain

import java.time.Instant

class RideEnergyAccumulator(
    private val clock: () -> Instant = { Instant.now() }
) {
    var totalEnergyWh: Float = 0f
        private set
    var peakPowerW: Float = 0f
        private set
    var avgPowerW: Float = 0f
        private set
    var timeChargingSec: Float = 0f
        private set
    var timeDischargingSec: Float = 0f
        private set
    var timeStandbySec: Float = 0f
        private set
    var rideStartTime: Instant? = null
        private set
    var rideEndTime: Instant? = null
        private set

    private var totalOperatingTimeSec: Float = 0f
    private var batteryStartPct: Int? = null
    private var currentBatteryPct: Int? = null
    private var startDistanceMeters: Double? = null
    private var currentDistanceMeters: Double? = null

    fun start() {
        rideStartTime = clock()
        rideEndTime = null
        totalEnergyWh = 0f
        peakPowerW = 0f
        avgPowerW = 0f
        timeChargingSec = 0f
        timeDischargingSec = 0f
        timeStandbySec = 0f
        totalOperatingTimeSec = 0f
        batteryStartPct = null
        startDistanceMeters = null
    }

    fun stop() {
        if (rideStartTime != null && rideEndTime == null) {
            rideEndTime = clock()
        }
    }

    fun update(metrics: ForumsladerMetrics, deltaTimeSec: Float) {
        if (rideStartTime == null || rideEndTime != null) return

        if (batteryStartPct == null) {
            batteryStartPct = metrics.power.batteryLevelPercentage
        }
        currentBatteryPct = metrics.power.batteryLevelPercentage

        if (startDistanceMeters == null) {
            startDistanceMeters = metrics.distance.odometerMeters
        }
        currentDistanceMeters = metrics.distance.odometerMeters

        val power = metrics.power.dynamoPowerWatts
        if (power > peakPowerW) {
            peakPowerW = power
        }

        totalEnergyWh += power * (deltaTimeSec / 3600f)

        totalOperatingTimeSec += deltaTimeSec
        if (totalOperatingTimeSec > 0) {
            avgPowerW = avgPowerW + (power - avgPowerW) * (deltaTimeSec / totalOperatingTimeSec)
        }

        when (metrics.power.chargeState) {
            ChargeState.CHARGING -> timeChargingSec += deltaTimeSec
            ChargeState.DISCHARGING -> timeDischargingSec += deltaTimeSec
            ChargeState.STANDBY, ChargeState.FULL -> timeStandbySec += deltaTimeSec
        }
    }

    fun toSummary(): RideEnergySummary? {
        val start = rideStartTime ?: return null
        val duration = java.time.Duration.between(start, rideEndTime ?: clock()).seconds
        val distance = ((currentDistanceMeters ?: 0.0) - (startDistanceMeters ?: 0.0)) / 1000.0

        return RideEnergySummary(
            rideDate = start,
            durationSec = duration,
            distanceKm = distance.toFloat(),
            totalEnergyWh = totalEnergyWh,
            avgPowerW = avgPowerW,
            peakPowerW = peakPowerW,
            chargingTimeSec = timeChargingSec.toLong(),
            dischargingTimeSec = timeDischargingSec.toLong(),
            standbyTimeSec = timeStandbySec.toLong(),
            batteryStartPct = batteryStartPct ?: 0,
            batteryEndPct = currentBatteryPct ?: 0
        )
    }
}
