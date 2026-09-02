package org.happycode.karoo.forumslader.ui.main

import android.content.Context
import io.hammerhead.karooext.models.UserProfile
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import java.util.Locale

class MetricFormatter(
    private val locale: Locale,
    private val isImperialDistance: Boolean,
    private val isImperialTemperature: Boolean,
    private val context: Context
) {
    fun format(id: String, rawValue: Double?): String {
        if (rawValue == null) return "---"
        return when (id) {
            DataFieldId.SPEED -> {
                val speedKmh = rawValue * 3.6
                if (isImperialDistance) {
                    String.format(locale, "%.1f mph", speedKmh * 0.621371)
                } else {
                    String.format(locale, "%.1f km/h", speedKmh)
                }
            }

            DataFieldId.TRIP_DISTANCE -> {
                val distanceKm = rawValue / 1000.0
                if (isImperialDistance) {
                    String.format(locale, "%.2f mi", distanceKm * 0.621371)
                } else {
                    String.format(locale, "%.2f km", distanceKm)
                }
            }

            DataFieldId.BATTERY_LEVEL -> String.format(locale, "%d%%", rawValue.toInt())
            DataFieldId.BATTERY_VOLTAGE -> String.format(locale, "%.1f V", rawValue)
            DataFieldId.BATTERY_CURRENT -> String.format(locale, "%d mA", rawValue.toInt())
            DataFieldId.CONSUMER_CURRENT -> String.format(locale, "%d mA", rawValue.toInt())

            DataFieldId.TEMPERATURE -> {
                if (isImperialTemperature) {
                    String.format(locale, "%.1f °F", (rawValue * 9 / 5) + 32)
                } else {
                    String.format(locale, "%.1f °C", rawValue)
                }
            }

            DataFieldId.GENERATOR_GEAR -> String.format(locale, "%d", rawValue.toInt())

            DataFieldId.CHARGE_STATE -> {
                when (rawValue.toInt()) {
                    0 -> context.getString(R.string.charge_state_standby)
                    1 -> context.getString(R.string.charge_state_charging)
                    2 -> context.getString(R.string.charge_state_discharging)
                    3 -> context.getString(R.string.charge_state_full)
                    else -> "---"
                }
            }

            DataFieldId.TRIP_ENERGY,
            DataFieldId.TOUR_ENERGY -> String.format(locale, "%.1f Wh", rawValue)

            DataFieldId.DYNAMO_POWER -> String.format(locale, "%.1f W", rawValue)

            DataFieldId.ODOMETER,
            DataFieldId.DAY_DISTANCE,
            DataFieldId.TOUR_DISTANCE,
            DataFieldId.BATTERY_RANGE -> {
                val distanceKm = rawValue / 1000.0
                if (isImperialDistance) {
                    String.format(locale, "%.2f mi", distanceKm * 0.621371)
                } else {
                    String.format(locale, "%.2f km", distanceKm)
                }
            }

            DataFieldId.FREQUENCY -> String.format(locale, "%.1f Hz", rawValue)
            else -> String.format(locale, "%.1f", rawValue)
        }
    }

    companion object {
        fun from(context: Context, userProfile: UserProfile?, locale: Locale): MetricFormatter {
            return MetricFormatter(
                locale = locale,
                isImperialDistance = userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                isImperialTemperature = userProfile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                context = context
            )
        }
    }
}
