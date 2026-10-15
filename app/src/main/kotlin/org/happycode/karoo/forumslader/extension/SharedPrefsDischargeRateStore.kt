package org.happycode.karoo.forumslader.extension

import android.content.SharedPreferences
import androidx.core.content.edit
import org.happycode.karoo.forumslader.domain.BatteryEstimator
import org.happycode.karoo.forumslader.domain.DischargeRateStore

class SharedPrefsDischargeRateStore(
    private val preferences: SharedPreferences
) : DischargeRateStore {

    fun loadInitialRate(): Float =
        preferences.getFloat(KEY_LAST_DISCHARGE_RATE, BatteryEstimator.DEFAULT_DISCHARGE_RATE)
            .takeIf { it >= BatteryEstimator.MIN_CREDIBLE_DISCHARGE_RATE }
            ?: BatteryEstimator.DEFAULT_DISCHARGE_RATE

    override fun save(ratePctPerKm: Float) {
        preferences.edit {
            putFloat(KEY_LAST_DISCHARGE_RATE, ratePctPerKm)
        }
    }

    companion object {
        private const val KEY_LAST_DISCHARGE_RATE = "last_discharge_rate_pct_per_km"
    }
}
