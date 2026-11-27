package org.happycode.karoo.forumslader.domain.estimation

/**
 * Forecast of upcoming route terrain deficit, climb distance to the bottleneck point,
 * and non-climb route share.
 */
data class RouteEnergyForecast(
    val peakDeficitPct: Float,
    val bottleneckClimbKm: Float,
    val nonClimbRouteFraction: Float,
) {
    /**
     * Computes usable battery capacity after deducting only the excess climb deficit
     * beyond what [effectiveRatePctPerKm] already budgets for [bottleneckClimbKm].
     */
    fun adjustedCapacityPct(
        currentLevelPct: Float,
        effectiveRatePctPerKm: Float,
    ): Float {
        val baselineClimbBudgetPct = bottleneckClimbKm * effectiveRatePctPerKm.coerceAtLeast(0f)
        val excessDeficitPct = (peakDeficitPct - baselineClimbBudgetPct).coerceAtLeast(0f)
        return (currentLevelPct - excessDeficitPct).coerceAtLeast(0f)
    }
}

/**
 * Evaluates upcoming climbs and elevation gain along a route to estimate peak battery deficit,
 * inter-climb dynamo recovery, and non-climb route share without double-counting climb distance.
 */
class RouteEnergyForecaster(
    private val elevationPenaltyPctPer100m: Float,
    private val batteryCapacityWh: Float,
    private val dynamoPowerCurve: DynamoPowerCurve,
) {
    /**
     * Forecasts upcoming terrain deficit and non-climb route fraction from [currentLevelPct].
     */
    fun forecast(
        currentLevelPct: Float,
        routeRemainingKm: Float?,
        upcomingElevationMeters: Double?,
        upcomingClimbs: List<ClimbSegment>,
        riderProfiles: Map<Int, GradientProfile>,
        currentConsumerPowerWatts: Float,
        headwindSpeedMs: Float?,
        batteryCapacityWh: Float = this.batteryCapacityWh,
    ): RouteEnergyForecast {
        val effectiveCapacityWh = batteryCapacityWh.takeIf { it > 0f } ?: this.batteryCapacityWh
        return when {
            upcomingClimbs.isNotEmpty() -> {
                forecastWithClimbs(
                    currentLevelPct,
                    routeRemainingKm,
                    upcomingClimbs,
                    riderProfiles,
                    currentConsumerPowerWatts,
                    headwindSpeedMs,
                    effectiveCapacityWh,
                )
            }

            else -> {
                forecastFromElevation(
                    routeRemainingKm,
                    upcomingElevationMeters,
                )
            }
        }
    }

    private fun forecastWithClimbs(
        currentLevelPct: Float,
        routeRemainingKm: Float?,
        upcomingClimbs: List<ClimbSegment>,
        riderProfiles: Map<Int, GradientProfile>,
        currentConsumerPowerWatts: Float,
        headwindSpeedMs: Float?,
        effectiveCapacityWh: Float,
    ): RouteEnergyForecast {
        val totalClimbKm = (upcomingClimbs.sumOf { it.lengthMeters } / METERS_PER_KM).toFloat()
        val nonClimbFraction = resolveNonClimbFraction(routeRemainingKm, totalClimbKm)
        val nonClimbKm = routeRemainingKm?.let { (it - totalClimbKm).coerceAtLeast(0f) } ?: 0f

        val (peakDeficitPct, bottleneckClimbKm) =
            when {
                nonClimbKm > 0f -> {
                    simulateClimbsWithRecovery(
                        currentLevelPct,
                        nonClimbKm,
                        upcomingClimbs,
                        riderProfiles,
                        currentConsumerPowerWatts,
                        headwindSpeedMs,
                        effectiveCapacityWh,
                    )
                }

                else -> {
                    upcomingClimbs.fold(0f) { acc, climb ->
                        acc + calculateClimbDrainPct(
                            climb,
                            riderProfiles,
                            currentConsumerPowerWatts,
                            headwindSpeedMs,
                            effectiveCapacityWh,
                        )
                    } to totalClimbKm
                }
            }

        return RouteEnergyForecast(
            peakDeficitPct = peakDeficitPct,
            bottleneckClimbKm = bottleneckClimbKm,
            nonClimbRouteFraction = nonClimbFraction,
        )
    }

    private fun simulateClimbsWithRecovery(
        currentLevelPct: Float,
        totalNonClimbKm: Float,
        upcomingClimbs: List<ClimbSegment>,
        riderProfiles: Map<Int, GradientProfile>,
        currentConsumerPowerWatts: Float,
        headwindSpeedMs: Float?,
        effectiveCapacityWh: Float,
    ): Pair<Float, Float> {
        val sortedClimbs = upcomingClimbs.sortedBy { it.startDistanceMeters }
        val recoveryPctPerKm =
            calculateFlatRecoveryPctPerKm(
                riderProfiles,
                currentConsumerPowerWatts,
                headwindSpeedMs,
                effectiveCapacityWh,
            )

        var simulatedLevelPct = currentLevelPct
        var minSimulatedLevelPct = currentLevelPct
        var accumulatedClimbKm = 0f
        var bottleneckClimbKm = 0f
        var remainingNonClimbKm = totalNonClimbKm
        var prevEndMeters: Double? = null

        sortedClimbs.forEach { climb ->
            val gapKm =
                prevEndMeters?.let { prevEnd ->
                    ((climb.startDistanceMeters - prevEnd).coerceAtLeast(0.0) / METERS_PER_KM)
                        .toFloat()
                        .coerceAtMost(remainingNonClimbKm)
                } ?: 0f

            if (gapKm > 0f && recoveryPctPerKm > 0f) {
                simulatedLevelPct = (simulatedLevelPct + (gapKm * recoveryPctPerKm)).coerceAtMost(MAX_BATTERY_PCT)
                remainingNonClimbKm = (remainingNonClimbKm - gapKm).coerceAtLeast(0f)
            }

            val climbDrainPct =
                calculateClimbDrainPct(
                    climb,
                    riderProfiles,
                    currentConsumerPowerWatts,
                    headwindSpeedMs,
                    effectiveCapacityWh,
                )
            accumulatedClimbKm += (climb.lengthMeters / METERS_PER_KM).toFloat()
            simulatedLevelPct -= climbDrainPct
            if (simulatedLevelPct < minSimulatedLevelPct) {
                minSimulatedLevelPct = simulatedLevelPct
                bottleneckClimbKm = accumulatedClimbKm
            }
            prevEndMeters = maxOf(prevEndMeters ?: 0.0, climb.endDistanceMeters)
        }

        val peakDeficitPct = (currentLevelPct - minSimulatedLevelPct).coerceAtLeast(0f)
        return peakDeficitPct to bottleneckClimbKm
    }

    private fun calculateClimbDrainPct(
        climb: ClimbSegment,
        riderProfiles: Map<Int, GradientProfile>,
        currentConsumerPowerWatts: Float,
        headwindSpeedMs: Float?,
        effectiveCapacityWh: Float,
    ): Float {
        val bucket = GradientProfile.bucketFor(climb.gradientPct)
        val baseProfile = riderProfiles[bucket]
        val profileForClimb =
            (baseProfile ?: GradientProfile.defaultFor(bucket)).let { profile ->
                headwindSpeedMs?.let { profile.adjustedForHeadwind(it) } ?: profile
            }
        val netBalanceWh =
            climb.predictNetEnergyBalanceWh(
                profileForClimb,
                baseProfile,
                dynamoPowerCurve,
                currentConsumerPowerWatts,
            )
        return if (netBalanceWh < 0f) (-netBalanceWh / effectiveCapacityWh) * PERCENT_SCALE else 0f
    }

    private fun calculateFlatRecoveryPctPerKm(
        riderProfiles: Map<Int, GradientProfile>,
        currentConsumerPowerWatts: Float,
        headwindSpeedMs: Float?,
        effectiveCapacityWh: Float,
    ): Float {
        val baseFlat = riderProfiles[0] ?: GradientProfile.defaultFor(0)
        val effectiveFlat = headwindSpeedMs?.let { baseFlat.adjustedForHeadwind(it) } ?: baseFlat
        val predictedPowerWatts = dynamoPowerCurve.predictPowerWatts(effectiveFlat, riderProfiles[0])
        val netSurplusWatts =
            (predictedPowerWatts - currentConsumerPowerWatts).coerceAtLeast(0f) *
                RECOVERY_EFFICIENCY
        if (netSurplusWatts <= 0f) return 0f

        val speedKmh = effectiveFlat.speedMetersPerSecond.coerceAtLeast(MIN_FLAT_SPEED_MS) * MS_TO_KMH
        val whRecoveredPerKm = netSurplusWatts / speedKmh
        return (whRecoveredPerKm / effectiveCapacityWh) * PERCENT_SCALE
    }

    private fun resolveNonClimbFraction(
        routeRemainingKm: Float?,
        totalClimbKm: Float,
    ): Float =
        when {
            routeRemainingKm == null || routeRemainingKm <= 0f -> 0f
            else -> ((routeRemainingKm - totalClimbKm) / routeRemainingKm).coerceIn(0f, 1f)
        }

    private fun forecastFromElevation(
        routeRemainingKm: Float?,
        upcomingElevationMeters: Double?,
    ): RouteEnergyForecast {
        val elevation = upcomingElevationMeters?.coerceAtLeast(0.0) ?: 0.0
        val rawPenalty = (elevation / METERS_PER_100M).toFloat() * elevationPenaltyPctPer100m
        val scaledPenalty = scaleElevationPenalty(rawPenalty, elevation, routeRemainingKm)
        return RouteEnergyForecast(
            peakDeficitPct = scaledPenalty,
            bottleneckClimbKm = 0f,
            nonClimbRouteFraction = 1f,
        )
    }

    private fun scaleElevationPenalty(
        rawPenalty: Float,
        elevationMeters: Double,
        routeRemainingKm: Float?,
    ): Float {
        val remainingKm = routeRemainingKm?.takeIf { it > 0f } ?: return rawPenalty
        val avgGradePct = ((elevationMeters / (remainingKm * METERS_PER_KM)) * PERCENT_SCALE).toFloat()
        val intensityScale =
            when {
                avgGradePct >= FULL_PENALTY_AVG_GRADE_PCT -> 1.0f
                else -> (avgGradePct / FULL_PENALTY_AVG_GRADE_PCT).coerceIn(MIN_ELEVATION_SCALE, 1.0f)
            }
        return rawPenalty * intensityScale
    }

    companion object {
        private const val METERS_PER_100M = 100.0
        private const val METERS_PER_KM = 1000.0
        private const val PERCENT_SCALE = 100f
        private const val MAX_BATTERY_PCT = 100f
        private const val MS_TO_KMH = 3.6f
        private const val MIN_FLAT_SPEED_MS = 2.0f
        private const val RECOVERY_EFFICIENCY = 0.70f
        private const val FULL_PENALTY_AVG_GRADE_PCT = 3.0f
        private const val MIN_ELEVATION_SCALE = 0.25f
    }
}
