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
    )

    companion object {
        fun metricsToDataFieldValues(metrics: ForumsladerMetrics): Map<String, Any> = mapOf(
            DataFieldId.BATTERY_LEVEL to metrics.batteryLevelPct,
            DataFieldId.BATTERY_VOLTAGE to metrics.batteryVoltage,
            DataFieldId.BATTERY_CURRENT to metrics.batteryCurrent,
            DataFieldId.CONSUMER_CURRENT to metrics.consumerCurrent,
            DataFieldId.SPEED to metrics.speedMs,
            DataFieldId.TRIP_DISTANCE to metrics.tripDistanceMeters,
            DataFieldId.FREQUENCY to metrics.frequency,
            DataFieldId.TEMPERATURE to metrics.temperatureCelsius,
        )
    }
}
