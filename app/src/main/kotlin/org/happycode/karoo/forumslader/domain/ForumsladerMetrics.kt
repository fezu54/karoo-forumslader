package org.happycode.karoo.forumslader.domain

/**
 * Aggregated metrics from the Forumslader device.
 */
data class ForumsladerMetrics(
    val power: Power,
    val dynamics: Dynamics,
    val environment: Environment,
    val energy: Energy,
    val distance: Distance
) {
    /**
     * Electronics and power management metrics.
     */
    data class Power(
        val batteryVoltage: Float,
        val batteryCurrent: Float,
        val consumerCurrent: Float,
        val batteryLevelPercentage: Int,
        val chargeState: ChargeState,
        val dynamoPowerWatts: Float,
        val statusMask: Int
    )

    /**
     * Driving dynamics calculated from dynamo frequency.
     */
    data class Dynamics(
        val frequency: Float,
        val speedMetersPerSecond: Float,
        val generatorGear: Int
    )

    /**
     * Environmental sensor data.
     */
    data class Environment(
        val temperatureCelsius: Float,
        val altitudeMeters: Float
    )

    /**
     * Cumulative energy consumption/generation.
     */
    data class Energy(
        val tripWattHours: Double,
        val tourWattHours: Double
    )

    /**
     * Cumulative distance measurements.
     */
    data class Distance(
        val tripMeters: Double,
        val dayMeters: Double,
        val tourMeters: Double,
        val odometerMeters: Double
    )
}
