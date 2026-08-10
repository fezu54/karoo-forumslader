package org.happycode.karoo.forumslader.adapters

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.forumslader.domain.RideEnergySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DataStoreRideHistoryGatewayTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var gateway: DataStoreRideHistoryGateway

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tmpFolder.newFile("test_datastore.preferences_pb") }
        )
        gateway = DataStoreRideHistoryGateway(dataStore)
    }

    @Test
    fun `should save and load summary`() = runTest {
        val summary = RideEnergySummary(
            rideDate = Instant.ofEpochMilli(1600000000000L),
            durationSec = 3600L,
            distanceKm = 25.5f,
            totalEnergyWh = 12.5f,
            avgPowerW = 5.2f,
            peakPowerW = 15.0f,
            chargingTimeSec = 2000L,
            dischargingTimeSec = 1000L,
            standbyTimeSec = 600L,
            batteryStartPct = 50,
            batteryEndPct = 80
        )

        gateway.saveSummary(summary)

        val history = gateway.getHistory()
        assertEquals(1, history.size)
        
        val loaded = history[0]
        assertEquals(summary.rideDate, loaded.rideDate)
        assertEquals(summary.durationSec, loaded.durationSec)
        assertEquals(summary.distanceKm, loaded.distanceKm, 0.01f)
        assertEquals(summary.totalEnergyWh, loaded.totalEnergyWh, 0.01f)
        assertEquals(summary.avgPowerW, loaded.avgPowerW, 0.01f)
        assertEquals(summary.peakPowerW, loaded.peakPowerW, 0.01f)
        assertEquals(summary.chargingTimeSec, loaded.chargingTimeSec)
        assertEquals(summary.dischargingTimeSec, loaded.dischargingTimeSec)
        assertEquals(summary.standbyTimeSec, loaded.standbyTimeSec)
        assertEquals(summary.batteryStartPct, loaded.batteryStartPct)
        assertEquals(summary.batteryEndPct, loaded.batteryEndPct)
    }

    @Test
    fun `should return empty list when no history exists`() = runTest {
        val history = gateway.getHistory()
        assertTrue(history.isEmpty())
    }

    @Test
    fun `should maintain max history size and order`() = runTest {
        for (i in 1..60) {
            val summary = RideEnergySummary(
                rideDate = Instant.ofEpochMilli(1600000000000L + i * 1000),
                durationSec = i.toLong(),
                distanceKm = 0f,
                totalEnergyWh = 0f,
                avgPowerW = 0f,
                peakPowerW = 0f,
                chargingTimeSec = 0L,
                dischargingTimeSec = 0L,
                standbyTimeSec = 0L,
                batteryStartPct = 0,
                batteryEndPct = 0
            )
            gateway.saveSummary(summary)
        }

        val history = gateway.getHistory()
        assertEquals(50, history.size)
        // Most recent should be at index 0 (which was i = 60)
        assertEquals(60L, history[0].durationSec)
        // Last one should be i = 11
        assertEquals(11L, history[49].durationSec)
    }
}
