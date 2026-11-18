package org.happycode.karoo.forumslader.application.estimation

import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.estimation.GradientProfile
import org.happycode.karoo.forumslader.domain.estimation.RiderProfileRepository

/**
 * Learns per-gradient rider speed, dynamo frequency, and power output into an in-memory cache,
 * optionally gating updates by distance traveled and batching repository writes via [flush].
 */
class RecordRiderProfileUseCase(
    private val repository: RiderProfileRepository,
    private val persistImmediately: Boolean = true,
    private val minDistanceStepMeters: Double = 0.0
) {
    private val cachedProfiles = mutableMapOf<Int, GradientProfile>()
    private val dirtyBuckets = mutableSetOf<Int>()
    private val accumulatedMetersByBucket = mutableMapOf<Int, Double>()
    private var lastTripMeters: Double? = null
    private var hasLoadedProfiles: Boolean = false

    /**
     * Seeds the in-memory profile cache with previously persisted profiles to avoid per-packet reads.
     */
    fun onProfilesLoaded(profiles: Map<Int, GradientProfile>) {
        cachedProfiles.clear()
        cachedProfiles.putAll(profiles)
        dirtyBuckets.clear()
        hasLoadedProfiles = true
    }

    /**
     * Records a telemetry observation for [gradientPct] when the rider is moving,
     * distance gating is satisfied, and full-battery hysteresis cutoffs are excluded from power learning.
     */
    suspend fun onMetricsAndGradient(
        metrics: ForumsladerMetrics,
        gradientPct: Float?
    ): GradientProfile? {
        val stepMeters = advanceTripDistance(metrics.distance.tripMeters)
        val validGradient = gradientPct?.takeIf { !it.isNaN() && isRecordable(metrics) } ?: return null
        val bucket = GradientProfile.bucketFor(validGradient)

        if (!consumeDistanceThreshold(bucket, stepMeters)) {
            return null
        }

        val speedMs = metrics.dynamics.speedMetersPerSecond
        val frequencyHz = metrics.dynamics.frequency
        val effectiveDynamoWatts = if (isHysteresisCutoff(metrics)) null else metrics.power.dynamoPowerWatts

        val existing = cachedProfiles[bucket] ?: if (hasLoadedProfiles) null else repository.findByGradient(bucket)
        val updated = existing?.updatedWith(speedMs, frequencyHz, effectiveDynamoWatts)
            ?: GradientProfile(bucket, speedMs, frequencyHz, effectiveDynamoWatts ?: 0f)

        cachedProfiles[bucket] = updated
        if (persistImmediately) {
            repository.save(updated)
        } else {
            dirtyBuckets.add(bucket)
        }
        return updated
    }

    /**
     * Persists all modified gradient buckets to [repository] in a single batch.
     */
    suspend fun flush() {
        if (dirtyBuckets.isEmpty()) return
        val bucketsToSave = dirtyBuckets.toList()
        dirtyBuckets.clear()
        bucketsToSave.forEach { bucket ->
            cachedProfiles[bucket]?.let { repository.save(it) }
        }
    }

    private fun advanceTripDistance(currentTripMeters: Double): Double {
        val previousMeters = lastTripMeters
        if (previousMeters != null && currentTripMeters < previousMeters) {
            accumulatedMetersByBucket.clear()
        }
        lastTripMeters = currentTripMeters
        return previousMeters
            ?.let { (currentTripMeters - it).coerceIn(0.0, MAX_VALID_STEP_METERS) }
            ?: 0.0
    }

    private fun consumeDistanceThreshold(bucket: Int, stepMeters: Double): Boolean {
        if (minDistanceStepMeters <= 0.0) return true

        val accumulated = (accumulatedMetersByBucket[bucket] ?: 0.0) + stepMeters
        return if (accumulated >= minDistanceStepMeters) {
            accumulatedMetersByBucket[bucket] = accumulated % minDistanceStepMeters
            true
        } else {
            accumulatedMetersByBucket[bucket] = accumulated
            false
        }
    }

    private fun isRecordable(metrics: ForumsladerMetrics): Boolean =
        metrics.dynamics.speedMetersPerSecond >= MIN_MOVING_SPEED_MS &&
            metrics.dynamics.frequency > MIN_ACTIVE_FREQUENCY_HZ &&
            metrics.power.chargeState != ChargeState.FULL

    private fun isHysteresisCutoff(metrics: ForumsladerMetrics): Boolean =
        metrics.dynamics.generatorGear == 0 ||
            (metrics.power.batteryLevelPercentage ?: 0) >= HYSTERESIS_SOC_THRESHOLD

    companion object {
        private const val MIN_MOVING_SPEED_MS = 1.2f
        private const val MIN_ACTIVE_FREQUENCY_HZ = 5.0f
        private const val HYSTERESIS_SOC_THRESHOLD = 98
        private const val MAX_VALID_STEP_METERS = 500.0
    }
}
