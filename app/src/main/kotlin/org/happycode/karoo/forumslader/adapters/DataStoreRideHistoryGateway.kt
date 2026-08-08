package org.happycode.karoo.forumslader.adapters

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.happycode.karoo.forumslader.domain.RideEnergySummary
import org.happycode.karoo.forumslader.domain.RideHistoryGateway
import java.time.Instant

class DataStoreRideHistoryGateway(
    private val dataStore: DataStore<Preferences>
) : RideHistoryGateway {
    companion object {
        private val KEY_HISTORY = stringPreferencesKey("ride_history")
        private const val MAX_HISTORY_SIZE = 50
    }

    override suspend fun saveSummary(summary: RideEnergySummary) {
        dataStore.edit { preferences ->
            val currentHistory = getHistoryInternal(preferences)
            val updatedHistory = (listOf(summary) + currentHistory).take(MAX_HISTORY_SIZE)
            preferences[KEY_HISTORY] = Json.encodeToString(updatedHistory.map { it.toDto() })
        }
    }

    override suspend fun getHistory(): List<RideEnergySummary> {
        val preferences = dataStore.data.first()
        return getHistoryInternal(preferences)
    }

    private fun getHistoryInternal(preferences: Preferences): List<RideEnergySummary> {
        val jsonString = preferences[KEY_HISTORY] ?: return emptyList()
        return try {
            Json.decodeFromString<List<RideEnergySummaryDto>>(jsonString).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class RideEnergySummaryDto(
        val rideDateMs: Long,
        val durationSec: Long,
        val distanceKm: Float,
        val totalEnergyWh: Float,
        val avgPowerW: Float,
        val peakPowerW: Float,
        val chargingTimeSec: Long,
        val dischargingTimeSec: Long,
        val standbyTimeSec: Long,
        val batteryStartPct: Int,
        val batteryEndPct: Int
    )

    private fun RideEnergySummary.toDto() = RideEnergySummaryDto(
        rideDateMs = rideDate.toEpochMilli(),
        durationSec = durationSec,
        distanceKm = distanceKm,
        totalEnergyWh = totalEnergyWh,
        avgPowerW = avgPowerW,
        peakPowerW = peakPowerW,
        chargingTimeSec = chargingTimeSec,
        dischargingTimeSec = dischargingTimeSec,
        standbyTimeSec = standbyTimeSec,
        batteryStartPct = batteryStartPct,
        batteryEndPct = batteryEndPct
    )

    private fun RideEnergySummaryDto.toDomain() = RideEnergySummary(
        rideDate = Instant.ofEpochMilli(rideDateMs),
        durationSec = durationSec,
        distanceKm = distanceKm,
        totalEnergyWh = totalEnergyWh,
        avgPowerW = avgPowerW,
        peakPowerW = peakPowerW,
        chargingTimeSec = chargingTimeSec,
        dischargingTimeSec = dischargingTimeSec,
        standbyTimeSec = standbyTimeSec,
        batteryStartPct = batteryStartPct,
        batteryEndPct = batteryEndPct
    )
}
