package org.happycode.karoo.forumslader.adapters

import android.content.Context
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerDataFieldsAdapter(private val context: Context) {
    // Datafield IDs that correspond to the dataTypes registered in Forumslader device
    object DataFieldId {
        const val BATTERY_LEVEL = "fl_battery_level"
        const val CONSUMER_CURRENT = "fl_consumer_current"
        const val SPEED = "fl_speed"
        const val TRIP_DISTANCE = "fl_trip_distance"
    }

    fun getDataFieldNames(): Map<String, String> = mapOf(
        DataFieldId.BATTERY_LEVEL to context.getString(R.string.datafield_battery_level),
        DataFieldId.CONSUMER_CURRENT to context.getString(R.string.datafield_consumer_current),
        DataFieldId.SPEED to context.getString(R.string.datafield_speed),
        DataFieldId.TRIP_DISTANCE to context.getString(R.string.datafield_trip_distance),
    )

    companion object {
        fun metricsToDataFieldValues(metrics: ForumsladerMetrics): Map<String, Any> = mapOf(
            DataFieldId.BATTERY_LEVEL to metrics.batteryLevelPct,
            DataFieldId.CONSUMER_CURRENT to metrics.consumerCurrent,
            DataFieldId.SPEED to metrics.speedMs,
            DataFieldId.TRIP_DISTANCE to metrics.tripDistanceMeters,
        )
    }
}
