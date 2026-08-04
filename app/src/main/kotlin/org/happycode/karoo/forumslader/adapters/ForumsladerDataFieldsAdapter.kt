package org.happycode.karoo.forumslader.adapters

import android.content.Context
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerDataFieldsAdapter(private val context: Context) {
    // Datafield IDs that correspond to the dataTypes registered in Forumslader device
    object DataFieldId {
        const val BATTERY_LEVEL = "fl_battery_level"
        const val BATTERY_VOLTAGE = "fl_battery_voltage"
        const val BATTERY_CURRENT = "fl_battery_current"
        const val CONSUMER_CURRENT = "fl_consumer_current"
        const val SPEED = "fl_speed"
        const val TRIP_DISTANCE = "fl_trip_distance"
        const val FREQUENCY = "fl_frequency"
        const val TEMPERATURE = "fl_temperature"
        const val GENERATOR_GEAR = "fl_generator_gear"
        const val CHARGE_STATE = "fl_charge_state"
        const val TRIP_ENERGY = "fl_trip_energy"
        const val TOUR_ENERGY = "fl_tour_energy"
        const val DYNAMO_POWER = "fl_dynamo_power"
        const val ODOMETER = "fl_odometer"
        const val DAY_DISTANCE = "fl_day_distance"
        const val TOUR_DISTANCE = "fl_tour_distance"
    }

    fun getDataFieldNames(): Map<String, String> = mapOf(
        DataFieldId.BATTERY_LEVEL to context.getString(R.string.datafield_battery_level),
        DataFieldId.BATTERY_VOLTAGE to context.getString(R.string.datafield_battery_voltage),
        DataFieldId.BATTERY_CURRENT to context.getString(R.string.datafield_battery_current),
        DataFieldId.CONSUMER_CURRENT to context.getString(R.string.datafield_consumer_current),
        DataFieldId.SPEED to context.getString(R.string.datafield_speed),
        DataFieldId.TRIP_DISTANCE to context.getString(R.string.datafield_trip_distance),
        DataFieldId.FREQUENCY to context.getString(R.string.datafield_frequency),
        DataFieldId.TEMPERATURE to context.getString(R.string.datafield_temperature),
        DataFieldId.GENERATOR_GEAR to context.getString(R.string.datafield_generator_gear),
        DataFieldId.CHARGE_STATE to context.getString(R.string.datafield_charge_state),
        DataFieldId.TRIP_ENERGY to context.getString(R.string.datafield_trip_energy),
        DataFieldId.TOUR_ENERGY to context.getString(R.string.datafield_tour_energy),
        DataFieldId.DYNAMO_POWER to context.getString(R.string.datafield_dynamo_power),
        DataFieldId.ODOMETER to context.getString(R.string.datafield_odometer),
        DataFieldId.DAY_DISTANCE to context.getString(R.string.datafield_day_distance),
        DataFieldId.TOUR_DISTANCE to context.getString(R.string.datafield_tour_distance),
    )

    companion object {
        fun metricsToDataFieldValues(metrics: ForumsladerMetrics): Map<String, Any> = mapOf(
            DataFieldId.BATTERY_LEVEL to metrics.power.batteryLevelPercentage,
            DataFieldId.BATTERY_VOLTAGE to metrics.power.batteryVoltage,
            DataFieldId.BATTERY_CURRENT to (metrics.power.batteryCurrent * 1000).toInt(),
            DataFieldId.CONSUMER_CURRENT to (metrics.power.consumerCurrent * 1000).toInt(),
            DataFieldId.SPEED to metrics.dynamics.speedMetersPerSecond,
            DataFieldId.TRIP_DISTANCE to metrics.distance.tripMeters,
            DataFieldId.FREQUENCY to metrics.dynamics.frequency,
            DataFieldId.TEMPERATURE to metrics.environment.temperatureCelsius,
            DataFieldId.GENERATOR_GEAR to metrics.dynamics.generatorGear,
            DataFieldId.CHARGE_STATE to metrics.power.chargeState.name,
            DataFieldId.TRIP_ENERGY to metrics.energy.tripWattHours,
            DataFieldId.TOUR_ENERGY to metrics.energy.tourWattHours,
            DataFieldId.DYNAMO_POWER to metrics.power.dynamoPowerWatts,
            DataFieldId.ODOMETER to metrics.distance.odometerMeters,
            DataFieldId.DAY_DISTANCE to metrics.distance.dayMeters,
            DataFieldId.TOUR_DISTANCE to metrics.distance.tourMeters,
        )
    }
}
