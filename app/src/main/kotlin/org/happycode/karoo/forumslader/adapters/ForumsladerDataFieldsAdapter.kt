package org.happycode.karoo.forumslader.adapters

import io.hammerhead.karooext.models.DataField
import io.hammerhead.karooext.models.SensorDataTypeDef
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

object ForumsladerDataFieldsAdapter {
    fun createDataFields(): List<DataField> = listOf(
        DataField(
            id = "forumslader_battery_level",
            name = "Battery Level",
            sensorDataTypeDef = SensorDataTypeDef.percentage()
        ),
        DataField(
            id = "forumslader_consumer_current",
            name = "Consumer Current",
            sensorDataTypeDef = SensorDataTypeDef.amps()
        ),
        DataField(
            id = "forumslader_speed",
            name = "Speed",
            sensorDataTypeDef = SensorDataTypeDef.speed()
        ),
        DataField(
            id = "forumslader_trip_distance",
            name = "Trip Distance",
            sensorDataTypeDef = SensorDataTypeDef.distance()
        ),
    )

    fun metricsToDataFieldValues(metrics: ForumsladerMetrics): Map<String, Any> = mapOf(
        "forumslader_battery_level" to metrics.batteryLevelPct,
        "forumslader_consumer_current" to metrics.consumerCurrent,
        "forumslader_speed" to metrics.speedKmh,
        "forumslader_trip_distance" to metrics.tripDistanceKm,
    )
}
