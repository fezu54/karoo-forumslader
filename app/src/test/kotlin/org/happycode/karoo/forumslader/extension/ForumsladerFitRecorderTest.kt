package org.happycode.karoo.forumslader.extension

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FitEffect
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

    private lateinit var recorder: ForumsladerFitRecorder
    private lateinit var emitter: Emitter<FitEffect>
    private var currentTimeMs: Long = 1000L

    @BeforeEach
    fun setUp() {
        emitter = mockk(relaxed = true)
        currentTimeMs = 1000L
        recorder = ForumsladerFitRecorder(
            fitEmitter = emitter,
            timeProvider = { currentTimeMs }
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should emit metrics when fitEmitter is present`() {
        // given
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

    @Test
    fun `should not emit metrics when fitEmitter is null`() {
        // given
        recorder.fitEmitter = null
        val metrics = createDummyMetrics()

        // when
        recorder.onMetricsReceived(metrics)

        // then
        verify(exactly = 0) { emitter.onNext(any()) }
    }

    @Test
    fun `should rate limit emissions to 1Hz`() {
        // given
        val metrics = createDummyMetrics()
        val effectSlot = slot<FitEffect>()
        every { emitter.onNext(capture(effectSlot)) } returns Unit

        // when - first emission at t=1000ms
        recorder.onMetricsReceived(metrics)

        // then - first emission succeeds
        verify(exactly = 1) { emitter.onNext(any()) }

        // when - second emission at t=1500ms (within 1000ms throttle interval)
        currentTimeMs = 1500L
        recorder.onMetricsReceived(metrics)

        // then - second emission is throttled
        verify(exactly = 1) { emitter.onNext(any()) }

        // when - third emission at t=2000ms (1000ms passed)
        currentTimeMs = 2000L
        recorder.onMetricsReceived(metrics)

        // then - third emission succeeds
        verify(exactly = 2) { emitter.onNext(any()) }
    }

    @Test
    fun `should emit developer fields with correct values and units`() {
        // given
        val metrics = createDummyMetrics()
        val effectSlot = slot<FitEffect>()
        every { emitter.onNext(capture(effectSlot)) } returns Unit

        // when
        recorder.onMetricsReceived(metrics)

        // then
        val message = effectSlot.captured as WriteToRecordMesg
        val voltage = message.values.first { it.developerField?.fieldDefinitionNumber == 0.toShort() }
        assertEquals("Battery Voltage", voltage.developerField?.fieldName)
        assertEquals("V", voltage.developerField?.units)
        assertEquals(12.5, voltage.value)

        val current = message.values.first { it.developerField?.fieldDefinitionNumber == 1.toShort() }
        assertEquals("Battery Current", current.developerField?.fieldName)
        assertEquals("A", current.developerField?.units)
        assertEquals(1.0, current.value)

        val power = message.values.first { it.developerField?.fieldDefinitionNumber == 2.toShort() }
        assertEquals("Dynamo Power", power.developerField?.fieldName)
        assertEquals("W", power.developerField?.units)
        assertEquals(5.0, power.value)

        val temp = message.values.first { it.developerField?.fieldDefinitionNumber == 3.toShort() }
        assertEquals("Temperature", temp.developerField?.fieldName)
        assertEquals("C", temp.developerField?.units)
        assertEquals(20.0, temp.value)

        val speed = message.values.first { it.developerField?.fieldDefinitionNumber == 4.toShort() }
        assertEquals("Speed", speed.developerField?.fieldName)
        assertEquals("km/h", speed.developerField?.units)
        assertEquals(36.0, speed.value)

        val energy = message.values.first { it.developerField?.fieldDefinitionNumber == 5.toShort() }
        assertEquals("Trip Energy", energy.developerField?.fieldName)
        assertEquals("Wh", energy.developerField?.units)
        assertEquals(10.0, energy.value)
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
