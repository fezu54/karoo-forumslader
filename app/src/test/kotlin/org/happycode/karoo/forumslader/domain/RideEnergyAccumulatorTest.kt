package org.happycode.karoo.forumslader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RideEnergyAccumulatorTest {

    @Test
    fun `should accumulate energy and power when ride is active`() {
        // given
        var currentTime = Instant.ofEpochSecond(1000)
        val accumulator = RideEnergyAccumulator(clock = { currentTime })

        // when
        accumulator.start()
        accumulator.update(createMetrics(powerW = 5f, chargeState = ChargeState.CHARGING), deltaTimeSec = 2f)
        currentTime = currentTime.plusSeconds(2)
        
        accumulator.update(createMetrics(powerW = 10f, chargeState = ChargeState.CHARGING), deltaTimeSec = 2f)
        currentTime = currentTime.plusSeconds(2)

        accumulator.stop()

        // then
        val summary = accumulator.toSummary()
        assertNotNull(summary)
        
        // Energy = 5W * 2s + 10W * 2s = 10J + 20J = 30J = 30 / 3600 Wh = 0.008333 Wh
        assertEquals((30f / 3600f), summary!!.totalEnergyWh, 0.0001f)
        
        // Avg power = 30J / 4s = 7.5W
        assertEquals(7.5f, summary.avgPowerW, 0.0001f)
        
        // Peak power = 10W
        assertEquals(10f, summary.peakPowerW, 0.0001f)
        
        assertEquals(4L, summary.chargingTimeSec)
        assertEquals(0L, summary.dischargingTimeSec)
        assertEquals(0L, summary.standbyTimeSec)
    }

    @Test
    fun `should not accumulate when ride is not started`() {
        val accumulator = RideEnergyAccumulator()
        
        accumulator.update(createMetrics(powerW = 5f, chargeState = ChargeState.CHARGING), deltaTimeSec = 2f)
        
        assertNull(accumulator.toSummary())
        assertEquals(0f, accumulator.totalEnergyWh, 0.0001f)
    }

    @Test
    fun `should ignore paused rides`() {
        var currentTime = Instant.ofEpochSecond(1000)
        val accumulator = RideEnergyAccumulator(clock = { currentTime })

        // Start
        accumulator.start()
        accumulator.update(createMetrics(powerW = 10f), deltaTimeSec = 2f)

        // Simulating pause: we just don't call update during pause, and time passes
        currentTime = currentTime.plusSeconds(10)

        // Resumed
        accumulator.update(createMetrics(powerW = 10f), deltaTimeSec = 2f)
        currentTime = currentTime.plusSeconds(2)
        
        accumulator.stop()
        
        val summary = accumulator.toSummary()
        assertNotNull(summary)
        
        // Energy = 10W * 4s = 40J = 40 / 3600 Wh
        assertEquals((40f / 3600f), summary!!.totalEnergyWh, 0.0001f)
        
        // Duration is 12 seconds, but active operating time is only 4 seconds.
        // Wait, durationSec should be from start to stop!
        assertEquals(12L, summary.durationSec)
        
        // Avg power is calculated over operating time, which is 4s, not 12s!
        assertEquals(10f, summary.avgPowerW, 0.0001f)
    }

    private fun createMetrics(
        powerW: Float = 0f,
        chargeState: ChargeState = ChargeState.STANDBY,
        batteryPct: Int = 50,
        odometer: Double = 1000.0
    ): ForumsladerMetrics {
        return ForumsladerMetrics(
            power = ForumsladerMetrics.Power(
                batteryVoltage = 12f,
                batteryCurrent = 0f,
                consumerCurrent = 0f,
                batteryLevelPercentage = batteryPct,
                chargeState = chargeState,
                dynamoPowerWatts = powerW,
                statusMask = 0
            ),
            dynamics = ForumsladerMetrics.Dynamics(0f, 0f, 0),
            environment = ForumsladerMetrics.Environment(20f, 0f),
            energy = ForumsladerMetrics.Energy(0.0, 0.0),
            distance = ForumsladerMetrics.Distance(0.0, 0.0, 0.0, odometer)
        )
    }
}
