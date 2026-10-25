package org.happycode.karoo.forumslader.extension

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
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
import org.happycode.karoo.forumslader.domain.RideEnergySummary
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_CURRENT
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_ENERGY
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_POWER
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_AVG_POWER
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_CHARGING_TIME
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_DISCHARGING_TIME
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_NET_BATTERY
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_PEAK_POWER
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_STANDBY_TIME
import org.happycode.karoo.forumslader.extension.ForumsladerFitRecorder.Companion.FIELD_SESSION_TOTAL_ENERGY
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

    should("emit session summary fields when writeSessionSummary is called with valid summary") {
        // given
        val summary = RideEnergySummary(
            totalEnergyWh = 12.5f,
            avgPowerW = 3.5f,
            peakPowerW = 8.0f,
            chargingTimeSec = 1800L,
            dischargingTimeSec = 600L,
            standbyTimeSec = 300L,
            batteryStartPct = 70,
            batteryEndPct = 85,
            hasData = true
        )

        // when
        recorder.writeSessionSummary(summary)

        // then
        val effectSlot = slot<FitEffect>()
        verify { emitter.onNext(capture(effectSlot)) }

        val message = effectSlot.captured.shouldBeInstanceOf<WriteToSessionMesg>()
        message.values shouldBe listOf(
            FieldValue(FIELD_SESSION_TOTAL_ENERGY, 12.5),
            FieldValue(FIELD_SESSION_AVG_POWER, 3.5),
            FieldValue(FIELD_SESSION_PEAK_POWER, 8.0),
            FieldValue(FIELD_SESSION_CHARGING_TIME, 1800.0),
            FieldValue(FIELD_SESSION_DISCHARGING_TIME, 600.0),
            FieldValue(FIELD_SESSION_STANDBY_TIME, 300.0),
            FieldValue(FIELD_SESSION_NET_BATTERY, 15.0)
        )
    }

    should("omit net battery field from session summary when battery percentages are null") {
        // given
        val summary = RideEnergySummary(
            totalEnergyWh = 5.0f,
            avgPowerW = 2.5f,
            peakPowerW = 6.0f,
            chargingTimeSec = 900L,
            dischargingTimeSec = 0L,
            standbyTimeSec = 0L,
            batteryStartPct = null,
            batteryEndPct = null,
            hasData = true
        )

        // when
        recorder.writeSessionSummary(summary)

        // then
        val effectSlot = slot<FitEffect>()
        verify { emitter.onNext(capture(effectSlot)) }

        val message = effectSlot.captured.shouldBeInstanceOf<WriteToSessionMesg>()
        message.values.shouldHaveSize(6)
    }

    should("not emit session summary when summary hasData is false or fitEmitter is null") {
        // given
        val emptySummary = RideEnergySummary(
            totalEnergyWh = 0f,
            avgPowerW = 0f,
            peakPowerW = 0f,
            chargingTimeSec = 0L,
            dischargingTimeSec = 0L,
            standbyTimeSec = 0L,
            batteryStartPct = null,
            batteryEndPct = null,
            hasData = false
        )

        // when
        recorder.writeSessionSummary(emptySummary)

        // then
        verify(exactly = 0) { emitter.onNext(any()) }

        // and when fitEmitter is null
        recorder.fitEmitter = null
        recorder.writeSessionSummary(emptySummary.copy(hasData = true))
        verify(exactly = 0) { emitter.onNext(any()) }
    }
})
