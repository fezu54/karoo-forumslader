package org.happycode.karoo.forumslader.domain.estimation

data class ClimbSegment(
    val startDistanceMeters: Double = 0.0,
    val lengthMeters: Double,
    val gradientPct: Float
) {
    val endDistanceMeters: Double
        get() = startDistanceMeters + lengthMeters

    fun remainingFrom(distanceAlongRouteMeters: Double): ClimbSegment? = when {
        endDistanceMeters <= distanceAlongRouteMeters -> null
        startDistanceMeters < distanceAlongRouteMeters -> copy(
            startDistanceMeters = distanceAlongRouteMeters,
            lengthMeters = endDistanceMeters - distanceAlongRouteMeters
        )
        else -> this
    }

    fun predictNetEnergyBalanceWh(
        profile: GradientProfile,
        baseProfile: GradientProfile?,
        dynamoPowerCurve: DynamoPowerCurve,
        consumerPowerWatts: Float
    ): Float = run {
        val estimatedSeconds = lengthMeters.toFloat() / profile.speedMetersPerSecond.coerceAtLeast(MIN_SPEED_MS)
        val estimatedHours = estimatedSeconds / SECONDS_PER_HOUR
        val predictedDynamoPower = dynamoPowerCurve.predictPowerWatts(profile, baseProfile)
        (predictedDynamoPower - consumerPowerWatts) * estimatedHours
    }

    companion object {
        private const val MIN_SPEED_MS = 1.0f
        private const val SECONDS_PER_HOUR = 3600f
    }
}
