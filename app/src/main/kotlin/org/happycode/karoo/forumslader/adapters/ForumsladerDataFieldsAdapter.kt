package org.happycode.karoo.forumslader.adapters

import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

object ForumsladerDataFieldsAdapter {
    // Datafield IDs that correspond to the dataTypes registered in Forumslader device
    object DataFieldId {
        const val BATTERY_LEVEL = "fl_battery_level"
        const val CONSUMER_CURRENT = "fl_consumer_current"
        const val SPEED = "fl_speed"
        const val TRIP_DISTANCE = "fl_trip_distance"
    }

    object DataFieldName {
        const val BATTERY_LEVEL = "Battery Level"
        const val CONSUMER_CURRENT = "Consumer Current"
        const val SPEED = "Speed"
        const val TRIP_DISTANCE = "Trip Distance"
    }

    fun metricsToDataFieldValues(metrics: ForumsladerMetrics): Map<String, Any> = mapOf(
        DataFieldId.BATTERY_LEVEL to metrics.batteryLevelPct,
        DataFieldId.CONSUMER_CURRENT to metrics.consumerCurrent,
        DataFieldId.SPEED to metrics.speedKmh,
        DataFieldId.TRIP_DISTANCE to metrics.tripDistanceKm,
    )
}
