package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.RideEnergyAccumulator
import org.happycode.karoo.forumslader.domain.RideHistoryGateway

class RideEnergyOrchestrator(
    private val accumulator: RideEnergyAccumulator,
    private val historyGateway: RideHistoryGateway
) {
    fun onRecordingStarted() {
        if (accumulator.rideStartTime == null || accumulator.rideEndTime != null) {
            accumulator.start()
        }
    }

    suspend fun onRideIdle() {
        if (accumulator.rideStartTime != null && accumulator.rideEndTime == null) {
            accumulator.stop()
            accumulator.toSummary()?.let { summary ->
                historyGateway.saveSummary(summary)
            }
        }
    }

    fun onMetricsReceived(metrics: ForumsladerMetrics, deltaSec: Float) {
        accumulator.update(metrics, deltaSec)
    }
}
