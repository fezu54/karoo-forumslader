package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.RideEnergyAccumulator
import org.happycode.karoo.forumslader.domain.RideEnergySummary
import org.happycode.karoo.forumslader.domain.RideHistoryGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import kotlinx.coroutines.test.runTest

class RideEnergyOrchestratorTest {

    private class FakeGateway : RideHistoryGateway {
        var savedSummary: RideEnergySummary? = null
        override suspend fun saveSummary(summary: RideEnergySummary) {
            savedSummary = summary
        }
        override suspend fun getHistory(): List<RideEnergySummary> = emptyList()
    }

    @Test
    fun `should start accumulator on recording started`() {
        val accumulator = RideEnergyAccumulator()
        val gateway = FakeGateway()
        val orchestrator = RideEnergyOrchestrator(accumulator, gateway)

        assertNull(accumulator.rideStartTime)
        
        orchestrator.onRecordingStarted()
        
        assertNotNull(accumulator.rideStartTime)
        assertNull(accumulator.rideEndTime)
    }

    @Test
    fun `should stop accumulator and save summary on ride idle`() = runTest {
        var time = Instant.ofEpochSecond(1000)
        val accumulator = RideEnergyAccumulator(clock = { time })
        val gateway = FakeGateway()
        val orchestrator = RideEnergyOrchestrator(accumulator, gateway)

        orchestrator.onRecordingStarted()
        
        time = time.plusSeconds(5)
        orchestrator.onRideIdle()
        
        assertNotNull(accumulator.rideEndTime)
        assertNotNull(gateway.savedSummary)
        assertEquals(5L, gateway.savedSummary?.durationSec)
    }

    @Test
    fun `should update metrics when recording`() {
        val accumulator = RideEnergyAccumulator()
        val gateway = FakeGateway()
        val orchestrator = RideEnergyOrchestrator(accumulator, gateway)

        orchestrator.onRecordingStarted()
        
        val metrics = ForumsladerMetrics(
            power = ForumsladerMetrics.Power(12f, 0f, 0f, 50, ChargeState.STANDBY, 10f, 0),
            dynamics = ForumsladerMetrics.Dynamics(0f, 0f, 0),
            environment = ForumsladerMetrics.Environment(20f, 0f),
            energy = ForumsladerMetrics.Energy(0.0, 0.0),
            distance = ForumsladerMetrics.Distance(0.0, 0.0, 0.0, 0.0)
        )
        
        orchestrator.onMetricsReceived(metrics, 2.0f)
        
        assertEquals(10f, accumulator.peakPowerW, 0.001f)
    }
}
