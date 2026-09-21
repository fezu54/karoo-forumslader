package org.happycode.karoo.forumslader.extension

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Distance
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Dynamics
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Energy
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Environment
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics.Power
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_CURRENT
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_ENERGY
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_POWER
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SPEED
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_TEMP
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_VOLTAGE

class ForumsladerFitRecorderTest : ShouldSpec({

    lateinit var emitter: Emitter<FitEffect>
    lateinit var recorder: ForumsladerFitRecorder
    var currentTimeMs: Long

    fun createDummyMetrics() = ForumsladerMetrics(
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

    beforeEach {
        emitter = mockk(relaxed = true)
        currentTimeMs = 1000L
        recorder = ForumsladerFitRecorder(
            fitEmitter = emitter,
            timeProvider = { currentTimeMs }
        )
    }

    should("emit metrics when fitEmitter is present") {
        // given
        val metrics = createDummyMetrics()

        // when
        recorder.onMetricsReceived(metrics)

        // then
        val effectSlot = slot<FitEffect>()
        verify { emitter.onNext(capture(effectSlot)) }

        val message = effectSlot.captured.shouldBeInstanceOf<WriteToRecordMesg>()
        message.values.shouldHaveSize(6)
    }

    should("not emit metrics when fitEmitter is null") {
        // given
        recorder.fitEmitter = null
        val metrics = createDummyMetrics()

        // when
        recorder.onMetricsReceived(metrics)

        // then
        verify(exactly = 0) { emitter.onNext(any()) }
    }

    should("rate limit emissions to 1Hz") {
        // given
        val metrics = createDummyMetrics()

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

    should("emit developer fields with correct values and units") {
        // given
        val metrics = createDummyMetrics()

        // when
        recorder.onMetricsReceived(metrics)

        // then
        val effectSlot = slot<FitEffect>()
        verify { emitter.onNext(capture(effectSlot)) }

        val message = effectSlot.captured.shouldBeInstanceOf<WriteToRecordMesg>()
        message.values shouldBe listOf(
            FieldValue(FIELD_VOLTAGE, 12.5),
            FieldValue(FIELD_CURRENT, 1.0),
            FieldValue(FIELD_POWER, 5.0),
            FieldValue(FIELD_TEMP, 20.0),
            FieldValue(FIELD_SPEED, 36.0),
            FieldValue(FIELD_ENERGY, 10.0)
        )
    }
})
