package org.happycode.karoo.forumslader.extension.estimation

import android.content.SharedPreferences
import androidx.core.content.edit
import org.happycode.karoo.forumslader.domain.estimation.BatteryCapacity
import org.happycode.karoo.forumslader.domain.estimation.BatteryCapacityStore

/**
 * Persists the last credible full-charge battery capacity (mAh) in SharedPreferences
 * so cold-start route range forecasts adapt immediately before the first $FLC,5 frame arrives.
 */
class SharedPrefsBatteryCapacityStore(
    private val preferences: SharedPreferences
) : BatteryCapacityStore {

    fun loadCapacityMah(): Int? =
        preferences.getInt(KEY_LAST_FULL_CHARGE_CAPACITY_MAH, MISSING_VALUE)
            .takeIf(BatteryCapacity::isCredibleMah)

    override fun saveCapacityMah(capacityMah: Int) {
        if (!BatteryCapacity.isCredibleMah(capacityMah)) return
        preferences.edit {
            putInt(KEY_LAST_FULL_CHARGE_CAPACITY_MAH, capacityMah)
        }
    }

    companion object {
        const val KEY_LAST_FULL_CHARGE_CAPACITY_MAH = "last_full_charge_capacity_mah"
        private const val MISSING_VALUE = -1
    }
}
