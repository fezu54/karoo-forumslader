package org.happycode.karoo.forumslader.application.estimation

import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.estimation.GradientProfile
import org.happycode.karoo.forumslader.domain.estimation.RiderProfileRepository

class RecordRiderProfileUseCase(
    private val repository: RiderProfileRepository
) {
    suspend fun onMetricsAndGradient(
        metrics: ForumsladerMetrics,
        gradientPct: Float?
    ): GradientProfile? {
        val validGradient = gradientPct?.takeIf { !it.isNaN() && isRecordable(metrics) } ?: return null
        val bucket = GradientProfile.bucketFor(validGradient)
        val speedMs = metrics.dynamics.speedMetersPerSecond
        val frequencyHz = metrics.dynamics.frequency
        val dynamoWatts = metrics.power.dynamoPowerWatts

        return (repository.findByGradient(bucket)
            ?.updatedWith(speedMs, frequencyHz, dynamoWatts)
            ?: GradientProfile(bucket, speedMs, frequencyHz, dynamoWatts))
            .also { repository.save(it) }
    }

    private fun isRecordable(metrics: ForumsladerMetrics): Boolean =
        metrics.dynamics.speedMetersPerSecond >= MIN_MOVING_SPEED_MS &&
            metrics.dynamics.frequency > MIN_ACTIVE_FREQUENCY_HZ &&
            metrics.power.chargeState != ChargeState.FULL

    companion object {
        private const val MIN_MOVING_SPEED_MS = 1.2f
        private const val MIN_ACTIVE_FREQUENCY_HZ = 5.0f
    }
}
