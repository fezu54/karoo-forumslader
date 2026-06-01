package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.hammerhead.karooext.models.DataField
import io.hammerhead.karooext.models.SensorDataTypeDef
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerDataFieldsAdapter(private val context: Context) {
    fun createDataFields(): List<DataField> = listOf(
        DataField(
            id = "forumslader_battery_level",
            name = context.getString(R.string.datafield_battery_level),
            sensorDataTypeDef = SensorDataTypeDef.percentage()
        ),
        DataField(
            id = "forumslader_consumer_current",
            name = context.getString(R.string.datafield_consumer_current),
            sensorDataTypeDef = SensorDataTypeDef.amps()
        ),
        DataField(
            id = "forumslader_speed",
            name = context.getString(R.string.datafield_speed),
            sensorDataTypeDef = SensorDataTypeDef.speed()
        ),
        DataField(
            id = "forumslader_trip_distance",
            name = context.getString(R.string.datafield_trip_distance),
            sensorDataTypeDef = SensorDataTypeDef.distance()
        ),
    )

    companion object {
        fun metricsToDataFieldValues(metrics: ForumsladerMetrics): Map<String, Any> = mapOf(
            "forumslader_battery_level" to metrics.batteryLevelPct,
            "forumslader_consumer_current" to metrics.consumerCurrent,
            "forumslader_speed" to metrics.speedKmh,
            "forumslader_trip_distance" to metrics.tripDistanceKm,
        )
    }
}
