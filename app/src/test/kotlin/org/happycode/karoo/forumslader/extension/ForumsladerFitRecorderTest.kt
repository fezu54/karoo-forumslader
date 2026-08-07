package org.happycode.karoo.forumslader.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Distance
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Dynamics
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Energy
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Environment
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Power
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForumsladerFitRecorderTest {

    private lateinit var karooSystem: KarooSystemService
    private lateinit var recorder: ForumsladerFitRecorder
    private lateinit var emitter: Emitter<FitEffect>

    @BeforeEach
    fun setUp() {
        karooSystem = mockk(relaxed = true)
        emitter = mockk(relaxed = true)

        recorder = ForumsladerFitRecorder(karooSystem)
        recorder.fitEmitter = emitter
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should not emit metrics when ride state is not recording`() {
        // given
        val metrics = createDummyMetrics()
        
        // when
        recorder.onMetricsReceived(metrics)

        // then
        verify(exactly = 0) { emitter.onNext(any()) }
    }

    @Test
    fun `should emit metrics when ride state is recording`() {
        // given
        recorder.rideState = RideState.Recording

        val metrics = createDummyMetrics()

        val effectSlot = slot<FitEffect>()
        every { emitter.onNext(capture(effectSlot)) } returns Unit

        // when
        recorder.onMetricsReceived(metrics)

        // then
        assertTrue(effectSlot.captured is WriteToRecordMesg)
        val message = effectSlot.captured as WriteToRecordMesg
        assertEquals(6, message.values.size)
        
        val voltageValue = message.values.find { it.developerField?.fieldName == "Battery Voltage" }
        assertEquals(12.5, voltageValue?.value)
        
        val speedValue = message.values.find { it.developerField?.fieldName == "Speed" }
        assertEquals(10.0 * 3.6, speedValue?.value) // 36.0 km/h
    }

    private fun createDummyMetrics() = ForumsladerMetrics(
        power = Power(
            batteryVoltage = 12.5f,
            batteryCurrent = 1.0f,
            consumerCurrent = 0.5f,
            dynamoPowerWatts = 5.0f,
            chargeState = ChargeState.CHARGING,
            batteryLevelPercentage = 80,
            statusMask = 0
        ),
        dynamics = Dynamics(
            speedMetersPerSecond = 10.0f,
            frequency = 50.0f,
            generatorGear = 2
        ),
        environment = Environment(
            temperatureCelsius = 20.0f,
            altitudeMeters = 100.0f
        ),
        distance = Distance(
            tripMeters = 1000.0,
            odometerMeters = 50000.0,
            dayMeters = 2000.0,
            tourMeters = 10000.0
        ),
        energy = Energy(
            tripWattHours = 10.0,
            tourWattHours = 50.0
        )
    )
}
