package org.happycode.karoo.forumslader.domain

import kotlin.math.roundToLong

class RideEnergyAccumulator(
    private val timeProvider: () -> Long = System::currentTimeMillis
) {
    var isRecording: Boolean = false
        private set

    private var totalEnergyWh: Float = 0f
    private var weightedPowerSumWSec: Float = 0f
    private var totalDurationSec: Float = 0f
    private var peakPowerW: Float = 0f
    private var chargingTimeSecFloat: Float = 0f
    private var dischargingTimeSecFloat: Float = 0f
    private var standbyTimeSecFloat: Float = 0f
    private var batteryStartPct: Int? = null
    private var batteryEndPct: Int? = null
    private var hasSamples: Boolean = false
    private var lastSampleTimestampMs: Long? = null

    fun onRideStart() {
        isRecording = true
        lastSampleTimestampMs = null
    }

    fun onRideResume() {
        isRecording = true
        lastSampleTimestampMs = null
    }

    fun onRidePause() {
        isRecording = false
        lastSampleTimestampMs = null
    }

    fun onMetrics(metrics: ForumsladerMetrics) {
        if (!isRecording) return

        val now = timeProvider()
        val deltaTimeSec = lastSampleTimestampMs?.let { previous ->
            ((now - previous) / MILLIS_PER_SECOND).coerceIn(0f, MAX_DELTA_TIME_SEC)
        } ?: 0f
        lastSampleTimestampMs = now

        update(metrics, deltaTimeSec)
    }

    fun update(metrics: ForumsladerMetrics, deltaTimeSec: Float) {
        if (deltaTimeSec < 0f) return

        hasSamples = true
        val powerW = metrics.power.dynamoPowerWatts.coerceAtLeast(0f)
        peakPowerW = maxOf(peakPowerW, powerW)

        metrics.power.batteryLevelPercentage?.let { level ->
            if (batteryStartPct == null) {
                batteryStartPct = level
            }
            batteryEndPct = level
        }

        if (deltaTimeSec > 0f) {
            totalEnergyWh += (powerW * deltaTimeSec) / SECONDS_PER_HOUR
            weightedPowerSumWSec += powerW * deltaTimeSec
            totalDurationSec += deltaTimeSec

            when (metrics.power.chargeState) {
                ChargeState.CHARGING -> chargingTimeSecFloat += deltaTimeSec
                ChargeState.DISCHARGING -> dischargingTimeSecFloat += deltaTimeSec
                ChargeState.STANDBY, ChargeState.FULL -> standbyTimeSecFloat += deltaTimeSec
            }
        }
    }

    fun toSummary(): RideEnergySummary {
        val avgPowerW = when {
            totalDurationSec > 0f -> weightedPowerSumWSec / totalDurationSec
            else -> 0f
        }
        return RideEnergySummary(
            totalEnergyWh = totalEnergyWh,
            avgPowerW = avgPowerW,
            peakPowerW = peakPowerW,
            chargingTimeSec = chargingTimeSecFloat.roundToLong(),
            dischargingTimeSec = dischargingTimeSecFloat.roundToLong(),
            standbyTimeSec = standbyTimeSecFloat.roundToLong(),
            batteryStartPct = batteryStartPct,
            batteryEndPct = batteryEndPct,
            hasData = hasSamples
        )
    }

    fun reset() {
        isRecording = false
        totalEnergyWh = 0f
        weightedPowerSumWSec = 0f
        totalDurationSec = 0f
        peakPowerW = 0f
        chargingTimeSecFloat = 0f
        dischargingTimeSecFloat = 0f
        standbyTimeSecFloat = 0f
        batteryStartPct = null
        batteryEndPct = null
        hasSamples = false
        lastSampleTimestampMs = null
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000f
        private const val SECONDS_PER_HOUR = 3600f
        const val MAX_DELTA_TIME_SEC = 30f
    }
}
