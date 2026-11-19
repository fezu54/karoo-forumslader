package org.happycode.karoo.forumslader.domain.estimation

import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

/**
 * Coordinates battery range estimation and route sufficiency checks by composing
 * [DischargeWindow], [ConsumerLoadModel], [HeadwindCompensator], and [RouteEnergyForecaster].
 */
class BatteryEstimator(
    windowMeters: Double = 20000.0,
    minMetersForEstimate: Double = 1000.0,
    elevationPenaltyPctPer100m: Float = 1.5f,
    headwindPenaltyPctPerKmPerMs: Float = 0.5f,
    maxStepMeters: Double = MAX_STEP_METERS,
    private val minCredibleDischargeRate: Float = MIN_CREDIBLE_DISCHARGE_RATE,
    private val maxCredibleDischargeRate: Float = MAX_CREDIBLE_DISCHARGE_RATE,
    emaAlpha: Float = EMA_ALPHA,
    initialDischargeRate: Float? = DEFAULT_DISCHARGE_RATE,
    dischargeRateStore: DischargeRateStore = DischargeRateStore {},
    batteryCapacityWh: Float = DEFAULT_BATTERY_CAPACITY_WH,
    dynamoPowerCurve: DynamoPowerCurve = DynamoPowerCurve()
) {
    private val dischargeWindow = DischargeWindow(
        windowMeters = windowMeters,
        minMetersForEstimate = minMetersForEstimate,
        maxStepMeters = maxStepMeters,
        minCredibleDischargeRate = minCredibleDischargeRate,
        maxCredibleDischargeRate = maxCredibleDischargeRate,
        emaAlpha = emaAlpha,
        initialDischargeRate = initialDischargeRate,
        dischargeRateStore = dischargeRateStore
    )
    private val consumerLoadModel = ConsumerLoadModel(batteryCapacityWh = batteryCapacityWh)
    private val headwindCompensator = HeadwindCompensator(headwindPenaltyPctPerKmPerMs = headwindPenaltyPctPerKmPerMs)
    private val routeEnergyForecaster = RouteEnergyForecaster(
        elevationPenaltyPctPer100m = elevationPenaltyPctPer100m,
        batteryCapacityWh = batteryCapacityWh,
        dynamoPowerCurve = dynamoPowerCurve
    )

    private val riderProfiles = mutableMapOf<Int, GradientProfile>()
    private var upcomingClimbs: List<ClimbSegment> = emptyList()
    private var currentConsumerPowerWatts: Float = 0f
    private var lastSpeedMetersPerSecond: Float? = null

    private var lastBatteryLevelPct: Int? = null
    private var lastChargeState: ChargeState? = null
    private var routeRemainingKm: Float? = null
    private var upcomingElevationMeters: Double? = null
    private var headwindSpeedMs: Float? = null

    fun onUpcomingClimbs(climbs: List<ClimbSegment>) {
        upcomingClimbs = climbs
    }

    fun onRiderProfilesLoaded(profiles: Map<Int, GradientProfile>) {
        riderProfiles.clear()
        riderProfiles.putAll(profiles)
    }

    fun onRiderProfileUpdated(profile: GradientProfile) {
        riderProfiles[profile.gradientPct] = profile
    }

    fun onMetrics(metrics: ForumsladerMetrics) {
        val currentLevel = metrics.power.batteryLevelPercentage ?: return
        val currentState = metrics.power.chargeState

        lastSpeedMetersPerSecond = metrics.dynamics.speedMetersPerSecond
        currentConsumerPowerWatts = consumerLoadModel.calculateConsumerPowerWatts(metrics.power)
        lastBatteryLevelPct = currentLevel
        lastChargeState = currentState

        dischargeWindow.recordSample(
            chargeState = currentState,
            batteryLevelPct = currentLevel,
            tripDistanceMeters = metrics.distance.tripMeters,
            consumerPowerWatts = currentConsumerPowerWatts,
            headwindSpeedMs = headwindSpeedMs
        )
    }

    fun onRouteRemaining(distanceMeters: Double?, upcomingElevation: Double?) {
        routeRemainingKm = distanceMeters?.let { (it / METERS_PER_KM).toFloat() }
        upcomingElevationMeters = upcomingElevation
    }

    fun onHeadwindSpeed(speedMs: Float?) {
        headwindSpeedMs = speedMs
    }

    fun getEstimate(): BatteryEstimate? {
        val currentLevel = lastBatteryLevelPct ?: return null
        val currentState = lastChargeState ?: return null
        val routeForecast = routeEnergyForecaster.forecast(
            currentLevelPct = currentLevel.toFloat(),
            routeRemainingKm = routeRemainingKm,
            upcomingElevationMeters = upcomingElevationMeters,
            upcomingClimbs = upcomingClimbs,
            riderProfiles = riderProfiles,
            currentConsumerPowerWatts = currentConsumerPowerWatts,
            headwindSpeedMs = headwindSpeedMs
        )
        val effectiveRate = calculateEffectiveRate(
            baseRate = dischargeWindow.lastDischargeRate,
            nonClimbRouteFraction = routeForecast.nonClimbRouteFraction
        )
        val estimatedRangeKm = effectiveRate?.takeIf { it > 0f }?.let { dischargeRate ->
            val adjustedCapacity = routeForecast.adjustedCapacityPct(
                currentLevelPct = currentLevel.toFloat(),
                effectiveRatePctPerKm = dischargeRate
            )
            (adjustedCapacity / dischargeRate).takeIf { range -> range.isFinite() }
        }
        val avgDischargeRatePctPerKm = when (currentState) {
            ChargeState.CHARGING, ChargeState.FULL -> 0f
            else -> effectiveRate ?: 0f
        }
        val isSufficientForRoute = routeRemainingKm?.let { remaining ->
            estimatedRangeKm?.let { it >= remaining } ?: when (currentState) {
                ChargeState.CHARGING, ChargeState.FULL -> true
                else -> null
            }
        }
        return BatteryEstimate(
            remainingCapacityPct = currentLevel,
            avgDischargeRatePctPerKm = avgDischargeRatePctPerKm,
            estimatedRangeKm = estimatedRangeKm,
            routeRemainingKm = routeRemainingKm,
            isSufficientForRoute = isSufficientForRoute,
            chargeState = currentState
        )
    }

    private fun calculateEffectiveRate(baseRate: Float?, nonClimbRouteFraction: Float): Float? =
        baseRate?.takeIf { it > 0f }?.let { rate ->
            val headwindPenalty = headwindCompensator.calculateRatePenaltyPctPerKm(
                currentHeadwindMs = headwindSpeedMs,
                baselineHeadwindMs = dischargeWindow.baselineHeadwindSpeedMs,
                nonClimbRouteFraction = nonClimbRouteFraction
            )
            val consumerDelta = consumerLoadModel.calculateRateAdjustmentPctPerKm(
                currentPowerWatts = currentConsumerPowerWatts,
                baselinePowerWatts = dischargeWindow.baselineConsumerPowerWatts,
                speedMetersPerSecond = lastSpeedMetersPerSecond,
                baseRatePctPerKm = rate
            )
            (rate + headwindPenalty + consumerDelta).coerceIn(minCredibleDischargeRate, maxCredibleDischargeRate)
        }

    companion object {
        const val DEFAULT_DISCHARGE_RATE = 1.0f
        const val MIN_CREDIBLE_DISCHARGE_RATE = 0.15f
        const val MAX_CREDIBLE_DISCHARGE_RATE = 5.0f
        const val DEFAULT_BATTERY_CAPACITY_WH = 13.3f
        private const val EMA_ALPHA = 0.35f
        const val MAX_STEP_METERS = 1_000.0
        private const val METERS_PER_KM = 1000.0
    }
}
